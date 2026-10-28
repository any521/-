// ============================================================
// 用户状态（09_frontend §9.5）：登录态 / 用户信息 / 权限码
// ============================================================
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, getUserInfo, type LoginForm, type UserInfo } from '@/api/auth'
import { hasToken } from '@/api/token'

export const useUserStore = defineStore('user', () => {
  const info = ref<UserInfo | null>(null)

  const isLogin = computed(() => hasToken())
  const nickname = computed(() => info.value?.nickname ?? '')
  const roles = computed(() => info.value?.roles ?? [])
  const permissions = computed(() => info.value?.permissions ?? [])

  async function login(form: LoginForm) {
    await apiLogin(form)
    info.value = await getUserInfo()
  }

  async function fetchInfo() {
    if (!info.value) info.value = await getUserInfo()
    return info.value
  }

  /** 按钮级权限判断（v-permission 指令使用） */
  function hasPermission(code: string | string[]): boolean {
    const codes = Array.isArray(code) ? code : [code]
    const owned = permissions.value
    if (owned.includes('*:*:*')) return true
    return codes.some((c) => owned.includes(c))
  }

  async function logout() {
    await apiLogout()
    info.value = null
  }

  return { info, isLogin, nickname, roles, permissions, login, fetchInfo, hasPermission, logout }
})
