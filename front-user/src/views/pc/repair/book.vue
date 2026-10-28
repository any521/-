<script setup lang="ts">
// PC 维修预约（F2 AI 预诊）
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { createRepairOrder, getCarbonAccount, type RepairBookResult } from '@/api/business'
import { listAddresses } from '@/api/addressBook'
import { AddressPicker, TIME_SLOTS } from '@shared/index'
import type { AddressValue } from '@shared/index'
import AppointTimePicker from '@/components/AppointTimePicker.vue'

// keep-alive 匹配名：从地址簿返回时保留已上传照片与表单
defineOptions({ name: 'pc-repair-book' })

const router = useRouter()
const address = ref<AddressValue>({ areaCode: '', areaName: '', detail: '' })
/** 常用地址（地址簿，点选即填充；未登录/接口失败静默为空） */
const savedAddresses = ref<AddressValue[] | null>(null)
const form = reactive({
  deviceType: '',
  faultDesc: '',
  date: '',
  timeSlot: TIME_SLOTS[0] ?? '',
  // 增值服务（V6：以旧换新 / 天然气配送顺带）
  oldForNew: false,
  gasDelivery: false,
})

const deviceTypes = ['家电维修', '家具维修', '电子产品维修', '其他']
const submitting = ref(false)
const result = ref<RepairBookResult | null>(null)

// ---- 物品照片（与预约回收一致：最多 9 张，供工作人员/技师查看） ----
const MAX_PHOTOS = 9
const uploadedImages = ref<string[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  if (files.length === 0) return
  const room = MAX_PHOTOS - uploadedImages.value.length
  if (files.length > room) showToast(`最多上传 ${MAX_PHOTOS} 张照片`)
  files.slice(0, room).forEach((file) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      uploadedImages.value.push(e.target?.result as string)
    }
    reader.readAsDataURL(file)
  })
  target.value = ''
}

function removeImage(index: number) {
  uploadedImages.value.splice(index, 1)
}

// ---- 用户自定义报酬积分（>=1；关闭=不支付，服务完成后真实转账给工作人员） ----
const useServiceFee = ref(false)
const customFee = ref('')
const balance = ref(0)
/** 余额不足自定义报酬：警示条 + 禁止提交 */
const feeInsufficient = computed(
  () => useServiceFee.value && Number(customFee.value) > 0 && balance.value < Number(customFee.value),
)

onMounted(async () => {
  // 报酬：加载碳账户余额（接口异常静默降级为不使用积分支付，不阻断下单主链路）
  try {
    balance.value = (await getCarbonAccount()).points
  } catch {
    /* 余额查询异常：不阻断下单 */
  }
  // 地址簿：快捷选择常用地址（失败静默，不影响手动填写）
  try {
    savedAddresses.value = await listAddresses()
  } catch {
    /* 地址簿异常：不展示快捷区 */
  }
})

// 从地址簿返回时刷新列表，让新增/编辑后的地址立刻可选（照片由 keep-alive 保留）
onActivated(async () => {
  try {
    savedAddresses.value = await listAddresses()
  } catch {
    /* 保持已有列表 */
  }
})

