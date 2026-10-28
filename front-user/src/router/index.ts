// ============================================================
// 用户端路由（D2）：路由懒加载 + 登录守卫
// meta.tab   —— 底部 Tab 页（App.vue 据此显示 van-tabbar）
// meta.auth  —— 需要登录（未登录跳 login 并带 redirect）
// meta.title —— 页面标题
// ============================================================
import { createRouter, createWebHistory } from 'vue-router'
import { hasToken, isAccessTokenExpired, tokenStore } from '@/api/token'
import { refreshAccessToken } from '@/api/auth'

// ============================================================
// 移动端路由
// ============================================================
const mobileRoutes = [
  {
    path: '/mobile/home',
    name: 'mobile-home',
    component: () => import('@/views/mobile/home.vue'),
    meta: { tab: true, auth: true, title: '首页' },
  },
  {
    path: '/mobile/orders',
    name: 'mobile-orders',
    component: () => import('@/views/mobile/orders/index.vue'),
    meta: { tab: true, auth: true, title: '我的订单' },
  },
  {
    path: '/mobile/mall',
    name: 'mobile-mall',
    component: () => import('@/views/mobile/mall.vue'),
    meta: { tab: true, title: '积分市场' },
  },
  // LoopFill B2：我的兑换单
  {
    path: '/mobile/mall/exchange',
    name: 'MallExchange',
    component: () => import('@/views/mobile/mall/exchange.vue'),
    meta: { auth: true, title: '我的兑换单' },
  },
  // 积分市场合并升级：我的徽章（虚拟收藏品墙）
  {
    path: '/mobile/mall/badges',
    name: 'mobile-mall-badges',
    component: () => import('@/views/mobile/mall/badges.vue'),
    meta: { auth: true, title: '我的徽章' },
  },
  {
    path: '/mobile/mine',
    name: 'mobile-mine',
    component: () => import('@/views/mobile/mine.vue'),
    meta: { tab: true, title: '我的' },
  },
  {
    path: '/mobile/login',
    name: 'mobile-login',
    component: () => import('@/views/mobile/login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/mobile/recycle/book',
    name: 'mobile-recycle-book',
    component: () => import('@/views/mobile/recycle/book.vue'),
    meta: { auth: true, title: '可回收预约' },
  },
  {
    path: '/mobile/kitchen/drop',
    name: 'mobile-kitchen-drop',
    component: () => import('@/views/mobile/kitchen/drop.vue'),
    meta: { auth: true, title: '垃圾投放' },
  },
  {
    path: '/mobile/repair/book',
    name: 'mobile-repair-book',
    component: () => import('@/views/mobile/repair/book.vue'),
    meta: { auth: true, title: '维修预约' },
  },
  {
    path: '/mobile/orders/:id',
    name: 'mobile-order-detail',
    component: () => import('@/views/mobile/orders/detail.vue'),
    meta: { auth: true, title: '订单详情' },
  },
  {
    path: '/mobile/carbon/bill',
    name: 'mobile-carbon-bill',
    component: () => import('@/views/mobile/carbon/bill.vue'),
    meta: { auth: true, title: '低碳账单' },
  },
  {
    path: '/mobile/prices',
    name: 'mobile-prices',
    component: () => import('@/views/mobile/prices.vue'),
    meta: { title: '实时报价' },
  },
  {
    path: '/mobile/science',
    name: 'mobile-science',
    component: () => import('@/views/mobile/science/index.vue'),
    meta: { title: '科普专栏' },
  },
  // V42：科普专栏 UGC（详情/发布/我的文章）
  {
    path: '/mobile/science/article/:id',
    name: 'mobile-science-article',
    component: () => import('@/views/mobile/science/article/[id].vue'),
    meta: { title: '文章详情' },
  },
  {
    path: '/mobile/science/create',
    name: 'mobile-science-create',
    component: () => import('@/views/mobile/science/create.vue'),
    meta: { auth: true, title: '发布文章' },
  },
  {
    path: '/mobile/science/my',
    name: 'mobile-science-my',
    component: () => import('@/views/mobile/science/my.vue'),
    meta: { auth: true, title: '我的文章' },
  },
  {
    path: '/mobile/science/knowledge',
    name: 'mobile-science-knowledge',
    component: () => import('@/views/mobile/science.vue'),
    meta: { title: '分类知识速查' },
  },
  {
    path: '/mobile/ar-scan',
    name: 'mobile-ar-scan',
    component: () => import('@/views/mobile/ai/ar-scan.vue'),
    meta: { title: 'AR 分类指导' },
  },
  {
    path: '/mobile/voice-steward',
    name: 'mobile-voice-steward',
    component: () => import('@/views/mobile/ai/voice-steward.vue'),
    meta: { auth: true, title: '语音碳管家' },
  },
  // GapFill B1：用户中心三页
  {
    path: '/mobile/user/realname',
    name: 'mobile-user-realname',
    component: () => import('@/views/mobile/user/realname.vue'),
    meta: { auth: true, title: '实名认证' },
  },
  {
    path: '/mobile/user/message',
    name: 'mobile-user-message',
    component: () => import('@/views/mobile/user/message.vue'),
    meta: { auth: true, title: '消息中心' },
  },
  {
    path: '/mobile/user/complaint',
    name: 'mobile-user-complaint',
    component: () => import('@/views/mobile/user/complaint.vue'),
    meta: { auth: true, title: '反馈投诉' },
  },
  // GapFill B6：设置密码
  {
    path: '/mobile/user/password',
    name: 'mobile-user-password',
    component: () => import('@/views/mobile/user/password.vue'),
    meta: { auth: true, title: '设置密码' },
  },
  // address-book：地址簿（省市区 + 定位 + 片区，预约/收货统一取用）
  {
    path: '/mobile/address-book',
    name: 'mobile-address-book',
    component: () => import('@/views/mobile/address-book.vue'),
    meta: { auth: true, title: '地址簿' },
  },
  // onsite01：工作人员申请（G1，C-O1；/mobile/im 与 /pc/im 由 onsite03 注册）
  {
    path: '/mobile/staff/apply',
    name: 'mobile-staff-apply',
    component: () => import('@/views/mobile/staff/apply.vue'),
    meta: { auth: true, title: '工作人员申请' },
  },
  // onsite04：地图（G4，C-O4）——订单实时追踪 / 回收站点
  {
    path: '/mobile/map/track/:bizType/:orderId',
    name: 'mobile-map-track',
    component: () => import('@/views/mobile/map/track.vue'),
    meta: { auth: true, title: '订单实时追踪' },
  },
  {
    path: '/mobile/map/stations',
    name: 'mobile-map-stations',
    component: () => import('@/views/mobile/map/stations.vue'),
    meta: { auth: true, title: '回收站点' },
  },
  // onsite03：在线聊天（IM，C-O3；挂载点由 mine.vue/orders.detail 消费）
  {
    path: '/mobile/im',
    name: 'mobile-im',
    component: () => import('@/views/mobile/im/list.vue'),
    meta: { auth: true, title: '在线聊天' },
  },
  {
    path: '/mobile/im/:convId',
    name: 'mobile-im-chat',
    component: () => import('@/views/mobile/im/chat.vue'),
    meta: { auth: true, title: '聊天' },
  },
  // LoopFill B5 创意 C1：环保树 + 签到成就
  {
    path: '/mobile/eco/tree',
    name: 'mobile-eco-tree',
    component: () => import('@/views/mobile/eco/tree.vue'),
    meta: { auth: true, title: '环保树' },
  },
  {
    path: '/mobile/eco/sign',
    name: 'mobile-eco-sign',
    component: () => import('@/views/mobile/eco/sign.vue'),
    meta: { auth: true, title: '签到成就' },
  },
  // LoopFill B4：垃圾投放记录 + 碳减排可信证书
  {
    path: '/mobile/kitchen/records',
    name: 'mobile-kitchen-records',
    component: () => import('@/views/mobile/kitchen/records.vue'),
    meta: { auth: true, title: '垃圾投放记录' },
  },
  {
    path: '/mobile/carbon/certificate',
    name: 'mobile-carbon-certificate',
    component: () => import('@/views/mobile/carbon/certificate.vue'),
    meta: { auth: true, title: '碳减排可信证书' },
  },
  // LoopFill B6 创意 C2：变废为宝社区 + 片区环保排行榜
  {
    path: '/mobile/community',
    name: 'mobile-community',
    component: () => import('@/views/mobile/community/index.vue'),
    meta: { auth: true, title: '变废为宝社区' },
  },
  {
    path: '/mobile/community/rank',
    name: 'mobile-community-rank',
    component: () => import('@/views/mobile/community/rank.vue'),
    meta: { auth: true, title: '片区环保排行榜' },
  },
  // Upgrade U4：闲置好物（原跳蚤市场）—— 入口并入 /mobile/mall 闲置好物 Tab，保留发布/详情/我的/订单子页面
  {
    path: '/mobile/flea/publish',
    name: 'mobile-flea-publish',
    component: () => import('@/views/mobile/flea/publish.vue'),
    meta: { auth: true, title: '发布闲置' },
  },
  {
    path: '/mobile/flea/detail/:id',
    name: 'mobile-flea-detail',
    component: () => import('@/views/mobile/flea/detail.vue'),
    meta: { auth: true, title: '商品详情' },
  },
  {
    path: '/mobile/flea/mine',
    name: 'mobile-flea-mine',
    component: () => import('@/views/mobile/flea/mine.vue'),
    meta: { auth: true, title: '跳蚤市场 · 我的' },
  },
  {
    path: '/mobile/flea/orders',
    name: 'mobile-flea-orders',
    component: () => import('@/views/mobile/flea/orders.vue'),
    meta: { auth: true, title: '我卖出的' },
  },
]

// ============================================================
// PC端路由
// ============================================================
const pcRoutes = [
  {
    path: '/pc/login',
    name: 'pc-login',
    component: () => import('@/views/pc/login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/pc',
    component: () => import('@/views/pc/layout.vue'),
    children: [
      {
        path: 'home',
        name: 'pc-home',
        component: () => import('@/views/pc/home.vue'),
        meta: { auth: true, title: '首页' },
      },
      {
        path: 'recycle/book',
        name: 'pc-recycle-book',
        component: () => import('@/views/pc/recycle/book.vue'),
        meta: { auth: true, title: '可回收预约' },
      },
      {
        path: 'kitchen/drop',
        name: 'pc-kitchen-drop',
        component: () => import('@/views/pc/kitchen/drop.vue'),
        meta: { auth: true, title: '垃圾投放' },
      },
      {
        path: 'repair/book',
        name: 'pc-repair-book',
        component: () => import('@/views/pc/repair/book.vue'),
        meta: { auth: true, title: '维修预约' },
      },
      {
        path: 'prices',
        name: 'pc-prices',
        component: () => import('@/views/pc/prices.vue'),
        meta: { title: '实时报价' },
      },
      {
        path: 'mall',
        name: 'pc-mall',
        component: () => import('@/views/pc/mall.vue'),
        meta: { title: '积分市场' },
      },
      // LoopFill B2：我的兑换单
      {
        path: 'mall/exchange',
        name: 'pc-mall-exchange',
        component: () => import('@/views/pc/mall/exchange.vue'),
        meta: { auth: true, title: '我的兑换单' },
      },
      // 积分市场合并升级：我的徽章（虚拟收藏品墙）
      {
        path: 'mall/badges',
        name: 'pc-mall-badges',
        component: () => import('@/views/pc/mall/badges.vue'),
        meta: { auth: true, title: '我的徽章' },
      },
      {
        path: 'science',
        name: 'pc-science',
        component: () => import('@/views/pc/science.vue'),
        meta: { title: '科普专栏' },
      },
      // V42：科普专栏 UGC（PC 详情/发布）
      {
        path: 'science/article/:id',
        name: 'pc-science-article',
        component: () => import('@/views/pc/science/article/[id].vue'),
        meta: { title: '文章详情' },
      },
      {
        path: 'science/create',
        name: 'pc-science-create',
        component: () => import('@/views/pc/science/create.vue'),
        meta: { auth: true, title: '发布文章' },
      },
      // G5 新增页面
      {
        path: 'orders',
        name: 'pc-orders',
        component: () => import('@/views/pc/orders/index.vue'),
        meta: { auth: true, title: '我的订单' },
      },
      {
        path: 'orders/:id',
        name: 'pc-order-detail',
        component: () => import('@/views/pc/orders/detail.vue'),
        meta: { auth: true, title: '订单详情' },
      },
      {
        path: 'mine',
        name: 'pc-mine',
        component: () => import('@/views/pc/mine.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: 'carbon/bill',
        name: 'pc-carbon-bill',
        component: () => import('@/views/pc/carbon/bill.vue'),
        meta: { auth: true, title: '低碳账单' },
      },
      {
        path: 'ai/ar-scan',
        name: 'pc-ar-scan',
        component: () => import('@/views/pc/ai/ar-scan.vue'),
        meta: { title: 'AR 分类指导' },
      },
      {
        path: 'ai/voice-steward',
        name: 'pc-voice-steward',
        component: () => import('@/views/pc/ai/voice-steward.vue'),
        meta: { auth: true, title: '语音碳管家' },
      },
      // GapFill B1：用户中心三页
      {
        path: 'user/realname',
        name: 'pc-user-realname',
        component: () => import('@/views/pc/user/realname.vue'),
        meta: { auth: true, title: '实名认证' },
      },
      {
        path: 'user/message',
        name: 'pc-user-message',
        component: () => import('@/views/pc/user/message.vue'),
        meta: { auth: true, title: '消息中心' },
      },
      {
        path: 'user/complaint',
        name: 'pc-user-complaint',
        component: () => import('@/views/pc/user/complaint.vue'),
        meta: { auth: true, title: '反馈投诉' },
      },
      // GapFill B6：设置密码
      {
        path: 'user/password',
        name: 'pc-user-password',
        component: () => import('@/views/pc/user/password.vue'),
        meta: { auth: true, title: '设置密码' },
      },
      // address-book：地址簿（省市区 + 定位 + 片区，预约/收货统一取用）
      {
        path: 'address-book',
        name: 'pc-address-book',
        component: () => import('@/views/pc/address-book.vue'),
        meta: { auth: true, title: '地址簿' },
      },
      // onsite01：工作人员申请（G1，C-O1；/pc/im 由 onsite03 注册）
      {
        path: 'staff/apply',
        name: 'pc-staff-apply',
        component: () => import('@/views/pc/staff/apply.vue'),
        meta: { auth: true, title: '工作人员申请' },
      },
      // onsite04：地图（G4，C-O4）——订单实时追踪 / 回收站点
      {
        path: 'map/track/:bizType/:orderId',
        name: 'pc-map-track',
        component: () => import('@/views/pc/map/track.vue'),
        meta: { auth: true, title: '订单实时追踪' },
      },
      {
        path: 'map/stations',
        name: 'pc-map-stations',
        component: () => import('@/views/pc/map/stations.vue'),
        meta: { auth: true, title: '回收站点' },
      },
      // onsite03：在线聊天（IM，C-O3；挂载点由 mine.vue/orders.detail 消费）
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
      // LoopFill B5 创意 C1：环保树 + 签到成就
      {
        path: 'eco/tree',
        name: 'pc-eco-tree',
        component: () => import('@/views/pc/eco/tree.vue'),
        meta: { auth: true, title: '环保树' },
      },
      {
        path: 'eco/sign',
        name: 'pc-eco-sign',
        component: () => import('@/views/pc/eco/sign.vue'),
        meta: { auth: true, title: '签到成就' },
      },
      // LoopFill B4：垃圾投放记录 + 碳减排可信证书
      {
        path: 'kitchen/records',
        name: 'pc-kitchen-records',
        component: () => import('@/views/pc/kitchen/records.vue'),
        meta: { auth: true, title: '垃圾投放记录' },
      },
      {
        path: 'carbon/certificate',
        name: 'pc-carbon-certificate',
        component: () => import('@/views/pc/carbon/certificate.vue'),
        meta: { auth: true, title: '碳减排可信证书' },
      },
      // LoopFill B6 创意 C2：变废为宝社区 + 片区环保排行榜
      {
        path: 'community',
        name: 'pc-community',
        component: () => import('@/views/pc/community/index.vue'),
        meta: { auth: true, title: '变废为宝社区' },
      },
      {
        path: 'community/rank',
        name: 'pc-community-rank',
        component: () => import('@/views/pc/community/rank.vue'),
        meta: { auth: true, title: '片区环保排行榜' },
      },
      // Upgrade U4：闲置好物（原跳蚤市场）—— 入口并入 /pc/mall 闲置好物 Tab，保留发布/详情/我的/订单子页面
      {
        path: 'flea/publish',
        name: 'pc-flea-publish',
        component: () => import('@/views/pc/flea/publish.vue'),
        meta: { auth: true, title: '发布闲置' },
      },
      {
        path: 'flea/detail/:id',
        name: 'pc-flea-detail',
        component: () => import('@/views/pc/flea/detail.vue'),
        meta: { auth: true, title: '商品详情' },
      },
      {
        path: 'flea/mine',
        name: 'pc-flea-mine',
        component: () => import('@/views/pc/flea/mine.vue'),
        meta: { auth: true, title: '跳蚤市场 · 我的' },
      },
      {
        path: 'flea/orders',
        name: 'pc-flea-orders',
        component: () => import('@/views/pc/flea/orders.vue'),
        meta: { auth: true, title: '我卖出的' },
      },
    ]
  }
]

/** 设备判定：PC 视口且非移动 UA → 桌面端（与登录守卫共用，catch-all 重定向也需要） */
function isDesktopViewport(): boolean {
  return !navigator.userAgent.match(
    /(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i
  ) && window.innerWidth >= 1024
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/mobile/home' },
    ...mobileRoutes,
    ...pcRoutes,
    // catch-all：必须在这里补设备前缀，否则简写路径（/recycle/book、/prices 等）
    // 会在守卫执行前被 redirect '/' 吞掉，导致首页快捷索引 / 「全部」点击无反应
    {
      path: '/:pathMatch(.*)*',
      redirect: (to) => {
        const path = to.path
        if (path.startsWith('/mobile') || path.startsWith('/pc')) {
          return isDesktopViewport() ? '/pc/home' : '/mobile/home'
        }
        return { path: (isDesktopViewport() ? '/pc' : '/mobile') + path }
      },
    },
  ],
})

// 登录与设备守卫
router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title} · 点废成金` : '点废成金'

  const isPC = isDesktopViewport()

  const path = to.path

  // 根路径 → 设备首页
  if (path === '/') {
    return isPC ? '/pc/home' : '/mobile/home'
  }

  // 设备分流：跨端访问自动跳转（PC 访问 /mobile/xxx → /pc/xxx，反之亦然）
  if (isPC && path.startsWith('/mobile')) {
    return { path: path.replace('/mobile', '/pc') }
  }
  if (!isPC && path.startsWith('/pc')) {
    return { path: path.replace('/pc', '/mobile') }
  }

  // 无设备前缀的路径：自动补前缀
  // 兼容业务代码里的简写路径（/recycle/book、/login、/home、/orders/123 等），
  // 避免被 catch-all 路由吞掉跳回首页
  if (!path.startsWith('/mobile') && !path.startsWith('/pc')) {
    const prefix = isPC ? '/pc' : '/mobile'
    return { path: prefix + path }
  }

  // auth check：先查令牌是否存在，再查 access 是否过期
  // 过期时主动续期，避免页面加载后 API 401 触发硬跳转导致背景一闪而过
  if (to.meta.auth) {
    if (!hasToken() && !(await refreshAccessToken())) {
      // 无令牌且续期失败：清理令牌后替换为登录页。用 replace 而非 push 避免 history 堆积，
      // 否则用户点“返回”回到受保护页时又被踢回登录页，反复循环造成“一直登录失效”的错觉
      tokenStore.clear()
      const loginName = isPC ? 'pc-login' : 'mobile-login'
      return { name: loginName, query: { redirect: to.fullPath }, replace: true }
    }
    if (isAccessTokenExpired()) {
      const pair = await refreshAccessToken()
      if (!pair) {
        // refresh token 也失效：清令牌跳登录（软导航，不加载目标页）
        tokenStore.clear()
        const loginName = isPC ? 'pc-login' : 'mobile-login'
        return { name: loginName, query: { redirect: to.fullPath }, replace: true }
      }
    }
  }

  if (to.name?.toString().includes('login') && hasToken() && !isAccessTokenExpired()) {
    return isPC ? '/pc/home' : '/mobile/home'
  }

  return true
})

// PC 路由切换后重置 Lenis 滚动位置（修复 keep-alive 滚动错乱）
router.afterEach((to) => {
  if (to.path.startsWith('/pc')) {
    import('@/utils/lenis').then(({ scrollToTop }) => scrollToTop())
  }
})

export default router
