// ============================================================
// 共享组件请求注册点：各端创建自己的 DfRequest 实例后调用
// registerSharedRequest 注册，供共享组件（AvatarUploader 等）
// 走统一请求层（Token 注入 / 401 自动续期 / 错误码提示）
// ============================================================
import type { DfRequest } from './createRequest'

let sharedRequest: DfRequest | null = null

export function registerSharedRequest(req: DfRequest) {
  sharedRequest = req
}

export function getSharedRequest(): DfRequest | null {
  return sharedRequest
}
