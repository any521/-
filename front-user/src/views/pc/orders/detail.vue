<script setup lang="ts">
// PC 订单详情：状态条 + 全程信息 + 取消/改期 + 完成后评价（B2 接真实后端）+ 员工实时定位（B2 替换 Mock）
// LoopFill B1：按 bizType 分发（REPAIR 评价/IM；KITCHEN/MALL 详情链接），解除 RECYCLE 硬编码
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import {
  getOrderDetail, cancelOrder, rescheduleOrder, type MyOrder,
  checkReviewed, submitReview, getStaffLocation,
} from '@/api/business'
import { createConversation } from '@/api/im'
import { OrderStatus, ORDER_STATUS_TEXT } from '@shared/http/types'
import { OrderStatusBar, TIME_SLOTS } from '@shared/index'
import { formatMoney, formatCarbon } from '@shared/utils/format'

const route = useRoute()
const router = useRouter()

/** 业务类型：query 优先（RECYCLE/REPAIR 走本页；KITCHEN/MALL/FLEA 只读展示 + 详情链接） */
  const bizType = computed<MyOrder['bizType']>(() => {
    const v = route.query.bizType
    return v === 'REPAIR' || v === 'KITCHEN' || v === 'MALL' || v === 'FLEA' ? v : 'RECYCLE'
  })

const order = ref<MyOrder | null>(null)
const loading = ref(true)

/** 待接单才允许取消/改期（memory.md 状态机；仅 RECYCLE） */
const canModify = computed(
  () => bizType.value === 'RECYCLE' && order.value?.status === OrderStatus.PENDING,
)
const isFinished = computed(() => order.value?.status === OrderStatus.FINISHED)

// ---- 联系工作人员挂载点（C-O2-4：onsite03 接入，创建/复用会话后跳 /im/:convId） ----
// LoopFill B1：IM 白名单含 RECYCLE/REPAIR，仅这两种类型展示
const canContact = computed(
  () => (bizType.value === 'RECYCLE' || bizType.value === 'REPAIR') && (order.value?.status === 1 || order.value?.status === 2),
)
async function onContactStaff() {
  if (!order.value) return
  try {
    // 幂等创建/复用会话（未接单时后端 400，拦截器已 toast 文案）
    // 注意：雪花 ID 19 位超 JS 安全整数，必须用 route 原始字符串而非 order.value.id（Number 已失真）
    const conv = await createConversation(bizType.value, String(route.params.id))
    // 显式带 /pc 前缀直达聊天页（不依赖守卫补前缀，避免未匹配落入 catch-all 跳首页）
    router.push({ path: `/pc/im/${conv.id}`, query: { peerName: conv.peerName } })
  } catch {
    // 失败提示已由拦截器统一弹出
  }
}

const showReschedule = ref(false)
const newDate = ref('')
const newSlot = ref(TIME_SLOTS[0] ?? '')
const dateOptions = Array.from({ length: 7 }, (_, i) => {
  const d = new Date(Date.now() + (i + 1) * 86400000)
  return d.toISOString().slice(0, 10)
})

// ---- 评价（B2：接真实后端，每单每人一次，已评价回显星级） ----
const rating = ref(0)
const reviewComment = ref('')
const reviewed = ref(false)
const submitting = ref(false)

// ---- 实时定位（B2：接单后每 5s 轮询员工位置） ----
const staffLoc = ref<{ lng: number; lat: number; staffName: string } | null>(null)
let locTimer: number | undefined
const mapUrl = computed(() => {
  const loc = staffLoc.value
  if (!loc) return ''
  return `https://uri.amap.com/marker?position=${loc.lng},${loc.lat}&name=${encodeURIComponent('回收员')}&src=point&coordinate=gaode`
})

async function load() {
  loading.value = true
  try {
    // LoopFill B1：雪花 ID 保持字符串防精度丢失
    order.value = await getOrderDetail(String(route.params.id), bizType.value)
    if (order.value.status === OrderStatus.FINISHED) {
      const c = await checkReviewed(bizType.value, order.value.id)
      reviewed.value = c.reviewed
      rating.value = c.rating ?? 0
    }
  } finally {
    loading.value = false
  }
}

