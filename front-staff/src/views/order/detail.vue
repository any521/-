<script setup lang="ts">
// 订单详情：发消息联系客户 / 高德导航 / 开始上门 / 按业务类型进入核验登记或维修作业
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showToast } from 'vant'
import { OrderStatus, ORDER_STATUS_TEXT } from '@shared/http/types'
import { formatWeight } from '@shared/utils/format'
import { getStaffOrderDetail, type StaffOrder } from '@/api/business'
import { createConversation } from '@/api/im'
import { geocode } from '@/api/map'
import { openAmapNavigation } from '@/utils/amapNavi'

const route = useRoute()
const router = useRouter()

const order = ref<StaffOrder | null>(null)
const orderId = String(route.params.id)

/** 已接单/进行中才可作业 */
const workable = computed(
  () => order.value && (order.value.status === OrderStatus.ACCEPTED || order.value.status === OrderStatus.PROCESSING),
)

/** 发消息：复用/创建订单会话（清运无客户会话，不展示入口） */
const chatLoading = ref(false)
async function onChat() {
  if (!order.value) return
  chatLoading.value = true
  try {
    const conv = await createConversation(order.value.bizType === 'REPAIR' ? 'REPAIR' : 'RECYCLE', order.value.id)
    router.push(`/mobile/im/${conv.id}?peerName=${encodeURIComponent(order.value.customerName)}`)
  } catch {
    // 无权限/异常：错误文案已由拦截器 toast，停留在详情页
  } finally {
    chatLoading.value = false
  }
}

/** 高德真导航：优先订单坐标，旧数据/无坐标时地址转坐标兜底 */
async function onNavigate() {
  if (!order.value) return
  const o = order.value
  let lng = Number(o.lng)
  let lat = Number(o.lat)
  if (!lng || !lat) {
    try {
      const g = await geocode(o.address)
      if (g) {
        lng = g.lng
        lat = g.lat
      }
    } catch {
      // 降级提示
    }
  }
  if (!lng || !lat) {
    showFailToast('无法定位该地址，请检查网络后重试')
    return
  }
  openAmapNavigation(lng, lat, o.address)
}

onMounted(async () => {
  order.value = await getStaffOrderDetail(orderId)
})
</script>

<template>
  <div class="page">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />

    <template v-if="order">
      <div class="card">
        <div class="detail__row">
          <span class="detail__title">{{ order.title }}</span>
          <van-tag :type="order.status === OrderStatus.ABNORMAL ? 'danger' : 'primary'">
            {{ ORDER_STATUS_TEXT[order.status] }}
          </van-tag>
        </div>
        <van-cell-group :border="false">
          <van-cell title="订单号" :value="order.orderNo" />
          <van-cell title="预约时间" :value="order.appointTime" />
          <van-cell title="客户" :value="`${order.customerName} ${order.customerPhone}`" />
          <van-cell title="地址" :value="order.address" />
          <van-cell v-if="order.estimateWeight" title="预估重量" :value="formatWeight(order.estimateWeight)" />
          <van-cell v-if="order.servicePoints" title="用户报酬" :value="`${order.servicePoints} 积分（完成后到账）`" />
          <van-cell v-if="order.remark" title="备注" :value="order.remark" />
        </van-cell-group>
      </div>

      <!-- F2 预诊（维修单，低置信为空不展示） -->
      <div v-if="order.aiPrediagnosis" class="card">
        <div class="card__title detail__ai-title">
          <SvgIcon name="ai-robot" :size="16" />
          <span>AI 预诊（供参考）</span>
        </div>
        <p class="detail__ai">{{ order.aiPrediagnosis }}</p>
      </div>

      <div class="detail__actions">
        <van-button
          v-if="order.bizType !== 'KITCHEN'"
          icon="chat-o"
          round
          plain
          type="primary"
          :loading="chatLoading"
          @click="onChat"
        >
          发消息
        </van-button>
        <van-button icon="guide-o" round plain type="primary" @click="onNavigate">导航</van-button>

        <template v-if="workable">
          <!-- 维修单走报告/配件；回收与清运走核验登记 -->
          <van-button
            v-if="order.bizType === 'REPAIR'"
            round
            type="primary"
            @click="router.push(`/repair/${order.id}/report`)"
          >
            填写维修报告
          </van-button>
          <van-button v-else round type="primary" @click="router.push(`/orders/${order.id}/register`)">
            核验登记
          </van-button>
        </template>
        <van-button
          round
          plain
          type="danger"
          @click="router.push({ path: '/abnormal', query: { orderId: order.id } })"
        >
          异常上报
        </van-button>
      </div>
    </template>
    <van-skeleton v-else title :row="8" />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.detail__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $df-space-sm;
}

.detail__title {
  font-weight: 600;
  font-size: $df-font-size-md;
}

.detail__ai-title {
  display: flex;
  align-items: center;
  gap: 4px;
}

.detail__ai {
  margin: 0;
  color: var(--df-color-primary-dark);
  font-size: $df-font-size-sm;
  line-height: 1.6;
}

.detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: $df-space-sm;
  margin-top: $df-space-lg;

  .van-button {
    flex: 1 1 40%;
  }
}
</style>
