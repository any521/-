// ============================================================
// 员工 store（Pinia）：登录态 / 员工档案 / 角色差异化视图依据
// Token 本体由 api/token.ts 保存在内存；Refresh Token 由 HttpOnly Cookie 管理。
// ============================================================
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { TokenPair } from '@shared/http/types'
import type { StaffRole } from '@shared/data/baseData'
import { getProfile, loginByAccount, logout as apiLogout, type StaffProfile } from '@/api/auth'
import { listAreas, changeMyArea as apiChangeMyArea, type AreaRow } from '@/api/business'
import { tokenStore, hasToken, onTokenCleared } from '@/api/token'

export const useStaffStore = defineStore('staff', () => {
  const profile = ref<StaffProfile | null>(null)

  // 令牌被 401 踢出/登出清空时，画像同步置空，避免残留导致"已登录假象"
  onTokenCleared(() => {
    profile.value = null
  })

  /** 当前角色（三类员工差异化视图依据），未加载档案时按回收员兜底 */
  const role = computed<StaffRole>(() => profile.value?.role ?? 'RECYCLER')
  const isTechnician = computed(() => role.value === 'TECHNICIAN')

  /** 启用片区缓存（换片区下拉 / 订单片区名映射共用） */
  const areas = ref<AreaRow[]>([])

  /** 当前负责片区 ID（雪花字符串） */
  const myAreaId = computed(() => profile.value?.areaId ?? '')

  /** 片区 ID → 片区名（未知/已移除回退为“片区{id}”） */
  function areaNameOf(id?: string | null): string {
    if (!id || id === '0' || id === 'null') return '未分配'
    return areas.value.find((a) => a.id === id)?.areaName ?? `片区${id}`
  }

  /** 当前负责片区名 */
  const myAreaName = computed(() => areaNameOf(myAreaId.value))

  async function loadAreas() {
    areas.value = await listAreas()
  }

  const isLogin = () => hasToken()

  async function login(staffNo: string, password: string) {
    const pair: TokenPair = await loginByAccount(staffNo, password)
    tokenStore.setTokens(pair)
    try {
      await fetchProfile()
    } catch {
      // 画像失败不影响登录态（令牌已下发即登录成功），后续页面会补拉
    }
  }

  async function fetchProfile() {
    if (!hasToken()) return
    profile.value = await getProfile()
  }

  /** 自助换片区：调后端 → 刷新画像（areaId）→ 刷新片区缓存（新片区名） */
  async function changeArea(areaId: string) {
    await apiChangeMyArea(areaId)
    await Promise.all([fetchProfile(), loadAreas()])
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      tokenStore.clear()
      profile.value = null
    }
  }

  return {
    profile, role, isTechnician, isLogin, login, fetchProfile, logout,
    areas, myAreaId, myAreaName, areaNameOf, loadAreas, changeArea,
  }
})
