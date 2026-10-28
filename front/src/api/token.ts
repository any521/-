// ============================================================
// 令牌存取（TokenStore 实现，注入共享 createRequest）
// Access Token 仅存当前页面内存；Refresh Token 由后端 HttpOnly Cookie 管理。
// ============================================================
import type { TokenPair } from '@shared/http/types'
import type { TokenStore } from '@shared/http/createRequest'

let accessToken: string | null = null

export const tokenStore: TokenStore = {
  getAccessToken: () => accessToken,
  getRefreshToken: () => null,
  setTokens(pair: TokenPair) {
    accessToken = pair.accessToken
  },
  clear() {
    accessToken = null
  },
}

export const hasToken = () => !!tokenStore.getAccessToken()

/**
 * 解码 JWT access token 判断是否已过期。
 * - 无令牌 → 过期（需跳登录）
 * - 非 JWT 格式（Mock 令牌等无 exp 声明）→ 未过期，交给后端 401 兜底
 * - 有效 JWT：提前 30s 判过期，避免请求在途中失效
 * 用于路由守卫在页面加载前拦截过期令牌。
 */
export function isAccessTokenExpired(token?: string | null): boolean {
  const t = token ?? tokenStore.getAccessToken()
  if (!t) return true
  try {
    const parts = t.split('.')
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
