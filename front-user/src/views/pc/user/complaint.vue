<script setup lang="ts">
// PC 投诉反馈：双 tab「提交 / 我的单」（GapFill B1 V5）
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import {
  submitComplaint, pageMyComplaints,
  COMPLAINT_TYPE_TEXT, COMPLAINT_STATUS_TEXT,
  type ComplaintItem,
} from '@/api/business'

defineOptions({ name: 'pc-user-complaint' })

const router = useRouter()
const activeTab = ref<'submit' | 'mine'>('submit')

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
  <div class="pc-complaint">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">反馈投诉</h1>
      </div>

      <div class="tabs">
        <button class="tab" :class="{ 'tab--active': activeTab === 'submit' }" @click="activeTab = 'submit'">提交</button>
        <button class="tab" :class="{ 'tab--active': activeTab === 'mine' }" @click="activeTab = 'mine'">我的单</button>
      </div>

      <!-- 提交 tab -->
      <div v-if="activeTab === 'submit'" class="section-card">
        <div class="form-row">
          <label class="form-label">订单类型</label>
          <select v-model="form.orderType" class="form-select">
            <option v-for="o in ORDER_TYPES" :key="o.value" :value="o.value">{{ o.name }}</option>
          </select>
        </div>
        <div class="form-row">
          <label class="form-label">订单号</label>
          <input v-model="form.orderId" class="form-input" type="text" inputmode="numeric" placeholder="选填，关联订单（雪花 ID 较长，请保持完整）" />
        </div>
        <div class="form-row">
          <label class="form-label">类型</label>
          <select v-model="form.type" class="form-select">
            <option v-for="t in TYPE_OPTIONS" :key="t.value" :value="t.value">{{ t.name }}</option>
          </select>
        </div>
        <div class="form-row form-row--textarea">
          <label class="form-label">诉求描述</label>
          <textarea
            v-model="form.content"
            class="form-textarea"
            rows="4"
            maxlength="500"
            placeholder="请详细描述您的问题和诉求（必填）"
          />
        </div>
        <button class="submit-btn" :disabled="submitting" @click="onSubmit">
          {{ submitting ? '提交中…' : '提交反馈' }}
        </button>
      </div>

      <!-- 我的单 tab -->
      <div v-else class="section-card">
        <div v-if="mineFinished && mine.length === 0" class="empty">暂无反馈记录</div>
        <div v-for="c in mine" :key="c.id" class="item">
          <div class="item-head">
            <span class="item-type">{{ COMPLAINT_TYPE_TEXT[c.type] ?? `类型${c.type}` }}</span>
            <span class="item-status" :class="`item-status--${statusTagType(c.status)}`">
              {{ COMPLAINT_STATUS_TEXT[c.status] ?? '待处理' }}
            </span>
          </div>
          <p class="item-content">{{ c.content }}</p>
          <div class="item-meta">
            <span>{{ c.createTime }}</span>
            <span v-if="c.orderId !== '0' && c.orderId !== ''">单号 {{ c.orderId }}</span>
          </div>
          <div v-if="c.handleResult" class="item-result">
            <b>处理结果：</b>{{ c.handleResult }}（{{ c.handleTime }}）
          </div>
        </div>
        <div v-if="!mineFinished" class="load-more">
          <button :disabled="mineLoading" @click="loadMine">{{ mineLoading ? '加载中…' : '加载更多' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-complaint {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 700px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: 1px solid var(--df-border-color);
  border-radius: 8px;
  background: var(--df-bg-card);
  color: var(--df-text-regular);
  font-size: 14px;
  cursor: pointer;

  &:hover { color: var(--df-color-primary); border-color: var(--df-color-primary); }
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin: 0;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.tab {
  padding: 10px 28px;
  border: 1px solid var(--df-border-color);
  border-radius: 10px;
  background: var(--df-bg-card);
  color: var(--df-text-regular);
  font-size: 15px;
  cursor: pointer;

  &--active {
    background: var(--df-color-primary);
    border-color: var(--df-color-primary);
    color: #fff;
  }
}

.section-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 28px;
  margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.form-row {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--df-border-color);

  &--textarea { align-items: flex-start; }
}

.form-label {
  width: 100px;
  color: var(--df-text-regular);
  font-size: 15px;
}

.form-input,
.form-select,
.form-textarea {
  flex: 1;
  border: 1px solid var(--df-border-color);
  border-radius: 8px;
  background: var(--df-bg-fill);
  color: var(--df-text-primary);
  font-size: 14px;
  padding: 8px 12px;
  outline: none;

  &:focus { border-color: var(--df-color-primary); }

  &::placeholder { color: var(--df-text-tertiary); }
}

.form-select {
  max-width: 200px;
}

.form-textarea {
  resize: vertical;
  line-height: 1.6;
}

.submit-btn {
  margin-top: 24px;
  width: 100%;
  padding: 12px 0;
  border: none;
  border-radius: 10px;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.3s;

  &:hover { opacity: 0.9; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.item {
  padding: 18px 0;
  border-bottom: 1px solid var(--df-border-color);

  &:last-child { border-bottom: none; }
}

.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.item-type {
  font-weight: 600;
  color: var(--df-text-primary);
  font-size: 15px;
}

.item-status {
  font-size: 13px;
  padding: 2px 10px;
  border-radius: 10px;

  &--success { background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent); color: var(--df-color-primary); }
  &--danger { background: color-mix(in srgb, var(--df-color-danger) 10%, transparent); color: var(--df-color-danger); }
  &--warning { background: color-mix(in srgb, var(--df-color-warning) 12%, transparent); color: var(--df-color-warning); }
}

.item-content {
  margin: 12px 0;
  color: var(--df-text-regular);
  font-size: 14px;
  line-height: 1.7;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  color: var(--df-text-tertiary);
  font-size: 13px;
}

.item-result {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--df-bg-fill);
  color: var(--df-text-regular);
  font-size: 14px;
  line-height: 1.6;
}

.empty {
  padding: 48px 0;
  text-align: center;
  color: var(--df-text-tertiary);
}

.load-more {
  text-align: center;
  padding-top: 16px;

  button {
    padding: 8px 32px;
    border: 1px solid var(--df-border-color);
    border-radius: 8px;
    background: var(--df-bg-card);
    color: var(--df-text-regular);
    cursor: pointer;

    &:hover { color: var(--df-color-primary); }
    &:disabled { opacity: 0.6; cursor: not-allowed; }
  }
}
</style>
