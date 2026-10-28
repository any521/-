<script setup lang="ts">
// PC 垃圾投放（拍照 + 袋数，距垃圾回收站 50m 内才可投放，工作人员审核后发分）
// + 当前位置 → 最近垃圾回收站（管理员设置）最优路线
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { submitKitchenDrop, type KitchenDropResult } from '@/api/business'
import { uploadFile } from '@/api/file'
import { pageStations, routePlan, type StationItem } from '@/api/map'
import { openAmapNavigation } from '@/utils/amapNavi'
import { wgs84ToGcj02 } from '@/utils/coord'
import {
  ensureAmap,
  createMap,
  addMarker,
  drawPolyline,
  fitBounds,
  destroyMap,
} from '@/composables/useAmap'

defineOptions({ name: 'pc-kitchen-drop' })

const router = useRouter()
// 垃圾照片（与预约回收一致：最多 9 张；首张作为工作人员审核照片）
const MAX_PHOTOS = 9
const photos = ref<{ url: string; file: File }[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const bags = ref(3)
const submitting = ref(false)
const result = ref<KitchenDropResult | null>(null)

// ---- 当前位置 → 最近垃圾回收站 最优路线（50m 硬校验） ----
// 地图默认关闭：需要时点开关或提交时自动打开（减少无效定位/地图加载）
const showStationMap = ref(false)
const mapWrapEl = ref<HTMLElement | null>(null)
const locating = ref(false)
const myPos = ref<[number, number] | null>(null)
const nearestStation = ref<StationItem | null>(null)
const nearestDist = ref<number | null>(null)
const routeInfo = ref<{ distance: number; duration: number } | null>(null)
const noStationNearby = ref(false)
let mapHandle: ReturnType<typeof createMap> | null = null

/** 投放半径硬限制：距最近垃圾回收站 ≤50m 才可投放（与后端校验一致） */
const DROP_RADIUS = 50
/** 100km 地图检索半径：超出则提示「当前 100km 内没有垃圾站点」 */
const NEARBY_RADIUS = 100_000

/** 两点球面距离（米），用于挑选最近站点 */
function haversine(a: [number, number], b: [number, number]) {
  const rad = Math.PI / 180
  const dLat = (b[1] - a[1]) * rad
  const dLng = (b[0] - a[0]) * rad
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(a[1] * rad) * Math.cos(b[1] * rad) * Math.sin(dLng / 2) ** 2
  return 2 * 6371000 * Math.asin(Math.sqrt(s))
}

function locateMe(): Promise<[number, number]> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) return reject(new Error('浏览器不支持定位'))
    navigator.geolocation.getCurrentPosition(
      // 浏览器返回 WGS-84，高德地图/接口/站点距离需 GCJ-02：定位成功即转换，否则偏差数百米且 50m 投放校验失真
      (p) => resolve(wgs84ToGcj02(p.coords.longitude, p.coords.latitude)),
      () => reject(new Error('获取定位失败')),
      { timeout: 8000 },
    )
  })
}

async function initRouteMap() {
  if (!mapWrapEl.value || mapHandle || locating.value) return
  locating.value = true
  noStationNearby.value = false
  try {
    const ok = await ensureAmap()
    const pos = await locateMe().catch(() => null)
    // 地图初始化失败（key 未生效等）不阻断站点展示：仅定位成功即可计算最近站点并显示名称，地图区域留空
    if (!pos) return
    myPos.value = pos
    // 全国站点数据量大：带坐标按 100km 半径后端过滤，仅返回附近站点；失败回退空数组
    const stations = await pageStations('KITCHEN', pos[0], pos[1], NEARBY_RADIUS / 1000)
      .catch(() => [] as StationItem[])
    // 100km 范围内筛选（与后端过滤双保险）；无站点 → 自定义空状态提示
    const nearby = stations.filter((s) => haversine(pos, [s.lng, s.lat]) <= NEARBY_RADIUS)
    if (!nearby.length) {
      noStationNearby.value = true
      return
    }
    // 距离最近的垃圾回收站
    const nearest = nearby.reduce((best, s) =>
      haversine(pos, [s.lng, s.lat]) < haversine(pos, [best.lng, best.lat]) ? s : best,
    )
    nearestStation.value = nearest
    nearestDist.value = Math.round(haversine(pos, [nearest.lng, nearest.lat]))
    // 后端路线规划（最优路线折线）；失败时直线兜底
    let polyline: [number, number][] = [pos, [nearest.lng, nearest.lat]]
    try {
      const route = await routePlan({
        originLng: pos[0],
        originLat: pos[1],
        destLng: nearest.lng,
        destLat: nearest.lat,
        mode: 'walking',
      })
      if (route.polyline?.length) {
        polyline = route.polyline
        routeInfo.value = { distance: route.distance, duration: route.duration }
      }
    } catch {
      /* 路线规划失败：直线兜底 */
    }

    // 地图绘制仅在 AMap 可用时执行（不可用则仅展示站点信息与路线距离）
    if (ok) {
      mapHandle = createMap(mapWrapEl.value, pos, 15)
      // marker 常显文字标签：站点名称/「我的位置」直接标注在地图上，title 仅作点击 tooltip
      addMarker(mapHandle, pos, {
        title: '我的位置',
        label: { content: '我的位置', direction: 'top' },
      })
      addMarker(mapHandle, [nearest.lng, nearest.lat], {
        title: nearest.name,
        label: {
          content: nearest.name.length > 10 ? `${nearest.name.slice(0, 10)}…` : nearest.name,
          direction: 'top',
        },
      })
      // 最优路线折线（后端步行规划，失败已退化为直线）；独立 try-catch 防止绘制异常中断整段渲染
      try {
        drawPolyline(mapHandle, polyline, {
          strokeColor: '#3b95fc',
          strokeWeight: 5,
          strokeStyle: 'dashed',
        })
      } catch {
        /* 线绘制失败不影响点位与视野 */
      }
      fitBounds(mapHandle, [pos, [nearest.lng, nearest.lat]])
    }
  } catch {
    /* 地图/定位失败：静默降级，提交时再拦截 */
  } finally {
    locating.value = false
  }
}

