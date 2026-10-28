<script setup lang="ts">
// 接单台（Tab 1）：今日订单按状态分组，抢单/接单 + 弱网离线兜底
// 弱网策略：列表失败回退本地缓存；接单失败（网络层）入离线队列，恢复后重放
import { computed, onActivated, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import { OrderStatus, ORDER_STATUS_TEXT } from '@shared/http/types'
import { formatWeight } from '@shared/utils/format'
import { getAllOrders, acceptOrder, acceptUrlOf, type StaffOrder } from '@/api/business'
import { getImUnreadTotal } from '@/api/im'
import { connect, onMessage as onWsMessage } from '@/composables/useImSocket'
import { cacheOrders, loadCachedOrders, enqueueAction, useOffline } from '@/composables/useOffline'
import { useStaffLocation } from '@/composables/useStaffLocation'
import { useStaffStore } from '@/store/staff'

defineOptions({ name: 'tasks' })

const router = useRouter()
const staffStore = useStaffStore()
const staffLocation = useStaffLocation()
const { online } = useOffline((synced) => {
  showSuccessToast(`网络已恢复，${synced} 条离线接单已同步`)
  staffLocation.start()
  loadOrders()
})

const orders = ref<StaffOrder[]>([])
const loading = ref(false)
const refreshing = ref(false)
/** 是否处于缓存兜底展示（弱网） */
const fromCache = ref(false)
const cacheTime = ref('')
/** 状态筛选：-1 全部 */
const activeStatus = ref(-1)

/** 片区分类：'ALL' 全部片区 / 'MINE' 仅当前片区（用户可切片区后即时生效） */
const activeScope = ref<'ALL' | 'MINE'>('ALL')
const scopeTabs: { value: 'ALL' | 'MINE'; label: string }[] = [
  { value: 'ALL', label: '全部片区' },
  { value: 'MINE', label: '当前片区' },
]

/** 任务类型分类：'ALL' 全部 / 'RECYCLE' 回收 / 'REPAIR' 维修 / 'KITCHEN' 清运 */
const activeType = ref<'ALL' | 'RECYCLE' | 'REPAIR' | 'KITCHEN'>('ALL')
const typeTabs: { value: 'ALL' | 'RECYCLE' | 'REPAIR' | 'KITCHEN'; label: string }[] = [
  { value: 'ALL', label: '全部任务' },
  { value: 'RECYCLE', label: '回收任务' },
  { value: 'REPAIR', label: '维修任务' },
  { value: 'KITCHEN', label: '清运任务' },
]

/** onsite03：IM 未读角标（消息入口，收到 MSG 帧即刷新） */
const imUnread = ref(0)
let offImWs: (() => void) | null = null
async function loadImUnread() {
  try {
    imUnread.value = await getImUnreadTotal()
  } catch {
    // 未读数失败静默降级
  }
}

const statusTabs = [
  { value: -1, label: '全部' },
  { value: OrderStatus.PENDING, label: '待接单' },
  { value: OrderStatus.ACCEPTED, label: '已接单' },
  { value: OrderStatus.PROCESSING, label: '进行中' },
  { value: OrderStatus.FINISHED, label: '已完成' },
  { value: OrderStatus.CANCELED, label: '已取消' },
  { value: OrderStatus.ABNORMAL, label: '异常' },
]

const filtered = computed(() =>
  orders.value.filter((o) => {
    const statusOk = activeStatus.value === -1 || o.status === activeStatus.value
    const scopeOk = activeScope.value !== 'MINE' || o.areaId === staffStore.myAreaId
    const typeOk = activeType.value === 'ALL' || o.bizType === activeType.value
    return statusOk && scopeOk && typeOk
  }),
)
const pendingCount = computed(() => orders.value.filter((o) => o.status === OrderStatus.PENDING).length)

/** 跨片区订单（全量列表仅展示，接单仍限本片区；可换片区后刷新再接） */
const isOtherArea = (order: StaffOrder) => !!order.areaId && order.areaId !== staffStore.myAreaId

/** 接单工种限制：回收单仅回收员、维修单仅技术员可接（清运任务直派无接单） */
const canAccept = (order: StaffOrder) =>
  order.bizType === 'REPAIR'
    ? staffStore.role === 'TECHNICIAN'
    : order.bizType === 'RECYCLE'
      ? staffStore.role === 'RECYCLER'
      : false

/** 待接单卡片操作区：接单按钮文案/禁用态（跨片区或工种不符时禁用） */
const acceptLabel = (order: StaffOrder) => {
  if (isOtherArea(order)) return '跨片区 · 不可接'
  if (order.bizType === 'REPAIR' && staffStore.role !== 'TECHNICIAN') return '仅技术员可接'
  if (order.bizType === 'RECYCLE' && staffStore.role !== 'RECYCLER') return '仅回收员可接'
  return '立即接单'
}
const acceptDisabled = (order: StaffOrder) => isOtherArea(order) || !canAccept(order)

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await getAllOrders()
    cacheOrders(orders.value)
    fromCache.value = false
  } catch {
    // 弱网回退：读最近一次缓存
    const cached = loadCachedOrders()
    if (cached) {
      orders.value = cached.list
      fromCache.value = true
      cacheTime.value = new Date(cached.time).toLocaleTimeString()
    }
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function onAccept(order: StaffOrder) {
  if (isOtherArea(order)) {
    showFailToast('仅可接本片区订单，请先在“我的”切换片区')
    return
  }
  if (!online.value) {
    // 离线：入队待重放（URL 按业务类型路由），本地先行乐观标记
    enqueueAction({ url: acceptUrlOf(order), method: 'put', label: `接单 ${order.orderNo}` })
    order.status = OrderStatus.ACCEPTED
    showToast('已离线暂存，网络恢复后自动接单')
    return
  }
  try {
    await acceptOrder(order)
    order.status = OrderStatus.ACCEPTED
    cacheOrders(orders.value)
    showSuccessToast('接单成功')
    // B2 实时定位：接单成功后启动位置上报（单例防重复，权限失败静默降级）
    staffLocation.start()
  } catch {
    /* BizError 已由请求层 toast */
  }
}

const statusTagType = (s: OrderStatus) =>
  s === OrderStatus.PENDING ? 'warning' : s === OrderStatus.FINISHED ? 'default' : s === OrderStatus.ABNORMAL ? 'danger' : 'primary'

// KeepAlive 缓存页（App.vue include）：首次由 onMounted 加载，后续每次激活（登记完成/接单返回）重新拉取
let firstActivate = true
onActivated(() => {
  if (firstActivate) {
    firstActivate = false
    return
  }
  loadOrders()
  loadImUnread()
})

onMounted(async () => {
  if (!staffStore.profile) await staffStore.fetchProfile()
  staffStore.loadAreas()
  loadOrders()
  // onsite03：IM 连接 + 未读订阅
  connect()
  offImWs = onWsMessage(() => loadImUnread())
  loadImUnread()
})

onBeforeUnmount(() => {
  offImWs?.()
})
</script>

<template>
  <div class="page tasks">
    <div class="card tasks__head">
      <div>
        <div class="tasks__hello">{{ staffStore.profile?.name ?? '师傅' }} · {{ staffStore.profile?.roleName }}</div>
        <div class="tasks__sub">今日待接 {{ pendingCount }} 单 · 片区 {{ staffStore.myAreaName }}</div>
      </div>
      <div class="tasks__head-actions">
        <van-button size="small" type="primary" icon="guide-o" @click="router.push('/route')">路线导航</van-button>
        <!-- onsite03：消息入口（IM 未读角标，C-O3；路由 /im 由 onsite03 注册） -->
        <van-badge :content="imUnread || undefined" :max="99">
          <van-button size="small" type="primary" plain icon="chat-o" @click="router.push('/im')">消息</van-button>
        </van-badge>
      </div>
    </div>

    <van-notice-bar v-if="fromCache" left-icon="clock-o" color="#ed6a0c" background="#fffbe8">
      弱网模式：展示 {{ cacheTime }} 缓存数据，下拉可重试
    </van-notice-bar>

    <van-tabs v-model:active="activeStatus" shrink>
      <van-tab v-for="t in statusTabs" :key="t.value" :name="t.value" :title="t.label" />
    </van-tabs>

    <van-tabs v-model:active="activeScope" shrink class="tasks__scope">
      <van-tab v-for="s in scopeTabs" :key="s.value" :name="s.value" :title="s.label" />
    </van-tabs>

    <van-tabs v-model:active="activeType" shrink class="tasks__scope">
      <van-tab v-for="t in typeTabs" :key="t.value" :name="t.value" :title="t.label" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="loadOrders">
      <van-skeleton v-if="loading && !orders.length" title :row="6" />
      <van-empty v-else-if="!filtered.length" description="暂无相关订单" />

      <div
        v-for="order in filtered"
        :key="order.id"
        class="card tasks__item"
        @click="router.push(`/orders/${order.id}`)"
      >
        <div class="tasks__row">
          <span class="tasks__title">{{ order.title }}</span>
          <van-tag :type="statusTagType(order.status)">{{ ORDER_STATUS_TEXT[order.status] }}</van-tag>
        </div>
        <div class="tasks__meta">
          <van-icon name="location-o" /> {{ order.address }}
        </div>
        <div class="tasks__meta">
          <van-icon name="location-o" /> 所属片区：{{ order.areaName }}
          <van-tag v-if="isOtherArea(order)" plain type="warning" style="margin-left: 6px">跨片区</van-tag>
        </div>
        <div class="tasks__meta"><van-icon name="clock-o" /> {{ order.appointTime }}</div>
        <div v-if="order.estimateWeight" class="tasks__meta">
          <van-icon name="balance-o" /> 预估 {{ formatWeight(order.estimateWeight) }}
        </div>
        <div v-if="order.aiPrediagnosis" class="tasks__ai">
          <SvgIcon name="ai-robot" :size="16" />
          <span>{{ order.aiPrediagnosis }}</span>
        </div>

        <div v-if="order.status === OrderStatus.PENDING" class="tasks__actions">
          <van-button
            size="small"
            :type="acceptDisabled(order) ? 'default' : 'primary'"
            round
            :disabled="acceptDisabled(order)"
            @click.stop="onAccept(order)"
          >
            {{ acceptLabel(order) }}
          </van-button>
        </div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.tasks {
  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__head-actions {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
  }

  &__hello {
    font-size: $df-font-size-md;
    font-weight: 600;
  }

  &__sub {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__item {
    margin-top: $df-space-md;
  }

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__title {
    font-weight: 600;
  }

  &__meta {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__ai {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: $df-space-sm;
    padding: $df-space-sm;
    background: var(--df-color-primary-light);
    border-radius: $df-radius-lg;
    font-size: $df-font-size-sm;
    color: var(--df-color-primary-dark);
  }

  &__actions {
    margin-top: $df-space-md;
    text-align: right;
  }

  &__scope {
    margin-top: $df-space-xs;
  }
}
</style>
