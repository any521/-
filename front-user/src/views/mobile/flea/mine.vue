<script setup lang="ts">
// 跳蚤市场我的（Upgrade U4）：双 tab——我发布的（全状态 + 下架）/ 我买到的（取消/确认收货/评价）
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast, showToast } from 'vant'
import {
  pageFleaMineItems, pageFleaBuyerOrders,
  offFleaItem, cancelFleaOrder, receiveFleaOrder, reviewFleaOrder,
  FLEA_ITEM_STATUS_TEXT, FLEA_ORDER_STATUS_TEXT,
  type FleaItem, type FleaOrder,
} from '@/api/business'

defineOptions({ name: 'flea-mine' })

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
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
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
  } catch {
    // 评价失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    reviewing.value = false
  }
}

function onReviewClose() {
  if (!reviewing.value) reviewTarget.value = null
}

function toDetail(item: FleaItem) {
  router.push(`/mobile/flea/detail/${item.id}`)
}

function toPublish() {
  router.push('/mobile/flea/publish')
}

onMounted(() => {
  if (activeTab.value === 0) loadMine()
  else loadBought()
})
</script>

<template>
  <div class="page flea-mine">
    <van-nav-bar title="跳蚤市场 · 我的" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="activeTab" sticky>
      <!-- 我发布的 -->
      <van-tab title="我发布的">
        <van-list v-model:loading="mineLoading" :finished="mineFinished" finished-text="没有更多了" @load="loadMine">
          <div v-if="mineList.length > 0" class="flea-mine__list">
            <div v-for="item in mineList" :key="item.id" class="card flea-mine__item" @click="toDetail(item)">
              <div class="flea-mine__item-img">
                <img v-if="item.imageUrl" v-lazy="item.imageUrl" alt="" />
                <div v-else class="flea-mine__item-img-fallback"><SvgIcon name="common-cube" :size="28" /></div>
              </div>
              <div class="flea-mine__item-body">
                <div class="flea-mine__item-title">{{ item.title }}</div>
                <div class="flea-mine__item-foot">
                  <span class="flea-mine__price">{{ item.price }} 积分</span>
                  <span class="flea-mine__status" :class="'flea-mine__status--' + item.status">
                    {{ ITEM_STATUS[item.status] }}
                  </span>
                </div>
                <van-button
                  v-if="item.status === 1"
                  size="mini"
                  round
                  plain
                  type="warning"
                  class="flea-mine__off-btn"
                  @click.stop="onOff(item)"
                >下架</van-button>
              </div>
            </div>
          </div>
          <van-empty v-if="mineFinished && mineList.length === 0" description="还没有发布过闲置">
            <van-button size="small" round type="primary" plain @click="toPublish">发布闲置</van-button>
          </van-empty>
        </van-list>
      </van-tab>

      <!-- 我买到的 -->
      <van-tab title="我买到的">
        <van-list v-model:loading="boughtLoading" :finished="boughtFinished" finished-text="没有更多了" @load="loadBought">
          <div v-if="boughtList.length > 0" class="flea-mine__list">
            <div v-for="order in boughtList" :key="order.id" class="card flea-mine__order">
              <div class="flea-mine__order-head">
                <span class="flea-mine__order-no">单号 {{ order.orderNo }}</span>
                <span class="flea-mine__status" :class="'flea-mine__status--' + order.status">
                  {{ ORDER_STATUS[order.status] }}
                </span>
              </div>
              <div class="flea-mine__order-body">
                <div class="flea-mine__item-img">
                  <img v-if="order.itemImage" v-lazy="order.itemImage" alt="" />
                  <div v-else class="flea-mine__item-img-fallback"><SvgIcon name="common-cube" :size="28" /></div>
                </div>
                <div class="flea-mine__item-body">
                  <div class="flea-mine__item-title">{{ order.itemTitle }}</div>
                  <div class="flea-mine__item-foot">
                    <span class="flea-mine__price">{{ order.price }} 积分</span>
                    <span class="flea-mine__seller">卖家 {{ order.peerName }}</span>
                  </div>
                  <div v-if="order.rating" class="flea-mine__reviewed">
                    <van-rate :model-value="order.rating" readonly size="12" />
                    <span v-if="order.comment" class="flea-mine__reviewed-text">{{ order.comment }}</span>
                  </div>
                </div>
              </div>
              <div class="flea-mine__order-actions">
                <template v-if="order.status === 0">
                  <van-button size="mini" round plain type="danger" @click="onCancel(order)">取消订单</van-button>
                </template>
                <template v-else-if="order.status === 1">
                  <van-button size="mini" round type="primary" @click="onReceive(order)">确认收货</van-button>
                </template>
                <template v-else-if="order.status === 2 && !order.rating">
                  <van-button size="mini" round plain type="primary" @click="onReview(order)">评价</van-button>
                </template>
              </div>
            </div>
          </div>
          <van-empty v-if="boughtFinished && boughtList.length === 0" description="还没有买到的商品，去市场逛逛吧">
            <van-button size="small" round type="primary" plain @click="router.push('/mall')">去逛逛</van-button>
          </van-empty>
        </van-list>
      </van-tab>
    </van-tabs>

    <!-- 评价弹层：星级 + 评论 -->
    <van-popup :show="!!reviewTarget" position="bottom" round class="flea-mine__review" @update:show="onReviewClose">
      <template v-if="reviewTarget">
        <div class="flea-mine__review-title">评价商品</div>
        <div class="flea-mine__review-item van-ellipsis">{{ reviewTarget.itemTitle }}</div>
        <div class="flea-mine__review-rate">
          <van-rate v-model="reviewRating" size="24" />
        </div>
        <van-field
          v-model="reviewComment"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="分享交易体验（选填，≤200 字）"
        />
        <van-button type="primary" round block :loading="reviewing" class="flea-mine__review-btn" @click="confirmReview">
          提交评价
        </van-button>
      </template>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.flea-mine {
  &__list {
    padding: $df-space-md;
  }

  &__item,
  &__order {
    margin-bottom: $df-space-sm;
  }

  &__item {
    display: flex;
    gap: $df-space-sm;
    padding: $df-space-sm;
    cursor: pointer;
  }

  &__order {
    padding: $df-space-sm;
  }

  &__order-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: $df-space-xs;
    border-bottom: 1px dashed var(--df-border-color);
  }

  &__order-no {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__order-body {
    display: flex;
    gap: $df-space-sm;
    padding: $df-space-sm 0;
  }

  &__item-img {
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

  &__item-img-fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--df-bg-page);
    color: var(--df-text-secondary);
  }

  &__item-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  &__item-title {
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-text-primary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__item-foot {
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

  &__seller {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__status {
    flex-shrink: 0;
    font-size: $df-font-size-xs;
    font-weight: 600;

    &--0 { color: var(--df-text-secondary); }
    &--1 { color: var(--df-color-primary); }
    &--2 { color: var(--df-text-secondary); }
    &--3 { color: var(--df-color-danger); }
  }

  &__off-btn {
    align-self: flex-start;
    margin-top: $df-space-xs;
  }

  &__reviewed {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
    margin-top: $df-space-xs;
    color: var(--df-color-warning);
    font-size: $df-font-size-xs;

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

  &__order-actions {
    display: flex;
    justify-content: flex-end;
    padding-top: $df-space-xs;
    border-top: 1px dashed var(--df-border-color);
  }

  &__review {
    padding: $df-space-lg $df-space-md calc($df-space-lg + env(safe-area-inset-bottom));
  }

  &__review-title {
    font-size: $df-font-size-lg;
    font-weight: 600;
    text-align: center;
    margin-bottom: $df-space-md;
  }

  &__review-item {
    padding: $df-space-sm 0;
    border-bottom: 1px solid var(--df-border-color);
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__review-rate {
    display: flex;
    justify-content: center;
    padding: $df-space-md 0;
  }

  &__review-btn {
    margin-top: $df-space-lg;
  }
}
</style>
