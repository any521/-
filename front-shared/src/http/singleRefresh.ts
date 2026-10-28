// ============================================================
// 全局单飞刷新协调器（429 修复配套）
// 路由守卫 / 个人中心恢复会话 / 401 自动续期 三路共用同一次 refresh，
// 避免并发刷新撞后端刷新锁（10s）产生 HTTP 429 被误判为登录失效。
// ============================================================
import type { TokenPair } from './types'

let inflight: Promise<TokenPair | null> | null = null

/** 单飞：已有续期请求在途则复用其 Promise；fn 首次调用执行，其余共享结果 */
export function singleRefresh(fn: () => Promise<TokenPair | null>): Promise<TokenPair | null> {
  if (!inflight) {
    inflight = fn().finally(() => {
      // 置空放在微任务后，保证并发调用共享同一次刷新
      setTimeout(() => {
        inflight = null
      }, 0)
    })
  }
  return inflight
}
