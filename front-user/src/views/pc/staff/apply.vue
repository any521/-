<script setup lang="ts">
// PC 工作人员申请（G1，onsite01）：工种单选 + 实名拦截 + 申请态只读/重申请
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { useUserStore } from '@/store/user'
import { hasToken } from '@/api/token'
import { getRealNameStatus } from '@/api/business'
import { applyStaff, getStaffApplyStatus, type StaffApplyStatus } from '@/api/staff'

defineOptions({ name: 'pc-staff-apply' })

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const submitting = ref(false)
const realVerified = ref(false)
const applyInfo = ref<StaffApplyStatus>({ applied: false })
const form = ref<{ staffType: 1 | 2 | 3; staffName: string; phone: string; reason: string }>({
  staffType: 1,
  staffName: '',
  phone: '',
  reason: '',
})

async function load() {
  try {
    const [r, s] = await Promise.all([getRealNameStatus(), getStaffApplyStatus()])
    realVerified.value = r.status === 2
    applyInfo.value = s
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (!form.value.staffName.trim()) {
    showToast('请输入姓名')
    return
  }
  if (!/^1\d{10}$/.test(form.value.phone.trim())) {
    showToast('请输入正确的手机号')
    return
  }
  submitting.value = true
  try {
    await applyStaff({
      staffType: form.value.staffType,
      staffName: form.value.staffName.trim(),
      phone: form.value.phone.trim(),
      reason: form.value.reason.trim() || undefined,
    })
    showSuccessToast('已提交，请等待审核')
    router.replace('/pc/mine')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (hasToken() && !userStore.profile) userStore.fetchProfile()
  load()
})
</script>

<template>
  <div class="pc-staff-apply">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">工作人员申请</h1>
      </div>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else>
        <!-- 未实名：置顶提示 + 去实名 -->
        <div v-if="!realVerified" class="notice-bar">
          <span>申请工作人员需先完成实名认证</span>
          <button class="notice-btn" @click="router.push('/pc/user/realname')">去实名</button>
        </div>

        <!-- 审核中：只读态 -->
        <div v-else-if="applyInfo.applied && applyInfo.auditStatus === 0" class="section-card status-card">
          <div class="status-icon"><SvgIcon name="nav-clock" :size="48" /></div>
          <div class="status-title">申请审核中</div>
          <div class="status-tip">您的申请已提交，审核通过后即可切换工作人员模式，请耐心等待</div>
        </div>

        <!-- 已上岗：只读态 -->
        <div v-else-if="applyInfo.applied && applyInfo.auditStatus === 1" class="section-card status-card">
          <div class="status-icon"><SvgIcon name="carbon-checked" :size="48" /></div>
          <div class="status-title">已上岗</div>
          <div class="status-tip">审核已通过，可前往「个人中心」切换工作人员模式</div>
          <button class="submit-btn" @click="router.replace('/pc/mine')">返回个人中心</button>
        </div>

        <!-- 已驳回 / 未申请：表单 -->
        <template v-else>
          <div v-if="applyInfo.applied && applyInfo.auditStatus === 2" class="section-card rejected-card">
            <div class="rejected-reason">上次申请未通过：{{ applyInfo.reason || '未填写原因' }}</div>
            <div class="status-tip">请修正信息后重新申请</div>
          </div>

          <div class="section-card">
            <h3 class="section-title">申请工种</h3>
            <div class="type-grid">
              <button
                class="type-card"
                :class="{ 'type-card--active': form.staffType === 1 }"
                @click="form.staffType = 1"
              >
                <div class="type-name">回收员</div>
                <div class="type-desc">上门回收、称重登记</div>
              </button>
              <button
                class="type-card"
                :class="{ 'type-card--active': form.staffType === 2 }"
                @click="form.staffType = 2"
              >
                <div class="type-name">清运工</div>
                <div class="type-desc">垃圾清运、投放审核</div>
              </button>
              <button
                class="type-card"
                :class="{ 'type-card--active': form.staffType === 3 }"
                @click="form.staffType = 3"
              >
                <div class="type-name">维修技术员</div>
                <div class="type-desc">上门维修、检修报告</div>
              </button>
            </div>
          </div>

          <div class="section-card">
            <h3 class="section-title">申请信息</h3>
            <div class="form-row">
              <label class="form-label">姓名</label>
              <input v-model="form.staffName" class="form-input" placeholder="请输入姓名" maxlength="32" />
            </div>
            <div class="form-row">
              <label class="form-label">手机号</label>
              <input v-model="form.phone" class="form-input" placeholder="请输入手机号" maxlength="11" />
            </div>
            <div class="form-row form-row--static">
              <label class="form-label">所属片区</label>
              <span class="form-static">可不选，审核通过后由平台分配片区</span>
            </div>
            <div class="form-row form-row--textarea">
              <label class="form-label">备注</label>
              <textarea
                v-model="form.reason"
                class="form-textarea"
                rows="2"
                placeholder="选填：自我介绍 / 从业经验等"
                maxlength="255"
              ></textarea>
            </div>
            <button class="submit-btn" :disabled="submitting" @click="onSubmit">
              {{ submitting ? '提交中…' : applyInfo.applied && applyInfo.auditStatus === 2 ? '重新申请' : '提交申请' }}
            </button>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-staff-apply {
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

.loading {
  padding: 48px;
  text-align: center;
  color: var(--df-text-secondary);
}

.notice-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  margin-bottom: 24px;
  border-radius: 12px;
  background: #fffbe8;
  color: #ed6a0c;
  font-size: 14px;
}

.notice-btn {
  flex-shrink: 0;
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;

  &:hover { opacity: 0.9; }
}

.section-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 28px;
  margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.status-card {
  text-align: center;
  padding: 48px 28px;
}

.rejected-card {
  text-align: center;
  padding: 24px 28px;
}

.status-icon {
  color: var(--df-color-primary);
}

.status-title {
  margin-top: 16px;
  font-size: 22px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.rejected-reason {
  color: var(--df-color-danger);
  font-size: 15px;
}

.status-tip {
  margin-top: 8px;
  color: var(--df-text-secondary);
  font-size: 14px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin: 0 0 20px;
}

.type-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.type-card {
  padding: 20px;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.3s ease;

  &--active {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent);
  }
}

.type-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.type-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--df-text-secondary);
}

.form-row {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--df-border-color);

  &--static { align-items: flex-start; }
  &--textarea { align-items: flex-start; }
}

.form-label {
  width: 100px;
  flex-shrink: 0;
  color: var(--df-text-regular);
  font-size: 15px;
}

.form-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--df-text-primary);
  font-size: 15px;

  &::placeholder { color: var(--df-text-tertiary); }
}

.form-static {
  color: var(--df-text-secondary);
  font-size: 14px;
}

.form-textarea {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  resize: none;
  color: var(--df-text-primary);
  font-size: 15px;
  line-height: 1.6;

  &::placeholder { color: var(--df-text-tertiary); }
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
</style>
