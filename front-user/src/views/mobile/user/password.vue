<script setup lang="ts">
// 设置密码：验证码登录注册的账号无可见密码（邮箱桥派生密码），此处凭验证码设置新密码
// B6：登录态 + 邮箱验证码二次校验（验证码 = 持有邮箱凭证，替代旧密码）
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { sendEmailCode, setPassword } from '@/api/auth'
import { useUserStore } from '@/store/user'

defineOptions({ name: 'user-password' })

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
  } catch {
    // 发送失败：拦截器已 Toast，此处避免未捕获异常
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
  } catch {
    // 提交失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page password">
    <van-nav-bar title="设置密码" left-arrow @click-left="router.back()" />

    <div class="password__tip">
      验证码登录注册的账号未设置密码，设置后可使用「邮箱 + 密码」登录。验证码将发送至当前账号
      <b>{{ email }}</b>
    </div>

    <van-cell-group inset class="password__form">
      <van-field
        v-model="code"
        type="digit"
        maxlength="6"
        label="验证码"
        placeholder="6 位验证码"
      >
        <template #button>
          <van-button
            size="small"
            type="primary"
            plain
            :disabled="countdown > 0 || sending"
            @click.prevent="onSendCode"
          >
            {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
          </van-button>
        </template>
      </van-field>
      <van-field
        v-model="password"
        type="password"
        label="新密码"
        placeholder="至少 6 位"
        clearable
      />
      <van-field
        v-model="confirmPwd"
        type="password"
        label="确认密码"
        placeholder="再次输入新密码"
        clearable
      />
    </van-cell-group>

    <div class="password__submit">
      <van-button round block type="primary" :disabled="!canSubmit" :loading="submitting" @click="onSubmit">
        确认设置
      </van-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.password {
  &__tip {
    margin: $df-space-lg $df-space-lg 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    line-height: 1.7;
  }

  &__form {
    margin-top: $df-space-md;
  }

  &__submit {
    margin: $df-space-lg;
  }
}
</style>
