<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useUnreadStore } from '@/store/unread'
import { dfConfirm, dfToast } from '@shared/feedback'
import { applyThemeWithReveal, getNextTheme, themeState, THEME_LABELS } from '@shared/theme'
import { HOME_ITEM, mainGroups, featureGroups } from '@/nav.config'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 主题外观（两套：亮色/深色，共享响应式状态，与“我的”页选择器实时同步）
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

// 导航数据（nav.config.ts 同源）：菜单1 首页直达 + 菜单2 主要页面 4 分组 + 菜单3 特色页面 3 分组
// 菜单4 消息为 foot 独立入口（带未读角标）
const isActive = (path: string) => {
  return route.path === path
}

const handleLogin = () => {
  router.push('/pc/login')
}

// ---- 头像 + 悬浮快捷菜单 ----
const menuOpen = ref(false)

const quickLinks = [
  { label: '个人中心', icon: 'nav-user', path: '/pc/mine' },
  { label: '我的订单', icon: 'nav-memo', path: '/pc/orders' },
  { label: '我的徽章', icon: 'carbon-user-cert', path: '/pc/mall/badges' },
  { label: '低碳账单', icon: 'carbon-carbon-leaf', path: '/pc/carbon/bill' },
]

/** 品牌头像：已登录展开快捷菜单，未登录跳登录页 */
function onAvaClick() {
  if (!userStore.isLogin()) {
    router.push('/pc/login')
    return
  }
  menuOpen.value = !menuOpen.value
}

function goQuick(path: string) {
  menuOpen.value = false
  router.push(path)
}

async function onLogout() {
  menuOpen.value = false
  const ok = await dfConfirm({
    kind: 'confirm',
    tone: 'warning',
    title: '退出登录',
    message: '确定要退出当前账号吗？',
    confirmText: '退出',
    danger: true,
  })
  if (!ok) return
  await userStore.logout()
  dfToast('已退出登录', 'success')
  router.replace('/pc/home')
}

// ---- 消息入口（未读红点数字角标）：store 统一 WS/轮询/已读同步 ----
const unreadStore = useUnreadStore()
const unreadCount = computed(() => unreadStore.count)

function goMessages() {
  router.push('/pc/user/message')
}

onMounted(() => {
  // 静默恢复会话：刷新后内存 Token 丢失/过期时用 Refresh Cookie 续期，并补拉画像
  userStore.restoreSession()
  unreadStore.start()
})

// 路由切换后刷新（从消息页返回时角标立即同步）
watch(() => route.path, () => {
  unreadStore.scheduleRefresh(0)
  menuOpen.value = false
})
</script>

