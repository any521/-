<script setup lang="ts">
// 维修预约：设备类型 + 故障描述 + 地址时段；提交后展示 F2 AI 预诊
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { createRepairOrder, getCarbonAccount, type RepairBookResult } from '@/api/business'
import { listAddresses, type AddressBookEntry } from '@/api/addressBook'
import { AddressBookSelect, REPAIR_TYPES, TIME_SLOTS } from '@shared/index'
import type { AddressValue } from '@shared/index'
import AppointTimePicker from '@/components/AppointTimePicker.vue'

// keep-alive 匹配名：从地址簿返回时保留已上传照片与表单
defineOptions({ name: 'repair-book' })

const router = useRouter()

const address = ref<AddressValue>({ areaCode: '', areaName: '', detail: '' })
/** 地址簿列表（选择菜单数据源；未登录/接口失败静默为空） */
const savedAddresses = ref<AddressBookEntry[] | null>(null)
const form = reactive({
  deviceType: REPAIR_TYPES[0]?.code ?? '',
  faultDesc: '',
  date: '',
  timeSlot: TIME_SLOTS[0] ?? '',
  // 增值服务（V6：以旧换新 / 天然气配送顺带）
  oldForNew: false,
  gasDelivery: false,
})

const submitting = ref(false)
const result = ref<RepairBookResult | null>(null)

// ---- 物品照片（与预约回收一致：最多 9 张，供工作人员查看） ----
const photos = ref<UploaderFileListItem[]>([])

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

/** 地址簿按钮：跳转地址簿管理页（新增/编辑后返回本页继续预约） */
function goAddressBook() {
  router.push('/mobile/address-book')
}

