// ============================================================
// 工作人员申请 API（G1，onsite01，契约 C-O1）
// POST /api/staff/apply + GET /api/staff/apply/status
// 禁止追加到 business.ts（onsite02 独占）
// ============================================================
import { request } from './request'
import { hasToken, isAccessTokenExpired } from './token'
import { refreshAccessToken } from './auth'

export interface StaffApplyForm {
  /** 1回收员 2清运工（环卫员） 3维修技术员 */
  staffType: 1 | 2 | 3
  staffName: string
  phone: string
  /** 片区 ID（可空=审核通过后由平台分配） */
  areaId?: number
  reason?: string
}

export interface StaffApplyStatus {
  applied: boolean
  /** 0待审核 1已通过 2已驳回（未申请为 undefined） */
  auditStatus?: number
  staffType?: number
  staffId?: number
  /** 驳回原因（已驳回时展示；旧数据无审核备注时退回申请理由） */
  reason?: string
}

export const applyStaff = (form: StaffApplyForm) => request.post('/staff/apply', form)
export const getStaffApplyStatus = () => request.get<StaffApplyStatus>('/staff/apply/status')

/** 探测员工端应用是否可用（dev 经 /staff 代理到 5175；未启动时代理 502/ECONNREFUSED；生产为同域子路径） */
export async function probeStaffApp(timeoutMs = 3000): Promise<boolean> {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeoutMs)
  try {
    const resp = await fetch(import.meta.env.BASE_URL + 'staff/', { method: 'GET', signal: controller.signal })
    return resp.ok
  } catch {
    return false
  } finally {
    window.clearTimeout(timer)
  }
}

/**
 * 免登录切换员工端（C-O1，双身份互通）：
 * 1. access token 缺失/已过期时先静默续期，保证 switch-staff 有有效会话；
 * 2. 调 POST /auth/switch-staff 用当前 USER 会话换取 STAFF 会话（后端校验在岗员工身份，
 *    同时种 df_refresh_STAFF Cookie，员工端会话可独立续期）；
 * 3. 切换失败（非在岗/网络）直接返回 false：不再回退中转 USER access token ——
 *    员工端按角色鉴权（RECYCLER/SANITATION/TECHNICIAN），USER 令牌必然 403 重新登录。
 * 中转 key 由员工端首屏 restoreFromHandoff 读取后即删（dev 经 /staff 代理同源；
 * window.open 新 tab 会复制 opener 的 sessionStorage）。
 */
export async function handoffToStaff(): Promise<boolean> {
  if (!hasToken() || isAccessTokenExpired()) {
    const pair = await refreshAccessToken()
    if (!pair) return false
  }
  try {
    const res = (await request.post('/auth/switch-staff')) as unknown as { accessToken?: string }
    if (res?.accessToken) {
      sessionStorage.setItem('df_staff_access', res.accessToken)
      return true
    }
  } catch {
    // 切换失败（非在岗/接口异常），交由调用方提示，不携带无效令牌跳转
  }
  return false
}
