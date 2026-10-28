<script setup lang="ts">
// 登录页（D1）：对接真实后端账密登录
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/store/user'
import { tokenStore } from '@/api/token'
import RecycleLogo from '@/components/RecycleLogo.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.login(form)
    // 登录后也写 session-access-token（路由守卫恢复用）
    const accessToken = tokenStore.getAccessToken()
    if (accessToken) sessionStorage.setItem('df_admin_at', accessToken)
    router.push((route.query.redirect as string) ?? '/')
  } finally {
    loading.value = false
  }
}

// 报头日期
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})
</script>

<template>
  <div class="login">
    <div class="login__card">
      <!-- 报头双线 -->
      <div class="login__masthead">
        <div class="login__masthead-top">
          <h1>点废成金</h1>
          <p class="login__masthead-slogan">管理驾驶舱 · 森林编辑部</p>
        </div>
        <div class="login__masthead-meta">
          <span class="login__date">{{ today }}</span>
          <span>城市再生资源智能运营平台 v2.0</span>
        </div>
      </div>

      <!-- 品牌区 -->
      <div class="login__brand">
        <RecycleLogo class="login__logo" :size="44" />
        <div class="login__brand-txt">
          <h2>欢迎回到编辑部</h2>
          <p>请凭账号密码入场</p>
        </div>
      </div>

      <!-- 表单 -->
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="'User'" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="密码" :prefix-icon="'Lock'" />
        </el-form-item>
        <el-button type="primary" size="large" class="login__submit" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>

      <div class="login__foot">
        <span class="login__foot-ver">© 点废成金 · 环保编辑部</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: $df-space-xl;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(74, 139, 61, 0.18), transparent 55%),
    radial-gradient(ellipse at 80% 90%, rgba(45, 90, 61, 0.22), transparent 55%),
    transparent;
  position: relative;

  &__card {
    position: relative;
    width: 420px;
    max-width: 100%;
    padding: $df-space-xxl;
    background: var(--df-bg-card);
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-xl;
    box-shadow: var(--df-shadow-overlay);
  }

  // 报头双线（顶部 + 底部）
  &__masthead {
    padding-bottom: $df-space-md;
    margin-bottom: $df-space-xl;
    border-top: 3px double var(--df-color-primary);
    border-bottom: 1px solid var(--df-border-color);
    padding-top: $df-space-md;
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

  &__submit {
    width: 100%;
    background: var(--df-color-primary);
    border-color: var(--df-color-primary);
    font-weight: 700;
    letter-spacing: 0.05em;
    border-radius: $df-radius-md;

    &:hover {
      background: var(--df-color-primary-dark);
      border-color: var(--df-color-primary-dark);
    }
  }

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
