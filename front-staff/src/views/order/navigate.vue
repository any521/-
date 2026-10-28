<script setup lang="ts">
// ============================================================
// 智能路线导航（onsite04 全量升级，G4）：
// - 真实高德地图：我的位置（浏览器定位）+ 途经点 Marker（序号）+ 分段路径规划折线
// - 起点=我的位置（失败用途经点首点）；途经点坐标经 geocode(address) 补齐（后端 10min 缓存）
// - 保留「跳系统地图 App」URI 兜底；地图不可用 → 回退 SVG 示意（零丢失）
// 契约：onsite_overview.md C-O4
// ============================================================
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showToast } from 'vant'
import { getTodayRoute, type RoutePlan, type RouteStop } from '@/api/business'
import { geocode, routePlan } from '@/api/map'
import { openAmapNavigation } from '@/utils/amapNavi'
import { wgs84ToGcj02 } from '@/utils/coord'
import {
  createMap, destroyMap, ensureAmap, mapReady, addMarker, drawPolyline, fitBounds,
} from '@/composables/useAmap'

defineOptions({ name: 'order-navigate' })

const router = useRouter()
const plan = ref<RoutePlan | null>(null)

/** 带坐标的途经点（RouteStop 无坐标字段，前端 geocode 补齐） */
interface StopPoint extends RouteStop {
  lng: number
  lat: number
}
const stops = ref<StopPoint[]>([])
/** 我的位置（浏览器定位；不可用时为 null） */
const myLoc = ref<[number, number] | null>(null)
/** 实际规划累计距离/耗时文案（全段失败时为空） */
const routeText = ref('')

const mapEl = ref<HTMLDivElement | null>(null)
let mapHandle: ReturnType<typeof createMap> | null = null
let myMarker: unknown = null
let stopMarkers: unknown[] = []
let polylines: unknown[] = []
let watchId: number | null = null

/** 示意图坐标：按序号均匀铺在画布上（降级模式） */
function stopPoint(i: number, total: number): { x: number; y: number } {
  const x = 30 + (i / Math.max(total - 1, 1)) * 240
  const y = 60 + (i % 2 === 0 ? 0 : 50) + i * 6
  return { x, y }
}

function polyline(total: number): string {
  return Array.from({ length: total }, (_, i) => {
    const p = stopPoint(i, total)
    return `${p.x},${p.y}`
  }).join(' ')
}

/** 途经点坐标补齐（geocode 失败的点丢弃，页面按剩余点渲染） */
async function loadStopsWithCoords(p: RoutePlan) {
  const points = await Promise.all(
    p.stops.map(async (s): Promise<StopPoint | null> => {
      try {
        const g = await geocode(s.address)
        return g ? { ...s, lng: g.lng, lat: g.lat } : null
      } catch {
        return null
      }
    }),
  )
  return points.filter((x): x is StopPoint => x !== null).sort((a, b) => a.seq - b.seq)
}

/** 途经点 Marker（序号圆标） */
function drawStops() {
  if (!mapHandle) return
  const { map } = mapHandle
  stopMarkers.forEach((m) => (map as { remove: (o: unknown) => void }).remove(m))
  stopMarkers = []
  stops.value.forEach((s) => {
    const m = addMarker(mapHandle!, [s.lng, s.lat], {
      content: `<div style="width:24px;height:24px;line-height:24px;text-align:center;border-radius:50%;background:#2f6b5e;color:#fff;font-size:13px;font-weight:600;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.3)">${s.seq}</div>`,
    })
    ;(m as { on: (ev: string, cb: () => void) => void }).on('click', () => {
      router.push(`/orders/${s.orderId}`)
    })
    stopMarkers.push(m)
  })
}

