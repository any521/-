<script setup lang="ts">
// 垃圾投放：拍照上传 + 袋数 + 投放时段提示
// + 当前位置 → 最近垃圾回收站（50m 内才可投放，工作人员审核后发分）最优路线地图
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { submitKitchenDrop, type KitchenDropResult } from '@/api/business'
import { uploadFile } from '@/api/file'
import { isKitchenDropOpen, KITCHEN_DROP_HOURS } from '@shared/data/baseData'
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

const router = useRouter()

const photos = ref<UploaderFileListItem[]>([])
const bags = ref(1)
const submitting = ref(false)
const result = ref<KitchenDropResult | null>(null)

const dropOpen = computed(() => isKitchenDropOpen())

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
  const photo = photos.value[0]
  if (!photo?.content) {
    showToast('请先拍摄投放照片')
    return
  }
  submitting.value = true
  try {
    // 照片先上传服务器取 URL（后端 photo_url 为短链接字段，不存 base64）
    let blob: File | Blob | null = photo.file ?? null
    if (!blob) blob = await (await fetch(String(photo.content))).blob()
    const up = await uploadFile(blob, 'kitchen', `drop-${Date.now()}.jpg`)
    result.value = await submitKitchenDrop({
      bags: bags.value,
      photoBase64: up.url,
      lng: myPos.value[0],
      lat: myPos.value[1],
    })
  } catch {
    showToast('照片上传或提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

function reset() {
  result.value = null
  photos.value = []
  bags.value = 1
}
</script>

<template>
  <div class="page drop">
    <van-nav-bar title="垃圾投放" left-arrow @click-left="router.back()" />

    <!-- 时段提示（业务规则：每晚 20-23 点） -->
    <van-notice-bar
      :color="dropOpen ? '#2f6b5e' : '#f29d28'"
      :background="dropOpen ? '#dff0ea' : '#fdf6e9'"
      left-icon="clock-o"
      :text="
        dropOpen
          ? '投放时段开放中（' + KITCHEN_DROP_HOURS.start + ':00-' + KITCHEN_DROP_HOURS.end + ':00），距垃圾回收站 50 米内拍照上报，工作人员审核后发放积分'
          : '当前不在投放时段（每晚 ' + KITCHEN_DROP_HOURS.start + ':00-' + KITCHEN_DROP_HOURS.end + ':00），仍可提交但需在垃圾回收站 50 米内'
      "
    />

    <!-- 当前位置 → 最近垃圾回收站（50m 内才可投放）最优路线 -->
    <div class="card">
      <div class="card__title"><SvgIcon name="nav-location-pin" /> 最近垃圾回收站</div>
      <div class="drop__switch-row">
        <span>寻找最近站点并显示最优路线</span>
        <van-switch v-model="showStationMap" size="20px" />
      </div>
      <template v-if="showStationMap">
        <div class="drop__map-wrap">
          <div ref="mapWrapEl" class="drop__map" />
          <div v-if="locating" class="drop__map-mask">定位并规划最优路线中…</div>
          <div v-else-if="noStationNearby" class="drop__station-empty">
            <div class="drop__station-empty-icon">
              <SvgIcon name="map" :size="30" />
            </div>
            <p class="drop__station-empty-title">当前 100km 内没有垃圾回收站</p>
            <p class="drop__station-empty-desc">需在垃圾回收站 50 米内才能投放，请联系管理员增设站点</p>
          </div>
          <div v-else-if="!nearestStation" class="drop__map-mask">
            定位失败或地图未配置，请允许定位后重试
          </div>
        </div>
        <div v-if="nearestStation" class="drop__station">
          <strong>{{ nearestStation.name }}</strong>
          <span
            v-if="nearestDist !== null"
            class="drop__station-dist"
            :class="{ 'drop__station-dist--ok': dropAllowed, 'drop__station-dist--far': !dropAllowed }"
          >
            距我 {{ nearestDist }} 米{{ dropAllowed ? ' · 50 米内可投放' : ' · 需在 50 米内才能投放' }}
          </span>
          <span v-if="routeInfo" class="drop__station-route">
            最优路线：步行约 {{ routeInfo.duration }} 分钟 · {{ (routeInfo.distance / 1000).toFixed(1) }} km
          </span>
          <button type="button" class="drop__nav-btn" @click="openAmapNavigation(nearestStation.lng, nearestStation.lat, nearestStation.name)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <polygon points="3 11 22 2 13 21 11 13 3 11" />
            </svg>
            前往导航
          </button>
        </div>
      </template>
    </div>

    <!-- 审核结果 -->
    <template v-if="result">
      <div class="card drop__result">
        <div class="drop__result-icon">
          <SvgIcon name="nav-hourglass" size="48" />
        </div>
        <div class="drop__result-title">{{ result.auditResult }}</div>
        <div class="drop__result-reason">{{ result.reason }}</div>
        <div class="drop__result-points">审核通过后每袋 +2 积分</div>
      </div>
      <van-button round block type="primary" @click="reset">再投一单</van-button>
      <van-button round block plain type="primary" class="drop__back" @click="router.push('/carbon/bill')">
        查看低碳账单
      </van-button>
      <van-button round block plain type="primary" class="drop__back" @click="router.push('/kitchen/records')">
        查看我的投放记录
      </van-button>
    </template>

    <!-- 上报表单 -->
    <template v-else>
      <div class="card">
        <div class="card__title"><SvgIcon name="ai-camera" /> 破袋投放照片（工作人员审核，最多 9 张）</div>
        <van-uploader v-model="photos" :max-count="9" capture="camera" multiple reupload />
        <p class="drop__tip">拍摄要求：垃圾已破袋、桶内可见；首张作为审核照片，工作人员核验后发放积分</p>
      </div>

      <div class="card">
        <div class="card__title"><SvgIcon name="carbon-shopping-bag" /> 投放袋数</div>
        <van-stepper v-model="bags" :min="1" :max="9" integer button-size="28" />
        <span class="drop__bags-tip">每袋 +2 积分（审核通过后发放）</span>
      </div>

      <van-button
        round
        block
        type="primary"
        :loading="submitting"
        :disabled="!dropAllowed"
        loading-text="提交中…"
        @click="onSubmit"
      >
        {{ dropAllowed ? '提交投放上报' : '距垃圾回收站需在 50 米内' }}
      </van-button>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.drop {
  padding-top: 0;

  &__switch-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $df-space-sm;
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
  }

  &__map-wrap {
    position: relative;
  }

  &__map {
    height: 220px;
    border-radius: $df-radius-lg;
    overflow: hidden;
    background: var(--df-bg-card);
  }

  &__map-mask {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: $df-radius-lg;
    background: rgba(127, 127, 127, 0.12);
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__station-empty {
    position: absolute;
    inset: 0;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 20px 24px;
    text-align: center;
    border-radius: $df-radius-lg;
    background: linear-gradient(160deg, color-mix(in srgb, var(--df-color-secondary) 7%, transparent) 0%, color-mix(in srgb, var(--df-color-primary) 5%, transparent) 100%);
    border: 2px dashed color-mix(in srgb, var(--df-color-secondary) 35%, transparent);
    backdrop-filter: blur(2px);
  }

  &__station-empty-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 12%, transparent);
    border: 1px solid color-mix(in srgb, var(--df-color-secondary) 35%, transparent);
    animation: station-empty-bounce 2s ease-in-out infinite;
  }

  &__station-empty-title {
    margin: 0;
    font-size: $df-font-size-md;
    font-weight: 700;
    color: var(--df-text-primary);
  }

  &__station-empty-desc {
    margin: 0;
    font-size: $df-font-size-xs;
    line-height: 1.6;
    color: var(--df-text-secondary);
  }

  &__station {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-top: $df-space-sm;
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
  }

  &__station-dist {
    font-weight: 600;

    &--ok {
      color: var(--df-color-primary);
    }

    &--far {
      color: var(--df-color-warning);
    }
  }

  &__station-route {
    color: var(--df-color-primary);
    font-weight: 600;
  }

  &__nav-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    align-self: flex-start;
    margin-top: $df-space-sm;
    padding: $df-space-xs $df-space-lg;
    border: none;
    border-radius: $df-radius-round;
    background: var(--df-color-primary);
    color: #fff;
    font-size: $df-font-size-sm;
    font-weight: 600;
    cursor: pointer;

    svg {
      width: 14px;
      height: 14px;
    }

    &:active {
      opacity: 0.85;
    }
  }

  &__tip {
    margin: $df-space-sm 0 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__bags-tip {
    margin-left: $df-space-md;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__result {
    text-align: center;
    padding: $df-space-xl;
  }

  &__result-icon {
    font-size: 48px;
  }

  &__result-title {
    margin-top: $df-space-sm;
    font-size: $df-font-size-lg;
    font-weight: 600;
  }

  &__result-reason {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__result-points {
    margin-top: $df-space-md;
    color: var(--df-color-gold);
    font-weight: 600;
  }

  &__back {
    margin-top: $df-space-md;
  }
}

@keyframes station-empty-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.van-notice-bar {
  margin-bottom: $df-space-md;
  border-radius: $df-radius-lg;
}
</style>
