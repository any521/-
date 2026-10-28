<script setup lang="ts">
// 我的兑换单（LoopFill B2）：状态筛选 Tab + 分页列表 + 详情弹层 + 待发货可取消
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import {
  pageMyExchanges, getExchangeDetail, cancelExchange, EXCHANGE_STATUS_TEXT,
  type MallExchangeItem,
} from '@/api/business'

defineOptions({ name: 'mall-exchange' })

const router = useRouter()

const tabs = [
  { name: '', label: '全部' },
  { name: '0', label: '已兑换' },
  { name: '1', label: '已发货' },
  { name: '2', label: '已完成' },
  { name: '3', label: '已取消' },
]
const activeTab = ref('')

const list = ref<MallExchangeItem[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
let pageNum = 1

/** 详情弹层 */
const detailVisible = ref(false)
const detail = ref<MallExchangeItem | null>(null)
const detailLoading = ref(false)

async function loadMore() {
  loading.value = true
  try {
    const page = await pageMyExchanges(
      { pageNum, pageSize: 10 },
      activeTab.value === '' ? undefined : Number(activeTab.value),
    )
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

function statusType(s: number) {
  if (s === 2) return 'success'
  if (s === 1) return 'primary'
  return 'default'
}

async function openDetail(item: MallExchangeItem) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getExchangeDetail(item.id)
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    detailLoading.value = false
  }
}

async function onCancel() {
  const d = detail.value
  if (!d) return
  // 用户点「取消」会 reject，必须捕获避免 Unhandled Rejection 报错
  try {
    await showConfirmDialog({
      title: '取消兑换',
      message: `确定取消「${d.productName}」？积分将退回账户`,
    })
  } catch {
    return
  }
  try {
    const vo = await cancelExchange(d.id)
    detail.value = vo
    showSuccessToast('积分已退回')
    reload()
  } catch {
    // 请求失败：拦截器已 Toast，此处避免未捕获异常
  }
}
</script>

<template>
  <div class="page exchange">
    <van-nav-bar title="我的兑换单" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="activeTab" sticky @change="reload">
      <van-tab v-for="t in tabs" :key="t.name" :name="t.name" :title="t.label" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-for="e in list" :key="e.id" class="exchange__item card" @click="openDetail(e)">
          <div class="exchange__head">
            <span class="exchange__name van-ellipsis">{{ e.productName }}</span>
            <van-tag :type="statusType(e.status)" plain>{{ e.statusDesc || EXCHANGE_STATUS_TEXT[e.status] }}</van-tag>
          </div>
          <div class="exchange__meta">
            <div>兑换时间：{{ e.createTime }}</div>
            <div class="exchange__address">{{ e.address || '虚拟商品，无需收货地址' }}</div>
          </div>
          <div class="exchange__foot">
            <span class="exchange__cost">-{{ e.pointsCost }} 积分 × {{ e.quantity }}</span>
            <span
              v-if="e.carbonLogId"
              class="exchange__trace"
              @click.stop="router.push('/carbon/bill')"
            >
              点击查看碳流水
            </span>
          </div>
        </div>
        <van-empty v-if="finished && list.length === 0" description="还没有兑换记录，去商城逛逛" />
      </van-list>
    </van-pull-refresh>

    <!-- 详情弹层 -->
    <van-popup v-model:show="detailVisible" position="bottom" round class="exchange__detail">
      <van-loading v-if="detailLoading" class="exchange__detail-loading" />
      <template v-else-if="detail">
        <div class="exchange__detail-title">兑换单详情</div>
        <div class="exchange__detail-row">
          <span>兑换单号</span><strong>{{ detail.exchangeNo }}</strong>
        </div>
        <div class="exchange__detail-row">
          <span>商品</span><strong>{{ detail.productName }} × {{ detail.quantity }}</strong>
        </div>
        <div class="exchange__detail-row">
          <span>积分</span><strong class="exchange__cost">-{{ detail.pointsCost }}</strong>
        </div>
        <div class="exchange__detail-row">
          <span>状态</span><strong>{{ detail.statusDesc || EXCHANGE_STATUS_TEXT[detail.status] }}</strong>
        </div>
        <div class="exchange__detail-row">
          <span>收货地址</span><strong>{{ detail.address || '虚拟商品，无需收货地址' }}</strong>
        </div>
        <div class="exchange__detail-row">
          <span>兑换时间</span><strong>{{ detail.createTime }}</strong>
        </div>
        <div v-if="detail.balanceAfter != null" class="exchange__detail-row">
          <span>积分余额</span><strong>{{ detail.balanceAfter }}</strong>
        </div>
        <van-button
          v-if="detail.status === 0"
          type="danger"
          plain
          round
          block
          class="exchange__detail-cancel"
          @click="onCancel"
        >
          取消兑换
        </van-button>
      </template>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.exchange {
  &__item {
    margin-bottom: $df-space-md;
  }

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: $df-space-sm;
  }

  &__name {
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
    justify-content: space-between;
    align-items: center;
    font-size: $df-font-size-sm;
  }

  &__cost {
    color: var(--df-color-gold);
    font-weight: 600;
  }

  &__trace {
    color: var(--df-color-primary);
    font-weight: 600;
  }

  &__detail {
    padding: $df-space-lg $df-space-md calc($df-space-lg + env(safe-area-inset-bottom));
  }

  &__detail-loading {
    padding: 40px 0;
  }

  &__detail-title {
    font-size: $df-font-size-lg;
    font-weight: 600;
    text-align: center;
    margin-bottom: $df-space-md;
  }

  &__detail-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: $df-space-md;
    padding: $df-space-sm 0;
    border-bottom: 1px solid var(--df-border-color);
    font-size: $df-font-size-sm;

    span {
      color: var(--df-text-secondary);
      flex-shrink: 0;
    }

    strong {
      text-align: right;
      word-break: break-all;
    }
  }

  &__detail-cancel {
    margin-top: $df-space-lg;
  }
}
</style>
