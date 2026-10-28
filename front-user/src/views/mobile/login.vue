<script setup lang="ts">
// 登录注册（邮箱验证码 / 账号密码；首登自动注册；163 邮箱真实发送随机 6 位验证码）
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { sendEmailCode } from '@/api/auth'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

type LoginMode = 'code' | 'password'
const loginMode = ref<LoginMode>('code')

const email = ref('')
const code = ref('')
const password = ref('')
const sending = ref(false)
const submitting = ref(false)
/** 验证码倒计时（秒），0 表示可发送 */
const countdown = ref(0)

// 报头日期
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})

const emailValid = computed(() => /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(email.value))
const canSubmit = computed(() => {
  if (!emailValid.value) return false
  return loginMode.value === 'code' ? code.value.length === 6 : password.value.length >= 6
})

async function onSendCode() {
  if (!emailValid.value) {
    showToast('请输入正确的邮箱')
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
  submitting.value = true
  try {
    if (loginMode.value === 'code') {
      await userStore.login(email.value, code.value)
    } else {
      await userStore.loginByPwd(email.value, password.value)
    }
    showSuccessToast('登录成功')
    router.replace(String(route.query.redirect ?? '/home'))
  } catch {
    // 登录失败：拦截器已 Toast（验证码错误/网络异常），此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <!-- 报头双线（顶部 + 底部） -->
    <div class="login__masthead">
      <div class="login__masthead-top">
        <h1>点废成金</h1>
        <p class="login__masthead-slogan">生活报 · 客户端</p>
      </div>
      <div class="login__masthead-meta">
        <span class="login__date">{{ today }}</span>
        <span>碳积分可信记账 · v2.0</span>
      </div>
    </div>

    <!-- 品牌区 -->
    <div class="login__brand">
      <div class="login__logo">
        <SvgIcon name="recycle-recycle-symbol" :size="44" color="var(--df-color-primary)" />
      </div>
      <div class="login__brand-txt">
        <h2>欢迎来到编辑部</h2>
        <p>请凭邮箱验证码或密码入场</p>
      </div>
    </div>

    <!-- 登录纸卡 -->
    <div class="login__paper">
      <div class="login__tabs">
        <button
          class="login__tab"
          :class="{ 'login__tab--active': loginMode === 'code' }"
          @click="loginMode = 'code'"
        >
          验证码登录
        </button>
        <button
          class="login__tab"
          :class="{ 'login__tab--active': loginMode === 'password' }"
          @click="loginMode = 'password'"
        >
          密码登录
        </button>
      </div>

      <van-form class="login__form" @submit="onSubmit">
        <!-- 普通 cell-group：由 form 统一控制内边距，避免 inset 自带 margin 导致对齐错乱 -->
        <van-cell-group>
          <van-field
            v-model="email"
            type="text"
            label="邮箱"
            placeholder="请输入邮箱"
            clearable
          />
          <!-- 验证码模式 -->
          <van-field
            v-if="loginMode === 'code'"
            v-model="code"
            type="digit"
            maxlength="6"
            label="验证码"
            placeholder="请输入 6 位验证码"
          />
          <!-- 密码模式 -->
          <van-field
            v-else
            v-model="password"
            type="password"
            label="密码"
            placeholder="请输入密码（至少 6 位）"
            clearable
          />
        </van-cell-group>
        <!-- 验证码发送按钮：独立成行，宽度与 cell-group 一致，避免超出卡片 -->
        <van-button
          v-if="loginMode === 'code'"
          class="login__send-code"
          block
          size="small"
          type="primary"
          plain
          round
          :disabled="countdown > 0 || sending"
          @click.prevent="onSendCode"
        >
          {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
        </van-button>
        <div class="login__submit">
          <van-button round block type="primary" native-type="submit" :disabled="!canSubmit" :loading="submitting">
            {{ loginMode === 'code' ? '登录 / 注册' : '登录' }}
          </van-button>
          <p class="login__tip font-hand">
            {{ loginMode === 'code' ? '未注册邮箱验证后自动创建账号' : '首次使用请先用验证码模式注册' }}
          </p>
          <p class="login__agree">登录即表示同意<a href="#">《用户协议》</a>和<a href="#">《隐私政策》</a></p>
        </div>
      </van-form>
    </div>

    <!-- 报脚 -->
    <div class="login__foot">
      <span class="login__foot-ver">© 点废成金 · 环保编辑部</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.login {
  min-height: 100vh;
  // 纵向 flex：纸卡 margin:auto 实现垂直居中（内容超高时自动回到顶部滚动，不裁剪）
  display: flex;
  flex-direction: column;
  // 上下左右四边留白：小屏不贴边，大屏表单悬浮居中
  padding: $df-space-xl 20px;
  background:
    radial-gradient(ellipse at 20% 10%, rgba(74, 139, 61, 0.18), transparent 55%),
    radial-gradient(ellipse at 80% 90%, rgba(45, 90, 61, 0.22), transparent 55%),
    var(--df-bg-page);
  position: relative;
  box-sizing: border-box;

  // 报头双线（顶部 + 底部）
  &__masthead {
    position: relative;
    z-index: 1;
    flex-shrink: 0;
    padding: $df-space-md 0;
    margin-bottom: $df-space-md;
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
    position: relative;
    z-index: 1;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: $df-space-md;
    margin-bottom: $df-space-md;
  }

  &__logo {
    flex: 0 0 auto;
    color: var(--df-color-primary);

    :deep(svg) {
      width: 36px;
      height: 36px;
    }
  }

  &__brand-txt {
    h2 {
      margin: 0;
      font-family: $df-font-serif;
      font-size: $df-font-size-md;
      font-weight: 800;
      color: var(--df-text-primary);
      letter-spacing: -0.01em;
    }

    p {
      margin: 2px 0 0;
      font-size: $df-font-size-xs;
      color: var(--df-text-secondary);
      font-family: $df-font-hand;
    }
  }

  // 登录纸卡（报刊版式）
  &__paper {
    position: relative;
    z-index: 1;
    flex-shrink: 0;
    // 垂直居中：上下 margin 自动均分剩余空间（内容超高时自动回顶部滚动）
    margin: auto 0;
    background: var(--df-bg-card);
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-lg;
    box-shadow: var(--df-shadow-card);
    overflow: hidden;
  }

  &__tabs {
    display: flex;
    border-bottom: 1px solid var(--df-border-color);
  }

  &__tab {
    flex: 1;
    padding: 10px 0;
    font-size: $df-font-size-base;
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
        bottom: -1px;
        left: 50%;
        transform: translateX(-50%);
        width: 32px;
        height: 3px;
        background: var(--df-color-primary);
        border-radius: 2px;
      }
    }
  }

  &__form {
    // 统一内边距：所有表单元素（cell-group、按钮）都在这个安全区内，避免贴边/超出
    padding: $df-space-md $df-space-lg $df-space-lg;

    // 输入框 cell：上下增加 padding 消除拥挤，缩短 label 宽度让输入框更长
    :deep(.van-cell) {
      padding: $df-space-md $df-space-lg;
      align-items: center;
      &::after {
        left: $df-space-lg;
        right: $df-space-lg;
      }
      .van-field__label {
        width: auto;
        flex: 0 0 56px;
        margin-right: $df-space-md;
        color: var(--df-text-regular);
        font-size: $df-font-size-base;
      }
      .van-cell__value {
        flex: 1;
        min-width: 0;
      }
      .van-field__control {
        font-size: $df-font-size-base;
      }
    }
  }

  // 验证码发送按钮：独立成行，宽度 = form 内容区宽度，不超出卡片
  &__send-code {
    margin-top: $df-space-md;
    height: 40px;
    font-size: $df-font-size-sm;
    letter-spacing: 0.02em;
  }

  &__submit {
    margin-top: $df-space-md;
  }

  &__tip {
    margin-top: $df-space-sm;
    text-align: center;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__agree {
    margin-top: 4px;
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
    position: relative;
    z-index: 1;
    flex-shrink: 0;
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
