// ============================================================
// 令牌存取（TokenStore 实现，注入共享 createRequest）
// Access Token 仅存当前页面内存；Refresh Token 由后端 HttpOnly Cookie 管理。
// ============================================================
import type { TokenPair } from '@shared/http/types'
import type { TokenStore } from '@shared/http/createRequest'

let accessToken: string | null = null

// 令牌被清空时的监听器（staff store 注册：token 清空 → 同步清 profile）
type ClearListener = () => void
const clearListeners = new Set<ClearListener>()
/** 注册令牌清空回调，返回取消函数 */
export function onTokenCleared(fn: ClearListener): () => void {
  clearListeners.add(fn)
  return () => clearListeners.delete(fn)
}

function notifyCleared() {
  clearListeners.forEach((fn) => fn())
}

export const tokenStore: TokenStore = {
  getAccessToken: () => accessToken,
  getRefreshToken: () => null,
  setTokens(pair: TokenPair) {
    accessToken = pair.accessToken
  },
  clear() {
    accessToken = null
    notifyCleared()
  },
}

export const hasToken = () => !!tokenStore.getAccessToken()

/**
 * 解码 JWT access token 判断是否已过期。
 * - 无令牌 → 过期（需跳登录）
 * - 非 JWT 格式（Mock 令牌等无 exp 声明）→ 未过期，交给后端 401 兜底
 * - 有效 JWT：提前 30s 判过期，避免请求在途中失效
 * 用于路由守卫在页面加载前拦截过期令牌，防止进入页面后才被 401 踢出。
 */
export function isAccessTokenExpired(): boolean {
  const token = tokenStore.getAccessToken()
  if (!token) return true
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return false
    // base64url → base64，兼容 JWT 的 URL 安全编码
    const b64 = parts[1]!.replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(b64))
    if (typeof payload.exp !== 'number') return false
    return payload.exp * 1000 <= Date.now() + 30_000
  } catch {
    return false
  }
}

/**
 * 免登录切换（C-O1）：从用户端 sessionStorage 中转读取 access token 恢复会话，用后即删。
 * 仅恢复内存令牌，不落持久化；无中转令牌时静默跳过（走正常登录/Refresh Cookie 链路）。
 */
export function restoreFromHandoff(): boolean {
  try {
    const token = sessionStorage.getItem('df_staff_access')
    if (!token) return false
    sessionStorage.removeItem('df_staff_access')
    tokenStore.setTokens({ accessToken: token, refreshToken: null, expiresIn: 0 })
    return true
  } catch {
    return false
  }
}
