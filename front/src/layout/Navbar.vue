<script setup lang="ts">
// 顶栏（D1）：折叠开关 + 衬线大标题 + 邮票风日期 + 主题切换 + 用户下拉（退出登录）
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import { applyThemeWithReveal, getNextTheme, themeState, THEME_LABELS } from '@shared/theme'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const title = computed(() => (route.meta.title as string) ?? '工作台')

// 报头日期（报刊发行样式）
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
})

// 主题模式（两套：亮色/深色，共享响应式状态，与用户端/员工端同步）
const nextThemeLabel = computed(() => THEME_LABELS[getNextTheme(themeState.value)])
function onToggleTheme(e: MouseEvent) {
  const next = getNextTheme(themeState.value)
  const rect = (e.currentTarget as HTMLElement | null)?.getBoundingClientRect()
  applyThemeWithReveal(
    next,
    rect ? rect.left + rect.width / 2 : window.innerWidth / 2,
    rect ? rect.top + rect.height / 2 : window.innerHeight / 2,
  )
}

async function handleCommand(command: string) {
  if (command !== 'logout') return
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  // 先清理本地状态（不等待 API 返回）
  const logoutPromise = userStore.logout()
  permissionStore.reset()
  await logoutPromise
  // 强制整页刷新，清空动态路由与 keep-alive 缓存
  window.location.href = `${import.meta.env.BASE_URL}login`
}
</script>

<template>
  <div class="navbar">
    <div class="navbar__left">
      <el-icon class="navbar__collapse" @click="appStore.toggleSidebar()">
        <Expand v-if="appStore.sidebarCollapsed" />
        <Fold v-else />
      </el-icon>
      <div class="navbar__title">
        <h2>{{ title }}</h2>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="title && route.path !== '/dashboard'">{{ title }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>
    </div>

    <div class="navbar__right">
      <span class="navbar__date">
        <el-icon><Calendar /></el-icon>
        <span>{{ today }}</span>
      </span>

      <el-tooltip :content="`切换到${nextThemeLabel}模式`" placement="bottom">
        <button class="navbar__theme" @click="onToggleTheme">
          <el-icon>
            <Sunny v-if="themeState === 'light'" />
            <Moon v-else />
          </el-icon>
        </button>
      </el-tooltip>

      <el-dropdown @command="handleCommand">
        <span class="navbar__user">
          <el-avatar :size="32" class="navbar__avatar">{{ userStore.nickname.slice(0, 1) }}</el-avatar>
          <span class="navbar__user-name">{{ userStore.nickname }}</span>
          <el-icon class="navbar__caret"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  width: 100%;
  gap: var(--df-space-md);

  &__left {
    display: flex;
    align-items: center;
    gap: var(--df-space-md);
    min-width: 0;
  }

  &__collapse {
    font-size: 18px;
    cursor: pointer;
    color: var(--df-text-regular);
    padding: 6px;
    border-radius: var(--df-radius-sm);
    transition: all 0.15s ease;

    &:hover {
      color: var(--df-color-primary);
      background: var(--df-color-primary-light);
    }
  }

  &__title {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;

    h2 {
      margin: 0;
      font-family: var(--df-font-serif);
      font-size: 20px;
      font-weight: 800;
      letter-spacing: -0.02em;
      color: var(--df-color-title);
      line-height: 1.2;
    }

    :deep(.el-breadcrumb) {
      font-size: 12px;
    }
  }

  &__right {
    display: flex;
    align-items: center;
    gap: var(--df-space-md);
  }

  &__date {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: var(--df-text-secondary);
    background: var(--df-bg-card);
    border: 1.5px dashed var(--df-color-primary);
    border-radius: 4px;
    padding: 7px 13px;
    box-shadow: var(--df-shadow-card);

    .el-icon {
      color: var(--df-text-secondary);
      font-size: 14px;
    }
  }

  &__theme {
    width: 38px;
    height: 38px;
    border: none;
    border-radius: var(--df-radius-sm);
    background: var(--df-bg-card);
    box-shadow: var(--df-shadow-card);
    display: grid;
    place-items: center;
    cursor: pointer;
    color: var(--df-text-primary);
    transition: all 0.18s ease;

    &:hover {
      transform: scale(0.94);
      color: var(--df-color-primary);
    }

    .el-icon {
      font-size: 18px;
    }
  }

  &__user {
    display: flex;
    align-items: center;
    gap: var(--df-space-sm);
    cursor: pointer;
    color: var(--df-text-primary);
    outline: none;
    padding: 4px 12px 4px 4px;
    border: 1px solid var(--df-border-color);
    border-radius: var(--df-radius-round);
    background: var(--df-bg-card);
    transition: all 0.2s ease;

    &:hover {
      border-color: var(--df-color-primary);
      color: var(--df-color-primary);
    }
  }

  &__avatar {
    background: var(--df-color-primary);
    color: var(--df-on-accent);
    font-size: var(--df-font-size-sm);
    font-family: var(--df-font-serif);
    font-weight: 700;
  }

  &__user-name {
    font-size: 14px;
    font-weight: 500;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__caret {
    font-size: 12px;
    color: var(--df-text-secondary);
  }
}
</style>
