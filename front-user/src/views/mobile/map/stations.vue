<script setup lang="ts">
// ============================================================
// 回收站点地图（onsite04，G4）：
// - 定位当前城市：仅展示当前位置 100km 内的全部站点（地图标记 + 我的位置）
// - 底部类型筛选 + 点击 Marker 弹详情卡（地址/营业时间/高德导航快捷按钮）
// - 降级：定位失败 → 展示全部站点；mapReady=false → 提示文案 + 列表兜底
// 契约：onsite_overview.md C-O4
// ============================================================
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pageStations, type StationItem } from '@/api/map'
import { openAmapNavigation } from '@/utils/amapNavi'
import { wgs84ToGcj02 } from '@/utils/coord'
import {
  createMap, destroyMap, ensureAmap, mapReady, addMarker, fitBounds,
} from '@/composables/useAmap'

defineOptions({ name: 'map-stations' })

const router = useRouter()

const tabs = [
  { name: '', label: '全部' },
  { name: 'RECYCLE', label: '回收站' },
  { name: 'KITCHEN', label: '厨余站' },
  { name: 'COLLECT', label: '集散点' },
]
const activeType = ref('')
const stations = ref<StationItem[]>([])
const selected = ref<StationItem | null>(null)
const showDetail = ref(false)
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
      content: `<div style="width:14px;height:14px;border-radius:50%;background:${TYPE_COLOR[s.stationType]};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.3)"></div>`,
      // 站点名称常显（超长省略，避免挤压地图）
      label: {
        content: `<div style="max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:2px 8px;font-size:12px;color:#fff;background:${TYPE_COLOR[s.stationType]};border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.3)">${escapeHtml(s.name)}</div>`,
        direction: 'top',
      },
    })
    ;(m as { on: (ev: string, cb: () => void) => void }).on('click', () => {
      selected.value = s
      showDetail.value = true
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

async function onTypeChange() {
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
  <div class="stations">
    <van-nav-bar title="回收站点" left-arrow @click-left="router.back()" />

    <template v-if="mapReady">
      <!-- 全屏地图 -->
      <div ref="mapEl" class="stations__map" />

      <!-- 定位状态 -->
      <div class="stations__status">
        <span v-if="locating">正在定位，仅显示当前位置 100km 内的站点…</span>
        <span v-else-if="myPos">当前位置 100km 内共 {{ filtered().length }} 个站点</span>
        <span v-else class="stations__status--fallback">定位失败，展示全部站点</span>
      </div>

      <!-- 类型筛选 -->
      <div class="stations__tabs">
        <button
          v-for="t in tabs"
          :key="t.name"
          class="stations__tab"
          :class="{ active: activeType === t.name }"
          @click="activeType = t.name; onTypeChange()"
        >
          {{ t.label }}
        </button>
      </div>
    </template>

    <!-- 降级：地图未配置 → 列表兜底 -->
    <div v-else class="stations__fallback">
      <van-empty :description="loading ? '加载中…' : '地图未配置，以下为站点列表'" />
      <div class="card stations__list">
        <div v-for="s in stations.slice(0, 100)" :key="s.id" class="stations__item">
          <span class="stations__dot" :style="{ background: TYPE_COLOR[s.stationType] }" />
          <div class="stations__info">
            <div class="stations__name">{{ s.name }}</div>
            <div class="stations__meta">{{ s.address }}<template v-if="s.openHours"> · {{ s.openHours }}</template></div>
          </div>
          <button class="stations__nav" type="button" @click="openAmapNavigation(s.lng, s.lat, s.name)">
            导航
          </button>
        </div>
      </div>
    </div>

    <!-- 站点详情卡 -->
    <van-popup v-model:show="showDetail" position="bottom" round class="stations__popup">
      <template v-if="selected">
        <div class="stations__popup-name">{{ selected.name }}</div>
        <div class="stations__popup-meta">地址：{{ selected.address }}</div>
        <div class="stations__popup-meta">营业时间：{{ selected.openHours || '—' }}</div>
        <van-button round block type="primary" @click="openAmapNavigation(selected.lng, selected.lat, selected.name)">
          高德导航前往
        </van-button>
      </template>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.stations {
  &__map {
    width: 100%;
    // 减去 van-nav-bar(46px) + 底部 nav(62px) + safe-area，避免地图被底栏遮挡
    height: calc(100vh - 46px - var(--df-nav-h) - env(safe-area-inset-bottom));
  }

  &__status {
    position: fixed;
    top: calc(46px + env(safe-area-inset-top) + 12px);
    left: 50%;
    transform: translateX(-50%);
    max-width: calc(100vw - 32px);
    padding: 6px 14px;
    font-size: $df-font-size-xs;
    color: var(--df-text-regular);
    background: rgba(255, 255, 255, 0.95);
    border-radius: 999px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    white-space: nowrap;
    z-index: 2;

    &--fallback {
      color: var(--df-color-warning);
    }
  }

  &__tabs {
    position: fixed;
    left: 50%;
    // 抬高到底栏上方：底栏高 var(--df-nav-h) + safe-area-inset-bottom + 16px 间隙
    bottom: calc(var(--df-nav-h) + env(safe-area-inset-bottom) + 16px);
    transform: translateX(-50%);
    display: flex;
    gap: 4px;
    padding: 4px;
    max-width: calc(100vw - 16px);
    background: rgba(255, 255, 255, 0.95);
    border-radius: 999px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
    z-index: 5;
  }

  &__tab {
    // 文字横向：nowrap 防止压缩换行；flex-shrink:0 防止 flex 容器挤压
    flex-shrink: 0;
    white-space: nowrap;
    padding: 6px 16px;
    border-radius: 999px;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
    background: transparent;
    border: none;

    &.active {
      background: var(--df-color-primary);
      color: #fff;
      font-weight: 600;
    }
  }

  &__fallback {
    padding: $df-space-md;
    // 给底栏留空，避免最后几项被遮挡
    padding-bottom: calc(var(--df-nav-h) + env(safe-area-inset-bottom) + $df-space-lg);
  }

  &__list {
    padding: 0 $df-space-md;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    padding: $df-space-md 0;
    border-bottom: 1px solid var(--df-border-color);

    &:last-child {
      border-bottom: none;
    }
  }

  &__dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__name {
    font-weight: 500;
    font-size: $df-font-size-base;
  }

  &__meta {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__nav {
    color: var(--df-color-primary);
    font-size: $df-font-size-sm;
    font-weight: 600;
    flex-shrink: 0;
    background: transparent;
    border: none;
    cursor: pointer;
    padding: 4px;
  }

  &__popup {
    padding: $df-space-lg;
  }

  &__popup-name {
    font-size: $df-font-size-lg;
    font-weight: 600;
    margin-bottom: $df-space-sm;
  }

  &__popup-meta {
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    margin-bottom: $df-space-xs;
  }
}
</style>
