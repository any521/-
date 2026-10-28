<script setup lang="ts">
// ============================================================
// PC 回收站点地图（onsite04，G4）——与 mobile 版同逻辑，桌面布局
// - 定位当前城市：仅展示当前位置 100km 内的全部站点（地图标记 + 我的位置）
// - 类型筛选 + 点击 Marker 弹详情（地址/营业时间/高德导航快捷按钮）
// - 降级：定位失败 → 展示全部站点；地图未配置 → 列表兜底
// 契约：onsite_overview.md C-O4
// ============================================================
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { pageStations, type StationItem } from '@/api/map'
import { openAmapNavigation } from '@/utils/amapNavi'
import { wgs84ToGcj02 } from '@/utils/coord'
import {
  createMap, destroyMap, ensureAmap, mapReady, addMarker, fitBounds,
} from '@/composables/useAmap'

defineOptions({ name: 'pc-map-stations' })

const tabs = [
  { name: '', label: '全部' },
  { name: 'RECYCLE', label: '回收站' },
  { name: 'KITCHEN', label: '厨余站' },
  { name: 'COLLECT', label: '集散点' },
]
const activeType = ref('')
const stations = ref<StationItem[]>([])
const selected = ref<StationItem | null>(null)
const loading = ref(false)

/** 当前位置（定位成功才有值）；100km 检索半径：仅展示周边 100km 内站点 */
const NEARBY_RADIUS = 100_000
const myPos = ref<[number, number] | null>(null)
const locating = ref(false)

const mapEl = ref<HTMLDivElement | null>(null)
let mapHandle: ReturnType<typeof createMap> | null = null
let markers: unknown[] = []

/** 类型色标（与站点 Marker 圆点一致） */
const TYPE_COLOR: Record<StationItem['stationType'], string> = {
  RECYCLE: '#2f6b5e',
  KITCHEN: '#f29d28',
  COLLECT: '#3d8b85',
}

const TYPE_TEXT: Record<StationItem['stationType'], string> = {
  RECYCLE: '回收站',
  KITCHEN: '厨余站',
  COLLECT: '集散点',
}

/** 站点名转义（Marker label 以 HTML 注入，防止名称含特殊字符破坏结构） */
function escapeHtml(v: string) {
  return v.replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' } as Record<string, string>
  )[c] ?? c)
}

/** 两点球面距离（米） */
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
      // 浏览器返回 WGS-84，高德地图/接口需 GCJ-02：定位成功即转换，后续渲染/距离/检索统一坐标系
      (p) => resolve(wgs84ToGcj02(p.coords.longitude, p.coords.latitude)),
      () => reject(new Error('获取定位失败')),
      { timeout: 8000 },
    )
  })
}

/** 当前筛选结果：定位成功 → 仅 100km 内站点；定位失败 → 全部站点 */
const filtered = () => {
  const base = stations.value.filter((s) => !activeType.value || s.stationType === activeType.value)
  if (!myPos.value) return base
  return base.filter((s) => haversine(myPos.value!, [s.lng, s.lat]) <= NEARBY_RADIUS)
}

/** 清空并重绘当前筛选的站点 Marker（含我的位置定位点） */
function renderStations() {
  if (!mapHandle) return
  const { map } = mapHandle
  markers.forEach((m) => (map as { remove: (o: unknown) => void }).remove(m))
  markers = []
  const list = filtered()
  // 我的位置：蓝色定位点
  if (myPos.value) {
    const m = addMarker(mapHandle!, myPos.value, {
      title: '我的位置',
      content: `<div style="width:14px;height:14px;border-radius:50%;background:#3b95fc;border:3px solid #fff;box-shadow:0 1px 6px rgba(59,149,252,.6)"></div>`,
      label: {
        content: `<div style="white-space:nowrap;padding:2px 8px;font-size:12px;color:#fff;background:#3b95fc;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.3)">我的位置</div>`,
        direction: 'top',
      },
    })
    markers.push(m)
  }
  // 全量降级（定位失败）时 marker 数量保护：仅渲染前 300 个，避免 5600+ 全国站点卡死地图
  list.slice(0, 300).forEach((s) => {
    const m = addMarker(mapHandle!, [s.lng, s.lat], {
      title: s.name,
      content: `<div style="width:16px;height:16px;border-radius:50%;background:${TYPE_COLOR[s.stationType]};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.3)"></div>`,
      // 站点名称常显（超长省略，避免挤压地图）
      label: {
        content: `<div style="max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:2px 8px;font-size:12px;color:#fff;background:${TYPE_COLOR[s.stationType]};border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.3)">${escapeHtml(s.name)}</div>`,
        direction: 'top',
      },
    })
    ;(m as { on: (ev: string, cb: () => void) => void }).on('click', () => {
      selected.value = s
    })
    markers.push(m)
  })
  const bounds: [number, number][] = list.map((s) => [s.lng, s.lat] as [number, number])
  if (myPos.value) bounds.unshift(myPos.value)
  if (bounds.length) {
    if (bounds.length === 1 && myPos.value) {
      // 仅我的位置（100km 内无站点）：直接定中心，避免单点过度放大
      ;(map as { setZoomAndCenter: (z: number, c: unknown) => void }).setZoomAndCenter(10, myPos.value)
    } else {
      fitBounds(mapHandle, bounds, 12)
    }
  }
}

function onTypeChange() {
  renderStations()
}

