<script setup lang="ts">
// PC 跳蚤市场我的（Upgrade U4）：双 tab——我发布的（全状态 + 下架）/ 我买到的（取消/确认收货/评价）
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast, showToast } from 'vant'
import {
  pageFleaMineItems, pageFleaBuyerOrders,
  offFleaItem, cancelFleaOrder, receiveFleaOrder, reviewFleaOrder,
  FLEA_ITEM_STATUS_TEXT, FLEA_ORDER_STATUS_TEXT,
  type FleaItem, type FleaOrder,
} from '@/api/business'

defineOptions({ name: 'pc-flea-mine' })

const route = useRoute()
const router = useRouter()

/** 0=我发布的 1=我买到的（query.tab 可直达：published/bought） */
const activeTab = ref(route.query.tab === 'bought' ? 1 : 0)

// ---------------- 我发布的 ----------------
const mineList = ref<FleaItem[]>([])
const mineLoading = ref(false)
const mineFinished = ref(false)
let minePage = 1

// ---------------- 我买到的 ----------------
const boughtList = ref<FleaOrder[]>([])
const boughtLoading = ref(false)
const boughtFinished = ref(false)
let boughtPage = 1

const ITEM_STATUS = FLEA_ITEM_STATUS_TEXT
const ORDER_STATUS = FLEA_ORDER_STATUS_TEXT

async function loadMine() {
  mineLoading.value = true
  try {
    const page = await pageFleaMineItems({ pageNum: minePage, pageSize: 10 })
    if (minePage === 1) mineList.value = []
    mineList.value.push(...page.list)
    mineFinished.value = mineList.value.length >= page.total
    minePage += 1
  } catch {
    // U3 未补 /flea/items/mine 端点时静默降级为空列表
    if (minePage === 1) mineList.value = []
    mineFinished.value = true
    showToast('暂无发布记录')
  } finally {
    mineLoading.value = false
  }
}

function resetMine() {
  minePage = 1
  mineFinished.value = false
  loadMine()
}

async function loadBought() {
  boughtLoading.value = true
  try {
    const page = await pageFleaBuyerOrders({ pageNum: boughtPage, pageSize: 10 })
    if (boughtPage === 1) boughtList.value = []
    boughtList.value.push(...page.list)
    boughtFinished.value = boughtList.value.length >= page.total
    boughtPage += 1
  } finally {
    boughtLoading.value = false
  }
}

function resetBought() {
  boughtPage = 1
  boughtFinished.value = false
  loadBought()
}

watch(activeTab, (v) => {
  if (v === 0 && mineList.value.length === 0) resetMine()
  if (v === 1 && boughtList.value.length === 0) resetBought()
})

// ---------------- 操作 ----------------
async function onOff(item: FleaItem) {
  try {
    await showConfirmDialog({ title: '下架闲置', message: '确定下架「' + item.title + '」吗？' })
  } catch {
    return
  }
  try {
    await offFleaItem(item.id)
    showSuccessToast('已下架')
    resetMine()
  } catch {
    showFailToast('操作失败，请重试')
  }
}

async function onCancel(order: FleaOrder) {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确定取消该订单？冻结的积分将退回' })
  } catch {
    return
  }
  try {
    await cancelFleaOrder(order.id)
    showSuccessToast('已取消，积分已退回')
    resetBought()
  } catch {
    showFailToast('操作失败，请重试')
  }
}

async function onReceive(order: FleaOrder) {
  try {
    await showConfirmDialog({ title: '确认收货', message: '确认已收到？积分将支付给卖家' })
  } catch {
    return
  }
  try {
    await receiveFleaOrder(order.id)
    showSuccessToast('收货成功，积分已支付给卖家')
    resetBought()
  } catch {
    showFailToast('操作失败，请重试')
  }
}

// ---------------- 评价弹层 ----------------
const reviewTarget = ref<FleaOrder | null>(null)
const reviewRating = ref(5)
const reviewComment = ref('')
const reviewing = ref(false)

function onReview(order: FleaOrder) {
  reviewTarget.value = order
  reviewRating.value = 5
  reviewComment.value = ''
}

async function confirmReview() {
  const order = reviewTarget.value
  if (!order) return
  if (!reviewRating.value || reviewRating.value < 1) {
    showToast('请选择星级')
    return
  }
  reviewing.value = true
  try {
    await reviewFleaOrder(order.id, reviewRating.value, reviewComment.value.trim() || undefined)
    showSuccessToast('评价成功')
    reviewTarget.value = null
    resetBought()
  } finally {
    reviewing.value = false
  }
}

