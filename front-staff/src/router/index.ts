// ============================================================
// 员工端路由（D3）：PC/移动双端路由 + 懒加载 + 登录守卫
// meta.tab   —— 底部 Tab 页（App.vue 据此显示 van-tabbar，仅移动端）
// meta.auth  —— 需要登录（未登录跳对应端 login 并带 redirect）
// meta.title —— 页面标题
// 双端分流：/mobile/* 移动端（底部 Tab）；/pc/* PC 端（顶部导航布局）
// 视图内简写路径（/tasks、/orders/1 等）由 beforeEach 自动补设备前缀
// ============================================================
import { createRouter, createWebHistory } from 'vue-router'
import { hasToken, tokenStore, isAccessTokenExpired } from '@/api/token'
import { refreshAccessToken } from '@/api/auth'
import { isPC } from '@/utils/device'

// ============================================================
// 移动端路由
// ============================================================
const mobileRoutes = [
  // ---- 底部三 Tab ----
  {
    path: '/mobile/tasks',
    name: 'mobile-tasks',
    component: () => import('@/views/tasks.vue'),
    meta: { tab: true, auth: true, title: '接单台' },
  },
  {
    path: '/mobile/work',
    name: 'mobile-work',
    component: () => import('@/views/work.vue'),
    meta: { tab: true, auth: true, title: '工作台' },
  },
  {
    path: '/mobile/mine',
    name: 'mobile-mine',
    component: () => import('@/views/mine.vue'),
    meta: { tab: true, auth: true, title: '我的' },
  },
  // ---- 登录 ----
  {
    path: '/mobile/login',
    name: 'mobile-login',
    component: () => import('@/views/login.vue'),
    meta: { title: '员工登录' },
  },
  // ---- 订单作业链路 ----
  {
    path: '/mobile/orders/:id',
    name: 'mobile-order-detail',
    component: () => import('@/views/order/detail.vue'),
    meta: { auth: true, title: '订单详情' },
  },
  {
    path: '/mobile/route',
    name: 'mobile-route-nav',
    component: () => import('@/views/order/navigate.vue'),
    meta: { auth: true, title: '智能路线导航' },
  },
  {
    path: '/mobile/orders/:id/register',
    name: 'mobile-order-register',
    component: () => import('@/views/order/register.vue'),
    meta: { auth: true, title: '核验登记' },
  },
  // ---- 维修作业（技术员） ----
  {
    path: '/mobile/repair/:id/report',
    name: 'mobile-repair-report',
    component: () => import('@/views/repair/report.vue'),
    meta: { auth: true, title: '维修报告' },
  },
  {
    path: '/mobile/repair/:id/parts',
    name: 'mobile-repair-parts',
    component: () => import('@/views/repair/parts.vue'),
    meta: { auth: true, title: '配件登记' },
  },
  // ---- 工作台功能 ----
  {
    path: '/mobile/abnormal',
    name: 'mobile-abnormal',
    component: () => import('@/views/abnormal.vue'),
    meta: { auth: true, title: '异常上报' },
  },
  {
    path: '/mobile/kitchen/audit',
    name: 'mobile-kitchen-audit',
    component: () => import('@/views/kitchen/audit.vue'),
    meta: { auth: true, title: '投放审核' },
  },
  {
    path: '/mobile/performance',
    name: 'mobile-performance',
    component: () => import('@/views/performance.vue'),
    meta: { auth: true, title: '我的绩效' },
  },
  // ---- 排班与请假（B3，员工自助） ----
  {
    path: '/mobile/leave',
    name: 'mobile-leave',
    component: () => import('@/views/leave.vue'),
    meta: { auth: true, title: '请假' },
  },
  {
    path: '/mobile/schedule',
    name: 'mobile-schedule',
    component: () => import('@/views/schedule.vue'),
    meta: { auth: true, title: '我的排班' },
  },
  // ---- 在线聊天（onsite03，C-O3；入口在 tasks.vue 消息按钮） ----
  {
    path: '/mobile/im',
    name: 'mobile-im',
    component: () => import('@/views/im/list.vue'),
    meta: { auth: true, title: '在线聊天' },
  },
  {
    path: '/mobile/im/:convId',
    name: 'mobile-im-chat',
    component: () => import('@/views/im/chat.vue'),
    meta: { auth: true, title: '聊天' },
  },
]

