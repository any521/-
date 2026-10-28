<script setup lang="ts">
// 订单详情：状态条 + 全程信息 + 取消/改期（仅待接单）+ 完成后评价（B2 接真实后端）+ 员工实时定位（B2 替换 Mock）
// LoopFill B1：按 bizType 分发（REPAIR 评价/IM；KITCHEN/MALL 详情链接），解除 RECYCLE 硬编码
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
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
    // 显式带 /mobile 前缀直达聊天页（不依赖守卫补前缀，避免未匹配落入 catch-all 跳首页）
    router.push({ path: `/mobile/im/${conv.id}`, query: { peerName: conv.peerName } })
  } catch {
    // 失败提示已由拦截器统一弹出
  }
}

// ---- 改期弹层 ----
const showReschedule = ref(false)
const newDate = ref('')
const newSlot = ref(TIME_SLOTS[0] ?? '')
/** 未来 7 天可约日期 */
const dateOptions = Array.from({ length: 7 }, (_, i) => {
  const d = new Date(Date.now() + (i + 1) * 86400000)
  return d.toISOString().slice(0, 10)
})

// ---- 评价（B2：接真实后端，每单每人一次，已评价回显星级） ----
const rating = ref(0)
const reviewComment = ref('')
const reviewed = ref(false)
const submitting = ref(false)

// ---- 实时定位（B2：接单后每 5s 轮询员工位置，替换随机 Mock） ----
const staffLoc = ref<{ lng: number; lat: number; staffName: string } | null>(null)
let locTimer: number | undefined
/** 高德 URI 跳转链接（有位置时展示） */
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
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
}

async function onCancel() {
  // 用户点「取消」会 reject，必须捕获避免 Unhandled Rejection 报错
  try {
    await showConfirmDialog({ title: '取消订单', message: '确定取消该预约吗？' })
  } catch {
    return
  }
  try {
    await cancelOrder(order.value!.id)
    showSuccessToast('已取消')
    await load()
  } catch {
    // 请求失败：拦截器已 Toast，此处避免未捕获异常
  }
}

async function onReschedule() {
  if (!newDate.value) return
  try {
    await rescheduleOrder(order.value!.id, newDate.value, newSlot.value)
    showReschedule.value = false
    showSuccessToast('改期成功')
    await load()
  } catch {
    // 请求失败：拦截器已 Toast，此处避免未捕获异常
  }
}

