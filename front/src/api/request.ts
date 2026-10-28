// ============================================================
// admin 请求实例（复用 D0 共享 createRequest）
// 全量对接真实后端（经 vite proxy 透传 8080）
// ============================================================
import { ElMessage } from 'element-plus'
import { createRequest } from '@shared/http/createRequest'
import { registerSharedRequest } from '@shared/http/sharedHttp'
import { tokenStore } from './token'

export const request = createRequest({
  baseURL: import.meta.env.VITE_API_BASE,
  clientType: 'ADMIN',
  tokenStore,
  onUnauthorized() {
    // 避免与 router 循环依赖，直接整页跳登录
    window.location.href = `${import.meta.env.BASE_URL}login`
  },
  toast: (msg) => ElMessage.error(msg),
})

// 注册到共享包：共享组件（AvatarUploader 等）复用同一 Token 注入 / 401 续期链路
registerSharedRequest(request)
