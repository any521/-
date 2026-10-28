<script setup lang="ts">
// 请假（B3，对接 StaffOpsController）：双 tab —— 请假申请（日期区间+事由） / 我的请假（分页状态列表）
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { pageMyLeaves, submitLeave, type LeaveRequestItem } from '@/api/business'

defineOptions({ name: 'leave' })

const router = useRouter()

const activeTab = ref<'apply' | 'mine'>('apply')

// ---- 请假申请 ----
const showStartPicker = ref(false)
const showEndPicker = ref(false)
const form = reactive({ startDate: '', endDate: '', reason: '' })
const submitting = ref(false)

const pickerMin = new Date(new Date().getFullYear() - 1, 0, 1)
const pickerMax = new Date(new Date().getFullYear() + 1, 11, 31)

function onStartConfirm({ selectedValues }: { selectedValues: string[] }) {
  form.startDate = selectedValues.join('-')
  showStartPicker.value = false
}
function onEndConfirm({ selectedValues }: { selectedValues: string[] }) {
  form.endDate = selectedValues.join('-')
  showEndPicker.value = false
}

async function onSubmit() {
  if (!form.startDate || !form.endDate) {
    showFailToast('请选择请假日期区间')
    return
  }
  if (form.startDate > form.endDate) {
    showFailToast('开始日期不能晚于结束日期')
    return
  }
  if (!form.reason.trim()) {
    showFailToast('请填写请假事由')
    return
  }
  submitting.value = true
  try {
    await submitLeave({ startDate: form.startDate, endDate: form.endDate, reason: form.reason.trim() })
    showSuccessToast('请假已提交，等待审核')
    form.startDate = ''
    form.endDate = ''
    form.reason = ''
    resetList()
    activeTab.value = 'mine'
    onLoad()
  } finally {
    submitting.value = false
  }
}

// ---- 我的请假（分页） ----
const list = ref<LeaveRequestItem[]>([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const finished = ref(false)

const leaveTagType: Record<number, 'warning' | 'success' | 'danger'> = { 0: 'warning', 1: 'success', 2: 'danger' }
const leaveStatusText: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已驳回' }

function resetList() {
  list.value = []
  pageNum.value = 1
  total.value = 0
  finished.value = false
}

async function onLoad() {
  loading.value = true
  try {
    const page = await pageMyLeaves({ pageNum: pageNum.value, pageSize })
    const rows = page.list ?? []
    list.value = [...list.value, ...rows]
    total.value = Number(page.total ?? 0)
    finished.value = list.value.length >= total.value
    pageNum.value += 1
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page leave">
    <van-nav-bar title="请假" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="activeTab">
      <!-- 请假申请 -->
      <van-tab title="请假申请" name="apply">
        <div class="card leave__form">
          <van-cell-group :border="false">
            <van-field
              v-model="form.startDate"
              label="开始日期"
              placeholder="选择开始日期"
              readonly
              is-link
              @click="showStartPicker = true"
            />
            <van-field
              v-model="form.endDate"
              label="结束日期"
              placeholder="选择结束日期"
              readonly
              is-link
              @click="showEndPicker = true"
            />
            <van-field
              v-model="form.reason"
              label="请假事由"
              type="textarea"
              rows="3"
              maxlength="200"
              show-word-limit
              placeholder="请填写请假事由（必填，200 字以内）"
            />
          </van-cell-group>
          <van-button
            round
            block
            type="primary"
            class="leave__submit"
            :loading="submitting"
            @click="onSubmit"
          >
            提交申请
          </van-button>
        </div>
      </van-tab>

      <!-- 我的请假 -->
      <van-tab title="我的请假" name="mine">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
          <div v-for="item in list" :key="item.id" class="card leave__item">
            <div class="leave__item-head">
              <span class="leave__item-range">{{ item.startDate }} ~ {{ item.endDate }}</span>
              <van-tag :type="leaveTagType[item.status] ?? 'default'" round>
                {{ leaveStatusText[item.status] ?? item.status }}
              </van-tag>
            </div>
            <div class="leave__item-reason">{{ item.reason }}</div>
            <div class="leave__item-meta">
              {{ item.createTime.replace('T', ' ').slice(0, 16) }}
              <template v-if="item.status === 2">
                <van-icon name="warning-o" /> 驳回原因：{{ item.auditRemark || '未填写' }}
              </template>
              <template v-else-if="item.status === 0">待审核单暂不可撤销</template>
            </div>
          </div>
          <van-empty v-if="!loading && finished && list.length === 0" description="暂无请假记录" />
        </van-list>
      </van-tab>
    </van-tabs>

    <van-popup v-model:show="showStartPicker" position="bottom" round>
      <van-date-picker
        :min-date="pickerMin"
        :max-date="pickerMax"
        @confirm="onStartConfirm"
        @cancel="showStartPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showEndPicker" position="bottom" round>
      <van-date-picker
        :min-date="pickerMin"
        :max-date="pickerMax"
        @confirm="onEndConfirm"
        @cancel="showEndPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.leave {
  &__form {
    margin-top: $df-space-md;
  }

  &__submit {
    margin-top: $df-space-lg;
  }

  &__item {
    margin: $df-space-md;
    margin-bottom: 0;

    &:last-child {
      margin-bottom: $df-space-md;
    }
  }

  &__item-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $df-space-sm;
  }

  &__item-range {
    font-weight: 600;
    font-size: $df-font-size-md;
  }

  &__item-reason {
    margin-top: $df-space-sm;
    color: var(--df-text-primary);
    font-size: $df-font-size-base;
  }

  &__item-meta {
    margin-top: $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }
}
</style>