async function onSubmit() {
  if (!form.deviceType) { showToast('请选择维修类型'); return }
  if (!address.value.areaCode && !address.value.province || !address.value.detail) { showToast('请填写完整上门地址（省市区或片区 + 详细地址）'); return }
  if (!form.date) { showToast('请选择预约时间'); return }
  if (useServiceFee.value && Number(customFee.value) < 1) {
    showToast('报酬积分至少 1 分')
    return
  }
  if (feeInsufficient.value) {
    showToast('碳账户余额不足支付报酬')
    return
  }

  submitting.value = true
  try {
    result.value = await createRepairOrder({
      deviceType: form.deviceType,
      faultDesc: form.faultDesc,
      address: address.value,
      date: form.date,
      timeSlot: form.timeSlot,
      oldForNew: form.oldForNew ? 1 : 0,
      gasDelivery: form.gasDelivery ? 1 : 0,
      // 报酬积分（关闭开关=0 不使用，完成服务后真实转账给工作人员）
      servicePoints: useServiceFee.value ? Number(customFee.value) : 0,
    })
    showSuccessToast(
      useServiceFee.value
        ? `预约成功 ${result.value.orderNo}，报酬将在服务完成后结算`
        : `预约成功 ${result.value.orderNo}`,
    )
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pc-repair-book">
    <div class="container">
      <h1 class="page-title">维修预约</h1>
      <p class="page-desc">延长产品寿命，减少资源浪费，支持循环经济</p>

      <div class="form-card">
        <div class="form-field">
          <label><SvgIcon name="ai-camera" :size="20" /> 物品照片（选填，最多 9 张）</label>
          <p class="photo-desc">拍摄待维修物品照片，供技师提前了解情况</p>

          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            style="display: none"
            @change="handleFileSelect"
          />

          <div v-if="uploadedImages.length === 0" class="upload-area" @click="triggerFileInput">
            <SvgIcon name="ai-camera" :size="48" color="var(--df-text-secondary)" />
            <p>点击上传物品照片（最多 9 张）</p>
          </div>

          <div v-else class="photos-grid">
            <div v-for="(img, i) in uploadedImages" :key="i" class="photo-item">
              <img :src="img" class="preview-image" />
              <button class="photo-remove" type="button" title="移除" @click="removeImage(i)">×</button>
            </div>
            <div v-if="uploadedImages.length < MAX_PHOTOS" class="photo-add" @click="triggerFileInput">
              <SvgIcon name="ai-camera" :size="28" color="var(--df-text-secondary)" />
              <span>继续添加</span>
            </div>
          </div>
        </div>

        <div class="form-field">
          <label><SvgIcon name="repair-plug" :size="20" /> 维修类型</label>
          <div class="chip-grid">
            <button
              v-for="t in deviceTypes"
              :key="t"
              class="chip"
              :class="{ active: form.deviceType === t }"
              @click="form.deviceType = t"
            >
              {{ t }}
            </button>
          </div>
        </div>

        <div class="form-field">
          <label><SvgIcon name="repair-wrench" :size="20" /> 问题描述</label>
          <textarea v-model="form.faultDesc" placeholder="请详细描述需要维修的问题" rows="4"></textarea>
        </div>

        <div class="form-field">
          <label><SvgIcon name="common-location" :size="20" /> 上门地址</label>
          <AddressPicker
            v-model="address"
            locatable
            :saved="savedAddresses"
            manageable
            @open-book="router.push('/pc/address-book')"
          />
        </div>

        <div class="form-field">
          <label><SvgIcon name="nav-calendar" :size="20" /> 预约时间</label>
          <AppointTimePicker v-model:date="form.date" v-model:time-slot="form.timeSlot" />
        </div>

        <div class="form-field">
          <label><SvgIcon name="nav-money-bag" :size="20" /> 增值服务</label>
          <van-checkbox v-model="form.oldForNew" class="value-checkbox">
            <span class="value-name">以旧换新</span>
            <span class="value-desc">旧设备抵扣，回收员上门回收旧件并结算，支持天然气补贴</span>
          </van-checkbox>
          <van-checkbox v-model="form.gasDelivery" class="value-checkbox">
            <span class="value-name">天然气配送顺带</span>
            <span class="value-desc">维修同时顺带配送桶装气/气瓶登记</span>
          </van-checkbox>
        </div>

        <!-- 用户自定义报酬积分（>=1；服务完成后真实转账给工作人员） -->
        <div class="form-field">
          <label><SvgIcon name="nav-money-bag" :size="20" /> 工作人员报酬（选填）</label>
          <div class="fee-row">
            <span class="fee-label">支付积分报酬</span>
            <van-switch v-model="useServiceFee" />
          </div>
          <template v-if="useServiceFee">
            <div class="fee-input-row">
              <input
                v-model="customFee"
                type="number"
                min="1"
                class="fee-input"
                placeholder="≥1，任意自定义"
              />
              <span class="fee-input-unit">积分</span>
            </div>
            <div class="fee-balance">当前余额：{{ balance }} 积分</div>
            <van-notice-bar
              v-if="feeInsufficient"
              color="#ee0a24"
              background="#fff8f8"
              text="碳账户余额不足，请调低报酬或关闭支付"
            />
          </template>
        </div>

        <div v-if="result?.aiPrediagnosis" class="diagnosis-box">
          <SvgIcon name="ai-microphone" :size="20" /> AI 预诊：{{ result.aiPrediagnosis }}
        </div>

        <button class="submit-btn" :disabled="submitting" @click="onSubmit">
          {{ submitting ? '提交中…' : '提交预约' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-repair-book {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container { max-width: 800px; margin: 0 auto; }

.page-title {
  font-size: 48px; font-weight: 700; color: var(--df-text-primary);
  margin-bottom: 16px; text-align: center;
}

.page-desc {
  font-size: 18px; color: var(--df-text-regular);
  margin-bottom: 48px; text-align: center;
}

.form-card {
  background: var(--df-bg-card); border-radius: 16px; padding: 48px;
  box-shadow: var(--df-shadow-card);
}

.form-field {
  margin-bottom: 32px;
  label { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: var(--df-text-primary); margin-bottom: 16px; }
  textarea {
    width: 100%; padding: 14px 16px; font-size: 16px; border: 2px solid var(--df-border-color);
    border-radius: 12px; transition: all 0.3s ease; font-family: inherit;
    &:focus { outline: none; border-color: var(--df-color-primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--df-color-secondary) 10%, transparent); }
  }
}

.chip-grid { display: flex; flex-wrap: wrap; gap: 12px; }

/* 物品照片上传（与预约回收 PC 页一致） */
.photo-desc {
  margin: -8px 0 16px;
  font-size: 14px;
  color: var(--df-text-secondary);
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  border: 3px dashed var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  p {
    font-size: 16px;
    color: var(--df-text-regular);
    margin: 12px 0 0;
  }
}

.photos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 16px;
}

.photo-item {
  position: relative;

  .preview-image {
    width: 100%;
    height: 130px;
    object-fit: cover;
    border-radius: 12px;
  }
}

.photo-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 15px;
  line-height: 1;
  cursor: pointer;

  &:hover {
    background: rgba(0, 0, 0, 0.8);
  }
}

.photo-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 130px;
  border: 2px dashed var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  color: var(--df-text-secondary);
  font-size: 13px;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }
}

