<script setup lang="ts">
// 可回收预约：定位地址 + 品类多选 + 预估重量 + 日期时段
import { ref, reactive, computed, watch, onMounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { createRecycleOrder, getCarbonAccount } from '@/api/business'
import { listAddresses, type AddressBookEntry } from '@/api/addressBook'
import { AddressBookSelect, CategorySelect, TIME_SLOTS, RECYCLE_CATEGORIES } from '@shared/index'
import type { AddressValue } from '@shared/index'
import AppointTimePicker from '@/components/AppointTimePicker.vue'

// keep-alive 匹配名：从地址簿返回时保留已上传照片与表单
defineOptions({ name: 'recycle-book' })

const router = useRouter()
const route = useRoute()

const address = ref<AddressValue>({ areaCode: '', areaName: '', detail: '' })
/** 地址簿列表（选择菜单数据源；未登录/接口失败静默为空） */
const savedAddresses = ref<AddressBookEntry[] | null>(null)
const categories = ref<string[]>([])
const form = reactive({
  estimateWeight: 5,
  date: '',
  timeSlot: TIME_SLOTS[0] ?? '',
  remark: '',
})

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

// ---- 拍照上传多张照片（供工作人员查看） ----
const photos = ref<UploaderFileListItem[]>([])



async function onPhotoRead(item: UploaderFileListItem | UploaderFileListItem[]) {
  // 多张照片上传，无 AI 处理
}

/** 按当日报价估算收益 */
const estimateIncome = computed(() => {
  if (categories.value.length === 0) return 0
  const avg =
    RECYCLE_CATEGORIES.filter((c) => categories.value.includes(c.code)).reduce((s, c) => s + c.price, 0) /
    categories.value.length
  return Number((avg * form.estimateWeight).toFixed(2))
})

async function onSubmit() {
  if ((!address.value.areaCode && !address.value.province) || !address.value.detail) {
    showToast('请填写完整上门地址（省市区或片区 + 详细地址）')
    return
  }
  if (categories.value.length === 0) {
    showToast('请至少选择一个品类')
    return
  }
  // 检查是否上传了物品照片（必填）
  if (photos.value.length === 0) {
    showToast('请上传物品照片（必填）')
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
      categories: categories.value,
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
    photos.value = []
    categories.value = []
    form.remark = ''
    form.estimateWeight = 5
    router.replace('/orders')
  } catch {
    // 提交失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}

/** 地址簿按钮：跳转地址簿管理页（新增/编辑后返回本页继续预约） */
function goAddressBook() {
  router.push('/mobile/address-book')
}

// ---- 浏览器缓存恢复：无缓存，照片模式 ----
onMounted(() => {
  // query 预选独立于缓存恢复执行（cam04：扫描识别 → 预约页预选品类 + 提示）
  const queryCategory = typeof route.query.category === 'string' ? route.query.category : ''
  if (queryCategory && RECYCLE_CATEGORIES.some((c) => c.code === queryCategory)) {
    categories.value = [queryCategory]
    showToast('已按扫描识别品类预选')
  }
})
</script>

<template>
  <div class="page book">
    <van-nav-bar title="可回收预约" left-arrow @click-left="router.back()" />

    <div class="card">
      <div class="card__title"><SvgIcon name="ai-camera" /> 物品照片（必填，最多 9 张）</div>
      <p class="book__photo-desc">拍摄要回收的大概物品照片，供工作人员查看</p>
      <van-uploader 
        v-model="photos" 
        :max-count="9" 
        multiple
        reupload 
        @read="onPhotoRead"
      >
        <div class="uploader-hint"><SvgIcon name="ai-camera" :size="14" /> 点击拍照或上传图片（最多 9 张）</div>
      </van-uploader>
    </div>

    <div class="card">
      <div class="card__title"><SvgIcon name="nav-location-pin" /> 上门地址</div>
      <div class="book__address-row">
        <AddressBookSelect v-model="address" :addresses="savedAddresses" @open-book="goAddressBook" />
        <button type="button" class="book__address-book-btn" @click="goAddressBook">地址簿</button>
      </div>
    </div>

    <div class="card">
      <div class="card__title"><SvgIcon name="recycle-recycle-symbol" /> 回收品类（多选）</div>
      <CategorySelect v-model="categories" />
    </div>

    <div class="card">
      <div class="card__title"><SvgIcon name="recycle-scale" /> 预估重量</div>
      <van-slider v-model="form.estimateWeight" :min="1" :max="100" :step="1">
        <template #button>
          <div class="book__slider-btn">{{ form.estimateWeight }}kg</div>
        </template>
      </van-slider>
      <div v-if="estimateIncome > 0" class="book__estimate">预估收益 ¥{{ estimateIncome.toFixed(2) }}（按当日均价）</div>
    </div>

    <div class="card">
      <div class="card__title"><SvgIcon name="nav-calendar" /> 上门时间</div>
      <AppointTimePicker v-model:date="form.date" v-model:time-slot="form.timeSlot" />
    </div>

    <div class="card">
      <van-field v-model="form.remark" label="备注" placeholder="如：大件家电需两人搬运（选填）" maxlength="50" />
    </div>

    <!-- 用户自定义报酬积分（>=1；服务完成后真实转账给工作人员） -->
    <div class="card">
      <div class="card__title"><SvgIcon name="nav-money-bag" /> 工作人员报酬（选填）</div>
      <div class="book__fee-row">
        <span class="book__fee-label">支付积分报酬</span>
        <van-switch v-model="useServiceFee" size="20px" />
      </div>
      <template v-if="useServiceFee">
        <van-field
          v-model="customFee"
          type="number"
          label="报酬积分"
          placeholder="≥1，任意自定义"
          :maxlength="6"
        />
        <div class="book__fee-balance">当前余额：{{ balance }} 积分</div>
        <van-notice-bar
          v-if="feeInsufficient"
          color="#ee0a24"
          background="#fff8f8"
          text="碳账户余额不足，请调低报酬或关闭支付"
        />
      </template>
    </div>

    <van-button round block type="primary" :loading="submitting" @click="onSubmit">提交预约</van-button>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;
/* 物品照片上传按钮自定义样式 */
.uploader-hint {
  font-size: $df-font-size-sm;
  color: var(--df-text-secondary);
  text-align: center;
  margin-top: 4px;
}

/* 物品照片说明文案 */
.book__photo-desc {
  margin: -4px 0 8px;
  color: var(--df-text-secondary);
  font-size: $df-font-size-sm;
}

.book {
  padding-top: 0;

  &__slider-btn {
    padding: 2px 8px;
    border-radius: $df-radius-round;
    background: var(--df-color-primary);
    color: #fff;
    font-size: $df-font-size-xs;
    white-space: nowrap;
  }

  &__estimate {
    margin-top: $df-space-md;
    color: var(--df-color-gold);
    font-size: $df-font-size-sm;
  }

  &__ai-status {
    margin-top: $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;

    &--done {
      color: var(--df-text-primary);
      font-weight: 600;
    }

    &--cache {
      color: var(--df-color-primary);
      font-weight: 600;
    }
  }

  &__label {
    margin: $df-space-sm 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: $df-space-sm;
  }

  &__fee-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__fee-label {
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__fee-empty {
    margin-top: $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__fee-balance {
    margin-top: $df-space-sm;
    color: var(--df-color-gold);
    font-size: $df-font-size-sm;
  }
}

// 地址选择：下拉选择 + 地址簿按钮
.book__address-row {
  display: flex;
  align-items: stretch;
  gap: $df-space-sm;
}

.book__address-book-btn {
  flex-shrink: 0;
  padding: 0 $df-space-md;
  border: 1px solid var(--df-color-primary);
  border-radius: $df-radius-lg;
  background: var(--df-color-primary-light);
  color: var(--df-color-primary-dark);
  font-size: $df-font-size-sm;
  white-space: nowrap;
  cursor: pointer;

  &:active {
    opacity: 0.8;
  }
}
</style>
