<script setup lang="ts">
// ============================================================
// 订单实时追踪（onsite04，G4）：
// - 地图渲染员工位置 Marker + 用户地址 → routePlan(driving) 折线
// - 实时性：LOCATION 帧订阅（onsite03 WS）+ 5s 轮询兜底并存
// - 降级：mapReady=false → 「高德 URI 跳转」按钮 + 提示，页面不崩
// 契约：onsite_overview.md C-O4 / C-O3-5
// ============================================================
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrderDetail, getStaffLocation, type MyOrder, type StaffLocationVO } from '@/api/business'
import { geocode, routePlan, type RouteResult } from '@/api/map'
import { connect, subscribeLocation, imConnected } from '@/composables/useImSocket'
import {
  createMap, destroyMap, ensureAmap, mapReady, addMarker, drawPolyline, fitBounds,
} from '@/composables/useAmap'
import { OrderStatus, ORDER_STATUS_TEXT } from '@shared/http/types'

defineOptions({ name: 'map-track' })

const route = useRoute()
const router = useRouter()

const bizType = String(route.params.bizType ?? 'RECYCLE').toUpperCase()
// 雪花 ID 保持字符串传递，Number 转换会丢精度致详情查无此单
const orderId = String(route.params.orderId)

const order = ref<MyOrder | null>(null)
const staffLoc = ref<StaffLocationVO | null>(null)
const destPoint = ref<[number, number] | null>(null)
const routeInfo = ref<RouteResult | null>(null)
const loading = ref(true)

const mapEl = ref<HTMLDivElement | null>(null)
let mapHandle: ReturnType<typeof createMap> | null = null
let staffMarker: unknown = null
let destMarker: unknown = null
let polyline: unknown = null
let pollTimer: number | undefined
let unsubLocation: (() => void) | null = null

/** 仅 RECYCLE 具备员工位置接口（REPAIR 无同构接口，偏差记录：仅支持回收单） */
const supported = computed(() => bizType === 'RECYCLE')
const statusText = computed(() => (order.value ? ORDER_STATUS_TEXT[order.value.status] : ''))

/** 降级/兜底：高德 URI 跳转（有员工位置时） */
const uriUrl = computed(() => {
  const loc = staffLoc.value
  if (!loc) return ''
  return `https://uri.amap.com/marker?position=${loc.lng},${loc.lat}&name=${encodeURIComponent(
    loc.staffName || '回收员',
  )}&src=point&coordinate=gaode`
})

/** 距离/耗时文案（routeInfo 为空展示占位） */
const routeText = computed(() => {
  const r = routeInfo.value
  if (!r) return '等待位置数据…'
  const km = r.distance >= 1000 ? `${(r.distance / 1000).toFixed(1)} 公里` : `${r.distance} 米`
  return `${km} · 约 ${r.duration} 分钟`
})

/** 员工 Marker 定位（LOCATION 帧/轮询共用；无地图时仅存数据） */
function moveStaffMarker(lng: number, lat: number) {
  if (!mapHandle) return
  if (staffMarker) {
    ;(staffMarker as { setPosition: (p: unknown) => void }).setPosition(
      new (mapHandle.amap as NonNullable<ReturnType<typeof createMap>['amap']>).LngLat(lng, lat),
    )
  } else {
    staffMarker = addMarker(mapHandle, [lng, lat], { title: '回收员', animation: 'AMAP_ANIMATION_DROP' })
  }
}

/** 路径规划：员工位置 → 订单地址（driving 折线 + 距离耗时） */
async function planRoute() {
  const loc = staffLoc.value
  const dest = destPoint.value
  if (!loc || !dest || !mapHandle) return
  try {
    const r = await routePlan({
      originLng: loc.lng,
      originLat: loc.lat,
      destLng: dest[0],
      destLat: dest[1],
      mode: 'driving',
    })
    routeInfo.value = r
    if (polyline) {
      ;(mapHandle.map as { remove: (o: unknown) => void }).remove(polyline)
    }
    polyline = drawPolyline(mapHandle, r.polyline.length ? r.polyline : [[loc.lng, loc.lat], dest])
    fitBounds(mapHandle, [[loc.lng, loc.lat], dest], 13)
  } catch {
    // 5001/网络错误：拦截器已 Toast，保持现状
  }
}