<template>
  <!-- data-lenis-prevent：侧栏内滚轮走原生滚动，避免 Lenis 劫持导致“滑动的是页面不是菜单” -->
  <aside class="pc-sidebar" data-lenis-prevent>
    <!-- 品牌区（模板 .brand + .ava：头像化，点击展开快捷菜单/登录） -->
    <div class="pc-sidebar__brand">
      <div
        class="pc-sidebar__ava"
        :class="{ 'is-login': userStore.isLogin() }"
        :title="userStore.isLogin() ? '个人中心' : '立即登录'"
        @click="onAvaClick"
      >
        <img v-if="userStore.isLogin() && userStore.profile?.avatar" :src="userStore.profile.avatar" alt="头像" />
        <span v-else class="pc-sidebar__ava-fallback">
          <SvgIcon name="nav-user" :size="20" />
        </span>
        <span v-if="userStore.isLogin()" class="pc-sidebar__ava-cam">
          <SvgIcon name="ai-camera" :size="13" />
        </span>

        <!-- 快捷菜单（已登录展开） -->
        <Transition name="pc-sidebar-menu">
          <div v-if="menuOpen" class="pc-sidebar__menu" @click.stop>
            <div class="pc-sidebar__menu-user">
              <div class="pc-sidebar__menu-name">{{ userStore.profile?.nickname || '环保达人' }}</div>
              <div class="pc-sidebar__menu-email">{{ userStore.profile?.email || '点废成金会员' }}</div>
            </div>
            <div class="pc-sidebar__menu-links">
              <button
                v-for="link in quickLinks"
                :key="link.path"
                class="pc-sidebar__menu-item"
                @click.stop="goQuick(link.path)"
              >
                <span class="pc-sidebar__menu-icon">
                  <SvgIcon :name="link.icon" :size="16" />
                </span>
                <span class="pc-sidebar__menu-label">{{ link.label }}</span>
                <SvgIcon name="common-arrow-right" :size="14" class="pc-sidebar__menu-chevron" />
              </button>
            </div>
            <button class="pc-sidebar__menu-logout" @click.stop="onLogout">
              <SvgIcon name="common-close" :size="14" />
              退出登录
            </button>
          </div>
        </Transition>
      </div>
      <div class="pc-sidebar__logo-text">
        <h1>点废成金</h1>
        <p>生活报 · 客户端</p>
      </div>
    </div>

    <!-- 未登录：登录入口 -->
    <div v-if="!userStore.isLogin()" class="pc-sidebar__login-wrap">
      <button class="pc-sidebar__login" @click="handleLogin">立即登录</button>
    </div>

    <!-- 导航：菜单1 首页直达 / 菜单2 主要页面 / 菜单3 特色页面 -->
    <nav class="pc-sidebar__nav">
      <div class="pc-sidebar__sep">首页</div>
      <a
        class="pc-sidebar__navi"
        :class="{ 'is-active': isActive(HOME_ITEM.pc) }"
        @click="router.push(HOME_ITEM.pc)"
      >
        <SvgIcon :name="HOME_ITEM.icon" :size="18" />
        <span>{{ HOME_ITEM.label }}</span>
      </a>

      <div class="pc-sidebar__sep">主要页面</div>
      <template v-for="grp in mainGroups" :key="'m-' + grp.title">
        <div class="pc-sidebar__sub">{{ grp.title }}</div>
        <a
          v-for="item in grp.items"
          :key="item.pc"
          class="pc-sidebar__navi"
          :class="{ 'is-active': isActive(item.pc) }"
          @click="router.push(item.pc)"
        >
          <SvgIcon :name="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </a>
      </template>

      <div class="pc-sidebar__sep">特色页面</div>
      <template v-for="grp in featureGroups" :key="'f-' + grp.title">
        <div class="pc-sidebar__sub">{{ grp.title }}</div>
        <a
          v-for="item in grp.items"
          :key="item.pc"
          class="pc-sidebar__navi"
          :class="{ 'is-active': isActive(item.pc) }"
          @click="router.push(item.pc)"
        >
          <SvgIcon :name="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </a>
      </template>
    </nav>

    <!-- 底部：主题切换（模板 .theme-toggle pill）+ 消息（菜单4 入口，带未读角标）+ 退出 + 吉祥物 + 版本 -->
    <div class="pc-sidebar__foot">
      <div class="pc-sidebar__actions">
        <button
          class="theme-toggle"
          :title="`切换到${nextThemeLabel}模式`"
          @click="onToggleTheme"
        >
          <SvgIcon name="nav-moon" class="ic-moon" :size="14" />
          <SvgIcon name="nav-sun" class="ic-sun" :size="14" />
          <span>{{ nextThemeLabel }}</span>
        </button>
        <button
          v-if="userStore.isLogin()"
          class="pc-sidebar__action"
          title="消息中心"
          @click="goMessages"
        >
          <SvgIcon name="carbon-email" :size="18" />
          <span v-if="unreadCount > 0" class="pc-sidebar__badge">
            {{ unreadCount > 99 ? '99+' : unreadCount }}
          </span>
        </button>
        <button
          v-if="userStore.isLogin()"
          class="pc-sidebar__action"
          title="退出登录"
          @click="onLogout"
        >
          <SvgIcon name="common-close" :size="18" />
        </button>
      </div>
      <div class="pc-sidebar__meta">
        <svg class="mascot-svg" viewBox="0 0 60 70" xmlns="http://www.w3.org/2000/svg" aria-label="森林小人吉祥物">
          <path class="leaf-hat" d="M12 24 C12 16 19 9 30 9 C41 9 48 16 48 24 C43 19 36 16 30 16 C24 16 17 19 12 24 Z" />
          <path class="leaf-top" d="M30 3 C25 8 23 15 30 22 C37 15 35 8 30 3 Z" />
          <circle class="head" cx="30" cy="30" r="11" />
          <circle class="eye" cx="26" cy="29" r="1.3" />
          <circle class="eye" cx="34" cy="29" r="1.3" />
          <path class="smile" d="M26 34 Q30 37 34 34" />
          <path class="body" d="M19 44 Q30 42 41 44 L43 62 Q30 64 17 62 Z" />
          <path class="sprig" d="M30 44 L30 56 M30 50 C27 49 25 51 26 53 C28 53 30 52 30 50 Z M30 53 C33 52 35 54 34 56 C32 56 30 55 30 53 Z" />
        </svg>
        <span class="pc-sidebar__ver">点废成金 · 每日更新</span>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.pc-sidebar {
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
  overscroll-behavior: contain;
}

// ---------- 品牌区（模板 .brand + .ava 头像化） ----------
.pc-sidebar__brand {
  padding: 24px 20px 18px;
  display: flex;
  align-items: center;
  gap: 13px;
}

.pc-sidebar__ava {
  position: relative;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  flex: 0 0 auto;
  background: var(--df-drawer-hover);
  box-shadow: 0 0 0 1px var(--df-drawer-active);
  cursor: pointer;

  // 不能用 overflow:hidden 裁圆角：快捷菜单是它的绝对定位子元素（top:calc(100%+8px)），
  // 会被裁剪成“点了头像没反应”。改由 img 自身圆角裁剪，遮罩层同步加圆角。
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    border-radius: 13px;
  }
}

