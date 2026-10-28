<script setup lang="ts">
// PC 我的兑换单（LoopFill B2）：状态筛选 Tab + 分页列表 + 详情弹层 + 待发货可取消
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  pageMyExchanges, getExchangeDetail, cancelExchange, EXCHANGE_STATUS_TEXT,
  type MallExchangeItem,
} from '@/api/business'
import { dfConfirm, dfToast } from '@shared/feedback'

defineOptions({ name: 'pc-mall-exchange' })

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
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum = 1
  finished.value = false
  loadMore()
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
  } finally {
    detailLoading.value = false
  }
}

async function onCancel() {
  const d = detail.value
  if (!d) return
  const ok = await dfConfirm({
    kind: 'confirm',
    tone: 'warning',
    title: '取消兑换',
    message: `确定取消「${d.productName}」？积分将退回账户`,
    confirmText: '确认取消',
    danger: true,
  })
  if (!ok) return
  const vo = await cancelExchange(d.id)
  detail.value = vo
  dfToast('积分已退回', 'success')
  reload()
}
</script>

<template>
  <div class="pc-exchange">
    <div class="container">
      <h1 class="page-title">我的兑换单</h1>

      <div class="card">
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

        <div v-else-if="finished && list.length === 0" class="empty">还没有兑换记录，去商城逛逛</div>

        <div v-else class="exchange-list">
          <div v-for="e in list" :key="e.id" class="exchange-item" @click="openDetail(e)">
            <div class="exchange-item__head">
              <div class="exchange-item__name">{{ e.productName }}</div>
              <span class="exchange-item__status" :class="`exchange-item__status--${statusType(e.status)}`">
                {{ e.statusDesc || EXCHANGE_STATUS_TEXT[e.status] }}
              </span>
            </div>
            <div class="exchange-item__meta">
              <span>兑换时间：{{ e.createTime }}</span>
              <span>{{ e.address || '虚拟商品，无需收货地址' }}</span>
            </div>
            <div class="exchange-item__foot">
              <span class="exchange-item__cost">-{{ e.pointsCost }} 积分 × {{ e.quantity }}</span>
              <span
                v-if="e.carbonLogId"
                class="exchange-item__trace"
                @click.stop="router.push('/pc/carbon/bill')"
              >
                点击查看碳流水
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

    <!-- 详情弹层 -->
    <div v-if="detailVisible" class="pc-exchange__mask" @click.self="detailVisible = false">
      <div class="pc-exchange__dialog">
        <h3 class="pc-exchange__dialog-title">兑换单详情</h3>
        <van-loading v-if="detailLoading" class="pc-exchange__dialog-loading" />
        <template v-else-if="detail">
          <div class="pc-exchange__dialog-row">
            <span>兑换单号</span><strong>{{ detail.exchangeNo }}</strong>
          </div>
          <div class="pc-exchange__dialog-row">
            <span>商品</span><strong>{{ detail.productName }} × {{ detail.quantity }}</strong>
          </div>
          <div class="pc-exchange__dialog-row">
            <span>积分</span><strong class="pc-exchange__cost">-{{ detail.pointsCost }}</strong>
          </div>
          <div class="pc-exchange__dialog-row">
            <span>状态</span><strong>{{ detail.statusDesc || EXCHANGE_STATUS_TEXT[detail.status] }}</strong>
          </div>
          <div class="pc-exchange__dialog-row">
            <span>收货地址</span><strong>{{ detail.address || '虚拟商品，无需收货地址' }}</strong>
          </div>
          <div class="pc-exchange__dialog-row">
            <span>兑换时间</span><strong>{{ detail.createTime }}</strong>
          </div>
          <div v-if="detail.balanceAfter != null" class="pc-exchange__dialog-row">
            <span>积分余额</span><strong>{{ detail.balanceAfter }}</strong>
          </div>
          <div class="pc-exchange__dialog-actions">
            <button class="pc-exchange__dialog-cancel" @click="detailVisible = false">关闭</button>
            <button v-if="detail.status === 0" class="pc-exchange__dialog-danger" @click="onCancel">
              取消兑换
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-exchange {
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

.card {
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

.exchange-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exchange-item {
  padding: 20px;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 3%, transparent);
  }

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  &__name {
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__status {
    font-size: 13px;
    font-weight: 500;
    padding: 4px 12px;
    border-radius: 999px;

    &--primary { background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary); }
    &--success { background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary); }
    &--default { background: var(--df-bg-card); color: var(--df-text-secondary); }
  }

  &__meta {
    display: flex;
    gap: 24px;
    margin-bottom: 12px;
    font-size: 14px;
    color: var(--df-text-secondary);
  }

  &__foot {
    display: flex;
    gap: 16px;
    font-size: 14px;
  }

  &__cost {
    color: var(--df-color-gold);
    font-weight: 600;
  }

  &__trace {
    margin-left: auto;
    color: var(--df-color-primary);
    font-weight: 600;
    cursor: pointer;
  }
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

.pc-exchange__mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.pc-exchange__dialog {
  width: 520px;
  max-width: calc(100vw - 48px);
  padding: 32px;
  background: var(--df-bg-card);
  border-radius: 16px;
  box-shadow: var(--df-shadow-float);

  &-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--df-text-primary);
    text-align: center;
    margin-bottom: 20px;
  }

  &-loading {
    padding: 40px 0;
  }

  &-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    padding: 10px 0;
    border-bottom: 1px solid var(--df-border-color);
    font-size: 14px;

    span {
      color: var(--df-text-secondary);
      flex-shrink: 0;
    }

    strong {
      text-align: right;
      word-break: break-all;
      color: var(--df-text-primary);
    }
  }

  &-actions {
    display: flex;
    gap: 12px;
    margin-top: 24px;
  }

  &-cancel {
    flex: 1;
    padding: 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--df-text-regular);
    background: transparent;
    border: 2px solid var(--df-border-color);
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      border-color: var(--df-color-primary);
      color: var(--df-color-primary);
    }
  }

  &-danger {
    flex: 1;
    padding: 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--df-color-danger);
    background: transparent;
    border: 2px solid var(--df-color-danger);
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      background: rgba(231, 76, 60, 0.08);
    }
  }
}
</style>
