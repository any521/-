<script setup lang="ts">
// 根组件：移动端 topbar（菜单+标题+date-chip+主题切换）+ 4 菜单底栏 + 模板抽屉 + 路由出口
// 4 菜单：首页（直达）/ 主要页面（抽屉）/ 特色页面（抽屉）/ 消息（直达）
// PC 端由 /pc/layout 侧栏接管，不渲染 topbar / nav / drawer
import { computed, ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useUnreadStore } from '@/store/unread'
import { applyThemeWithReveal, getNextTheme, themeState, THEME_LABELS } from '@shared/theme'
import { HOME_ITEM, MESSAGE_ITEM, mainGroups, featureGroups } from '@/nav.config'
import Build3dToast from '@/components/Build3dToast.vue'
import GlobalNotice from '@/components/GlobalNotice.vue'
import SvgIcon from '@/components/SvgIcon.vue'
import HeroMascot from '@/components/HeroMascot.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const unreadStore = useUnreadStore()

const isMobile = computed(() => route.path.startsWith('/mobile'))
const isLoginPage = computed(() => Boolean(route.name?.toString().includes('login')))
/** topbar / 底栏：移动端非登录页常驻（模板固定壳层，消息菜单任意页可达） */
const showShell = computed(() => isMobile.value && !isLoginPage.value)

/** 非首页内页注入统一紧凑 hero（首页 mobile/home 自带完整 hero），实现“每页都显示 hero” */
const showInnerHero = computed(() => showShell.value && route.path !== HOME_ITEM.mobile)
const innerHeroTitle = computed(() => (route.meta.title as string) || '点废成金')

// 主题外观（亮色/深色，与 PC 端 PcNav 同步）
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

/** 4 菜单 active 状态：仅菜单1 首页 / 菜单4 消息 为直达页有高亮；菜单2/3 为抽屉入口 */
const tabActive = computed<'home' | 'message' | ''>(() => {
  const p = route.path
  if (p === HOME_ITEM.mobile) return 'home'
  if (p === MESSAGE_ITEM.mobile) return 'message'
  return ''
})

function goTab(key: 'home' | 'message') {
  router.push(key === 'home' ? HOME_ITEM.mobile : MESSAGE_ITEM.mobile)
}

// ---- 抽屉（模板结构：dh / dsep / dgroup / ditem / dfoot）----
const drawerOpen = ref(false)
const drawerRef = ref<HTMLElement | null>(null)
/** 打开抽屉并滚动定位到「主要页面」或「特色页面」区块 */
function openDrawer(section: 'main' | 'feature') {
  drawerOpen.value = true
  nextTick(() => {
    const el = drawerRef.value?.querySelector<HTMLElement>(`[data-section="${section}"]`)
    el?.scrollIntoView({ block: 'start' })
  })
}
function closeDrawer() { drawerOpen.value = false }

function goPath(path: string) {
  drawerOpen.value = false
  router.push(path)
}

/** 抽屉品牌头像：已登录进个人中心，未登录进登录页 */
function goProfile() {
  drawerOpen.value = false
  router.push(userStore.isLogin() ? '/mobile/mine' : '/mobile/login')
}

const isActive = (path: string) => route.path === path

// ---- topbar 日期与标题 ----
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
})
const topbarTitle = computed(() => (route.meta.title as string) || '点废成金')
const topbarSub = computed(() => {
  if (route.meta.tab) return '生活报 · 客户端'
  return '点废成金 · 生活报'
})

// ---- 消息未读（菜单4 角标）：store 统一 WS/轮询/已读同步 ----
const unreadCount = computed(() => unreadStore.count)

onMounted(() => {
  userStore.restoreSession()
  unreadStore.start()
})

onUnmounted(() => {
  // 根组件卸载（整页销毁）才停；HMR/路由切换不会走到这里
  unreadStore.stop()
})

watch(() => route.path, () => {
  unreadStore.scheduleRefresh(0)
  drawerOpen.value = false
})
</script>