onMounted(async () => {
  loading.value = true
  // 先定位：成功 → 仅拉周边 100km 站点（全国数据 5600+ 条，必须带坐标检索）；失败 → 全量降级
  const pos = await locateMe().catch(() => null)
  myPos.value = pos
  try {
    stations.value =
      (await pageStations(undefined, pos?.[0], pos?.[1], pos ? NEARBY_RADIUS / 1000 : undefined)) ?? []
  } catch {
    // 5001/网络错误：拦截器已 Toast
  } finally {
    loading.value = false
  }

  const ok = await ensureAmap()
  if (!ok) return
  await nextTick()
  if (!mapEl.value) return
  mapHandle = createMap(mapEl.value, [120.1536, 30.2875], 11)
  renderStations()
})

onUnmounted(() => {
  if (mapEl.value) {
    destroyMap(mapEl.value)
    mapHandle = null
    markers = []
  }
})
</script>

<template>
  <div class="pc-stations">
    <div class="container">
      <h1 class="page-title">回收站点</h1>

      <div class="stations-card">
        <!-- 定位状态 + 类型筛选 -->
        <div class="stations-bar">
          <span v-if="locating" class="stations-bar__hint">正在定位，仅显示当前位置 100km 内的站点…</span>
          <span v-else-if="myPos" class="stations-bar__hint">
            当前位置 100km 内共 {{ filtered().length }} 个站点
          </span>
          <span v-else class="stations-bar__hint stations-bar__hint--fallback">
            定位失败，展示全部站点
          </span>
          <div class="stations-tabs">
            <button
              v-for="t in tabs"
              :key="t.name"
              class="stations-tabs__btn"
              :class="{ active: activeType === t.name }"
              @click="activeType = t.name; onTypeChange()"
            >
              {{ t.label }}
            </button>
          </div>
        </div>

        <!-- 地图 + 详情 -->
        <div class="stations-body">
          <div v-if="mapReady" ref="mapEl" class="stations-map" />

          <!-- 降级：地图未配置 → 列表兜底 -->
          <div v-else class="stations-map stations-map--fallback">
            <van-empty v-if="loading" description="加载中…" />
            <van-empty v-else description="地图未配置，以下为站点列表" />
            <div class="stations-fallback-list">
              <div v-for="s in stations.slice(0, 100)" :key="s.id" class="stations-fallback-item">
                <span class="stations-fallback-dot" :style="{ background: TYPE_COLOR[s.stationType] }" />
                <div class="stations-fallback-info">
                  <div class="stations-fallback-name">{{ s.name }} · {{ TYPE_TEXT[s.stationType] }}</div>
                  <div class="stations-fallback-meta">
                    {{ s.address }}<template v-if="s.openHours"> · {{ s.openHours }}</template>
                  </div>
                </div>
                <button class="stations-fallback-nav" type="button" @click="openAmapNavigation(s.lng, s.lat, s.name)">
                  导航
                </button>
              </div>
            </div>
          </div>

          <!-- 选中站点详情 -->
          <aside v-if="selected" class="stations-side">
            <div class="stations-side__name">{{ selected.name }}</div>
            <div class="stations-side__tag" :style="{ color: TYPE_COLOR[selected.stationType] }">
              {{ TYPE_TEXT[selected.stationType] }}
            </div>
            <div class="stations-side__row">
              <span>地址</span>
              <span>{{ selected.address }}</span>
            </div>
            <div class="stations-side__row">
              <span>营业时间</span>
              <span>{{ selected.openHours || '—' }}</span>
            </div>
            <button class="stations-side__nav" type="button" @click="openAmapNavigation(selected.lng, selected.lat, selected.name)">
              <SvgIcon name="nav-location-pin" :size="14" />
              高德导航前往
            </button>
          </aside>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.pc-stations {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 1000px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 48px;
  text-align: center;
}

.stations-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-card);
}

// 定位状态 + 类型筛选
.stations-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 24px;

  &__hint {
    font-size: 13px;
    color: var(--df-text-secondary);

    &--fallback {
      color: var(--df-color-warning);
    }
  }
}

.stations-tabs {
  display: flex;
  gap: 8px;

  &__btn {
    padding: 8px 20px;
    border-radius: 999px;
    border: 1px solid var(--df-border-color);
    background: transparent;
    color: var(--df-text-secondary);
    font-size: 14px;
    cursor: pointer;

    &.active {
      background: var(--df-color-primary);
      border-color: var(--df-color-primary);
      color: #fff;
      font-weight: 600;
    }
  }
}

.stations-body {
  display: grid;
  grid-template-columns: 1fr 260px;
  gap: 24px;
}

.stations-map {
  height: 560px;
  border-radius: 12px;
  overflow: hidden;

  &--fallback {
    overflow: auto;
    border: 1px dashed var(--df-border-color);
  }
}

.stations-fallback-list {
  padding: 0 16px 16px;
}

.stations-fallback-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--df-border-color);

  &:last-child {
    border-bottom: none;
  }
}

.stations-fallback-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.stations-fallback-info {
  flex: 1;
  min-width: 0;
}

.stations-fallback-name {
  font-size: 14px;
  font-weight: 500;
}

.stations-fallback-meta {
  font-size: 12px;
  color: var(--df-text-secondary);
}

.stations-fallback-nav {
  color: var(--df-color-primary);
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
}

.stations-side {
  padding: 20px;
  background: transparent;
  border-radius: 12px;
  align-self: start;

  &__name {
    font-size: 16px;
    font-weight: 600;
  }

  &__tag {
    display: inline-block;
    margin: 8px 0 12px;
    font-size: 13px;
    font-weight: 600;
  }

  &__row {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 8px 0;
    font-size: 14px;

    span:first-child {
      color: var(--df-text-secondary);
      flex-shrink: 0;
    }

    span:last-child {
      text-align: right;
    }
  }

  &__nav {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    width: 100%;
    margin-top: 16px;
    padding: 10px 0;
    text-align: center;
    color: var(--df-color-primary);
    font-weight: 600;
    background: transparent;
    border: 1px solid var(--df-color-primary);
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
    }
  }
}
</style>
