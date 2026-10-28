<script setup lang="ts">
// 跳蚤市场我卖出的（Upgrade U4，卖家视角）：待发货发货 / 待收货 / 已完成 / 已取消
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import {
  pageFleaSellerOrders, shipFleaOrder,
  FLEA_ORDER_STATUS_TEXT,
  type FleaOrder,
} from '@/api/business'

defineOptions({ name: 'flea-orders' })

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
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
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
  <div class="page flea-orders">
    <van-nav-bar title="我卖出的" left-arrow @click-left="router.back()" />

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
      <div v-if="list.length > 0" class="flea-orders__list">
        <div v-for="order in list" :key="order.id" class="card flea-orders__order">
          <div class="flea-orders__head">
            <span class="flea-orders__no">单号 {{ order.orderNo }}</span>
            <span class="flea-orders__status" :class="'flea-orders__status--' + order.status">
              {{ ORDER_STATUS[order.status] }}
            </span>
          </div>
          <div class="flea-orders__body">
            <div class="flea-orders__img">
              <img v-if="order.itemImage" v-lazy="order.itemImage" alt="" />
              <div v-else class="flea-orders__img-fallback"><SvgIcon name="common-cube" :size="28" /></div>
            </div>
            <div class="flea-orders__info">
              <div class="flea-orders__title">{{ order.itemTitle }}</div>
              <div class="flea-orders__row">
                <span class="flea-orders__price">{{ order.price }} 积分</span>
                <span class="flea-orders__peer">买家 {{ order.peerName }}</span>
              </div>
              <div class="flea-orders__address">收货地址：{{ order.address }}</div>
            </div>
          </div>
          <div v-if="order.status === 0" class="flea-orders__actions">
            <van-button size="mini" round type="primary" @click="onShip(order)">发货</van-button>
          </div>
        </div>
      </div>
      <van-empty v-if="finished && list.length === 0" description="还没有卖出记录，去发布闲置吧">
        <van-button size="small" round type="primary" plain @click="router.push('/mobile/flea/publish')">
          发布闲置
        </van-button>
      </van-empty>
    </van-list>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.flea-orders {
  &__list {
    padding: $df-space-md;
  }

  &__order {
    margin-bottom: $df-space-sm;
    padding: $df-space-sm;
  }

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: $df-space-xs;
    border-bottom: 1px dashed var(--df-border-color);
  }

  &__no {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__status {
    flex-shrink: 0;
    font-size: $df-font-size-xs;
    font-weight: 600;

    &--0 { color: var(--df-color-warning); }
    &--1 { color: var(--df-color-primary); }
    &--2 { color: var(--df-text-secondary); }
    &--3 { color: var(--df-color-danger); }
  }

  &__body {
    display: flex;
    gap: $df-space-sm;
    padding: $df-space-sm 0;
  }

  &__img {
    width: 72px;
    height: 72px;
    flex-shrink: 0;
    border-radius: $df-radius-sm;
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
    background: var(--df-bg-page);
    color: var(--df-text-secondary);
  }

  &__info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  &__title {
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-text-primary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__row {
    margin-top: auto;
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: $df-space-xs;
  }

  &__price {
    color: var(--df-color-gold);
    font-weight: 600;
    font-size: $df-font-size-base;
  }

  &__peer {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__address {
    margin-top: 2px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    padding-top: $df-space-xs;
    border-top: 1px dashed var(--df-border-color);
  }
}
</style>