/** 分段路径规划：起点 → 途经点① → … → 终点（段失败直线虚线兜底，整体路径连贯） */
async function planPath() {
  if (!mapHandle || stops.value.length === 0) return
  const { map } = mapHandle
  const first = stops.value[0]!
  const start: [number, number] = myLoc.value ?? [first.lng, first.lat]
  const points: [number, number][] = [start, ...stops.value.map((s) => [s.lng, s.lat] as [number, number])]

  polylines.forEach((p) => (map as { remove: (o: unknown) => void }).remove(p))
  polylines = []

  let totalDist = 0
  let totalDur = 0
  for (let i = 0; i < points.length - 1; i++) {
    const from = points[i]!
    const to = points[i + 1]!
    try {
      const r = await routePlan({
        originLng: from[0],
        originLat: from[1],
        destLng: to[0],
        destLat: to[1],
        mode: 'driving',
      })
      polylines.push(drawPolyline(mapHandle, r.polyline.length ? r.polyline : [from, to]))
      totalDist += r.distance
      totalDur += r.duration
    } catch {
      // 段规划失败（5001/网络）：直线虚线兜底，不中断整条路线
      polylines.push(drawPolyline(mapHandle, [from, to], { strokeColor: '#999', strokeStyle: 'dashed' }))
    }
  }
  routeText.value =
    totalDist > 0
      ? `${totalDist >= 1000 ? `${(totalDist / 1000).toFixed(1)} 公里` : `${totalDist} 米`} · 约 ${totalDur} 分钟`
      : ''
  fitBounds(mapHandle, points, 12)
}

/** 浏览器定位监听：驱动「我的位置」Marker 移动（仅本地展示，上报链路由 useStaffLocation 单例负责） */
function startGeolocation() {
  if (!('geolocation' in navigator)) return
  watchId = navigator.geolocation.watchPosition(
    (pos) => {
      // 浏览器返回 WGS-84，高德地图/路线规划需 GCJ-02：先转换再渲染，否则我的位置与途经点偏差数百米
      const [lng, lat] = wgs84ToGcj02(pos.coords.longitude, pos.coords.latitude)
      myLoc.value = [lng, lat]
      if (!mapHandle) return
      if (myMarker) {
        ;(myMarker as { setPosition: (p: unknown) => void }).setPosition(
          new (mapHandle.amap as NonNullable<ReturnType<typeof createMap>['amap']>).LngLat(lng, lat),
        )
      } else {
        myMarker = addMarker(mapHandle, [lng, lat], {
          title: '我的位置',
          content: '<div style="width:16px;height:16px;border-radius:50%;background:#3d8b85;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.3)"></div>',
        })
      }
    },
    () => {
      // 定位不可用：以途经点首点兜底为起点，路径照常规划
      planPath()
    },
  )
}

function stopGeolocation() {
  if (watchId !== null) {
    navigator.geolocation.clearWatch(watchId)
    watchId = null
  }
}

onMounted(async () => {
  try {
    plan.value = await getTodayRoute()
  } catch {
    // 不在岗/接口异常：避免骨架屏卡死，提示后留在空态
    showFailToast('路线加载失败，请稍后重试')
    return
  }
  if (!plan.value || !plan.value.stops.length) return
  stops.value = await loadStopsWithCoords(plan.value)
  if (!stops.value.length) return

  const ok = await ensureAmap()
  if (!ok || !mapEl.value) return
  mapHandle = createMap(mapEl.value, [120.1536, 30.2875], 12)
  drawStops()
  startGeolocation()
  await planPath()
})

onUnmounted(() => {
  stopGeolocation()
  if (mapEl.value) {
    destroyMap(mapEl.value)
    mapHandle = null
    stopMarkers = []
    polylines = []
    myMarker = null
  }
})
</script>

