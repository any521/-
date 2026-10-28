<script setup lang="ts">
// ============================================================
// PC 订单实时追踪（onsite04，G4）——与 mobile 版同逻辑，桌面布局
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

defineOptions({ name: 'pc-map-track' })

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
    order.value = await getOrderDetail(orderId)
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
  <div class="pc-track">
    <div class="container">
      <h1 class="page-title">订单实时追踪</h1>

      <div class="track-card">
        <!-- 状态卡 -->
        <div class="track-head">
          <div class="track-head__item">
            <span class="track-head__label">订单号</span>
            <span class="track-head__value">{{ order?.orderNo ?? '—' }}</span>
          </div>
          <div class="track-head__item">
            <span class="track-head__label">状态</span>
            <span class="track-head__value">{{ order ? statusText : '—' }}</span>
          </div>
          <div class="track-head__item">
            <span class="track-head__label">回收员</span>
            <span class="track-head__value">{{ staffLoc?.staffName || order?.staffName || '—' }}</span>
          </div>
          <div class="track-head__item">
            <span class="track-head__label">预计到达</span>
            <span class="track-head__value">{{ routeText }}</span>
          </div>
        </div>

        <van-empty v-if="!supported" description="该业务暂不支持实时追踪" />

        <template v-else>
          <!-- 地图 + 侧栏 -->
          <div class="track-body">
            <div v-if="mapReady" ref="mapEl" class="track-map" />
            <div v-else class="track-map track-map--fallback">
              <p>地图未配置</p>
              <van-button v-if="uriUrl" round type="primary" tag="a" :href="uriUrl" target="_blank" rel="noopener">
                在「高德地图」中查看
              </van-button>
              <van-button v-else round plain disabled>等待员工位置上报…</van-button>
            </div>

            <aside class="track-side">
              <div class="track-side__title">追踪信息</div>
              <div class="track-side__row">
                <span>服务地址</span>
                <span>{{ order?.address ?? '—' }}</span>
              </div>
              <div class="track-side__row">
                <span>预约时间</span>
                <span>{{ order?.appointTime ?? '—' }}</span>
              </div>
              <div class="track-side__row">
                <span>实时推送</span>
                <span>{{ imConnected ? '已连接' : '轮询中' }}</span>
              </div>
              <a
                v-if="uriUrl"
                class="track-side__link"
                :href="uriUrl"
                target="_blank"
                rel="noopener"
              >
                跳转高德地图 App
              </a>
            </aside>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.pc-track {
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

.track-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-card);
}

.track-head {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding-bottom: 24px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--df-border-color);

  &__label {
    display: block;
    color: var(--df-text-secondary);
    font-size: 13px;
    margin-bottom: 6px;
  }

  &__value {
    font-size: 15px;
    font-weight: 600;
    color: var(--df-text-primary);
  }
}

.track-body {
  display: grid;
  grid-template-columns: 1fr 260px;
  gap: 24px;
}

.track-map {
  height: 480px;
  border-radius: 12px;
  overflow: hidden;

  &--fallback {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 16px;
    border: 1px dashed var(--df-border-color);
    color: var(--df-text-secondary);
  }
}

.track-side {
  padding: 20px;
  background: transparent;
  border-radius: 12px;

  &__title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 16px;
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

  &__link {
    display: block;
    margin-top: 16px;
    padding: 10px 0;
    text-align: center;
    color: var(--df-color-primary);
    font-weight: 600;
    border: 1px solid var(--df-color-primary);
    border-radius: 999px;
  }
}
</style>
