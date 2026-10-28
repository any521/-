<script setup lang="ts">
// 根组件：移动端 topbar + 抽屉式导航 + 路由出口 + 移动端底部 Tab（接单台/工作台/我的）+ 弱网离线横幅
// PC 端由 /pc/layout 侧栏接管，不渲染 topbar / drawer / tabbar
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useOffline } from '@/composables/useOffline'
import { applyThemeWithReveal, getNextTheme, themeState, THEME_LABELS } from '@shared/theme'
import { useStaffStore } from '@/store/staff'
import { handoffToUser } from '@/api/auth'
import SvgIcon from '@/components/SvgIcon.vue'

const route = useRoute()
const router = useRouter()
const staffStore = useStaffStore()
const { online, queueSize } = useOffline()

const isMobile = computed(() => route.path.startsWith('/mobile'))
const isLoginPage = computed(() => Boolean(route.name?.toString().includes('login')))

/** 仅移动端 tab 页展示底栏（meta.tab 且 /mobile 前缀） */
const showTabbar = computed(() => Boolean(route.meta.tab) && isMobile.value)
/** 仅移动端展示 topbar（drawer 入口），登录页不显示 */
const showTopbar = computed(() => isMobile.value && !isLoginPage.value)

/**
 * tabbar item name 为去前缀的简写（tasks/work/mine），
 * 而路由 name 带设备前缀（mobile-tasks 等），此处做双向映射。
 */
const active = computed({
  get: () => String(route.name ?? '').replace(/^(mobile|pc)-/, ''),
  set: (name: string) => {
    const prefix = route.path.startsWith('/pc') ? 'pc-' : 'mobile-'
    router.push({ name: prefix + name })
  },
})

// ---- 主题外观 ----
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

// ---- 抽屉导航 ----
const drawerOpen = ref(false)
function openDrawer() { drawerOpen.value = true }
function closeDrawer() { drawerOpen.value = false }

// 抽屉导航分组（移动端专用，与 PC 侧栏分类对齐）
const navGroups = [
  {
    title: '工作台',
    items: [
      { label: '接单台', path: '/mobile/tasks', icon: 'todo-list-o' },
      { label: '工作台', path: '/mobile/work', icon: 'apps-o' },
      { label: '我的', path: '/mobile/mine', icon: 'user-o' },
    ],
  },
  {
    title: '订单作业',
    items: [
      { label: '异常上报', path: '/mobile/abnormal', icon: 'warning-o' },
      { label: '投放审核', path: '/mobile/kitchen/audit', icon: 'eye-o' },
      { label: '我的绩效', path: '/mobile/performance', icon: 'chart-trending-o' },
      { label: '在线聊天', path: '/mobile/im', icon: 'chat-o' },
    ],
  },
  {
    title: '排班自助',
    items: [
      { label: '我的排班', path: '/mobile/schedule', icon: 'calendar-o' },
      { label: '请假', path: '/mobile/leave', icon: 'clock-o' },
    ],
  },
]

function goPath(path: string) {
  drawerOpen.value = false
  router.push(path)
}

const isActive = (path: string) => route.path === path

async function onLogout() {
  drawerOpen.value = false
  await showConfirmDialog({ title: '退出登录', message: '确认退出当前账号？' })
  await staffStore.logout()
  router.replace('/mobile/login')
}

/** 双身份互通（C-O1）：先换回 USER 会话中转令牌再跳用户端，免重复登录 */
async function switchToClient() {
  drawerOpen.value = false
  const ok = await handoffToUser().catch(() => false)
  if (!ok) showToast('切换会话失败，跳转后可能需要重新登录')
  window.location.href = '/'
}

// ---- topbar 日期 ----
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
})

// ---- topbar 标题（跟随路由 meta.title） ----
const topbarTitle = computed(() => (route.meta.title as string) || '点废成金')
const topbarSub = computed(() => {
  if (route.meta.tab) return '员工端 · 接单编辑部'
  return '点废成金 · 员工端'
})

// ---- 员工身份 ----
const avatarChar = computed(() => (staffStore.profile?.name || '员').slice(0, 1))

onMounted(() => {
  staffStore.fetchProfile()
})

watch(() => route.path, () => {
  drawerOpen.value = false
})
</script>

