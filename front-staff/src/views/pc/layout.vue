<script setup lang="ts">
// PC 端布局：侧边栏（Logo/三 Tab/主题切换/员工信息/退出）+ 内容区
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { applyThemeWithReveal, getNextTheme, themeState, THEME_LABELS } from '@shared/theme'
import { useStaffStore } from '@/store/staff'
import { handoffToUser } from '@/api/auth'

defineOptions({ name: 'pc-layout' })

const router = useRouter()
const route = useRoute()
const staffStore = useStaffStore()

// 主题外观（两套：亮色/深色，共享响应式状态，与“我的”页选择器实时同步）
const nextThemeLabel = computed(() => THEME_LABELS[getNextTheme(themeState.value)])
const themeIcon = computed(() => (themeState.value === 'light' ? 'nav-sun' : 'nav-moon'))
function onToggleTheme(e: MouseEvent) {
  const next = getNextTheme(themeState.value)
  const rect = (e.currentTarget as HTMLElement | null)?.getBoundingClientRect()
  applyThemeWithReveal(
    next,
    rect ? rect.left + rect.width / 2 : window.innerWidth / 2,
    rect ? rect.top + rect.height / 2 : window.innerHeight / 2,
  )
}

/** 侧栏导航项（Tab 高亮按路径前缀匹配） */
const navItems = [
  { label: '接单台', path: '/pc/tasks', icon: 'todo-list-o' },
  { label: '工作台', path: '/pc/work', icon: 'apps-o' },
  { label: '我的', path: '/pc/mine', icon: 'user-o' },
]
const isActive = (path: string) => route.path === path

async function onLogout() {
  await showConfirmDialog({ title: '退出登录', message: '确认退出当前账号？' })
  await staffStore.logout()
  router.replace('/pc/login')
}

/** 双身份互通（C-O1）：先换回 USER 会话中转令牌再跳用户端，免重复登录
 * （BASE_URL=.../staff/，去尾缀即用户端；dev/nginx 同域反代） */
async function switchToClient() {
  const ok = await handoffToUser().catch(() => false)
  if (!ok) showToast('切换会话失败，跳转后可能需要重新登录')
  window.location.href = import.meta.env.BASE_URL.replace(/staff\/$/, '')
}

// 员工姓名首字（无头像时占位）
const avatarChar = computed(() => (staffStore.profile?.name || '员').slice(0, 1))
</script>

<template>
  <div class="pc-layout">
    <aside class="pc-aside">
      <!-- 品牌区 -->
      <div class="pc-aside__brand">
        <div class="pc-aside__ava">
          <SvgIcon name="nav-truck" :size="22" color="var(--df-drawer-text)" />
        </div>
        <div class="pc-aside__brand-txt">
          <h1>点废成金</h1>
          <p>员工端 · 接单编辑部</p>
        </div>
      </div>

      <!-- 员工身份卡 -->
      <div class="pc-aside__user" @click="router.push('/pc/mine')">
        <img
          v-if="staffStore.profile?.avatar"
          :src="staffStore.profile.avatar"
          class="pc-aside__avatar"
          alt="头像"
        />
        <span v-else class="pc-aside__avatar pc-aside__avatar--fallback">{{ avatarChar }}</span>
        <div class="pc-aside__user-info">
          <div class="pc-aside__user-name">{{ staffStore.profile?.name ?? '—' }}</div>
          <div class="pc-aside__user-sub">在线服务中</div>
        </div>
      </div>

      <!-- 导航列表 -->
      <nav class="pc-aside__nav">
        <div class="pc-aside__sep">工作台</div>
        <a
          v-for="item in navItems"
          :key="item.path"
          class="pc-aside__navi"
          :class="{ 'is-active': isActive(item.path) }"
          @click="router.push(item.path)"
        >
          <van-icon :name="item.icon" size="18" />
          <span>{{ item.label }}</span>
        </a>
      </nav>

      <!-- 底部：主题切换 + 切换客户端 + 退出 -->
      <div class="pc-aside__foot">
        <button
          class="pc-aside__action"
          :title="`切换到${nextThemeLabel}模式`"
          @click="onToggleTheme"
        >
          <SvgIcon :name="themeIcon" :size="18" />
        </button>
        <button class="pc-aside__action" title="切换到用户端" @click="switchToClient">
          <van-icon name="exchange" size="18" />
        </button>
        <button class="pc-aside__action" title="退出登录" @click="onLogout">
          <van-icon name="revoke" size="18" />
        </button>
      </div>
    </aside>

    <div class="pc-layout__content">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.pc-layout {
  min-height: 100vh;
  position: relative;
  background: transparent;
}

