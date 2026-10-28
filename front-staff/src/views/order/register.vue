<script setup lang="ts">
// 核验登记（09：上门核验重量录入 + 物资登记）
// 角色差异化：回收员按品类逐项录重；环卫员（厨余清运）录桶数+总重
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import { formatMoney, formatCarbon } from '@shared/utils/format'
import {
  getStaffOrderDetail, finishOrder,
  type StaffOrder, type FinishOrderResult,
} from '@/api/business'
import { useStaffStore } from '@/store/staff'

const route = useRoute()
const router = useRouter()
const staffStore = useStaffStore()

const orderId = String(route.params.id)
const order = ref<StaffOrder | null>(null)
const submitting = ref(false)
const result = ref<FinishOrderResult | null>(null)

/** 回收员：各品类核验重量（kg，字符串便于输入框绑定） */
const weights = ref<Record<string, string>>({})
/** 回收员：转运去向（后端溯源必填） */
const transferTo = ref('片区分拣中心')
/** 环卫员：桶数 + 总重 + 车号/终点（归档台账） */
const buckets = ref('')
const totalWeight = ref('')
const vehicleNo = ref('')
const destination = ref('厨余处置中心')
/** 现场照片（登记凭证，base64；真机拉起相机拍摄） */
const photo = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const remark = ref('')

const isKitchen = computed(() => order.value?.bizType === 'KITCHEN')

const recycleItems = computed(() =>
  RECYCLE_CATEGORIES
    .filter((c) => Number(weights.value[c.code] ?? 0) > 0)
    .map((c) => ({ code: c.code, name: c.name, weight: Number(weights.value[c.code]) })),
)

/** 实时预估金额（回收员核验时的现场报价） */
const estimateAmount = computed(() =>
  recycleItems.value.reduce((s, it) => {
    const cat = RECYCLE_CATEGORIES.find((c) => c.code === it.code)
    return s + (cat?.price ?? 0) * it.weight
  }, 0),
)

const canSubmit = computed(() =>
  isKitchen.value ? Number(buckets.value) > 0 && Number(totalWeight.value) > 0 : recycleItems.value.length > 0,
)

