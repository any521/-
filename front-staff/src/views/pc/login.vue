<script setup lang="ts">
// PC 端员工登录（工号+密码；演示账号由 V49 种子入库：R1001 回收员 / S2001 环卫员 / T3001 技术员，密码 123456）
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { useStaffStore } from '@/store/staff'

defineOptions({ name: 'pc-login' })

const route = useRoute()
const router = useRouter()
const staffStore = useStaffStore()

const staffNo = ref('')
const password = ref('')
const submitting = ref(false)

const canSubmit = computed(() => staffNo.value.length >= 4 && password.value.length >= 6)

/** 演示账号（V49 种子已入库，点击填充后走真实登录） */
const quickAccounts = [
  { no: 'R1001', label: '回收员', pwd: '123456' },
  { no: 'S2001', label: '环卫员', pwd: '123456' },
  { no: 'T3001', label: '技术员', pwd: '123456' },
]

function fillAccount(acc: { no: string; pwd: string }) {
  staffNo.value = acc.no
  password.value = acc.pwd
}

async function onSubmit() {
  submitting.value = true
  try {
    await staffStore.login(staffNo.value, password.value)
    showSuccessToast('登录成功')
    // 简写 redirect（如 /tasks）由路由守卫自动补 /pc 前缀
    router.replace(String(route.query.redirect ?? '/pc/tasks'))
  } finally {
    submitting.value = false
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
  <div class="pc-login">
    <div class="pc-login__card">
      <!-- 报头双线 -->
      <div class="pc-login__masthead">
        <div class="pc-login__masthead-top">
          <h1>点废成金</h1>
          <p class="pc-login__masthead-slogan">员工端 · 接单编辑部</p>
        </div>
        <div class="pc-login__masthead-meta">
          <span class="pc-login__date">{{ today }}</span>
          <span>接单 · 上门 · 核验登记 v2.0</span>
        </div>
      </div>

      <!-- 品牌区 -->
      <div class="pc-login__brand">
        <div class="pc-login__logo">
          <SvgIcon name="nav-truck" :size="44" color="var(--df-color-primary)" />
        </div>
        <div class="pc-login__brand-txt">
          <h2>欢迎归队，编辑部</h2>
          <p>请凭工号与密码签到</p>
        </div>
      </div>

      <van-form class="pc-login__form" @submit="onSubmit">
        <div class="pc-login__field">
          <label class="pc-login__label">工号</label>
          <van-field
            v-model="staffNo"
            placeholder="请输入员工工号"
            clearable
            autocapitalize="characters"
          />
        </div>
        <div class="pc-login__field">
          <label class="pc-login__label">密码</label>
          <van-field v-model="password" type="password" placeholder="请输入密码" />
        </div>

        <div class="pc-login__quick">
          <span class="pc-login__quick-label">演示账号：</span>
          <van-tag
            v-for="acc in quickAccounts"
            :key="acc.no"
            type="primary"
            plain
            size="medium"
            @click="fillAccount(acc)"
          >
            {{ acc.label }} {{ acc.no }}
          </van-tag>
        </div>

        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :disabled="!canSubmit"
          :loading="submitting"
          class="pc-login__submit"
        >
          登 录
        </van-button>
        <p class="pc-login__tip">账号由管理后台开通，忘记密码请联系片区管理员</p>
      </van-form>

      <!-- 报脚 -->
      <div class="pc-login__foot">
        <span class="pc-login__foot-ver">© 点废成金 · 接单编辑部</span>
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

  // 报头双线
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

  &__form {
    display: flex;
    flex-direction: column;
    gap: $df-space-md;
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: $df-space-xs;
  }

  &__label {
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-text-primary);
    font-family: $df-font-serif;
    letter-spacing: 0.03em;
  }

  &__quick {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: $df-space-sm;
    margin: $df-space-sm 0 $df-space-xs;
  }

  &__quick-label {
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
    font-family: $df-font-serif;
    letter-spacing: 0.03em;
  }

  &__submit {
    margin-top: $df-space-xs;
    letter-spacing: 0.05em;
    font-weight: 700;
  }

  &__tip {
    margin-top: $df-space-md;
    text-align: center;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
    font-family: $df-font-hand;
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
