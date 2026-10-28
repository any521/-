// ============================================================
// 用户端请求实例（复用 D0 共享 createRequest）
// 全量对接真实后端（经 vite proxy 透传 8080）
// ============================================================
import { dfToast } from '@shared/feedback'
import { createRequest } from '@shared/http/createRequest'
import { registerSharedRequest } from '@shared/http/sharedHttp'
import { tokenStore } from './token'

// 防止并发 401 触发多次跳转（Promise.all 场景下多个请求同时失败）
let isRedirectingToLogin = false

export const request = createRequest({
  baseURL: import.meta.env.VITE_API_BASE,
  clientType: 'USER',
  tokenStore,
  onUnauthorized() {
    // 先清除过期令牌，防止 router 守卫检测到 hasToken()=true 又跳回来
    tokenStore.clear()
    // 软导航跳登录：用 router.replace 避免整页硬刷新
    // （硬刷新会销毁 Vue 应用，导致 WebGL 流体背景一闪而过）
    // 动态导入 router 避免循环依赖（router → auth → request）
    if (isRedirectingToLogin) return
    isRedirectingToLogin = true
    const isPC =
      !navigator.userAgent.match(
        /(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i
      ) && window.innerWidth >= 1024
    const loginName = isPC ? 'pc-login' : 'mobile-login'
    import('@/router').then(({ default: router }) => {
      router
        .replace({ name: loginName, query: { redirect: window.location.pathname } })
        .finally(() => {
          isRedirectingToLogin = false
        })
    })
  },
  // 错误提示统一走自定义 DfToast（美观 + 文字色随主题），不再用 Vant 白字 toast
  toast: (msg) => dfToast(msg, 'error'),
})

// 注册到共享包：共享组件（AvatarUploader 等）复用同一 Token 注入 / 401 续期链路
registerSharedRequest(request)