async function onCancel() {
  if (!order.value) return
  await showConfirmDialog({ title: '取消订单', message: '确定取消该预约吗？' })
  await cancelOrder(order.value.id)
  showSuccessToast('已取消')
  await load()
}

async function onReschedule() {
  if (!newDate.value) return
  await rescheduleOrder(order.value!.id, newDate.value, newSlot.value)
  showReschedule.value = false
  showSuccessToast('改期成功')
  await load()
}

/** 提交评价（异步落库；成功后回显「已评价」） */
async function onRate() {
  if (!order.value || rating.value === 0 || submitting.value) return
  submitting.value = true
  try {
    // LoopFill B1：orderType 按 bizType 分发（后端已支持 RECYCLE/KITCHEN/REPAIR）
    await submitReview({
      orderType: bizType.value,
      orderId: order.value.id,
      rating: rating.value,
      comment: reviewComment.value || undefined,
    })
    reviewed.value = true
    showSuccessToast('感谢评价')
  } finally {
    submitting.value = false
  }
}

/** 定位轮询：仅 RECYCLE 有员工定位接口，拉一次接单员工位置（无接单/无上报返回 null，静默降级） */
async function pollStaffLocation() {
  if (!order.value || bizType.value !== 'RECYCLE' || (order.value.status !== 1 && order.value.status !== 2)) return
  try {
    staffLoc.value = await getStaffLocation(order.value.id)
  } catch {
    /* 轮询失败静默，下个周期重试 */
  }
}

function startLocPolling() {
  stopLocPolling()
  pollStaffLocation()
  locTimer = window.setInterval(pollStaffLocation, 5000)
}

function stopLocPolling() {
  if (locTimer !== undefined) {
    window.clearInterval(locTimer)
    locTimer = undefined
  }
}

/** 状态进入 1/2 开始轮询（仅 RECYCLE），脱离即停止 */
watch(
  () => order.value?.status,
  (s) => {
    if (bizType.value === 'RECYCLE' && (s === 1 || s === 2)) startLocPolling()
    else stopLocPolling()
  },
)

onMounted(load)
onBeforeUnmount(stopLocPolling)
</script>

