// ============================================================
// 用户 store（Pinia）：登录态 / 个人信息 / 适老化模式
// Token 本体由 api/token.ts 保存在内存；Refresh Token 由 HttpOnly Cookie 管理。
// ============================================================
import { ref } from 'vue'
import { defineStore, acceptHMRUpdate } from 'pinia'
import type { TokenPair } from '@shared/http/types'
import { getProfile, loginByPassword, logout as apiLogout, loginByEmailCode, refreshAccessToken } from '@/api/auth'
import type { UserProfile } from '@/api/auth'
import { tokenStore, hasToken, onTokenCleared, isAccessTokenExpired } from '@/api/token'

const ELDER_KEY = 'df_user_elder'

export const useUserStore = defineStore('user', () => {
  const profile = ref<UserProfile | null>(null)
  /** 适老化模式（F7 语音碳管家配套：大字体高对比），持久化到本地 */
  const elderMode = ref(localStorage.getItem(ELDER_KEY) === '1')

  // 令牌被 401 踢出/登出清空时，画像同步置空，避免残留导致"已登录假象"
  onTokenCleared(() => {
    profile.value = null
  })

  const isLogin = () => hasToken()

  /** 邮箱验证码登录（新入口：一次请求，不依赖派生密码） */
  async function login(email: string, code: string) {
    const pair: TokenPair = await loginByEmailCode(email, code)
    tokenStore.setTokens(pair)
    await fetchProfileSafe()
  }

  /** 账号密码登录（需已注册） */
  async function loginByPwd(username: string, password: string) {
    const pair: TokenPair = await loginByPassword(username, password)
    tokenStore.setTokens(pair)
    await fetchProfileSafe()
  }

  /** 拉取画像；失败不抛出（令牌已下发即视为登录成功，画像由后续页面/恢复会话补拉） */
  async function fetchProfileSafe() {
    try {
      await fetchProfile()
    } catch {
      // 画像失败不影响登录态，静默降级
    }
  }

  async function fetchProfile() {
    if (!hasToken()) return
    profile.value = await getProfile()
  }

  /**
   * 恢复会话（个人中心等非 auth 页面刷新后调用）：
   * 刷新页面会清空内存中的 Access Token，但 Refresh Token 仍在 HttpOnly Cookie 里。
   * 无令牌或令牌已过期时静默续期并拉取画像，避免刷新个人中心后误判为已退出登录。
   */
  async function restoreSession(): Promise<boolean> {
    if (!hasToken() || isAccessTokenExpired()) {
      if (!(await refreshAccessToken())) {
        // 续期失败 = 会话真正失效：同步清理令牌与画像，避免过期令牌残留造成"假登录"
        // （导航栏/个人页等所有 hasToken() 依赖方立即一致显示未登录，杜绝状态不同步）
        tokenStore.clear()
        return false
      }
    }
    if (!profile.value) await fetchProfileSafe()
    return true
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      tokenStore.clear()
      profile.value = null
    }
  }

  function toggleElderMode() {
    elderMode.value = !elderMode.value
    localStorage.setItem(ELDER_KEY, elderMode.value ? '1' : '0')
  }

  return { profile, elderMode, isLogin, login, loginByPwd, fetchProfile, restoreSession, logout, toggleElderMode }
})

// Pinia setup store HMR：热更新时保留 state 并替换方法
if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useUserStore, import.meta.hot))
}
