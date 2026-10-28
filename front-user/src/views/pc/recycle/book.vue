<script setup lang="ts">
// 可回收预约（PC）：多张照片上传（供工作人员查看）+ 地址 + 品类多选 + 预估重量 + 日期时段
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { createRecycleOrder, getCarbonAccount } from '@/api/business'
import { listAddresses } from '@/api/addressBook'
import { AddressPicker } from '@shared/index'
import type { AddressValue } from '@shared/index'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import AppointTimePicker from '@/components/AppointTimePicker.vue'

// keep-alive 匹配名：从地址簿返回时保留已上传照片与表单
defineOptions({ name: 'pc-recycle-book' })

const router = useRouter()

const address = ref<AddressValue>({ areaCode: '', areaName: '', detail: '' })
/** 常用地址（地址簿，点选即填充；未登录/接口失败静默为空） */
const savedAddresses = ref<AddressValue[] | null>(null)

const form = reactive({
  categories: [] as string[],
  estimateWeight: 5,
  date: '',
  timeSlot: '09:00-11:00',
  remark: '',
})

// ---- 拍照上传多张照片（供工作人员查看，无 AI 处理） ----
const MAX_PHOTOS = 9
const uploadedImages = ref<string[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const submitting = ref(false)

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

/** 品类图标映射（对齐共享字典图标名，PC 卡片走 SvgIcon） */
const CATEGORY_ICONS: Record<string, string> = {
  paper: 'recycle-paper',
  plastic: 'recycle-lotion-bottle',
  metal: 'recycle-can',
  glass: 'recycle-bottle',
  electronics: 'recycle-phone',
  appliance: 'recycle-tv',
  fabric: 'recycle-shirt',
}
const categories = RECYCLE_CATEGORIES.map((c) => ({
  code: c.code,
  name: c.name,
  icon: CATEGORY_ICONS[c.code] ?? 'recycle-recycle-symbol',
}))

function toggleCategory(code: string) {
  if (form.categories.includes(code)) {
    form.categories = form.categories.filter((c) => c !== code)
  } else {
    form.categories.push(code)
  }
}

const triggerFileInput = () => {
  fileInput.value?.click()
}

/** 多张照片读取（最多 9 张，纯上传无 AI 处理） */
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

async function handleSubmit() {
  if ((!address.value.areaCode && !address.value.province) || !address.value.detail) {
    showToast('请填写完整上门地址（省市区或片区 + 详细地址）')
    return
  }
  if (uploadedImages.value.length === 0) {
    showToast('请上传物品照片（必填）')
    return
  }
  if (form.categories.length === 0) {
    showToast('请至少选择一个品类')
    return
  }
  if (!form.date) {
    showToast('请选择预约日期')
    return
  }
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
    const { orderNo } = await createRecycleOrder({
      address: address.value,
      categories: form.categories,
      estimateWeight: form.estimateWeight,
      date: form.date,
      timeSlot: form.timeSlot,
      remark: form.remark,
      // 报酬积分（关闭开关=0 不使用，完成服务后真实转账给工作人员）
      servicePoints: useServiceFee.value ? Number(customFee.value) : 0,
    })
    showSuccessToast(
      useServiceFee.value ? `预约成功 ${orderNo}，报酬将在服务完成后结算` : `预约成功 ${orderNo}`,
    )
    // keep-alive 会缓存本页：下单成功后清空草稿，避免下次进入残留旧照片
    uploadedImages.value = []
    form.categories = []
    form.remark = ''
    form.estimateWeight = 5
    router.push('/pc/home')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pc-recycle-book">
    <div class="container">
      <h1 class="page-title">可回收预约</h1>
      <p class="page-desc">一键下单，上门回收，智能称重，实时结算</p>

      <!-- 照片上传与预览（多张，供工作人员查看） -->
      <div class="preview-section">
        <div class="preview-card">
          <h3 class="preview-title">
            <SvgIcon name="ai-camera" :size="24" />
            物品拍照预览（必填）
          </h3>
          <p class="preview-desc">拍摄要回收的大概物品照片，照片将发送给工作人员查看</p>

          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            style="display: none"
            @change="handleFileSelect"
          />

          <div v-if="uploadedImages.length === 0" class="upload-area" @click="triggerFileInput">
            <SvgIcon name="ai-camera" :size="64" color="var(--df-text-secondary)" />
            <p>点击上传物品照片（最多 9 张）</p>
            <span class="upload-hint">支持 JPG、PNG 格式</span>
          </div>

          <div v-else class="photos-grid">
            <div v-for="(img, i) in uploadedImages" :key="i" class="photo-item">
              <img :src="img" class="preview-image" />
              <button class="photo-remove" type="button" title="移除" @click="removeImage(i)">×</button>
            </div>
            <div v-if="uploadedImages.length < MAX_PHOTOS" class="photo-add" @click="triggerFileInput">
              <SvgIcon name="ai-camera" :size="32" color="var(--df-text-secondary)" />
              <span>继续添加</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 预约表单 -->
      <div class="form-section">
        <div class="form-card">
          <div class="form-field">
            <label>
              <SvgIcon name="common-location" :size="20" />
              上门地址
            </label>
            <AddressPicker
              v-model="address"
              locatable
              :saved="savedAddresses"
              manageable
              @open-book="router.push('/pc/address-book')"
            />
          </div>

          <div class="form-field">
            <label>
              <SvgIcon name="recycle-recycle-symbol" :size="20" />
              回收品类（可多选）
            </label>
            <div class="categories-grid">
              <div
                v-for="cat in categories"
                :key="cat.code"
                class="category-chip"
                :class="{ active: form.categories.includes(cat.code) }"
                @click="toggleCategory(cat.code)"
              >
                <SvgIcon class="category-icon" :name="cat.icon" :size="32" />
                <span class="category-name">{{ cat.name }}</span>
              </div>
            </div>
          </div>

          <div class="form-field">
            <label>
              <SvgIcon name="recycle-scale" :size="20" />
              预估重量：{{ form.estimateWeight }} kg
            </label>
            <input v-model="form.estimateWeight" type="range" min="1" max="100" />
            <div class="range-labels">
              <span>1kg</span>
              <span>100kg</span>
            </div>
          </div>

          <div class="form-field">
            <label>
              <SvgIcon name="nav-calendar" :size="20" />
              预约时间
            </label>
            <AppointTimePicker v-model:date="form.date" v-model:time-slot="form.timeSlot" />
          </div>

          <div class="form-field">
            <label>备注</label>
            <textarea v-model="form.remark" placeholder="如：大件家电需两人搬运（选填）" rows="4"></textarea>
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

          <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? '提交中…' : '提交预约' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-recycle-book {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 16px;
  text-align: center;
}

.page-desc {
  font-size: 18px;
  color: var(--df-text-regular);
  margin-bottom: 48px;
  text-align: center;
}

// 预览区域
.preview-section {
  margin-bottom: 48px;
}

.preview-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 48px;
  box-shadow: var(--df-shadow-card);
}

.preview-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-desc {
  font-size: 16px;
  color: var(--df-text-regular);
  margin-bottom: 32px;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px;
  border: 3px dashed var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  p {
    font-size: 18px;
    color: var(--df-text-regular);
    margin: 16px 0 8px;
  }
}

.upload-hint {
  font-size: 14px;
  color: var(--df-text-secondary);
}

/* 多张照片网格 */
.photos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 16px;
}