/** 定位成功且距最近垃圾回收站 ≤50m 才可投放 */
const dropAllowed = computed(() =>
  myPos.value !== null && nearestDist.value !== null && nearestDist.value <= DROP_RADIUS,
)

onMounted(() => {
  if (showStationMap.value) initRouteMap()
})

watch(showStationMap, (v) => {
  if (v) {
    nextTick(() => initRouteMap())
  } else {
    if (mapHandle && mapWrapEl.value) destroyMap(mapWrapEl.value)
    mapHandle = null
    nearestStation.value = null
    routeInfo.value = null
    noStationNearby.value = false
  }
})

onUnmounted(() => {
  if (mapWrapEl.value) destroyMap(mapWrapEl.value)
})

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  if (files.length === 0) return
  const room = MAX_PHOTOS - photos.value.length
  if (files.length > room) showToast(`最多上传 ${MAX_PHOTOS} 张照片`)
  files.slice(0, room).forEach((file) => {
    const reader = new FileReader()
    reader.onload = () => photos.value.push({ url: reader.result as string, file })
    reader.readAsDataURL(file)
  })
  target.value = ''
}

const triggerFileInput = () => fileInput.value?.click()

function removePhoto(index: number) {
  photos.value.splice(index, 1)
}

async function onSubmit() {
  if (!myPos.value) {
    showToast('请先开启定位，在垃圾回收站 50 米内才能投放')
    if (!showStationMap.value) {
      showStationMap.value = true
    } else {
      initRouteMap()
    }
    return
  }
  if (!dropAllowed.value) {
    showToast(`距最近垃圾回收站 ${nearestDist.value ?? 0} 米，需在 50 米内才能投放`)
    return
  }
  const first = photos.value[0]
  if (!first) {
    showToast('请先上传垃圾照片')
    return
  }
  submitting.value = true
  try {
    // 首张照片上传服务器取 URL（后端 photo_url 为短链接字段，不存 base64）
    const up = await uploadFile(first.file, 'kitchen', `drop-${Date.now()}.jpg`)
    result.value = await submitKitchenDrop({
      bags: bags.value,
      photoBase64: up.url,
      lng: myPos.value[0],
      lat: myPos.value[1],
    })
    showSuccessToast('投放上报成功，待工作人员审核')
  } catch {
    showToast('照片上传或提交失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pc-kitchen-drop">
    <div class="container">
      <h1 class="page-title">垃圾投放</h1>
      <p class="page-desc">距垃圾回收站 50 米内拍照上报，工作人员审核后发放积分</p>

      <!-- 当前位置 → 最近垃圾回收站（50m 内才可投放）最优路线 -->
      <div class="map-card">
        <div class="map-title">
          <SvgIcon name="nav-location-pin" :size="20" />
          最近垃圾回收站
        </div>
        <div class="switch-row">
          <span>寻找最近站点并显示最优路线</span>
          <van-switch v-model="showStationMap" />
        </div>
        <template v-if="showStationMap">
          <div class="map-wrap">
            <div ref="mapWrapEl" class="map-box" />
            <div v-if="locating" class="map-mask">定位并规划最优路线中…</div>
            <div v-else-if="noStationNearby" class="station-empty">
              <div class="station-empty__icon">
                <SvgIcon name="map" :size="36" />
              </div>
              <p class="station-empty__title">当前 100km 内没有垃圾回收站</p>
              <p class="station-empty__desc">需在垃圾回收站 50 米内才能投放，请联系管理员增设站点</p>
            </div>
            <div v-else-if="!nearestStation" class="map-mask">
              定位失败或地图未配置，请允许定位后重试
            </div>
          </div>
          <div v-if="nearestStation" class="station-info">
            <strong>{{ nearestStation.name }}</strong>
            <span
              v-if="nearestDist !== null"
              class="station-dist"
              :class="{ 'station-dist--ok': dropAllowed, 'station-dist--far': !dropAllowed }"
            >
              距我 {{ nearestDist }} 米{{ dropAllowed ? ' · 50 米内可投放' : ' · 需在 50 米内才能投放' }}
            </span>
            <span v-if="routeInfo" class="station-route">
              最优路线：步行约 {{ routeInfo.duration }} 分钟 · {{ (routeInfo.distance / 1000).toFixed(1) }} km
            </span>
            <button type="button" class="station-nav" @click="openAmapNavigation(nearestStation.lng, nearestStation.lat, nearestStation.name)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <polygon points="3 11 22 2 13 21 11 13 3 11" />
              </svg>
              前往导航
            </button>
          </div>
        </template>
      </div>

      <div class="form-card">
        <div class="form-field">
          <label><SvgIcon name="ai-camera" :size="20" /> 垃圾照片（最多 9 张）</label>
          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            style="display: none"
            @change="onFileChange"
          />
          <div v-if="photos.length === 0" class="photo-upload" @click="triggerFileInput">
            <SvgIcon name="ai-camera" :size="48" color="var(--df-text-secondary)" />
            <p>点击上传垃圾照片（最多 9 张，首张作为审核照片）</p>
          </div>
          <div v-else class="photos-grid">
            <div v-for="(p, i) in photos" :key="i" class="photo-item">
              <img :src="p.url" class="photo-preview" />
              <button class="photo-remove" type="button" title="移除" @click="removePhoto(i)">×</button>
            </div>
            <div v-if="photos.length < MAX_PHOTOS" class="photo-add" @click="triggerFileInput">
              <SvgIcon name="ai-camera" :size="28" color="var(--df-text-secondary)" />
              <span>继续添加</span>
            </div>
          </div>
        </div>

        <div class="form-field">
          <label><SvgIcon name="recycle-scale" :size="20" /> 袋数</label>
          <div class="bag-selector">
            <button :disabled="bags <= 1" @click="bags--">−</button>
            <span>{{ bags }} 袋</span>
            <button :disabled="bags >= 10" @click="bags++">+</button>
          </div>
        </div>

        <div v-if="result" class="result-box warn">
          <div class="result-title">审核结果：{{ result.auditResult }}</div>
          <p class="result-desc">{{ result.reason }}</p>
          <div class="result-points">审核通过后每袋 +2 积分</div>
        </div>

        <button v-if="result" class="records-btn" @click="router.push('/pc/kitchen/records')">查看我的投放记录</button>

        <button class="submit-btn" :disabled="submitting || !dropAllowed" @click="onSubmit">
          {{ submitting ? '提交中…' : dropAllowed ? '提交上报' : '距垃圾回收站需在 50 米内' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-kitchen-drop {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container { max-width: 700px; margin: 0 auto; }

.page-title {
  font-size: 48px; font-weight: 700; color: var(--df-text-primary);
  margin-bottom: 16px; text-align: center;
}

.page-desc {
  font-size: 18px; color: var(--df-text-regular);
  margin-bottom: 48px; text-align: center;
}

.map-card {
  background: var(--df-bg-card); border-radius: 16px; padding: 32px;
  box-shadow: var(--df-shadow-card); margin-bottom: 32px;
}

.map-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 18px; font-weight: 600; color: var(--df-text-primary); margin-bottom: 16px;
}

.switch-row {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px; font-size: 14px; color: var(--df-text-regular);
}

.map-wrap { position: relative; }

.map-box {
  height: 280px; border-radius: 12px; overflow: hidden; background: var(--df-bg-page, #f5f6f7);
}

.map-mask {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  border-radius: 12px; background: rgba(127, 127, 127, 0.12);
  color: var(--df-text-secondary); font-size: 14px;
}

.station-empty {
  position: absolute; inset: 0; z-index: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  padding: 24px; text-align: center;
  border-radius: 12px;
  background: linear-gradient(160deg, color-mix(in srgb, var(--df-color-secondary) 7%, transparent) 0%, color-mix(in srgb, var(--df-color-primary) 5%, transparent) 100%);
  border: 2px dashed color-mix(in srgb, var(--df-color-secondary) 35%, transparent);
  backdrop-filter: blur(2px);

  &__icon {
    display: flex; align-items: center; justify-content: center;
    width: 64px; height: 64px; border-radius: 50%;
    color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 12%, transparent);
    border: 1px solid color-mix(in srgb, var(--df-color-secondary) 35%, transparent);
    animation: station-empty-bounce 2s ease-in-out infinite;
  }

  &__title {
    margin: 0; font-size: 17px; font-weight: 700; color: var(--df-text-primary);
  }

  &__desc {
    margin: 0; font-size: 13px; line-height: 1.6; color: var(--df-text-secondary);
    max-width: 320px;
  }
}

@keyframes station-empty-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.station-info {
  display: flex; flex-direction: column; gap: 4px; margin-top: 12px;
  font-size: 14px; color: var(--df-text-regular);
}

.station-dist {
  font-weight: 600;
  &--ok { color: var(--df-color-primary); }
  &--far { color: var(--df-color-warning); }
}

.station-route { color: var(--df-color-primary); font-weight: 600; }

.station-nav {
  display: inline-flex; align-items: center; gap: 6px;
  align-self: flex-start;
  margin-top: 8px; padding: 8px 18px;
  border: none; border-radius: 20px;
  background: var(--df-color-primary);
  color: #fff; font-size: 14px; font-weight: 600;
  cursor: pointer; transition: all 0.3s ease;
  svg { width: 14px; height: 14px; }
  &:hover { transform: translateY(-1px); box-shadow: 0 4px 12px color-mix(in srgb, var(--df-color-secondary) 35%, transparent); }
}

.form-card {
  background: var(--df-bg-card); border-radius: 16px; padding: 40px;
  box-shadow: var(--df-shadow-card);
}

.form-field {
  margin-bottom: 32px;
  label { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: var(--df-text-primary); margin-bottom: 16px; }
}

.photo-upload {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; border: 3px dashed var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent); }
  p { margin: 12px 0 0; color: var(--df-text-secondary); font-size: 14px; }
}

.photo-preview { max-height: 200px; border-radius: 8px; }

.photos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 16px;
}

.photo-item {
  position: relative;

  .photo-preview {
    width: 100%;
    height: 130px;
    object-fit: cover;
    border-radius: 12px;
  }
}

.photo-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 15px;
  line-height: 1;
  cursor: pointer;

  &:hover {
    background: rgba(0, 0, 0, 0.8);
  }
}

.photo-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 130px;
  border: 2px dashed var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  color: var(--df-text-secondary);
  font-size: 13px;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }
}