async function onSubmit() {
  if (!form.faultDesc.trim()) {
    showToast('请描述故障现象')
    return
  }
  if ((!address.value.areaCode && !address.value.province) || !address.value.detail) {
    showToast('请填写完整上门地址（省市区或片区 + 详细地址）')
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
    if (useServiceFee.value) showToast('报酬将在服务完成后结算')
    // keep-alive 会缓存本页：下单成功后清空草稿，避免下次进入残留旧照片
    photos.value = []
    form.faultDesc = ''
    form.deviceType = REPAIR_TYPES[0]?.code ?? ''
  } catch {
    // 提交失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page repair">
    <van-nav-bar title="维修预约" left-arrow @click-left="router.back()" />

    <!-- 预约结果 + F2 预诊 -->
    <template v-if="result">
      <div class="card repair__result">
        <div class="repair__result-icon"><SvgIcon name="repair-wrench" /></div>
        <div class="repair__result-title">预约成功</div>
        <div class="repair__result-no">{{ result.orderNo }}</div>
      </div>
      <div v-if="result.aiPrediagnosis" class="card repair__diag">
        <div class="card__title"><SvgIcon name="ai-robot" /> AI 预诊建议</div>
        <p>{{ result.aiPrediagnosis }}</p>
        <p class="repair__diag-tip">预诊结果供技师参考，最终以上门检测为准</p>
      </div>
      <div v-else class="card repair__diag">
        <div class="card__title"><SvgIcon name="nav-speech-balloon" /> 温馨提示</div>
        <p>已为您安排人工诊断，技师将按预约时间上门检测</p>
      </div>
      <van-button round block type="primary" @click="router.replace('/orders')">查看我的订单</van-button>
    </template>

    <!-- 预约表单 -->
    <template v-else>
      <div class="card">
        <div class="card__title"><SvgIcon name="ai-camera" /> 物品照片（选填，最多 9 张）</div>
        <p class="repair__photo-desc">拍摄待维修物品照片，供技师提前了解情况</p>
        <!-- 不加 capture：避免强制调起相机，拍照/相册两种来源都可用 -->
        <van-uploader v-model="photos" :max-count="9" multiple reupload>
          <div class="repair__uploader-hint"><SvgIcon name="ai-camera" :size="14" /> 点击拍照或上传图片（最多 9 张）</div>
        </van-uploader>
      </div>

      <div class="card">
        <div class="card__title"><SvgIcon name="repair-plug" /> 设备类型</div>
        <div class="repair__chips">
          <van-tag
            v-for="t in REPAIR_TYPES"
            :key="t.code"
            size="large"
            :type="form.deviceType === t.code ? 'primary' : 'default'"
            @click="form.deviceType = t.code"
          >
            {{ t.name }}
          </van-tag>
        </div>
      </div>

      <div class="card">
        <div class="card__title"><SvgIcon name="nav-memo" /> 故障描述（AI 将据此预诊）</div>
        <van-field
          v-model="form.faultDesc"
          type="textarea"
          rows="3"
          autosize
          maxlength="200"
          show-word-limit
          placeholder="如：电饭煲通电后不加热，指示灯正常亮"
        />
      </div>

      <div class="card">
        <div class="card__title"><SvgIcon name="nav-location-pin" /> 上门地址</div>
        <div class="repair__address-row">
          <AddressBookSelect v-model="address" :addresses="savedAddresses" @open-book="goAddressBook" />
          <button type="button" class="repair__address-book-btn" @click="goAddressBook">地址簿</button>
        </div>
      </div>

      <div class="card">
        <div class="card__title"><SvgIcon name="nav-calendar" /> 上门时间</div>
        <AppointTimePicker v-model:date="form.date" v-model:time-slot="form.timeSlot" />
      </div>

      <div class="card">
        <div class="card__title"><SvgIcon name="nav-money-bag" /> 增值服务</div>
        <div class="repair__value-item">
          <van-checkbox v-model="form.oldForNew" icon-size="18px">
            <div class="repair__value-name">以旧换新</div>
            <div class="repair__value-desc">旧设备抵扣，回收员上门回收旧件并结算，支持天然气补贴</div>
          </van-checkbox>
        </div>
        <div class="repair__value-item">
          <van-checkbox v-model="form.gasDelivery" icon-size="18px">
            <div class="repair__value-name">天然气配送顺带</div>
            <div class="repair__value-desc">维修同时顺带配送桶装气/气瓶登记</div>
          </van-checkbox>
        </div>
      </div>

      <!-- 用户自定义报酬积分（>=1；服务完成后真实转账给工作人员） -->
      <div class="card">
        <div class="card__title"><SvgIcon name="nav-money-bag" /> 工作人员报酬（选填）</div>
        <div class="repair__fee-row">
          <span class="repair__fee-label">支付积分报酬</span>
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
          <div class="repair__fee-balance">当前余额：{{ balance }} 积分</div>
          <van-notice-bar
            v-if="feeInsufficient"
            color="#ee0a24"
            background="#fff8f8"
            text="碳账户余额不足，请调低报酬或关闭支付"
          />
        </template>
      </div>

      <van-button round block type="primary" :loading="submitting" @click="onSubmit">提交预约</van-button>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.repair {
  padding-top: 0;

  &__photo-desc {
    margin: -4px 0 8px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__uploader-hint {
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
    text-align: center;
    margin-top: 4px;
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: $df-space-sm;
  }

  &__label {
    margin: $df-space-sm 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__result {
    text-align: center;
    padding: $df-space-xl;
  }

  &__result-icon {
    font-size: 48px;
  }

  &__result-title {
    margin-top: $df-space-sm;
    font-size: $df-font-size-lg;
    font-weight: 600;
  }

  &__result-no {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__diag {
    p {
      margin: 0;
      line-height: 1.7;
    }
  }

  &__diag-tip {
    margin-top: $df-space-sm !important;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__value-item {
    padding: $df-space-sm 0;
    border-bottom: 1px solid var(--df-border-color);

    &:last-child {
      border-bottom: none;
    }
  }

  &__value-name {
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__value-desc {
    margin-top: 2px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
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
.repair__address-row {
  display: flex;
  align-items: stretch;
  gap: $df-space-sm;
}

.repair__address-book-btn {
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