<template>
  <div class="app">
    <!-- 弱网/离线提示：离线操作先入队，恢复后自动重放 -->
    <van-notice-bar
      v-if="!online"
      mode="closeable"
      color="#fff"
      background="#ee0a24"
      left-icon="warning-o"
    >
      当前网络不可用，接单等操作将离线暂存{{ queueSize ? `（待同步 ${queueSize} 条）` : '' }}
    </van-notice-bar>

    <!-- 移动端 topbar（菜单按钮 + 标题 + 日期 chip） -->
    <div v-if="showTopbar" class="df-topbar">
      <button class="df-topbar__menu" aria-label="打开菜单" @click="openDrawer">
        <SvgIcon name="nav-memo" :size="20" />
      </button>
      <div class="df-topbar__title">
        {{ topbarTitle }}
        <small>{{ topbarSub }}</small>
      </div>
      <span class="df-topbar__date">
        <van-icon name="calendar-o" size="14" />
        {{ today }}
      </span>
    </div>

    <router-view v-slot="{ Component }">
      <keep-alive :include="['mobile-tasks', 'mobile-work', 'mobile-mine', 'pc-tasks', 'pc-work', 'pc-mine']">
        <component :is="Component" />
      </keep-alive>
    </router-view>

    <van-tabbar v-if="showTabbar" v-model="active" route-mode="name" placeholder>
      <van-tabbar-item name="tasks" icon="todo-list-o">接单台</van-tabbar-item>
      <van-tabbar-item name="work" icon="apps-o">工作台</van-tabbar-item>
      <van-tabbar-item name="mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>

    <!-- 抽屉式侧滑菜单 -->
    <div class="df-scrim" :class="{ 'is-show': drawerOpen }" @click="closeDrawer" />
    <aside class="df-drawer" :class="{ 'is-show': drawerOpen }">
      <!-- 头部：品牌 + 关闭 -->
      <div class="df-drawer__head">
        <div class="df-drawer__brand">
          <SvgIcon name="nav-truck" :size="22" color="var(--df-drawer-text)" />
        </div>
        <div class="df-drawer__title">
          <h2>点废成金</h2>
          <p>员工端 · 接单编辑部</p>
        </div>
        <button class="df-drawer__close" aria-label="关闭菜单" @click="closeDrawer">
          <SvgIcon name="common-close" :size="16" />
        </button>
      </div>

      <!-- 员工身份条 -->
      <div class="df-drawer__user" @click="goPath('/mobile/mine')">
        <img
          v-if="staffStore.profile?.avatar"
          :src="staffStore.profile.avatar"
          class="df-drawer__avatar"
          alt="头像"
        />
        <span v-else class="df-drawer__avatar df-drawer__avatar--fallback">{{ avatarChar }}</span>
        <div class="df-drawer__user-info">
          <div class="df-drawer__user-name">{{ staffStore.profile?.name ?? '未登录' }}</div>
          <div class="df-drawer__user-sub">
            {{ staffStore.profile?.staffNo ?? '—' }} · {{ staffStore.myAreaName || '未分配片区' }}
          </div>
        </div>
      </div>

      <!-- 导航分组 -->
      <nav class="df-drawer__nav">
        <template v-for="(grp, idx) in navGroups" :key="idx">
          <div class="df-drawer__sep">{{ grp.title }}</div>
          <a
            v-for="item in grp.items"
            :key="item.path"
            class="df-drawer__item"
            :class="{ 'is-active': isActive(item.path) }"
            @click="goPath(item.path)"
          >
            <van-icon :name="item.icon" size="18" />
            <span>{{ item.label }}</span>
          </a>
        </template>
      </nav>

      <!-- 底部：主题切换 + 切换客户端 + 退出 -->
      <div class="df-drawer__foot">
        <button class="df-drawer__action" :title="`切换到${nextThemeLabel}模式`" @click="onToggleTheme">
          <SvgIcon :name="themeIcon" :size="18" />
        </button>
        <button class="df-drawer__action" title="切换到用户端" @click="switchToClient">
          <van-icon name="exchange" size="18" />
        </button>
        <button class="df-drawer__action" title="退出登录" @click="onLogout">
          <van-icon name="revoke" size="18" />
        </button>
        <span class="ver">© 点废成金 · 接单编辑部</span>
      </div>
    </aside>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

// 抽屉内的用户身份条 + 底部操作按钮（topbar / drawer / scrim 由全局样式提供）
.df-drawer {
  display: flex;
  flex-direction: column;
}

.df-drawer__user {
  display: flex;
  align-items: center;
  gap: 11px;
  margin: 6px 12px 8px;
  padding: 11px 12px;
  border-radius: 11px;
  background: var(--df-drawer-hover);
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover { background: var(--df-drawer-active); }
}

.df-drawer__avatar {
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

.df-drawer__user-info {
  flex: 1;
  min-width: 0;
}

.df-drawer__user-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--df-drawer-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.df-drawer__user-sub {
  font-size: 11px;
  color: var(--df-drawer-text-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}

.df-drawer__nav {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 12px;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb {
    background: var(--df-drawer-text-mute);
    border-radius: 6px;
  }
}

.df-drawer__action {
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

  &:hover { background: var(--df-drawer-hover); }
}
</style>