/* 用户自定义报酬：支付区块 */
/* 用户自定义报酬：输入行 */
.fee-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}

.fee-input {
  flex: 1;
  padding: 12px 16px;
  font-size: 15px;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  transition: all 0.3s ease;

  &:focus {
    outline: none;
    border-color: var(--df-color-primary);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
  }
}

.fee-input-unit {
  font-size: 15px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.fee-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fee-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.fee-empty {
  margin-top: 12px;
  font-size: 14px;
  color: var(--df-text-secondary);
}

.fee-balance {
  margin-top: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--df-color-gold);
}

.value-checkbox {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;

  &:last-child { margin-bottom: 0; }
}

.value-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.value-desc {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  color: var(--df-text-secondary);
}

.chip {
  padding: 10px 22px; font-size: 15px; font-weight: 500; color: var(--df-text-regular);
  background: transparent; border: 2px solid var(--df-border-color); border-radius: 999px;
  cursor: pointer; transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); }
  &.active { border-color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary); font-weight: 600; }
}

.diagnosis-box {
  padding: 16px; border-radius: 12px; margin-bottom: 24px;
  background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent); color: var(--df-color-carbon);
  font-size: 14px; line-height: 1.7;
  display: flex; align-items: flex-start; gap: 8px;
}

.submit-btn {
  width: 100%; padding: 16px; font-size: 18px; font-weight: 600; color: white;
  background: linear-gradient(135deg, var(--df-color-primary) 0%, var(--df-color-primary-dark) 100%);
  border: none; border-radius: 12px; cursor: pointer; transition: all 0.3s ease;
  &:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 20px color-mix(in srgb, var(--df-color-secondary) 40%, transparent); }
  &:disabled { opacity: 0.7; cursor: not-allowed; }
}

:deep(.df-address-picker__areas) { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
:deep(.df-address-picker__area) { padding: 8px 20px; border: 2px solid var(--df-border-color); border-radius: 999px; background: var(--df-bg-card); font-size: 14px; cursor: pointer; transition: all 0.3s ease;&.is-active { border-color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary-dark); font-weight: 600; } }
:deep(.df-address-picker__detail) { display: flex; gap: 12px; input { flex: 1; padding: 14px 16px; font-size: 16px; border: 2px solid var(--df-border-color); border-radius: 12px;&:focus { outline: none; border-color: var(--df-color-primary); } } }
:deep(.df-address-picker__locate) { padding: 0 20px; border: none; border-radius: 12px; background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary-dark); font-size: 14px; cursor: pointer;&:hover { background: var(--df-color-primary); color: white; } }
</style>