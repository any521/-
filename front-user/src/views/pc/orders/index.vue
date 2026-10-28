<script setup lang="ts">
// PC 我的订单：类型 chips + 状态筛选 Tab + 列表（四源聚合：回收/维修/垃圾投放/兑换，LoopFill B1）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyOrders, type MyOrder } from '@/api/business'
import { OrderStatus, ORDER_STATUS_TEXT } from '@shared/http/types'
import { formatMoney, formatCarbon } from '@shared/utils/format'

defineOptions({ name: 'pc-orders' })

const router = useRouter()

/** 类型筛选 chips：空串为全部（LoopFill B1，C18 并入跳蚤） */
const bizTabs = [
  { value: '', label: '全部' },
  { value: 'RECYCLE', label: '回收' },
  { value: 'REPAIR', label: '维修' },
  { value: 'KITCHEN', label: '垃圾' },
  { value: 'MALL', label: '兑换' },
  { value: 'FLEA', label: '闲置' },
]
const activeBiz = ref('')

const tabs = [
  { name: '', label: '全部' },
  { name: String(OrderStatus.PENDING), label: '待接单' },
  { name: String(OrderStatus.ACCEPTED), label: '已接单' },
  { name: String(OrderStatus.PROCESSING), label: '进行中' },
  { name: String(OrderStatus.FINISHED), label: '已完成' },
]
const activeTab = ref('')

const list = ref<MyOrder[]>([])
const loading = ref(false)
const finished = ref(false)
let pageNum = 1

const BIZ_ICON: Record<MyOrder['bizType'], string> = {
  RECYCLE: 'recycle-recycle-symbol',
  KITCHEN: 'kitchen-leafy-green',
  REPAIR: 'repair-wrench',
  MALL: 'carbon-shopping-bag',
  FLEA: 'nav-money-bag',
}

async function loadMore() {
  loading.value = true
  try {
    const page = await pageMyOrders({
      pageNum,
      pageSize: 10,
      status: activeTab.value || undefined,
      bizType: (activeBiz.value || undefined) as MyOrder['bizType'] | undefined,
    })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum = 1
  finished.value = false
  loadMore()
}

// 首屏加载（PC 端无 van-list @load，挂载时需显式拉取；切走再回重新挂载同样触发）
onMounted(() => {
  reload()
})

/** 订单状态 tag 颜色（四源归一化到 OrderStatus 后统一判定） */
function statusType(s: OrderStatus) {
  if (s === OrderStatus.FINISHED) return 'success'
  if (s === OrderStatus.CANCELED) return 'default'
  if (s === OrderStatus.ABNORMAL) return 'danger'
  return 'primary'
}

/** 点击进入详情：带前缀完整路径 + query 字符串（路由守卫会改写前缀并丢弃 query，必须同端直达）
 *  FLEA 无详情端点：跳「我买到的」页（C18） */
function goDetail(o: MyOrder) {
  const prefix = location.pathname.startsWith('/pc') ? '/pc' : '/mobile'
  if (o.bizType === 'FLEA') {
    router.push(`${prefix}/flea/mine?tab=bought`)
    return
  }
  router.push(`${prefix}/orders/${o.id}?bizType=${o.bizType}`)
}
</script>

<template>
  <div class="pc-orders">
    <div class="container">
      <h1 class="page-title">我的订单</h1>

      <div class="order-card">
        <!-- LoopFill B1：类型过滤 chips -->
        <div class="biz-chips">
          <button
            v-for="c in bizTabs"
            :key="c.value"
            class="chip"
            :class="{ active: activeBiz === c.value }"
            @click="activeBiz = c.value; reload()"
          >
            {{ c.label }}
          </button>
        </div>

        <div class="tabs">
          <button
            v-for="t in tabs"
            :key="t.name"
            class="tab"
            :class="{ active: activeTab === t.name }"
            @click="activeTab = t.name; reload()"
          >
            {{ t.label }}
          </button>
        </div>

        <div v-if="loading && list.length === 0" class="loading">加载中…</div>

        <div v-else-if="finished && list.length === 0" class="empty">暂无订单</div>

        <div v-else class="order-list">
          <div
            v-for="o in list"
            :key="o.bizType + '-' + o.id"
            class="order-item"
            @click="goDetail(o)"
          >
            <div class="order-item__head">
              <div class="order-item__title">
                <SvgIcon :name="BIZ_ICON[o.bizType]" :size="20" />
                {{ o.title }}
              </div>
              <span class="order-item__status" :class="`order-item__status--${statusType(o.status)}`">
                {{ o.statusDesc || ORDER_STATUS_TEXT[o.status] }}
              </span>
            </div>
            <div class="order-item__meta">
              <span v-if="o.appointTime">预约：{{ o.appointTime }}</span>
              <span v-if="o.address">{{ o.address }}</span>
            </div>
            <div class="order-item__foot">
              <span v-if="o.amount > 0" class="order-item__amount">{{ formatMoney(o.amount) }}</span>
              <span v-if="o.points !== 0" :class="o.points < 0 ? 'order-item__cost' : 'order-item__points'">
                {{ o.points > 0 ? `+${o.points}` : o.points }} 积分
              </span>
              <span v-if="o.carbon > 0" class="order-item__carbon">{{ formatCarbon(o.carbon) }}</span>
              <!-- onsite04：进行中回收单 → 实时追踪（G4） -->
              <span
                v-if="o.bizType === 'RECYCLE' && (o.status === OrderStatus.ACCEPTED || o.status === OrderStatus.PROCESSING)"
                class="order-item__track"
                @click.stop="router.push(`/pc/map/track/RECYCLE/${o.id}`)"
              >
                实时追踪
              </span>
            </div>
          </div>

          <button v-if="!finished && list.length > 0" class="load-more" :disabled="loading" @click="loadMore">
            {{ loading ? '加载中…' : '加载更多' }}
          </button>
          <p v-else-if="finished && list.length > 0" class="no-more">没有更多了</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-orders {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 900px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 48px;
  text-align: center;
}

.order-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-card);
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.tab {
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--df-text-regular);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }

  &.active {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
    font-weight: 600;
  }
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order-item {
  padding: 20px;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 3%, transparent);
  }
}

.order-item__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.order-item__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.order-item__status {
  font-size: 13px;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 999px;

  &--primary { background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary); }
  &--success { background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary); }
  &--default { background: var(--df-bg-card); color: var(--df-text-secondary); }
  &--danger { background: rgba(231, 76, 60, 0.1); color: var(--df-color-danger); }
}

.order-item__meta {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--df-text-secondary);
}

.order-item__foot {
  display: flex;
  gap: 16px;
  font-size: 14px;
}

.order-item__amount { color: var(--df-color-danger); font-weight: 600; }
.order-item__points { color: var(--df-color-gold); }
.order-item__carbon { color: var(--df-color-carbon); }

/* LoopFill B1：兑换扣分（负积分）展示 */
.order-item__cost { color: var(--df-color-danger); font-weight: 600; }

/* LoopFill B1：类型过滤 chips */
.biz-chips {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.chip {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--df-text-regular);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }

  &.active {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
    font-weight: 600;
  }
}

.order-item__track {
  margin-left: auto;
  color: var(--df-color-primary);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.load-more {
  width: 100%;
  padding: 12px;
  font-size: 15px;
  font-weight: 500;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.no-more {
  text-align: center;
  color: var(--df-text-secondary);
  font-size: 14px;
  margin: 16px 0 0;
}
</style>