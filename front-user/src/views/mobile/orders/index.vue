<script setup lang="ts">
// 我的订单：类型 chips + 状态筛选 Tab + 分页列表（四源聚合：回收/维修/垃圾投放/兑换，LoopFill B1）
import { ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyOrders, type MyOrder } from '@/api/business'
import { OrderStatus, ORDER_STATUS_TEXT } from '@shared/http/types'
import { formatMoney, formatCarbon } from '@shared/utils/format'

defineOptions({ name: 'orders' })

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

/** 状态筛选：空串为全部 */
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
const refreshing = ref(false)
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
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function reload() {
  pageNum = 1
  finished.value = false
  loadMore()
}

function onRefresh() {
  refreshing.value = true
  reload()
}

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

// KeepAlive 恢复自愈：首次挂载由 van-list 自动 load；恢复时列表为空（上次加载失败）自动重试
onActivated(() => {
  if (!loading.value && list.value.length === 0) reload()
})
</script>

<template>
  <div class="orders">
    <!-- LoopFill B1：类型过滤 chips（前端过滤，复用 pageMyOrders bizType 参数） -->
    <div class="orders__biz-chips">
      <van-tag
        v-for="c in bizTabs"
        :key="c.value"
        size="large"
        :type="activeBiz === c.value ? 'primary' : 'default'"
        @click="activeBiz = c.value; reload()"
      >
        {{ c.label }}
      </van-tag>
    </div>

    <van-tabs v-model:active="activeTab" sticky @change="reload">
      <van-tab v-for="t in tabs" :key="t.name" :name="t.name" :title="t.label" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div class="page">
          <div v-for="o in list" :key="o.bizType + '-' + o.id" class="card orders__item" @click="goDetail(o)">
            <div class="orders__head">
              <span class="orders__title"><SvgIcon :name="BIZ_ICON[o.bizType]" :size="16" /> {{ o.title }}</span>
              <van-tag :type="statusType(o.status)" plain>{{ o.statusDesc || ORDER_STATUS_TEXT[o.status] }}</van-tag>
            </div>
            <div class="orders__meta">
              <div v-if="o.appointTime">预约：{{ o.appointTime }}</div>
              <div v-if="o.address">{{ o.address }}</div>
            </div>
            <div class="orders__foot">
              <span v-if="o.amount > 0" class="orders__amount">{{ formatMoney(o.amount) }}</span>
              <span v-if="o.points !== 0" :class="o.points < 0 ? 'orders__cost' : 'orders__points'">
                {{ o.points > 0 ? `+${o.points}` : o.points }} 积分
              </span>
              <span v-if="o.carbon > 0" class="orders__carbon">{{ formatCarbon(o.carbon) }}</span>
              <!-- onsite04：进行中回收单 → 实时追踪（G4） -->
              <span
                v-if="o.bizType === 'RECYCLE' && (o.status === OrderStatus.ACCEPTED || o.status === OrderStatus.PROCESSING)"
                class="orders__track"
                @click.stop="router.push(`/map/track/RECYCLE/${o.id}`)"
              >
                实时追踪
              </span>
            </div>
          </div>
          <van-empty v-if="finished && list.length === 0" description="暂无订单" />
        </div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.orders {
  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: $df-space-sm;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
    font-weight: 500;
    font-size: $df-font-size-base;
  }

  &__meta {
    margin: $df-space-sm 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    line-height: 1.7;
  }

  &__foot {
    display: flex;
    gap: $df-space-md;
    font-size: $df-font-size-sm;
  }

  &__amount {
    color: var(--df-color-danger);
    font-weight: 600;
  }

  &__points {
    color: var(--df-color-gold);
  }

  /* LoopFill B1：兑换扣分（负积分）展示 */
  &__cost {
    color: var(--df-color-danger);
    font-weight: 600;
  }

  &__carbon {
    color: var(--df-color-carbon);
  }

  /* LoopFill B1：类型过滤 chips */
  &__biz-chips {
    display: flex;
    flex-wrap: wrap;
    gap: $df-space-sm;
    padding: $df-space-sm $df-space-md;
  }

  &__track {
    margin-left: auto;
    color: var(--df-color-primary);
    font-weight: 600;
    font-size: $df-font-size-sm;
  }
}
</style>