// ============================================================
// PC 端路由（统一挂 pc-layout，顶部导航）
// ============================================================
const pcRoutes = [
  {
    path: '/pc/login',
    name: 'pc-login',
    component: () => import('@/views/pc/login.vue'),
    meta: { title: '员工登录' },
  },
  {
    path: '/pc',
    component: () => import('@/views/pc/layout.vue'),
    children: [
      {
        path: 'tasks',
        name: 'pc-tasks',
        component: () => import('@/views/pc/tasks.vue'),
        meta: { auth: true, title: '接单台' },
      },
      {
        path: 'work',
        name: 'pc-work',
        component: () => import('@/views/pc/work.vue'),
        meta: { auth: true, title: '工作台' },
      },
      {
        path: 'mine',
        name: 'pc-mine',
        component: () => import('@/views/pc/mine.vue'),
        meta: { auth: true, title: '我的' },
      },
      // ---- 订单作业链路 ----
      {
        path: 'orders/:id',
        name: 'pc-order-detail',
        component: () => import('@/views/pc/order/detail.vue'),
        meta: { auth: true, title: '订单详情' },
      },
      {
        path: 'route',
        name: 'pc-route-nav',
        component: () => import('@/views/pc/order/navigate.vue'),
        meta: { auth: true, title: '智能路线导航' },
      },
      {
        path: 'orders/:id/register',
        name: 'pc-order-register',
        component: () => import('@/views/pc/order/register.vue'),
        meta: { auth: true, title: '核验登记' },
      },
      // ---- 维修作业（技术员） ----
      {
        path: 'repair/:id/report',
        name: 'pc-repair-report',
        component: () => import('@/views/pc/repair/report.vue'),
        meta: { auth: true, title: '维修报告' },
      },
      {
        path: 'repair/:id/parts',
        name: 'pc-repair-parts',
        component: () => import('@/views/pc/repair/parts.vue'),
        meta: { auth: true, title: '配件登记' },
      },
      // ---- 工作台功能 ----
      {
        path: 'abnormal',
        name: 'pc-abnormal',
        component: () => import('@/views/pc/abnormal.vue'),
        meta: { auth: true, title: '异常上报' },
      },
      {
        path: 'kitchen/audit',
        name: 'pc-kitchen-audit',
        component: () => import('@/views/pc/kitchen/audit.vue'),
        meta: { auth: true, title: '投放审核' },
      },
      {
        path: 'performance',
        name: 'pc-performance',
        component: () => import('@/views/pc/performance.vue'),
        meta: { auth: true, title: '我的绩效' },
      },
      // ---- 排班与请假（B3，员工自助） ----
      {
        path: 'leave',
        name: 'pc-leave',
        component: () => import('@/views/pc/leave.vue'),
        meta: { auth: true, title: '请假' },
      },
      {
        path: 'schedule',
        name: 'pc-schedule',
        component: () => import('@/views/pc/schedule.vue'),
        meta: { auth: true, title: '我的排班' },
      },
      // ---- 在线聊天 ----
      {
        path: 'im',
        name: 'pc-im',
        component: () => import('@/views/pc/im/list.vue'),
        meta: { auth: true, title: '在线聊天' },
      },
      {
        path: 'im/:convId',
        name: 'pc-im-chat',
        component: () => import('@/views/pc/im/chat.vue'),
        meta: { auth: true, title: '聊天' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/mobile/tasks' },
    ...mobileRoutes,
    ...pcRoutes,
    // 兜底路由：不直接 redirect（redirect 在守卫执行前解析，会把 /route、/im 等
    // 简写路径吞掉重定向回首页，导致视图内点击无反应）；由 beforeEach 的
    // not-found 分支统一处理（补设备前缀重试 / 按设备回首页），此组件永不被渲染
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/tasks.vue') },
  ],
})

// 登录与设备守卫
router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title} · 点废成金员工端` : '点废成金员工端'

  const pc = isPC()
  const path = to.path

  // 根路径 / 兜底路由 → 按设备跳对应端首页
  if (path === '/') {
    return pc ? '/pc/tasks' : '/mobile/tasks'
  }

  // 兜底路由命中（未匹配任何具体路由）：
  // - 无设备前缀的简写路径（/route、/im、/orders/1 等）→ 补前缀重试，让具体路由接管
  // - 其余非法路径 → 按设备回对应端首页
  if (to.name === 'not-found') {
    if (!path.startsWith('/mobile') && !path.startsWith('/pc')) {
      return { path: (pc ? '/pc' : '/mobile') + path }
    }
    return pc ? '/pc/tasks' : '/mobile/tasks'
  }

  // 设备分流：跨端访问自动跳转（PC 访问 /mobile/xxx → /pc/xxx，反之亦然）
  if (pc && path.startsWith('/mobile')) {
    return { path: path.replace('/mobile', '/pc') }
  }
  if (!pc && path.startsWith('/pc')) {
    return { path: path.replace('/pc', '/mobile') }
  }

  // 无设备前缀的路径：自动补前缀（兼容视图内简写路径 /tasks、/orders/1 等）
  if (!path.startsWith('/mobile') && !path.startsWith('/pc')) {
    return { path: (pc ? '/pc' : '/mobile') + path }
  }

  // auth check：无令牌或已过期 → 续期，失败则清令牌并替换到登录页（带回跳）
  // 用 replace 而非 push 避免 history 堆积，否则用户点"返回"回受保护页时又被踢回登录页，反复循环
  if (to.meta.auth && (!hasToken() || isAccessTokenExpired())) {
    if (!(await refreshAccessToken())) {
      tokenStore.clear()
      return { name: pc ? 'pc-login' : 'mobile-login', query: { redirect: to.fullPath }, replace: true }
    }
  }

  // 已登录访问登录页 → 回对应端首页
  if (to.name?.toString().includes('login') && hasToken()) {
    return pc ? '/pc/tasks' : '/mobile/tasks'
  }

  return true
})

export default router
