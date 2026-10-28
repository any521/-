<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { sendEmailCode } from '@/api/auth'
import { dfToast } from '@shared/feedback'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

type LoginMode = 'code' | 'password'
const loginMode = ref<LoginMode>('code')

const form = reactive({
  email: '',
  code: '',
  password: '',
})

const codeSent = ref(false)
const countdown = ref(0)
const loading = ref(false)

const emailValid = (v: string) => /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(v)

// 报头日期
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})

const sendCode = async () => {
  if (!form.email || !emailValid(form.email)) {
    dfToast('请输入正确的邮箱', 'error')
    return
  }

  try {
    await sendEmailCode(form.email)
    dfToast('验证码已发送至邮箱', 'success')
  } catch {
    dfToast('验证码发送失败，请稍后重试', 'error')
    return
  }

  codeSent.value = true
  countdown.value = 60

  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      codeSent.value = false
    }
  }, 1000)
}

const handleLogin = async () => {
  if (loginMode.value === 'code') {
    if (!form.email || !form.code) {
      dfToast('请输入邮箱和验证码', 'error')
      return
    }
  } else {
    if (!form.email || !form.password) {
      dfToast('请输入邮箱和密码', 'error')
      return
    }
  }

  loading.value = true
  try {
    if (loginMode.value === 'code') {
      await userStore.login(form.email, form.code)
    } else {
      await userStore.loginByPwd(form.email, form.password)
    }
    const redirect = (route.query.redirect as string) || '/pc/home'
    router.replace(redirect)
  } catch {
    // 错误已由 request 拦截器 toast，此处静默
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="pc-login">
    <div class="pc-login__card">
      <!-- 报头双线 -->
      <div class="pc-login__masthead">
        <div class="pc-login__masthead-top">
          <h1>点废成金</h1>
          <p class="pc-login__masthead-slogan">生活报 · 客户端</p>
        </div>
        <div class="pc-login__masthead-meta">
          <span class="pc-login__date">{{ today }}</span>
          <span>城市再生资源智能运营平台 v2.0</span>
        </div>
      </div>

      <!-- 品牌区 -->
      <div class="pc-login__brand">
        <div class="pc-login__logo">
          <SvgIcon name="recycle-recycle-symbol" :size="44" color="var(--df-color-primary)" />
        </div>
        <div class="pc-login__brand-txt">
          <h2>欢迎来到编辑部</h2>
          <p>请凭邮箱验证码或密码入场</p>
        </div>
      </div>

      <!-- 登录方式切换 -->
      <div class="pc-login__tabs">
        <button
          class="pc-login__tab"
          :class="{ 'pc-login__tab--active': loginMode === 'code' }"
          @click="loginMode = 'code'"
        >
          验证码登录
        </button>
        <button
          class="pc-login__tab"
          :class="{ 'pc-login__tab--active': loginMode === 'password' }"
          @click="loginMode = 'password'"
        >
          密码登录
        </button>
      </div>

      <div class="pc-login__form">
        <div class="pc-login__field">
          <label>邮箱</label>
          <input
            v-model="form.email"
            type="text"
            placeholder="请输入邮箱"
          />
        </div>

        <!-- 验证码模式 -->
        <div v-if="loginMode === 'code'" class="pc-login__field">
          <label>验证码</label>
          <div class="pc-login__code-group">
            <input
              v-model="form.code"
              type="text"
              placeholder="请输入验证码"
              maxlength="6"
            />
            <button
              class="pc-login__code-btn"
              :disabled="codeSent"
              @click="sendCode"
            >
              {{ codeSent ? `${countdown}秒后重试` : '获取验证码' }}
            </button>
          </div>
        </div>

        <!-- 密码模式 -->
        <div v-else class="pc-login__field">
          <label>密码</label>
          <input
            v-model="form.password"
            type="password"
            placeholder="请输入密码（至少 6 位）"
          />
        </div>

        <button
          class="pc-login__submit"
          :disabled="loading"
          @click="handleLogin"
        >
          {{ loading ? '登录中...' : (loginMode === 'code' ? '登录 / 注册' : '登录') }}
        </button>

        <p class="pc-login__tip">
          {{ loginMode === 'code' ? '未注册邮箱验证后自动创建账号' : '首次使用请先用验证码模式注册' }}
        </p>
        <p class="pc-login__agree">登录即表示同意<a href="#">《用户协议》</a>和<a href="#">《隐私政策》</a></p>
      </div>

      <!-- 报脚 -->
      <div class="pc-login__foot">
        <span class="pc-login__foot-ver">© 点废成金 · 环保编辑部</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

 .pc-login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $df-space-xl $df-space-lg;
  position: relative;

  background:
    radial-gradient(ellipse at 20% 20%, rgba(45, 90, 61, 0.18), transparent 55%),
    radial-gradient(ellipse at 80% 90%, rgba(107, 183, 122, 0.14), transparent 55%),
    transparent;

  &__card {
    position: relative;
    z-index: 1;
    width: 100%;
    max-width: 460px;
    padding: $df-space-xxl $df-space-xxl $df-space-xl;
    background: var(--df-bg-card);
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-xl;
    box-shadow: var(--df-shadow-overlay);
  }

  // 报头双线（顶部 + 底部）
  &__masthead {
    padding: $df-space-md 0;
    margin-bottom: $df-space-xl;
    border-top: 3px double var(--df-color-primary);
    border-bottom: 1px solid var(--df-border-color);
  }

  &__masthead-top {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: $df-space-sm;

    h1 {
      margin: 0;
      font-family: $df-font-serif;
      font-size: $df-font-size-xl;
      font-weight: 800;
      color: var(--df-color-title);
      letter-spacing: -0.02em;
      line-height: 1.2;
    }
  }

  &__masthead-slogan {
    margin: 0;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    letter-spacing: 0.05em;
    font-family: $df-font-hand;
  }

  &__masthead-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: $df-space-xs;
    padding-top: $df-space-xs;
    border-top: 1px solid var(--df-border-color);
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__date {
    font-family: $df-font-serif;
    letter-spacing: 0.05em;
  }

  // 品牌区
  &__brand {
    display: flex;
    align-items: center;
    gap: $df-space-md;
    margin-bottom: $df-space-xl;
  }

  &__logo {
    flex: 0 0 auto;
    color: var(--df-color-primary);
  }

  &__brand-txt {
    h2 {
      margin: 0;
      font-family: $df-font-serif;
      font-size: $df-font-size-lg;
      font-weight: 800;
      color: var(--df-text-primary);
      letter-spacing: -0.01em;
    }

    p {
      margin: 4px 0 0;
      font-size: $df-font-size-sm;
      color: var(--df-text-secondary);
      font-family: $df-font-hand;
    }
  }

  &__tabs {
    display: flex;
    margin-bottom: $df-space-xl;
    border-bottom: 2px solid var(--df-border-color);
  }

  &__tab {
    flex: 1;
    padding: $df-space-md 0;
    font-size: $df-font-size-md;
    font-weight: 500;
    color: var(--df-text-regular);
    background: transparent;
    border: none;
    cursor: pointer;
    position: relative;
    transition: color 0.2s ease;

    &--active {
      color: var(--df-color-primary);
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        bottom: -2px;
        left: 50%;
        transform: translateX(-50%);
        width: 40px;
        height: 3px;
        background: var(--df-color-primary);
        border-radius: 2px;
      }
    }
  }

  &__form {
    display: flex;
    flex-direction: column;
    gap: $df-space-md;
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: $df-space-xs;

    label {
      font-size: $df-font-size-sm;
      font-weight: 600;
      color: var(--df-text-primary);
      font-family: $df-font-serif;
      letter-spacing: 0.03em;
    }

    input {
      padding: 12px 16px;
      font-size: $df-font-size-md;
      border: 1.5px solid var(--df-border-color);
      border-radius: $df-radius-md;
      background: var(--df-bg-card);
      color: var(--df-text-primary);
      transition: all 0.3s ease;

      &:focus {
        outline: none;
        border-color: var(--df-color-primary);
        box-shadow: 0 0 0 3px color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
      }
    }
  }

  &__code-group {
    display: flex;
    gap: $df-space-sm;

    input {
      flex: 1;
    }
  }

  &__code-btn {
    padding: 0 20px;
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-color-primary);
    background: var(--df-color-primary-light);
    border: 1.5px solid var(--df-color-primary);
    border-radius: $df-radius-md;
    cursor: pointer;
    white-space: nowrap;
    transition: all 0.3s ease;

    &:hover:not(:disabled) {
      background: var(--df-color-primary);
      color: var(--df-on-accent);
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  &__submit {
    padding: 14px;
    font-size: $df-font-size-md;
    font-weight: 700;
    color: var(--df-on-accent);
    background: var(--df-color-primary);
    border: none;
    border-radius: $df-radius-md;
    cursor: pointer;
    transition: all 0.3s ease;
    margin-top: $df-space-xs;
    letter-spacing: 0.05em;

    &:hover:not(:disabled) {
      background: var(--df-color-primary-dark);
      transform: translateY(-2px);
      box-shadow: 0 6px 20px color-mix(in srgb, var(--df-color-secondary) 40%, transparent);
    }

    &:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }
  }

  &__tip {
    text-align: center;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    margin-top: $df-space-xs;
    font-family: $df-font-hand;
  }

  &__agree {
    text-align: center;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);

    a {
      color: var(--df-color-primary);
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }
  }

  // 报脚
  &__foot {
    margin-top: $df-space-xl;
    padding-top: $df-space-md;
    border-top: 1px dashed var(--df-border-color);
    text-align: center;
  }

  &__foot-ver {
    font-family: $df-font-serif;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    letter-spacing: 0.08em;
  }
}
</style>
