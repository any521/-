<script setup lang="ts">
// 员工登录（工号+密码；演示账号由 V49 种子入库：R1001 回收员 / S2001 环卫员 / T3001 技术员，密码 123456）
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { useStaffStore } from '@/store/staff'

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
    router.replace(String(route.query.redirect ?? '/tasks'))
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
  <div class="login">
    <!-- 报头双线 -->
    <div class="login__masthead">
      <div class="login__masthead-top">
        <h1>点废成金</h1>
        <p class="login__masthead-slogan">员工端 · 接单编辑部</p>
      </div>
      <div class="login__masthead-meta">
        <span class="login__date">{{ today }}</span>
        <span>接单 · 上门 · 核验登记</span>
      </div>
    </div>

    <!-- 品牌区 -->
    <div class="login__brand">
      <div class="login__logo">
        <SvgIcon name="nav-truck" :size="44" color="var(--df-color-primary)" />
      </div>
      <div class="login__brand-txt">
        <h2>欢迎归队，编辑部</h2>
        <p>请凭工号与密码签到</p>
      </div>
    </div>

    <!-- 登录纸卡 -->
    <div class="login__paper">
      <van-form class="login__form" @submit="onSubmit">
        <!-- 普通 cell-group：由 form 统一控制内边距，避免 inset 自带 margin 导致对齐错乱 -->
        <van-cell-group>
          <van-field
            v-model="staffNo"
            label="工号"
            placeholder="请输入员工工号"
            clearable
            autocapitalize="characters"
          />
          <van-field v-model="password" type="password" label="密码" placeholder="请输入密码" />
        </van-cell-group>

        <div class="login__quick">
          <span class="login__quick-label">演示账号（点击填充）</span>
          <div class="login__quick-grid">
            <button
              v-for="acc in quickAccounts"
              :key="acc.no"
              type="button"
              class="login__quick-item"
              @click="fillAccount(acc)"
            >
              <span class="login__quick-role">{{ acc.label }}</span>
              <span class="login__quick-no">{{ acc.no }}</span>
            </button>
          </div>
        </div>

        <div class="login__submit">
          <van-button round block type="primary" native-type="submit" :disabled="!canSubmit" :loading="submitting">
            登 录
          </van-button>
          <p class="login__tip font-hand">账号由管理后台开通，忘记密码请联系片区管理员</p>
        </div>
      </van-form>
    </div>

    <!-- 报脚 -->
    <div class="login__foot">
      <span class="login__foot-ver">© 点废成金 · 接单编辑部</span>
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

  // 登录纸卡
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

  &__form {
    // 统一内边距：所有表单元素（cell-group、演示账号、登录按钮）都在这个安全区内，避免贴边/超出
    padding: $df-space-lg $df-space-lg $df-space-xl;

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
        flex: 0 0 48px;
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

  &__quick {
    margin-top: $df-space-md;
  }

  &__quick-label {
    display: block;
    margin-bottom: $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
    font-family: $df-font-serif;
    letter-spacing: 0.03em;
  }

  &__quick-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: $df-space-md;
  }

  &__quick-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: $df-space-md $df-space-sm;
    border: 1px dashed var(--df-border-color);
    border-radius: $df-radius-sm;
    background: transparent;
    cursor: pointer;
    transition:
      border-color 0.2s ease,
      color 0.2s ease,
      background 0.2s ease;

    &:active {
      border-color: var(--df-color-primary);
      color: var(--df-color-primary);
      background: rgba(74, 139, 61, 0.08);
    }
  }

  &__quick-role {
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__quick-no {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    font-family: $df-font-serif;
    letter-spacing: 0.05em;
  }

  &__submit {
    margin-top: $df-space-xl;
  }

  &__tip {
    margin-top: $df-space-md;
    text-align: center;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
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
