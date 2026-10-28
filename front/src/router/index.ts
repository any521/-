// ============================================================
// 路由（09_frontend §9.3）：静态路由 + 登录后动态 addRoute
// 守卫：白名单 / Token 校验（含过期检测）/ 动态路由注册 / 标题
// ============================================================
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { hasToken, isAccessTokenExpired, tokenStore } from '@/api/token'
import { refreshAccessToken } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'

const Layout = () => import('@/layout/index.vue')

/** 静态路由：登录页 + 布局（工作台） + 兜底 */
const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    name: 'layout',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard.vue'),
        meta: { title: '工作台', icon: 'HomeFilled' },
      },
    ],
  },
  // 动态路由 addRoute 后仍生效（vue-router 按匹配分值而非注册顺序）
  { path: '/:pathMatch(.*)*', name: 'notFound', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: staticRoutes,
})

const WHITE_LIST = ['/login']

// 应用加载时先恢复 session-access-token（防止刷新后守卫误判）
if (!tokenStore.getAccessToken()) {
  const t = sessionStorage.getItem('df_admin_at')
  if (t && !isAccessTokenExpired(t)) {
    tokenStore.setTokens({ accessToken: t, refreshToken: '', expiresIn: 0 })
  }
}

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  
  // 1. 无 token 或 token 过期 → 强制跳登录页
  if (!hasToken() || isAccessTokenExpired()) {
    if (WHITE_LIST.includes(to.path)) return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  
  // 2. 已在登录页但 token 有效 → 重定向到工作台（避免重复登录）
  if (to.path === '/login') return { path: '/' }
  
  const permissionStore = usePermissionStore()
  if (!permissionStore.loaded) {
    try {
      // 拉取用户信息 + 菜单，动态注册业务路由
      await useUserStore().fetchInfo()
      const dynamicRoutes = await permissionStore.generateRoutes()
      dynamicRoutes.forEach((r) => router.addRoute('layout', r))
      // 重新进入目标路由（此时动态路由已就绪）
      return { ...to, replace: true }
    } catch {
      // 拉取失败（如 Token 失效）回登录页
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · ${import.meta.env.VITE_APP_TITLE}` : import.meta.env.VITE_APP_TITLE
})

export default router