function onTakePhoto() {
  // 真机直接拉起相机（capture），PC 弹文件选择；读为 base64 作为登记凭证
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
  if (!order.value) return
  const items = isKitchen.value
    ? [{ code: 'kitchen', name: '厨余', weight: Number(totalWeight.value) }]
    : recycleItems.value
  submitting.value = true
  try {
    result.value = await finishOrder(order.value, {
      items,
      transferTo: transferTo.value || undefined,
      vehicleNo: vehicleNo.value || undefined,
      destination: destination.value || undefined,
      photoBase64: photo.value || undefined,
      remark: remark.value || undefined,
    })
    showSuccessToast('登记完成，订单已完结')
  } catch (e) {
    showToast(e instanceof Error ? e.message : '登记提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  order.value = await getStaffOrderDetail(orderId)
})
</script>

<template>
  <div class="page">
    <van-nav-bar :title="isKitchen ? '清运登记' : '物资核验登记'" left-arrow @click-left="router.back()" />

    <!-- 完成结果：金额/积分/碳减排（入碳账本）；厨余为当日上报统一发分 -->
    <div v-if="result" class="card reg__done">
      <van-icon name="checked" color="#2f6b5e" size="48" />
      <h3>登记完成</h3>
      <p v-if="isKitchen" class="reg__kitchen-tip">清运已归档，当日投放用户统一发分</p>
      <van-cell-group v-else :border="false">
        <van-cell v-if="result.amount" title="应付客户" :value="formatMoney(result.amount)" />
        <van-cell title="客户得积分" :value="`${result.points} 分`" />
        <van-cell title="碳减排（入碳账本）" :value="formatCarbon(result.carbon)" />
        <van-cell v-if="result.carbonLogId" title="碳流水（哈希链可验证）" :value="result.carbonLogId" />
        <!-- 报酬结算：用户承诺报酬（完成时真实转账）；1 已入账 / 2 异常转人工 -->
        <van-cell
          v-if="result.settleStatus === 1"
          title="用户报酬入账"
          :value="`+${result.settlePoints ?? 0} 积分已到账`"
        />
        <van-cell v-else-if="result.settleStatus === 2" title="用户报酬" value="结算异常转人工" />
        <!-- 员工回收积分：与用户相同规则同步入账（bizKey 含 userId，同单两笔流水不冲突） -->
        <van-cell
          v-if="result.staffPoints"
          title="我的回收积分"
          :value="`+${result.staffPoints} 分已入账（与用户相同），当前余额 ${result.staffBalanceAfter ?? '—'}`"
        />
      </van-cell-group>
      <van-button round block type="primary" @click="router.replace('/tasks')">返回接单台</van-button>
    </div>

    <template v-else-if="order">
      <div class="card">
        <div class="card__title">{{ order.title }}</div>
        <div class="reg__meta">{{ order.customerName }} · {{ order.address }}</div>
      </div>

      <!-- 环卫员（厨余清运）：桶数 + 总重 + 归档台账 -->
      <div v-if="isKitchen" class="card">
        <div class="card__title">清运量核验</div>
        <van-field v-model="buckets" type="digit" label="清运桶数" placeholder="请输入桶数" />
        <van-field v-model="totalWeight" type="number" label="核验总重" placeholder="地磅/吊秤读数" suffix="kg" />
        <van-field v-model="vehicleNo" label="实际车号" placeholder="如 浙A·D12345（可选）" />
        <van-field v-model="destination" label="清运终点" placeholder="溯源台账终点" />
      </div>

      <!-- 回收员：分品类录重，实时算价 -->
      <div v-else class="card">
        <div class="card__title">分品类核验重量（当日报价）</div>
        <van-field
          v-for="cat in RECYCLE_CATEGORIES"
          :key="cat.code"
          v-model="weights[cat.code]"
          type="number"
          :placeholder="`¥${cat.price.toFixed(2)}/kg`"
          suffix="kg"
        >
          <template #label>
            <div class="reg__cat">
              <SvgIcon :name="cat.icon" :size="16" />
              <span>{{ cat.name }}</span>
            </div>
          </template>
        </van-field>
        <div class="reg__amount">现场预估应付：<b>{{ formatMoney(estimateAmount) }}</b></div>
        <van-field v-model="transferTo" label="转运去向" placeholder="溯源必填，如 XX 分拣中心" />
      </div>

      <div class="card">
        <div class="card__title">登记凭证</div>
        <van-button icon="photograph" plain type="primary" size="small" @click="onTakePhoto">
          {{ photo ? '已拍照，重拍' : '拍摄现场照片' }}
        </van-button>
        <input ref="fileInput" type="file" accept="image/*" capture="environment" hidden @change="onFileChange" />
        <van-field
          v-model="remark"
          rows="2"
          type="textarea"
          maxlength="100"
          show-word-limit
          placeholder="备注（可选）"
        />
      </div>

      <van-button
        round
        block
        type="primary"
        :disabled="!canSubmit"
        :loading="submitting"
        @click="onSubmit"
      >
        确认完成（{{ staffStore.profile?.roleName ?? '员工' }}签认）
      </van-button>
    </template>
    <van-skeleton v-else title :row="8" />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.reg__meta {
  color: var(--df-text-secondary);
  font-size: $df-font-size-sm;
}

.reg__cat {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--df-color-primary);
}

.reg__amount {
  margin-top: $df-space-md;
  text-align: right;
  font-size: $df-font-size-sm;

  b {
    color: var(--df-color-gold);
    font-size: $df-font-size-md;
  }
}

.reg__done {
  text-align: center;
  padding-top: $df-space-xl;

  h3 {
    margin: $df-space-sm 0 $df-space-md;
  }

  .reg__kitchen-tip {
    margin: 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  .van-button {
    margin-top: $df-space-lg;
  }
}
</style>
