<script setup lang="ts">
// 投诉反馈：双 tab「提交 / 我的单」（GapFill B1 V5）
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import {
  submitComplaint, pageMyComplaints,
  COMPLAINT_TYPE_TEXT, COMPLAINT_STATUS_TEXT,
  type ComplaintItem,
} from '@/api/business'

defineOptions({ name: 'user-complaint' })

const router = useRouter()
const activeTab = ref('submit')

// ---- 提交 tab ----
const form = reactive({
  orderType: 'RECYCLE',
  orderId: '',
  type: 1,
  content: '',
})
const submitting = ref(false)

const ORDER_TYPES = [
  { name: '回收订单', value: 'RECYCLE' },
  { name: '垃圾投放', value: 'KITCHEN' },
  { name: '维修订单', value: 'REPAIR' },
  { name: '商城订单', value: 'MALL' },
]
const TYPE_OPTIONS = Object.entries(COMPLAINT_TYPE_TEXT).map(([value, label]) => ({
  name: label,
  value: Number(value),
}))

// 弹层显隐
const orderTypeVisible = ref(false)
const typeVisible = ref(false)

async function onSubmit() {
  if (!form.content.trim()) {
    showToast('请填写诉求描述')
    return
  }
  submitting.value = true
  try {
    await submitComplaint({
      orderType: form.orderType,
      orderId: form.orderId.trim() ? form.orderId.trim() : undefined,
      type: form.type,
      content: form.content.trim(),
    })
    showSuccessToast('已提交，我们将尽快处理')
    form.orderId = ''
    form.content = ''
    activeTab.value = 'mine'
    reloadMine()
  } catch {
    // 提交失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}

// ---- 我的单 tab ----
const mine = ref<ComplaintItem[]>([])
const mineLoading = ref(false)
const mineFinished = ref(false)
let minePage = 1

async function loadMine() {
  mineLoading.value = true
  try {
    const page = await pageMyComplaints({ pageNum: minePage, pageSize: 10 })
    if (minePage === 1) mine.value = []
    mine.value.push(...page.list)
    mineFinished.value = mine.value.length >= page.total
    minePage += 1
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    mineLoading.value = false
  }
}

function reloadMine() {
  minePage = 1
  mineFinished.value = false
  loadMine()
}

function statusTagType(s: number) {
  if (s === 1) return 'success'
  if (s === 2) return 'danger'
  return 'warning'
}

onMounted(() => {
  loadMine()
})
</script>

<template>
  <div class="page complaint">
    <van-nav-bar title="反馈投诉" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="activeTab">
      <van-tab name="submit" title="提交">
        <div class="page">
          <van-cell-group inset>
            <van-field
              v-model="form.orderType"
              label="订单类型"
              readonly
              is-link
              @click="orderTypeVisible = true"
            />
            <van-field v-model="form.orderId" label="订单号" placeholder="选填，关联订单" type="text" />
            <van-field v-model="form.type" label="类型" readonly is-link @click="typeVisible = true" />
            <van-field
              v-model="form.content"
              label="诉求描述"
              type="textarea"
              rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请详细描述您的问题和诉求（必填）"
            />
          </van-cell-group>

          <div class="complaint__submit">
            <van-button round block type="primary" :loading="submitting" @click="onSubmit">提交反馈</van-button>
          </div>
        </div>
      </van-tab>

      <van-tab name="mine" title="我的单">
        <van-list v-model:loading="mineLoading" :finished="mineFinished" finished-text="没有更多了" @load="loadMine">
          <div class="page">
            <div v-for="c in mine" :key="c.id" class="card complaint__item">
              <div class="complaint__head">
                <span class="complaint__type">{{ COMPLAINT_TYPE_TEXT[c.type] ?? `类型${c.type}` }}</span>
                <van-tag :type="statusTagType(c.status)">{{ COMPLAINT_STATUS_TEXT[c.status] ?? '待处理' }}</van-tag>
              </div>
              <p class="complaint__content">{{ c.content }}</p>
              <div class="complaint__meta">
                <span>{{ c.createTime }}</span>
                <span v-if="c.orderId !== '0' && c.orderId !== ''">单号 {{ c.orderId }}</span>
              </div>
              <div v-if="c.handleResult" class="complaint__result">
                <b>处理结果：</b>{{ c.handleResult }}（{{ c.handleTime }}）
              </div>
            </div>
            <van-empty v-if="mineFinished && mine.length === 0" description="暂无反馈记录" />
          </div>
        </van-list>
      </van-tab>
    </van-tabs>

    <van-action-sheet
      v-model:show="orderTypeVisible"
      :actions="ORDER_TYPES"
      cancel-text="取消"
      @select="(a: { value: string }) => (form.orderType = a.value)"
    />
    <van-action-sheet
      v-model:show="typeVisible"
      :actions="TYPE_OPTIONS"
      cancel-text="取消"
      @select="(a: { value: number }) => (form.type = a.value)"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.complaint {
  &__submit {
    margin: $df-space-lg;
  }

  &__item {
    margin: $df-space-md $df-space-lg 0;
  }

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__type {
    font-weight: 600;
    font-size: $df-font-size-base;
  }

  &__content {
    margin: $df-space-sm 0;
    color: var(--df-text-regular);
    font-size: $df-font-size-base;
    line-height: 1.7;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    color: var(--df-text-tertiary);
    font-size: $df-font-size-xs;
  }

  &__result {
    margin-top: $df-space-sm;
    padding: $df-space-sm;
    border-radius: $df-radius-sm;
    background: var(--df-bg-fill);
    color: var(--df-text-regular);
    font-size: $df-font-size-sm;
    line-height: 1.6;
  }
}
</style>
