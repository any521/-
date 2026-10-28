// ============================================================
// 员工端请求实例（复用 D0 共享 createRequest）
// 全量对接真实后端（经 vite proxy 透传 8080）
// ============================================================
import { dfToast } from '@shared/feedback'
import { createRequest } from '@shared/http/createRequest'
import { registerSharedRequest } from '@shared/http/sharedHttp'
import { isPC } from '@/utils/device'
import { tokenStore } from './token'

export const request = createRequest({
  baseURL: import.meta.env.VITE_API_BASE,
  clientType: 'STAFF',
  tokenStore,
  onUnauthorized() {
    // 避免与 router 循环依赖，直接整页跳登录（PC/移动双端分流）
    window.location.href = `${import.meta.env.BASE_URL}${isPC() ? 'pc' : 'mobile'}/login`
  },
  // 错误提示统一走自定义 DfToast（美观 + 文字色随主题），不再用 Vant 白字 toast
  toast: (msg) => dfToast(msg, 'error'),
})

// 注册到共享包：共享组件（AvatarUploader 等）复用同一 Token 注入 / 401 续期链路
registerSharedRequest(request)