.pc-sidebar__ava-fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--df-drawer-text-mute);
}

.pc-sidebar__ava-cam {
  position: absolute;
  inset: 0;
  border-radius: 13px;
  display: grid;
  place-items: center;
  opacity: 0;
  background: rgba(35, 34, 32, 0.5);
  color: var(--df-drawer-text);
  transition: opacity 0.15s;

  .pc-sidebar__ava:hover & {
    opacity: 1;
  }
}

.pc-sidebar__logo-text {
  min-width: 0;

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
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// ---------- 登录按钮 ----------
.pc-sidebar__login-wrap {
  margin: 0 12px 8px;
  padding: 11px 12px;
}

.pc-sidebar__login {
  width: 100%;
  padding: 11px;
  font-size: 14px;
  font-weight: 700;
  color: var(--df-on-accent);
  background: var(--df-color-primary);
  border: none;
  border-radius: 11px;
  cursor: pointer;
  transition: filter 0.15s ease, transform 0.12s ease;

  &:hover {
    filter: brightness(1.1);
  }

  &:active {
    transform: scale(0.97);
  }
}

// ---------- 导航列表 ----------
.pc-sidebar__nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 6px 12px 12px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--df-drawer-text-mute);
    border-radius: 6px;
  }
}

.pc-sidebar__sep {
  font-size: 10.5px;
  opacity: 0.42;
  padding: 15px 14px 6px;
  letter-spacing: 0.1em;
  color: var(--df-drawer-text);
}

// 子分组标题（主要/特色页面内的业务分组）
.pc-sidebar__sub {
  font-size: 10px;
  opacity: 0.3;
  padding: 10px 14px 4px;
  letter-spacing: 0.14em;
  color: var(--df-drawer-text);
}

.pc-sidebar__navi {
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

  :deep(svg) {
    color: var(--df-drawer-text-mute);
    flex: 0 0 auto;
  }

  &.is-active :deep(svg) {
    color: var(--df-drawer-text);
  }
}

// ---------- 底部操作区（模板 .foot：主题 pill + 消息/退出 + 吉祥物 + 版本） ----------
.pc-sidebar__foot {
  padding: 12px 16px 14px;
  border-top: 1px solid var(--df-drawer-hover);
  flex: 0 0 auto;
}

.pc-sidebar__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pc-sidebar__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.pc-sidebar__ver {
  font-size: 11px;
  opacity: 0.42;
  color: var(--df-drawer-text);
  letter-spacing: 0.02em;
}

.pc-sidebar__action {
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

.pc-sidebar__badge {
  position: absolute;
  top: 4px;
  right: 4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  background: var(--df-color-stamp);
  border-radius: 999px;
}

// ---------- 快捷菜单弹层 ----------
// 挂在品牌头像下方展开；固定宽度以免受 46px 头像约束
.pc-sidebar__menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  width: 232px;
  padding: 8px;
  border-radius: 14px;
  background: var(--df-bg-card);
  border: 1px solid var(--df-border-color);
  box-shadow: var(--df-shadow-overlay);
  z-index: 40;

  &-user {
    padding: 10px 10px 8px;
    border-bottom: 1px solid var(--df-border-color);
    margin-bottom: 6px;
  }

  &-name {
    font-size: 14px;
    font-weight: 700;
    color: var(--df-text-primary);
  }

  &-email {
    font-size: 11px;
    color: var(--df-text-secondary);
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &-links {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &-item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 9px 10px;
    font-size: 13px;
    font-weight: 500;
    color: var(--df-text-regular);
    background: transparent;
    border: none;
    border-radius: 10px;
    cursor: pointer;
    text-align: left;
    transition: all 0.15s ease;

    &:hover {
      color: var(--df-color-primary);
      background: var(--df-color-primary-light);
    }
  }

  &-icon {
    width: 26px;
    height: 26px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    background: var(--df-bg-nested);
    color: var(--df-color-primary);
  }

  &-label {
    flex: 1;
  }

  &-chevron {
    color: var(--df-text-placeholder);
    transition: transform 0.2s ease;
  }

  &-item:hover &-chevron {
    transform: translateX(2px);
  }

  &-logout {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    width: 100%;
    margin-top: 6px;
    padding: 9px 0;
    font-size: 13px;
    font-weight: 600;
    color: var(--df-color-danger);
    background: transparent;
    border: 1px solid transparent;
    border-top: 1px solid var(--df-border-color);
    border-radius: 0 0 10px 10px;
    cursor: pointer;
    transition: all 0.15s ease;

    &:hover {
      background: var(--df-color-danger-muted);
    }
  }
}

.pc-sidebar-menu-enter-active,
.pc-sidebar-menu-leave-active {
  transition: opacity 0.22s ease, transform 0.22s cubic-bezier(0.34, 1.4, 0.64, 1);
}

.pc-sidebar-menu-enter-from,
.pc-sidebar-menu-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.94);
}
</style>