<template>
  <div class="app" :class="{ 'is-elder': userStore.elderMode }">
    <!-- 移动端 topbar（模板 .topbar：菜单按钮 + 标题 + date-chip + 主题切换） -->
    <div v-if="showShell" class="topbar">
      <button class="menu-btn" aria-label="打开菜单" @click="openDrawer('main')">
        <SvgIcon name="nav-menu" :size="18" />
      </button>
      <div class="title">
        {{ topbarTitle }}
        <small>{{ topbarSub }}</small>
      </div>
      <span class="date-chip">
        <SvgIcon name="nav-calendar" :size="13" />
        {{ today }}
      </span>
      <button
        class="theme-toggle"
        :title="`切换到${nextThemeLabel}模式`"
        aria-label="切换主题"
        @click="onToggleTheme"
      >
        <SvgIcon name="nav-moon" class="ic-moon" :size="16" />
        <SvgIcon name="nav-sun" class="ic-sun" :size="16" />
      </button>
    </div>

    <!-- 非首页内页统一注入紧凑 hero：放在 router-view 之外，避免 KeepAlive 出现多个子节点 -->
    <div v-if="showInnerHero" class="inner-hero">
      <HeroMascot compact :title="innerHeroTitle" subtitle="点废成金 · 生活报" :height="104" />
    </div>

    <router-view v-slot="{ Component }">
      <!-- 不能用 Transition 包裹：Transition out-in + KeepAlive 在 vue-router5/Vue3.5 下会状态机卡死，
           第二次进入缓存页面时路由出口变空白（实测必现、无报错、仅刷新可恢复）。KeepAlive 单独使用稳定。
           include 匹配组件 defineOptions name：home/user-message 直达缓存；orders/mall/mine 抽屉返回保留状态；
           *-book 预约页：上传照片后进地址簿再返回，必须保留本地照片/表单草稿 -->
      <keep-alive
        :include="[
          'home',
          'orders',
          'mall',
          'mine',
          'user-message',
          'recycle-book',
          'repair-book',
          'pc-recycle-book',
          'pc-repair-book',
        ]"
      >
        <component :is="Component" />
      </keep-alive>
    </router-view>

    <!-- 4 菜单底栏（模板 .nav：首页 / 主要页面 / 特色页面 / 消息） -->
    <nav v-if="showShell" class="nav">
      <button class="n" :class="{ active: tabActive === 'home' }" @click="goTab('home')">
        <span class="n-ic">
          <SvgIcon :name="HOME_ITEM.icon" :size="20" />
        </span>
        <span>{{ HOME_ITEM.label }}</span>
      </button>
      <button class="n" @click="openDrawer('main')">
        <span class="n-ic">
          <SvgIcon name="nav-star" :size="20" />
        </span>
        <span>主要页面</span>
      </button>
      <button class="n" @click="openDrawer('feature')">
        <span class="n-ic">
          <SvgIcon name="nav-books" :size="20" />
        </span>
        <span>特色页面</span>
      </button>
      <button class="n" :class="{ active: tabActive === 'message' }" @click="goTab('message')">
        <span class="n-ic">
          <SvgIcon :name="MESSAGE_ITEM.icon" :size="20" />
          <span v-if="unreadCount > 0" class="n-badge">
            {{ unreadCount > 99 ? '99+' : unreadCount }}
          </span>
        </span>
        <span>{{ MESSAGE_ITEM.label }}</span>
      </button>
    </nav>

    <!-- 抽屉（模板 .drawer：dh 品牌 + 主要/特色分组 + dfoot 吉祥物）
         必须与 topbar/nav 一致挂 showShell：否则 PC 端（showShell=false）仍会渲染，
         且样式仅在 @media(max-width:768px) 内定义，视口 >768px 时退化为文档流平铺在页面底部 -->
    <div v-if="showShell" class="scrim" :class="{ show: drawerOpen }" @click="closeDrawer" />
    <aside v-if="showShell" ref="drawerRef" class="drawer" :class="{ show: drawerOpen }">
      <div class="dh">
        <button class="dclose" aria-label="关闭菜单" @click="closeDrawer">
          <SvgIcon name="common-close" :size="16" />
        </button>
        <div class="brand" :title="userStore.isLogin() ? '个人中心' : '立即登录'" @click="goProfile">
          <img
            v-if="userStore.isLogin() && userStore.profile?.avatar"
            :src="userStore.profile.avatar"
            alt="头像"
          />
          <span v-else><SvgIcon name="nav-user" :size="18" /></span>
          <span class="brand-cam"><SvgIcon name="ai-camera" :size="14" /></span>
        </div>
        <div>
          <h2>点废成金</h2>
          <p>{{ userStore.isLogin() ? `你好，${userStore.profile?.nickname || '环保达人'}` : '生活报 · 客户端' }}</p>
        </div>
      </div>
      <div class="dsep" />

      <!-- 菜单2：主要页面（4 分组） -->
      <div data-section="main">
        <div class="dgroup">主要页面</div>
        <template v-for="grp in mainGroups" :key="'m-' + grp.title">
          <div class="dgroup dgroup--sub">{{ grp.title }}</div>
          <a
            v-for="item in grp.items"
            :key="item.mobile"
            class="ditem"
            :class="{ active: isActive(item.mobile) }"
            @click="goPath(item.mobile)"
          >
            <SvgIcon :name="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </a>
        </template>
      </div>

      <div class="dsep" />

      <!-- 菜单3：特色页面（3 分组） -->
      <div data-section="feature">
        <div class="dgroup">特色页面</div>
        <template v-for="grp in featureGroups" :key="'f-' + grp.title">
          <div class="dgroup dgroup--sub">{{ grp.title }}</div>
          <a
            v-for="item in grp.items"
            :key="item.mobile"
            class="ditem"
            :class="{ active: isActive(item.mobile) }"
            @click="goPath(item.mobile)"
          >
            <SvgIcon :name="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </a>
        </template>
      </div>

      <div class="dsep" />

      <!-- 底部：吉祥物 + 版本 -->
      <div class="dfoot">
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
        <span class="ver">点废成金 · 每日更新</span>
      </div>
    </aside>

    <!-- 全局 3D 构建任务浮窗（右下角，路由切换后仍提示） -->
    <Build3dToast />

    <!-- 全局公告浮层（顶部导航下方悬浮叠加，不占布局空间） -->
    <GlobalNotice />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

// 非首页内页注入的统一 hero：与 .page 容器同宽左右缝（12px），与顶栏隔开
.inner-hero {
  padding: $df-space-md $df-space-md 0;
}

// 底栏图标容器：作为 n-badge（absolute）的定位锚点
.n-ic {
  position: relative;
  display: inline-flex;
}

// 抽屉内子分组标题（较 dgroup 主区块更弱化）
.dgroup--sub {
  font-size: 10px;
  opacity: 0.3;
  padding: 10px 22px 3px;
  letter-spacing: 0.14em;
}
</style>