/** 轮询兜底：拉一次员工位置（无接单/无上报返回 null，静默降级） */
async function pollStaffLocation() {
  if (!supported.value) return
  try {
    const loc = await getStaffLocation(orderId)
    if (loc && (!staffLoc.value || loc.lng !== staffLoc.value.lng || loc.lat !== staffLoc.value.lat)) {
      staffLoc.value = loc
      moveStaffMarker(loc.lng, loc.lat)
      planRoute()
    }
  } catch {
    /* 轮询失败静默，下个周期重试 */
  }
}

function startPolling() {
  stopPolling()
  pollStaffLocation()
  pollTimer = window.setInterval(pollStaffLocation, 5000)
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

onMounted(async () => {
  try {
    // 订单信息（员工姓名/状态/地址）
    order.value = await getOrderDetail(orderId)
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
  if (!supported.value || !order.value) return

  // 地址 → 坐标（后端 10min 缓存；失败不阻塞，仅无路径）
  try {
    const g = await geocode(order.value.address)
    if (g) destPoint.value = [g.lng, g.lat]
  } catch {
    /* geocode 失败静默 */
  }

  const ok = await ensureAmap()
  if (!ok || !mapEl.value) return
  mapHandle = createMap(mapEl.value, [120.1536, 30.2875], 13)

  // 目的地 Marker（用户地址）
  if (destPoint.value) {
    destMarker = addMarker(mapHandle, destPoint.value, { title: '上门地址', animation: 'AMAP_ANIMATION_DROP' })
  }

  // WS：LOCATION 帧订阅（帧无 bizType/bizId 字段，按当前订单上下文直接消费；轮询持续校准）
  connect()
  unsubLocation = subscribeLocation((frame) => {
    const loc = frame?.location
    if (!loc) return
    staffLoc.value = { ...(staffLoc.value ?? { staffName: '', staffUserId: '' }), lng: loc.lng, lat: loc.lat }
    moveStaffMarker(loc.lng, loc.lat)
    planRoute()
  })

  // 轮询兜底（WS 未就绪/断线仍保底，二者并存）
  startPolling()
})

onUnmounted(() => {
  stopPolling()
  unsubLocation?.()
  unsubLocation = null
  if (mapEl.value) {
    destroyMap(mapEl.value)
    mapHandle = null
    staffMarker = null
    destMarker = null
    polyline = null
  }
})
</script>

<template>
  <div class="page track">
    <van-nav-bar title="订单实时追踪" left-arrow @click-left="router.back()" />

    <!-- 状态卡 -->
    <div class="card track__head">
      <div class="track__row">
        <span class="track__label">订单号</span>
        <span class="track__value">{{ order?.orderNo ?? '—' }}</span>
        <van-tag v-if="order" plain :type="order.status === OrderStatus.FINISHED ? 'success' : 'primary'">
          {{ statusText }}
        </van-tag>
      </div>
      <div class="track__row">
        <span class="track__label">回收员</span>
        <span class="track__value">{{ staffLoc?.staffName || order?.staffName || '—' }}</span>
      </div>
      <div class="track__row">
        <span class="track__label">预计</span>
        <span class="track__value">{{ routeText }}</span>
      </div>
      <div v-if="imConnected" class="track__live">实时推送已连接</div>
    </div>

    <!-- 不支持的业务（仅 RECYCLE 有员工位置接口） -->
    <van-empty v-if="!supported" description="该业务暂不支持实时追踪" />

    <template v-else>
      <!-- 真实地图 -->
      <div v-if="mapReady" ref="mapEl" class="track__map" />

      <!-- 降级：地图未配置 → URI 跳转兜底 -->
      <div v-else class="card track__fallback">
        <div class="track__fallback-tip">地图未配置，可跳转高德查看员工位置</div>
        <van-button v-if="uriUrl" block round type="primary" tag="a" :href="uriUrl" target="_blank" rel="noopener">
          在「高德地图」中查看
        </van-button>
        <van-button v-else block round plain disabled>等待员工位置上报…</van-button>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.track {
  &__head {
    margin-bottom: $df-space-md;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    margin: $df-space-xs 0;
    font-size: $df-font-size-sm;
  }

  &__label {
    flex-shrink: 0;
    color: var(--df-text-secondary);
  }

  &__value {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__live {
    margin-top: $df-space-xs;
    color: var(--df-color-carbon);
    font-size: $df-font-size-xs;
  }

  &__map {
    width: 100%;
    height: 55vh;
    border-radius: $df-radius-lg;
    overflow: hidden;
  }

  &__fallback {
    text-align: center;
  }

  &__fallback-tip {
    margin-bottom: $df-space-md;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }
}
</style>