<template>
  <div class="page">
    <van-nav-bar title="智能路线导航" left-arrow @click-left="router.back()" />

    <template v-if="plan">
      <div class="card nav__summary">
        <div class="nav__stat">
          <div class="nav__num">{{ plan.stops.length }}</div>
          <div class="nav__label">途经点</div>
        </div>
        <div class="nav__stat">
          <div class="nav__num">{{ plan.totalKm }}<small>km</small></div>
          <div class="nav__label">总里程</div>
        </div>
        <div class="nav__stat">
          <div class="nav__num">{{ plan.totalMin }}<small>min</small></div>
          <div class="nav__label">预计耗时</div>
        </div>
        <div class="nav__stat" v-if="routeText">
          <div class="nav__num nav__num--real">{{ routeText }}</div>
          <div class="nav__label">实测路线</div>
        </div>
      </div>

      <!-- 真实地图（onsite04 升级；不可用时回退 SVG 示意） -->
      <div class="card">
        <div class="card__title">路线导航</div>
        <template v-if="mapReady && stops.length">
          <div ref="mapEl" class="nav__map" />
          <van-button size="small" round class="nav__replan" @click="planPath">重新规划</van-button>
        </template>

        <!-- 降级：地图不可用 → 原 SVG 示意 -->
        <template v-else>
          <svg v-if="stops.length" viewBox="0 0 300 160" class="nav__map">
            <polyline
              :points="polyline(stops.length)"
              fill="none"
              stroke="#2f6b5e"
              stroke-width="2"
              stroke-dasharray="6 4"
            />
            <g v-for="(stop, i) in stops" :key="stop.orderId">
              <circle :cx="stopPoint(i, stops.length).x" :cy="stopPoint(i, stops.length).y" r="10" fill="#2f6b5e" />
              <text
                :x="stopPoint(i, stops.length).x"
                :y="stopPoint(i, stops.length).y + 4"
                text-anchor="middle"
                fill="#fff"
                font-size="10"
              >
                {{ stop.seq }}
              </text>
            </g>
          </svg>
          <van-empty v-else description="暂无待上门订单，先去接单吧" />
          <p class="nav__algo">调度策略：{{ plan.algorithm }}</p>
        </template>
      </div>

      <div v-for="stop in stops" :key="stop.orderId" class="card nav__stop">
        <div class="nav__seq">{{ stop.seq }}</div>
        <div class="nav__info" @click="router.push(`/orders/${stop.orderId}`)">
          <div class="nav__addr">{{ stop.address }}</div>
          <div class="nav__meta">{{ stop.customerName }} · {{ stop.appointTime }}</div>
        </div>
        <van-button size="small" type="primary" plain round @click="openAmapNavigation(stop.lng, stop.lat, stop.address)">
          去导航
        </van-button>
      </div>
    </template>
    <van-skeleton v-else title :row="8" />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.nav__summary {
  display: flex;
  justify-content: space-around;
  text-align: center;
}

.nav__num {
  font-size: 22px;
  font-weight: 700;
  color: var(--df-color-primary-dark);

  small {
    font-size: $df-font-size-xs;
    margin-left: 2px;
  }

  &--real {
    font-size: $df-font-size-sm;
    color: var(--df-color-carbon);
  }
}

.nav__label {
  margin-top: 2px;
  color: var(--df-text-secondary);
  font-size: $df-font-size-xs;
}

.nav__map {
  width: 100%;
  height: 55vh;
  background: var(--df-bg-page);
  border-radius: $df-radius-lg;
}

.nav__replan {
  margin-top: $df-space-sm;
}

.nav__algo {
  margin: $df-space-sm 0 0;
  color: var(--df-text-secondary);
  font-size: $df-font-size-xs;
}

.nav__stop {
  display: flex;
  align-items: center;
  gap: $df-space-md;
}

.nav__seq {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  line-height: 28px;
  text-align: center;
  border-radius: 50%;
  background: var(--df-color-primary);
  color: #fff;
  font-weight: 600;
}

.nav__info {
  flex: 1;
  min-width: 0;
}

.nav__addr {
  font-weight: 600;
}

.nav__meta {
  margin-top: 2px;
  color: var(--df-text-secondary);
  font-size: $df-font-size-sm;
}
</style>