function callStaff(phone: string) {
  if (phone) window.location.href = `tel:${phone}`
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
  } catch {
    // 评价失败：拦截器已 Toast，此处避免未捕获异常
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
  <div class="page detail">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />
    <van-skeleton :loading="loading" title :row="6">
      <template v-if="order">
        <!-- 状态 -->
        <div class="card">
          <OrderStatusBar :status="order.status" />
        </div>

        <!-- 基本信息 -->
        <van-cell-group inset>
          <van-cell title="订单号" :value="order.orderNo" />
          <van-cell title="业务类型" :value="order.title" />
          <van-cell v-if="Number(order.servicePoints ?? 0) > 0" title="承诺报酬" :value="`${order.servicePoints ?? 0} 积分（完成后付给工作人员）`" />
          <van-cell v-if="order.appointTime" title="预约时间" :value="order.appointTime" />
          <van-cell v-if="order.address" title="服务地址" :value="order.address" />
          <van-cell v-if="order.staffName !== '—'" title="服务人员" :value="order.staffName" />
          <van-cell v-if="order.staffName !== '—'" title="联系电话" :value="order.staffPhone" is-link
            @click="callStaff(order.staffPhone)" />
          <van-cell title="下单时间" :value="order.createTime" />
        </van-cell-group>

        <!-- 全程定位（B2：接单后轮询员工实时位置；无位置降级文案；仅 RECYCLE） -->
        <div v-if="order.bizType === 'RECYCLE' && (order.status === 1 || order.status === 2)" class="card detail__map">
          <template v-if="staffLoc">
            <SvgIcon name="nav-map" /> {{ staffLoc.staffName || order.staffName }} 正在赶来，位置实时更新
            <a class="detail__map-link" :href="mapUrl" target="_blank" rel="noopener">查看地图</a>
          </template>
          <template v-else>
            <SvgIcon name="nav-map" /> 已接单，等待员工出发
          </template>
          <!-- onsite03 交付：联系工作人员挂载点（onContactStaff 一行接入，创建/复用会话后跳 /im/:convId） -->
          <van-button v-if="canContact" size="small" plain type="primary" @click="onContactStaff()">联系工作人员</van-button>
        </div>

        <!-- 结算信息（仅 RECYCLE 有结算字段） -->
        <van-cell-group v-if="isFinished && order.bizType === 'RECYCLE'" inset title="结算信息">
          <van-cell v-if="order.amount > 0" title="交售金额" :value="formatMoney(order.amount)" />
          <van-cell title="获得积分" :value="`+${order.points}`" />
          <van-cell title="碳减排量" :value="formatCarbon(order.carbon)" label="已写入可信碳账本（哈希链锚定）" />
          <!-- 报酬结算：用户承诺报酬（完成时真实转账给工作人员）；1 已结算 / 2 异常转人工 -->
          <van-cell
            v-if="order.settleStatus === 1"
            title="报酬结算"
            :value="order.settleResult || `报酬 ${order.settlePoints} 积分 · 已结算给工作人员`"
          />
          <van-cell
            v-else-if="order.settleStatus === 2"
            title="报酬结算"
            :value="order.settleResult || '报酬结算异常，平台人工处理中'"
          />
        </van-cell-group>

        <!-- LoopFill B1：KITCHEN/MALL/FLEA 详情链接（B2/B4 页面建成后生效） -->
        <div v-if="order.bizType === 'KITCHEN' || order.bizType === 'MALL' || order.bizType === 'FLEA'" class="card">
          <div class="card__title">{{ order.bizType === 'KITCHEN' ? '垃圾投放记录' : order.bizType === 'MALL' ? '商城兑换单' : '闲置交易' }}</div>
          <van-cell v-if="order.points !== 0" title="积分" :value="order.points > 0 ? `+${order.points}` : `${order.points}`" />
          <van-cell
            :title="order.bizType === 'KITCHEN' ? '投放点' : order.bizType === 'MALL' ? '收货地址' : '收货地址'"
            :value="order.address || '—'"
          />
          <van-cell title="状态" :value="order.statusDesc || ORDER_STATUS_TEXT[order.status]" />
          <van-button
            class="detail__link-btn"
            round
            block
            type="primary"
            plain
            @click="router.push(order.bizType === 'KITCHEN' ? '/kitchen/records' : order.bizType === 'MALL' ? '/mall/exchange' : '/flea/mine?tab=bought')"
          >
            查看{{ order.bizType === 'KITCHEN' ? '投放记录' : order.bizType === 'MALL' ? '兑换单' : '闲置交易' }}
          </van-button>
        </div>

        <!-- 评价（B2 接后端：已完成订单可评一次，已评价回显星级；RECYCLE/REPAIR） -->
        <div v-if="isFinished && (order.bizType === 'RECYCLE' || order.bizType === 'REPAIR')" class="card">
          <div class="card__title">服务评价</div>
          <template v-if="!reviewed">
            <van-rate v-model="rating" :size="26" />
            <van-field
              v-model="reviewComment"
              type="textarea"
              rows="2"
              maxlength="500"
              placeholder="说说本次服务体验（选填）"
            />
            <van-button
              class="detail__rate-btn"
              size="small"
              type="primary"
              round
              :disabled="rating === 0 || submitting"
              :loading="submitting"
              @click="onRate"
            >
              提交评价
            </van-button>
          </template>
          <div v-else class="detail__rated">已评价 {{ rating }} 星，感谢您的反馈 <SvgIcon name="nav-star" /></div>
        </div>

        <!-- 操作（仅 RECYCLE 待接单可取消/改期） -->
        <div v-if="canModify" class="detail__actions">
          <van-button round block type="primary" plain @click="showReschedule = true">改期</van-button>
          <van-button round block type="danger" plain @click="onCancel">取消订单</van-button>
        </div>
      </template>
      <van-empty v-if="!loading && !order" description="订单不存在" />
    </van-skeleton>

    <!-- 改期弹层 -->
    <van-popup v-model:show="showReschedule" position="bottom" round class="detail__popup">
      <div class="card__title">选择新的上门时间</div>
      <div class="detail__label">日期</div>
      <div class="detail__chips">
        <van-tag
          v-for="d in dateOptions"
          :key="d"
          size="large"
          :type="newDate === d ? 'primary' : 'default'"
          @click="newDate = d"
        >
          {{ d.slice(5) }}
        </van-tag>
      </div>
      <div class="detail__label">时段</div>
      <div class="detail__chips">
        <van-tag
          v-for="s in TIME_SLOTS"
          :key="s"
          size="large"
          :type="newSlot === s ? 'primary' : 'default'"
          @click="newSlot = s"
        >
          {{ s }}
        </van-tag>
      </div>
      <van-button round block type="primary" :disabled="!newDate" @click="onReschedule">确认改期</van-button>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.detail {
  padding-top: 0;

  &__map {
    color: var(--df-color-carbon);
    font-size: $df-font-size-sm;
  }

  &__map-link {
    margin-left: $df-space-sm;
    color: var(--df-color-primary);
    font-weight: 600;
  }

  &__actions {
    display: flex;
    gap: $df-space-md;
    margin-top: $df-space-lg;
  }

  &__rate-btn {
    margin-top: $df-space-md;
  }

  &__rated {
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__popup {
    padding: $df-space-lg;
  }

  /* LoopFill B1：KITCHEN/MALL 详情链接按钮 */
  &__link-btn {
    margin-top: $df-space-md;
  }

  &__label {
    margin: $df-space-md 0 $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: $df-space-sm;
    margin-bottom: $df-space-md;
  }
}

.van-cell-group--inset {
  margin: 0 0 $df-space-md;
}
</style>