function toDetail(item: FleaItem) {
  router.push(`/pc/flea/detail/${item.id}`)
}

onMounted(() => {
  if (activeTab.value === 0) loadMine()
  else loadBought()
})
</script>

<template>
  <div class="pc-flea-mine">
    <div class="container">
      <h1 class="page-title">跳蚤市场 · 我的</h1>

      <div class="tabs">
        <span
          class="tabs__item"
          :class="{ 'tabs__item--on': activeTab === 0 }"
          @click="activeTab = 0"
        >我发布的</span>
        <span
          class="tabs__item"
          :class="{ 'tabs__item--on': activeTab === 1 }"
          @click="activeTab = 1"
        >我买到的</span>
      </div>

      <!-- 我发布的 -->
      <div v-show="activeTab === 0" class="card">
        <div v-if="mineLoading && mineList.length === 0" class="loading">加载中…</div>

        <div v-else-if="mineFinished && mineList.length === 0" class="empty">还没有发布过闲置</div>

        <div v-else class="list">
          <div v-for="item in mineList" :key="item.id" class="row" @click="toDetail(item)">
            <div class="row__img">
              <img v-if="item.imageUrl" v-lazy="item.imageUrl" alt="" />
              <div v-else class="row__img-fallback"><SvgIcon name="common-cube" :size="32" /></div>
            </div>
            <div class="row__body">
              <div class="row__title">{{ item.title }}</div>
              <div class="row__sub">
                <span class="row__price">{{ item.price }} 积分</span>
                <span class="row__status" :class="'row__status--' + item.status">
                  {{ ITEM_STATUS[item.status] }}
                </span>
              </div>
            </div>
            <button
              v-if="item.status === 1"
              class="row__action"
              @click.stop="onOff(item)"
            >下架</button>
          </div>
        </div>

        <button v-if="!mineFinished && mineList.length > 0" class="load-more" :disabled="mineLoading" @click="loadMine">
          {{ mineLoading ? '加载中…' : '加载更多' }}
        </button>
      </div>

      <!-- 我买到的 -->
      <div v-show="activeTab === 1" class="card">
        <div v-if="boughtLoading && boughtList.length === 0" class="loading">加载中…</div>

        <div v-else-if="boughtFinished && boughtList.length === 0" class="empty">还没有买到的商品，去市场逛逛吧</div>

        <div v-else class="list">
          <div v-for="order in boughtList" :key="order.id" class="order">
            <div class="order__head">
              <span class="order__no">单号 {{ order.orderNo }}</span>
              <span class="row__status" :class="'row__status--' + order.status">
                {{ ORDER_STATUS[order.status] }}
              </span>
            </div>
            <div class="order__body">
              <div class="row__img">
                <img v-if="order.itemImage" v-lazy="order.itemImage" alt="" />
                <div v-else class="row__img-fallback"><SvgIcon name="common-cube" :size="32" /></div>
              </div>
              <div class="row__body">
                <div class="row__title">{{ order.itemTitle }}</div>
                <div class="row__sub">
                  <span class="row__price">{{ order.price }} 积分</span>
                  <span class="row__peer">卖家 {{ order.peerName }}</span>
                </div>
                <div class="order__address">收货地址：{{ order.address }}</div>
                <div v-if="order.rating" class="order__reviewed">
                  <van-rate :model-value="order.rating" readonly size="14" />
                  <span v-if="order.comment" class="order__reviewed-text">{{ order.comment }}</span>
                </div>
              </div>
            </div>
            <div v-if="order.status === 0 || order.status === 1 || (order.status === 2 && !order.rating)" class="order__actions">
              <button v-if="order.status === 0" class="order__btn order__btn--danger" @click="onCancel(order)">取消订单</button>
              <button v-else-if="order.status === 1" class="order__btn order__btn--primary" @click="onReceive(order)">确认收货</button>
              <button v-else class="order__btn order__btn--plain" @click="onReview(order)">评价</button>
            </div>
          </div>
        </div>

        <button v-if="!boughtFinished && boughtList.length > 0" class="load-more" :disabled="boughtLoading" @click="loadBought">
          {{ boughtLoading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </div>

    <!-- 评价弹层：星级 + 评论 -->
    <div v-if="reviewTarget" class="pc-flea-mine__mask" @click.self="reviewTarget = null">
      <div class="pc-flea-mine__dialog">
        <h3 class="pc-flea-mine__dialog-title">评价商品</h3>
        <p class="pc-flea-mine__dialog-item van-ellipsis">{{ reviewTarget.itemTitle }}</p>
        <div class="pc-flea-mine__dialog-rate">
          <van-rate v-model="reviewRating" size="28" />
        </div>
        <textarea
          v-model="reviewComment"
          rows="3"
          maxlength="200"
          class="pc-flea-mine__dialog-textarea"
          placeholder="分享交易体验（选填，≤200 字）"
        ></textarea>
        <div class="pc-flea-mine__dialog-actions">
          <button class="pc-flea-mine__dialog-cancel" @click="reviewTarget = null">取消</button>
          <button class="pc-flea-mine__dialog-ok" :disabled="reviewing" @click="confirmReview">
            {{ reviewing ? '提交中…' : '提交评价' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-flea-mine {
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

.tabs {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 24px;

  &__item {
    padding: 8px 36px;
    border-radius: 999px;
    background: var(--df-bg-card);
    color: var(--df-text-secondary);
    font-size: 15px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      color: var(--df-color-primary);
    }

    &--on {
      background: var(--df-color-primary);
      color: #fff;
      font-weight: 600;
    }
  }
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

.row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--df-border-color);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: color-mix(in srgb, var(--df-color-secondary) 3%, transparent);
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

  &__body {
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

  &__status {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 600;

    &--0 { color: var(--df-text-secondary); }
    &--1 { color: var(--df-color-primary); }
    &--2 { color: var(--df-text-secondary); }
    &--3 { color: var(--df-color-danger); }
  }

  &__action {
    flex-shrink: 0;
    padding: 8px 20px;
    font-size: 14px;
    font-weight: 600;
    color: var(--df-color-warning);
    background: transparent;
    border: 2px solid var(--df-color-warning);
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      background: rgba(255, 150, 0, 0.08);
    }
  }
}

.order {
  padding: 16px 0;
  border-bottom: 1px solid var(--df-border-color);

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

  &__body {
    display: flex;
    gap: 16px;
    padding: 14px 0;
  }

  &__address {
    margin-top: 4px;
    font-size: 13px;
    color: var(--df-text-secondary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__reviewed {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
    font-size: 13px;
    color: var(--df-color-warning);

    .van-rate {
      flex-shrink: 0;
    }
  }

  &__reviewed-text {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: var(--df-text-secondary);
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding-top: 10px;
    border-top: 1px dashed var(--df-border-color);
  }

  &__btn {
    padding: 8px 24px;
    font-size: 14px;
    font-weight: 600;
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &--primary {
      color: #fff;
      background: var(--df-color-primary);
      border: none;

      &:hover { opacity: 0.9; }
    }

    &--danger {
      color: var(--df-color-danger);
      background: transparent;
      border: 2px solid var(--df-color-danger);

      &:hover { background: rgba(231, 76, 60, 0.06); }
    }

    &--plain {
      color: var(--df-color-primary);
      background: transparent;
      border: 2px solid var(--df-color-primary);

      &:hover { background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent); }
    }
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

.pc-flea-mine__mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.pc-flea-mine__dialog {
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
    margin-bottom: 16px;
  }

  &-item {
    padding: 12px 0;
    border-bottom: 1px solid var(--df-border-color);
    color: var(--df-text-secondary);
    font-size: 14px;
  }

  &-rate {
    display: flex;
    justify-content: center;
    padding: 16px 0;
  }

  &-textarea {
    width: 100%;
    padding: 12px 14px;
    font-size: 15px;
    font-family: inherit;
    line-height: 1.6;
    color: var(--df-text-primary);
    background: var(--df-bg-card);
    border: 2px solid var(--df-border-color);
    border-radius: 10px;
    outline: none;
    resize: vertical;
    transition: all 0.3s ease;

    &:focus {
      border-color: var(--df-color-primary);
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

  &-ok {
    flex: 1;
    padding: 12px;
    font-size: 15px;
    font-weight: 600;
    color: #fff;
    background: var(--df-color-primary);
    border: none;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover:not(:disabled) { opacity: 0.9; }
    &:disabled { opacity: 0.6; cursor: not-allowed; }
  }
}
</style>