.photo-item {
  position: relative;

  .preview-image {
    width: 100%;
    height: 150px;
    object-fit: cover;
    border-radius: 12px;
  }
}

.photo-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.8);
  }
}

.photo-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 150px;
  border: 2px dashed var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  color: var(--df-text-secondary);
  font-size: 14px;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }
}

// 表单区域
.form-section {
  margin-bottom: 48px;
}

.form-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 48px;
  box-shadow: var(--df-shadow-card);
}

.form-field {
  margin-bottom: 32px;

  label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-primary);
    margin-bottom: 16px;
  }

  textarea {
    width: 100%;
    padding: 14px 16px;
    font-size: 16px;
    border: 2px solid var(--df-border-color);
    border-radius: 12px;
    transition: all 0.3s ease;
    font-family: inherit;

    &:focus {
      outline: none;
      border-color: var(--df-color-primary);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    }
  }

  input[type='range'] {
    width: 100%;
    height: 6px;
    border-radius: 3px;
    background: var(--df-border-color);
    outline: none;
    -webkit-appearance: none;

    &::-webkit-slider-thumb {
      -webkit-appearance: none;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: var(--df-color-primary);
      cursor: pointer;
    }
  }
}

// 共享 AddressPicker 宿主样式（对齐 PC 表单视觉）
:deep(.df-address-picker__areas) {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

:deep(.df-address-picker__area) {
  padding: 8px 20px;
  border: 2px solid var(--df-border-color);
  border-radius: 999px;
  background: var(--df-bg-card);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;

  &.is-active {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary-dark);
    font-weight: 600;
  }
}

:deep(.df-address-picker__detail) {
  display: flex;
  gap: 12px;

  input {
    flex: 1;
    padding: 14px 16px;
    font-size: 16px;
    border: 2px solid var(--df-border-color);
    border-radius: 12px;
    transition: all 0.3s ease;

    &:focus {
      outline: none;
      border-color: var(--df-color-primary);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    }
  }
}

:deep(.df-address-picker__locate) {
  padding: 0 20px;
  border: none;
  border-radius: 12px;
  background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
  color: var(--df-color-primary-dark);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.3s ease;

  &:hover {
    background: var(--df-color-primary);
    color: white;
  }
}

.range-labels {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: var(--df-text-secondary);
  margin-top: 8px;
}

.categories-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

/* 用户自定义报酬：支付区块 */
.fee-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

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

.category-chip,
.time-chip {
  padding: 12px 20px;
  text-align: center;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  &.active {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    color: var(--df-color-primary);
    font-weight: 600;
  }
}

.category-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.category-name {
  font-size: 14px;
}

.submit-btn {
  width: 100%;
  padding: 16px;
  font-size: 18px;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, var(--df-color-primary) 0%, var(--df-color-primary-dark) 100%);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px color-mix(in srgb, var(--df-color-secondary) 40%, transparent);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
}
</style>