.bag-selector {
  display: flex; align-items: center; gap: 20px;
  button {
    width: 44px; height: 44px; font-size: 22px; font-weight: 600;
    color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    border: 2px solid var(--df-color-primary); border-radius: 50%; cursor: pointer;
    transition: all 0.3s ease;
    &:disabled { opacity: 0.4; cursor: not-allowed; }
    &:hover:not(:disabled) { background: var(--df-color-primary); color: white; }
  }
  span { font-size: 20px; font-weight: 600; color: var(--df-text-primary); min-width: 60px; text-align: center; }
}

.result-box {
  padding: 20px; border-radius: 12px; margin-bottom: 24px;
  background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
  &.warn { background: color-mix(in srgb, var(--df-color-gold) 10%, transparent); }
}
.result-title { font-size: 16px; font-weight: 600; color: var(--df-text-primary); }
.result-desc { margin: 8px 0 0; color: var(--df-text-regular); font-size: 14px; }
.result-points { margin-top: 8px; color: var(--df-color-gold); font-weight: 600; font-size: 18px; }

.submit-btn {
  width: 100%; padding: 16px; font-size: 18px; font-weight: 600; color: white;
  background: linear-gradient(135deg, var(--df-color-primary) 0%, var(--df-color-primary-dark) 100%);
  border: none; border-radius: 12px; cursor: pointer; transition: all 0.3s ease;
  &:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 20px color-mix(in srgb, var(--df-color-secondary) 40%, transparent); }
  &:disabled { opacity: 0.7; cursor: not-allowed; }
}

.records-btn {
  width: 100%; padding: 14px; margin-bottom: 16px; font-size: 16px; font-weight: 600;
  color: var(--df-color-primary); background: transparent;
  border: 2px solid var(--df-color-primary); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent); }
}
</style>