// ---------- 侧边栏 ----------
.pc-aside {
  background: linear-gradient(180deg, var(--df-bg-dark-top), var(--df-bg-dark));
  color: var(--df-drawer-text);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  width: var(--df-sidebar-w);
  height: 100vh;
  z-index: 30;
  overflow: hidden;
}

.pc-aside__brand {
  padding: 24px 20px 18px;
  display: flex;
  align-items: center;
  gap: 13px;
}

.pc-aside__ava {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  overflow: hidden;
  flex: 0 0 auto;
  background: var(--df-drawer-hover);
  box-shadow: 0 0 0 1px var(--df-drawer-active);
  display: grid;
  place-items: center;
}

.pc-aside__brand-txt {
  h1 {
    margin: 0;
    font-size: 17px;
    font-weight: 800;
    letter-spacing: -0.01em;
    font-family: var(--df-font-serif);
    color: var(--df-drawer-text);
  }

  p {
    margin: 3px 0 0;
    font-size: 11px;
    opacity: 0.5;
    letter-spacing: 0.05em;
  }
}

// ---------- 员工身份卡 ----------
.pc-aside__user {
  margin: 0 12px 8px;
  padding: 11px 12px;
  border-radius: 11px;
  background: var(--df-drawer-hover);
  display: flex;
  align-items: center;
  gap: 11px;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: var(--df-drawer-active);
  }
}

.pc-aside__avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 10px;
  object-fit: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--df-drawer-active);
  color: var(--df-drawer-text);
  font-family: var(--df-font-serif);
  font-size: 16px;
  font-weight: 700;

  &--fallback {
    background: var(--df-drawer-active);
  }
}

.pc-aside__user-info {
  flex: 1;
  min-width: 0;
}

.pc-aside__user-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--df-drawer-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pc-aside__user-sub {
  font-size: 11px;
  color: var(--df-drawer-text-mute);
  margin-top: 2px;
}

// ---------- 导航列表 ----------
.pc-aside__nav {
  flex: 1;
  overflow-y: auto;
  padding: 6px 12px 12px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--df-drawer-text-mute);
    border-radius: 6px;
  }
}

.pc-aside__sep {
  font-size: 10.5px;
  opacity: 0.42;
  padding: 15px 14px 6px;
  letter-spacing: 0.1em;
  color: var(--df-drawer-text);
}

.pc-aside__navi {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: 11px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: color-mix(in srgb, var(--df-drawer-text) 82%, transparent);
  transition: background 0.15s ease;
  margin-bottom: 2px;
  user-select: none;

  &:hover {
    background: var(--df-drawer-hover);
  }

  &.is-active {
    background: var(--df-drawer-active);
    color: var(--df-drawer-text);
    font-weight: 700;
  }

  :deep(.van-icon) {
    color: var(--df-drawer-text-mute);
    flex: 0 0 auto;
  }

  &.is-active :deep(.van-icon) {
    color: var(--df-drawer-text);
  }
}

// ---------- 底部操作区 ----------
.pc-aside__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  border-top: 1px solid var(--df-drawer-hover);
}

.pc-aside__action {
  position: relative;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 11px;
  background: transparent;
  color: var(--df-drawer-text);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background 0.15s ease;

  &:hover {
    background: var(--df-drawer-hover);
  }
}

// ---------- 内容区 ----------
.pc-layout__content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  margin-left: var(--df-sidebar-w);
  padding: 26px 34px 48px;
  background: transparent;
}

@media (max-width: 1024px) {
  .pc-layout__content {
    padding: 18px 18px 32px;
  }
}
</style>
