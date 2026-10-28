<script setup lang="ts">
// PC 跳蚤市场我卖出的（Upgrade U4，卖家视角）：待发货发货 / 待收货 / 已完成 / 已取消
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import {
  pageFleaSellerOrders, shipFleaOrder,
  FLEA_ORDER_STATUS_TEXT,
  type FleaOrder,
} from '@/api/business'

defineOptions({ name: 'pc-flea-orders' })

const router = useRouter()

const list = ref<FleaOrder[]>([])
const loading = ref(false)
const finished = ref(false)
let pageNum = 1

const ORDER_STATUS = FLEA_ORDER_STATUS_TEXT

async function loadMore() {
  loading.value = true
  try {
    const page = await pageFleaSellerOrders({ pageNum, pageSize: 10 })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } finally {
    loading.value = false
  }
}

async function onShip(order: FleaOrder) {
  try {
    await showConfirmDialog({ title: '确认发货', message: '已发货？确认后将通知买家等待收货' })
  } catch {
    return
  }
  try {
    await shipFleaOrder(order.id)
    showSuccessToast('已发货')
    pageNum = 1
    finished.value = false
    loadMore()
  } catch {
    showFailToast('操作失败，请重试')
  }
}

onMounted(() => {
  loadMore()
})
</script>

<template>
  <div class="pc-flea-orders">
    <div class="container">
      <h1 class="page-title">我卖出的</h1>

      <div class="card">
        <div v-if="loading && list.length === 0" class="loading">加载中…</div>

        <div v-else-if="finished && list.length === 0" class="empty">还没有卖出记录，去发布闲置吧</div>

        <div v-else class="list">
          <div v-for="order in list" :key="order.id" class="order">
            <div class="order__head">
              <span class="order__no">单号 {{ order.orderNo }}</span>
              <span class="order__status" :class="'order__status--' + order.status">
                {{ ORDER_STATUS[order.status] }}
              </span>
            </div>
            <div class="order__body">
              <div class="order__img">
                <img v-if="order.itemImage" v-lazy="order.itemImage" alt="" />
                <div v-else class="order__img-fallback"><SvgIcon name="common-cube" :size="32" /></div>
              </div>
              <div class="order__info">
                <div class="order__title">{{ order.itemTitle }}</div>
                <div class="order__sub">
                  <span class="order__price">{{ order.price }} 积分</span>
                  <span class="order__peer">买家 {{ order.peerName }}</span>
                </div>
                <div class="order__address">收货地址：{{ order.address }}</div>
              </div>
              <button v-if="order.status === 0" class="order__ship" @click="onShip(order)">发货</button>
            </div>
          </div>
        </div>

        <button v-if="!finished && list.length > 0" class="load-more" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
        <p v-else-if="finished && list.length > 0" class="no-more">没有更多了</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-flea-orders {
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

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.order {
  padding: 16px 0;
  border-bottom: 1px solid var(--df-border-color);

  &:last-of-type { border-bottom: none; }

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 10px;
    border-bottom: 1px dashed var(--df-border-color);
  }

  &__no {
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &__status {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 600;

    &--0 { color: var(--df-color-warning); }
    &--1 { color: var(--df-color-primary); }
    &--2 { color: var(--df-text-secondary); }
    &--3 { color: var(--df-color-danger); }
  }

  &__body {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 14px 0;
  }

  &__img {
    width: 72px;
    height: 72px;
    flex-shrink: 0;
    border-radius: 10px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }

  &__img-fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--df-bg-card);
    color: var(--df-text-secondary);
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-primary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__sub {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 8px;
  }

  &__price {
    color: var(--df-color-gold);
    font-weight: 700;
    font-size: 16px;
  }

  &__peer {
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &__address {
    margin-top: 4px;
    font-size: 13px;
    color: var(--df-text-secondary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__ship {
    flex-shrink: 0;
    padding: 10px 28px;
    font-size: 14px;
    font-weight: 600;
    color: #fff;
    background: var(--df-color-primary);
    border: none;
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover { opacity: 0.9; }
  }
}

.load-more {
  width: 100%;
  margin-top: 24px;
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
