<script setup lang="ts">
// PC 设置密码：验证码登录注册的账号无可见密码（邮箱桥派生密码），此处凭验证码设置新密码
// B6：登录态 + 邮箱验证码二次校验（验证码 = 持有邮箱凭证，替代旧密码）
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { sendEmailCode, setPassword } from '@/api/auth'
import { useUserStore } from '@/store/user'

defineOptions({ name: 'pc-user-password' })

const router = useRouter()
const userStore = useUserStore()

const code = ref('')
const password = ref('')
const confirmPwd = ref('')
const sending = ref(false)
const submitting = ref(false)
/** 验证码倒计时（秒），0 表示可发送 */
const countdown = ref(0)

const email = computed(() => userStore.profile?.email ?? '')
const canSubmit = computed(
  () => code.value.length === 6 && password.value.length >= 6 && password.value === confirmPwd.value
)

async function onSendCode() {
  if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(email.value)) {
    showToast('当前账号未绑定邮箱')
    return
  }
  sending.value = true
  try {
    await sendEmailCode(email.value)
    showSuccessToast('验证码已发送至邮箱')
    countdown.value = 60
    const timer = setInterval(() => {
      if (--countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  if (password.value !== confirmPwd.value) {
    showToast('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    await setPassword(code.value, password.value)
    showSuccessToast('密码设置成功，下次可用新密码登录')
    router.back()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pc-password">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">设置密码</h1>
      </div>

      <div class="section-card">
        <h3 class="section-title">设置新密码</h3>
        <p class="tip">
          验证码登录注册的账号未设置密码，设置后可使用「邮箱 + 密码」登录。验证码将发送至当前账号
          <b>{{ email }}</b>
        </p>
        <div class="form-row">
          <label class="form-label">验证码</label>
          <input v-model="code" class="form-input" placeholder="6 位验证码" maxlength="6" inputmode="numeric" />
          <button class="code-btn" :disabled="countdown > 0 || sending" @click="onSendCode">
            {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
          </button>
        </div>
        <div class="form-row">
          <label class="form-label">新密码</label>
          <input v-model="password" type="password" class="form-input" placeholder="至少 6 位" maxlength="32" />
        </div>
        <div class="form-row">
          <label class="form-label">确认密码</label>
          <input v-model="confirmPwd" type="password" class="form-input" placeholder="再次输入新密码" maxlength="32" />
        </div>
        <button class="submit-btn" :disabled="!canSubmit || submitting" @click="onSubmit">
          {{ submitting ? '提交中…' : '确认设置' }}
        </button>
        <p class="notice">设置成功后请牢记新密码，下次登录可使用「邮箱 + 密码」</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-password {
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

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin: 0 0 12px;
}

.tip {
  margin: 0 0 8px;
  color: var(--df-text-secondary);
  font-size: 14px;
  line-height: 1.7;
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

.code-btn {
  padding: 6px 14px;
  border: 1px solid var(--df-color-primary);
  border-radius: 8px;
  background: transparent;
  color: var(--df-color-primary);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;

  &:hover { background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent); }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
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