<template>
  <div class="pc-detail">
    <div class="container">
      <div class="detail-header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" />
          返回
        </button>
        <h1 class="page-title">订单详情</h1>
      </div>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else-if="order">
        <div class="detail-card">
          <OrderStatusBar :status="order.status" />
        </div>

        <div class="detail-card">
          <h3 class="card-title">基本信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">订单号</span>
              <span class="info-value">{{ order.orderNo }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">业务类型</span>
              <span class="info-value">{{ order.title }}</span>
            </div>
            <div v-if="Number(order.servicePoints ?? 0) > 0" class="info-item">
              <span class="info-label">承诺报酬</span>
              <span class="info-value">{{ order.servicePoints ?? 0 }} 积分（完成后付给工作人员）</span>
            </div>
            <div v-if="order.appointTime" class="info-item">
              <span class="info-label">预约时间</span>
              <span class="info-value">{{ order.appointTime }}</span>
            </div>
            <div v-if="order.address" class="info-item">
              <span class="info-label">服务地址</span>
              <span class="info-value">{{ order.address }}</span>
            </div>
            <div v-if="order.staffName !== '—'" class="info-item">
              <span class="info-label">服务人员</span>
              <span class="info-value">{{ order.staffName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">下单时间</span>
              <span class="info-value">{{ order.createTime }}</span>
            </div>
          </div>
        </div>

        <!-- 结算信息（仅 RECYCLE 有结算字段） -->
        <div v-if="isFinished && order.bizType === 'RECYCLE'" class="detail-card">
          <h3 class="card-title">结算信息</h3>
          <div class="info-grid">
            <div v-if="order.amount > 0" class="info-item">
              <span class="info-label">交售金额</span>
              <span class="info-value amount">{{ formatMoney(order.amount) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">获得积分</span>
              <span class="info-value points">+{{ order.points }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">碳减排量</span>
              <span class="info-value carbon">{{ formatCarbon(order.carbon) }}</span>
            </div>
            <!-- 报酬结算：用户承诺报酬（完成时真实转账给工作人员）；1 已结算 / 2 异常转人工 -->
            <div v-if="order.settleStatus === 1" class="info-item">
              <span class="info-label">报酬结算</span>
              <span class="info-value points">
                {{ order.settleResult || `报酬 ${order.settlePoints} 积分 · 已结算给工作人员` }}
              </span>
            </div>
            <div v-else-if="order.settleStatus === 2" class="info-item">
              <span class="info-label">报酬结算</span>
              <span class="info-value">{{ order.settleResult || '报酬结算异常，平台人工处理中' }}</span>
            </div>
          </div>
          <p class="carbon-hint">已写入可信碳账本（哈希链锚定）</p>
        </div>

        <!-- 员工实时位置（仅 RECYCLE） -->
        <div v-if="order.bizType === 'RECYCLE' && (order.status === 1 || order.status === 2)" class="detail-card">
          <h3 class="card-title">员工实时位置</h3>
          <template v-if="staffLoc">
            <p class="loc-text">{{ staffLoc.staffName || order.staffName }} 正在赶来，位置实时更新</p>
            <a class="loc-link" :href="mapUrl" target="_blank" rel="noopener">查看地图 ›</a>
          </template>
          <p v-else class="loc-text">已接单，等待员工出发</p>
          <!-- onsite03 交付：联系工作人员挂载点（onContactStaff 一行接入，创建/复用会话后跳 /im/:convId） -->
          <van-button v-if="canContact" size="small" plain type="primary" @click="onContactStaff()">联系工作人员</van-button>
        </div>

        <!-- LoopFill B1：KITCHEN/MALL/FLEA 详情链接（C18 并入跳蚤） -->
        <div v-if="order.bizType === 'KITCHEN' || order.bizType === 'MALL' || order.bizType === 'FLEA'" class="detail-card">
          <h3 class="card-title">{{ order.bizType === 'KITCHEN' ? '垃圾投放记录' : order.bizType === 'MALL' ? '商城兑换单' : '闲置交易' }}</h3>
          <div class="info-grid">
            <div v-if="order.points !== 0" class="info-item">
              <span class="info-label">积分</span>
              <span class="info-value points">{{ order.points > 0 ? `+${order.points}` : `${order.points}` }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">收货地址</span>
              <span class="info-value">{{ order.address || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">状态</span>
              <span class="info-value">{{ order.statusDesc || ORDER_STATUS_TEXT[order.status] }}</span>
            </div>
          </div>
          <button
            class="action-btn action-btn--primary detail-link-btn"
            @click="router.push(order.bizType === 'KITCHEN' ? '/kitchen/records' : order.bizType === 'MALL' ? '/mall/exchange' : '/flea/mine?tab=bought')"
          >
            查看{{ order.bizType === 'KITCHEN' ? '投放记录' : order.bizType === 'MALL' ? '兑换单' : '闲置交易' }}
          </button>
        </div>

        <!-- 服务评价（RECYCLE/REPAIR） -->
        <div v-if="isFinished && (order.bizType === 'RECYCLE' || order.bizType === 'REPAIR')" class="detail-card">
          <h3 class="card-title">服务评价</h3>
          <template v-if="!reviewed">
            <van-rate v-model="rating" :size="26" />
            <van-field
              v-model="reviewComment"
              type="textarea"
              rows="2"
              maxlength="500"
              placeholder="说说本次服务体验（选填）"
            />
            <button
              class="action-btn action-btn--primary rate-btn"
              :disabled="rating === 0 || submitting"
              @click="onRate"
            >
              {{ submitting ? '提交中…' : '提交评价' }}
            </button>
          </template>
          <div v-else class="reviewed-text">已评价 {{ rating }} 星，感谢您的反馈</div>
        </div>

        <div v-if="canModify" class="detail-actions">
          <button class="action-btn" @click="showReschedule = true">改期</button>
          <button class="action-btn action-btn--danger" @click="onCancel">取消订单</button>
        </div>
      </template>
      <div v-else class="empty">订单不存在</div>
    </div>

    <!-- 改期弹层 -->
    <div v-if="showReschedule" class="modal-overlay" @click.self="showReschedule = false">
      <div class="modal">
        <h3 class="modal-title">选择新的上门时间</h3>
        <div class="modal-label">日期</div>
        <div class="chips">
          <button
            v-for="d in dateOptions"
            :key="d"
            class="chip"
            :class="{ active: newDate === d }"
            @click="newDate = d"
          >
            {{ d.slice(5) }}
          </button>
        </div>
        <div class="modal-label">时段</div>
        <div class="chips">
          <button
            v-for="s in TIME_SLOTS"
            :key="s"
            class="chip"
            :class="{ active: newSlot === s }"
            @click="newSlot = s"
          >
            {{ s }}
          </button>
        </div>
        <div class="modal-actions">
          <button class="action-btn" @click="showReschedule = false">取消</button>
          <button class="action-btn action-btn--primary" :disabled="!newDate" @click="onReschedule">确认改期</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-detail {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 800px;
  margin: 0 auto;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 48px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 14px;
  color: var(--df-text-regular);
  background: var(--df-bg-card);
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin: 0;
}

.detail-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin: 0 0 20px;
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--df-border-color);

  &:last-child { border-bottom: none; padding-bottom: 0; }
}

.info-label {
  font-size: 15px;
  color: var(--df-text-secondary);
}

.info-value {
  font-size: 15px;
  font-weight: 500;
  color: var(--df-text-primary);

  &.amount { color: var(--df-color-danger); font-weight: 600; }
  &.points { color: var(--df-color-gold); font-weight: 600; }
  &.carbon { color: var(--df-color-carbon); font-weight: 600; }
}

.carbon-hint {
  margin: 16px 0 0;
  font-size: 13px;
  color: var(--df-text-secondary);
}

.loc-text {
  margin: 0;
  font-size: 15px;
  color: var(--df-text-regular);
}

.loc-link {
  display: inline-block;
  margin-top: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--df-color-primary);
  text-decoration: none;

  &:hover { text-decoration: underline; }
}

.rate-btn {
  display: inline-block;
  margin-top: 16px;
  font-size: 14px;
  padding: 10px 32px;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

/* LoopFill B1：KITCHEN/MALL 详情链接按钮 */
.detail-link-btn {
  margin-top: 20px;
  font-size: 14px;
  padding: 10px 32px;
}

/* LoopFill B1：订单不存在空态 */
.empty {
  text-align: center;
  padding: 80px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.reviewed-text {
  font-size: 15px;
  color: var(--df-text-secondary);
}

.detail-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 32px;
}

.action-btn {
  padding: 12px 40px;
  font-size: 16px;
  font-weight: 600;
  color: var(--df-color-primary);
  background: var(--df-bg-card);
  border: 2px solid var(--df-color-primary);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  &--primary {
    color: white;
    background: var(--df-color-primary);
    border-color: transparent;
    &:hover { background: var(--df-color-primary-dark); }
  }

  &--danger {
    color: var(--df-color-danger);
    border-color: var(--df-color-danger);
    &:hover { background: rgba(231, 76, 60, 0.05); }
  }
}

.loading {
  text-align: center;
  padding: 80px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

// 改期弹层
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  width: 460px;
  max-width: 90vw;
  background: var(--df-bg-card);
  border-radius: 20px;
  padding: 32px;
}

.modal-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin: 0 0 24px;
}

.modal-label {
  margin: 16px 0 8px;
  color: var(--df-text-secondary);
  font-size: 14px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.chip {
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 500;
  color: var(--df-text-regular);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover { border-color: var(--df-color-primary); }
  &.active {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
    font-weight: 600;
  }
}

.modal-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  justify-content: flex-end;
}
</style>