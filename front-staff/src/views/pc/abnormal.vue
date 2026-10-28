<script setup lang="ts">
// PC 端异常上报：联系不上/地址错误/货不符/设备故障等，可关联订单
// 上报后订单置异常态（状态机 5），由后台风控/客服跟进
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import { OrderStatus } from '@shared/http/types'
import { getTodayOrders, reportAbnormal, type StaffOrder } from '@/api/business'

defineOptions({ name: 'pc-abnormal' })

const route = useRoute()
const router = useRouter()

/** 异常类型（字典，联调期切 sys_dict） */
const ABNORMAL_TYPES = ['联系不上客户', '地址错误', '货不符/量不符', '设备故障', '客户取消', '其他']

const type = ref('')
const desc = ref('')
const photo = ref('')
const submitting = ref(false)
const ticketNo = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

/** 关联订单：详情页带 orderId 进入则预选；后端契约 orderId 必填（订单转异常态） */
const orders = ref<StaffOrder[]>([])
const orderId = ref<string | undefined>(
  route.query.orderId ? String(route.query.orderId) : undefined,
)
const showOrderPicker = ref(false)

const linkedOrder = computed(() => orders.value.find((o) => o.id === orderId.value))

const pickableOrders = computed(() =>
  orders.value.filter((o) => o.status !== OrderStatus.FINISHED && o.status !== OrderStatus.ABNORMAL),
)

function onPickOrder(order: StaffOrder) {
  orderId.value = order.id
  showOrderPicker.value = false
}

function onTakePhoto() {
  // 真机直接拉起相机（capture），PC 弹文件选择；读为 base64 作为现场凭证
  fileInput.value?.click()
}

function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    photo.value = String(reader.result)
    showToast('已拍摄现场照片')
  }
  reader.readAsDataURL(file)
  ;(e.target as HTMLInputElement).value = ''
}

async function onSubmit() {
  if (!type.value || !desc.value.trim()) {
    showFailToast('请选择异常类型并填写说明')
    return
  }
  if (!orderId.value) {
    showFailToast('请关联异常订单（后端需将订单转异常态）')
    return
  }
  submitting.value = true
  try {
    const res = await reportAbnormal({
      orderId: orderId.value,
      type: type.value,
      desc: desc.value.trim(),
      photoBase64: photo.value || undefined,
    })
    ticketNo.value = res.ticketNo
    showSuccessToast('上报成功')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  orders.value = await getTodayOrders()
})
</script>

<template>
  <div class="pc-page">
    <div class="pc-page__header">
      <button class="pc-back" @click="router.back()">← 返回</button>
      <h1 class="pc-page__title">异常上报</h1>
      <p class="pc-page__sub">上报后订单转异常态，后台风控跟进</p>
    </div>

    <!-- 上报结果 -->
    <div v-if="ticketNo" class="pc-card abnormal__done">
      <van-icon name="checked" color="#2f6b5e" size="56" />
      <h2>上报成功</h2>
      <p>工单号 {{ ticketNo }}，客服与后台风控将跟进处理。</p>
      <van-button round type="primary" @click="router.replace('/pc/tasks')">返回接单台</van-button>
    </div>

    <template v-else>
      <div class="abnormal__main">
        <div class="pc-card">
          <div class="abnormal__section-title">关联订单（必选）</div>
          <van-cell
            :title="linkedOrder ? linkedOrder.title : '选择今日在办订单'"
            :label="linkedOrder ? linkedOrder.orderNo : '上报后订单转异常态，后台风控跟进'"
            is-link
            @click="showOrderPicker = true"
          />

          <div class="abnormal__section-title">异常类型</div>
          <div class="abnormal__types">
            <van-tag
              v-for="t in ABNORMAL_TYPES"
              :key="t"
              round
              size="large"
              :type="type === t ? 'danger' : 'default'"
              :plain="type !== t"
              @click="type = t"
            >
              {{ t }}
            </van-tag>
          </div>
        </div>

        <div class="pc-card">
          <div class="abnormal__section-title">情况说明</div>
          <van-field
            v-model="desc"
            rows="4"
            type="textarea"
            maxlength="200"
            show-word-limit
            placeholder="请描述现场情况（时间/位置/沟通经过）"
          />
          <van-button icon="photograph" plain type="primary" size="small" class="abnormal__photo" @click="onTakePhoto">
            {{ photo ? '已拍照，重拍' : '拍摄现场照片' }}
          </van-button>
          <input ref="fileInput" type="file" accept="image/*" capture="environment" hidden @change="onFileChange" />
          <van-button round block type="danger" :loading="submitting" class="abnormal__submit" @click="onSubmit">
            提交上报
          </van-button>
        </div>
      </div>

      <!-- 订单选择弹层 -->
      <van-popup v-model:show="showOrderPicker" round>
        <div class="abnormal__picker">
          <div class="abnormal__picker-title">选择关联订单</div>
          <van-empty v-if="!pickableOrders.length" description="今日暂无在办订单" />
          <van-cell
            v-for="o in pickableOrders"
            :key="o.id"
            :title="o.title"
            :label="`${o.orderNo} · ${o.address}`"
            clickable
            @click="onPickOrder(o)"
          />
        </div>
      </van-popup>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.pc-back {
  border: none;
  background: transparent;
  color: var(--df-text-secondary);
  font-size: $df-font-size-base;
  cursor: pointer;
  padding: 4px 0;

  &:hover {
    color: var(--df-color-primary);
  }
}

.abnormal__main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $df-space-lg;
  align-items: start;
}

.abnormal__section-title {
  font-size: $df-font-size-md;
  font-weight: 600;
  margin: $df-space-md 0;
}

.abnormal__types {
  display: flex;
  flex-wrap: wrap;
  gap: $df-space-sm;
}

.abnormal__photo {
  margin-top: $df-space-md;
}

.abnormal__submit {
  margin-top: $df-space-xl;
}

.abnormal__done {
  text-align: center;
  padding: $df-space-xxl;
  max-width: 560px;
  margin-left: auto;
  margin-right: auto;

  h2 {
    margin: $df-space-md 0;
  }

  p {
    margin: 0 0 $df-space-lg;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }
}

.abnormal__picker {
  padding: $df-space-lg;
  width: 480px;
  max-height: 60vh;
  overflow-y: auto;
}

.abnormal__picker-title {
  font-size: $df-font-size-md;
  font-weight: 600;
  margin-bottom: $df-space-md;
}
</style>
