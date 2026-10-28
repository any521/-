<script setup lang="ts">
// PC 实名认证：状态机 0未认证 1待审 2已通过 3已驳回（GapFill B1 V1）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getRealNameStatus, submitRealName, type RealNameStatus } from '@/api/business'

defineOptions({ name: 'pc-user-realname' })

const router = useRouter()
const loading = ref(true)
const submitting = ref(false)
const info = ref<RealNameStatus>({ status: 0 })
const form = ref({ realName: '', idCard: '' })

// 身份证 18 位格式校验（同后端 RealNameSubmitDTO 正则）
const ID_CARD_RE = /^\d{17}[\dXx]$/

/** 姓名脱敏：首字 + **（展示用） */
const maskName = (name?: string) => (name ? `${name.slice(0, 1)}**` : '—')

async function loadStatus() {
  try {
    info.value = await getRealNameStatus()
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (form.value.realName.trim().length < 2 || form.value.realName.trim().length > 20) {
    showToast('真实姓名长度 2-20 字')
    return
  }
  if (!ID_CARD_RE.test(form.value.idCard.trim())) {
    showToast('身份证号格式不正确')
    return
  }
  submitting.value = true
  try {
    await submitRealName(form.value.realName.trim(), form.value.idCard.trim())
    showSuccessToast('已提交，请等待审核')
    await loadStatus()
  } finally {
    submitting.value = false
  }
}

onMounted(loadStatus)
</script>

<template>
  <div class="pc-realname">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">实名认证</h1>
      </div>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else>
        <!-- 已通过：完成态 -->
        <div v-if="info.status === 2" class="section-card status-card">
          <div class="status-icon"><SvgIcon name="carbon-checked" :size="48" /></div>
          <div class="status-title">实名认证已完成</div>
          <div class="status-name">姓名：{{ maskName(info.realName) }}</div>
          <div class="status-tip">身份信息已通过审核，不可修改</div>
        </div>

        <!-- 待审 -->
        <div v-else-if="info.status === 1" class="section-card status-card">
          <div class="status-icon"><SvgIcon name="nav-clock" :size="48" /></div>
          <div class="status-title">审核中</div>
          <div class="status-tip">实名资料已提交，预计 1-3 个工作日完成审核，请耐心等待</div>
        </div>

        <!-- 已驳回 -->
        <div v-else-if="info.status === 3" class="section-card status-card">
          <div class="status-icon status-icon--danger"><SvgIcon name="nav-close" :size="48" /></div>
          <div class="status-title">审核未通过</div>
          <div class="status-reason">原因：{{ info.remark || '未填写' }}</div>
          <div class="status-tip">请修正后重新提交</div>
        </div>

        <!-- 未认证 / 已驳回：表单 -->
        <div v-if="info.status === 0 || info.status === 3" class="section-card">
          <h3 class="section-title">身份信息</h3>
          <div class="form-row">
            <label class="form-label">真实姓名</label>
            <input v-model="form.realName" class="form-input" placeholder="请输入真实姓名" maxlength="20" />
          </div>
          <div class="form-row">
            <label class="form-label">身份证号</label>
            <input v-model="form.idCard" class="form-input" placeholder="请输入 18 位身份证号" maxlength="18" />
          </div>
          <button class="submit-btn" :disabled="submitting" @click="onSubmit">
            {{ submitting ? '提交中…' : '提交认证' }}
          </button>
          <p class="notice">实名信息仅用于服务核验，我们将严格保护您的隐私安全</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-realname {
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

.status-icon {
  color: var(--df-color-primary);

  &--danger { color: var(--df-color-danger); }
}

.status-title {
  margin-top: 16px;
  font-size: 22px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.status-name {
  margin-top: 12px;
  font-size: 16px;
  color: var(--df-text-regular);
}

.status-reason {
  margin-top: 12px;
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

.form-row {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--df-border-color);
}

.form-label {
  width: 100px;
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

.notice {
  margin-top: 12px;
  text-align: center;
  color: var(--df-text-tertiary);
  font-size: 13px;
}
</style>
