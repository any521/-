// 认证 API（已对齐 Track A：/auth/login + /user/profile）
// 员工工号+密码登录走 /auth/login（username/password）
// 个人信息 → GET /user/profile（ProfileVO），本层适配为 StaffProfile
// 演示账号（V49 种子）：R1001（回收员）/ S2001（环卫员）/ T3001（技术员），密码 123456
import axios from 'axios'
import type { Result, TokenPair } from '@shared/http/types'
import type { StaffRole } from '@shared/data/baseData'
import { request } from './request'
import { tokenStore, hasToken, isAccessTokenExpired } from './token'
import { singleRefresh } from '@shared/http/singleRefresh'

export interface StaffProfile {
  id: number
  /** 工号 */
  staffNo: string
  name: string
  email: string
  /** 头像 URL（Upgrade U1：员工也是 sys_user，走同一 /user/avatar 端点） */
  avatar: string
  /** 三类员工角色（memory.md 术语表） */
  role: StaffRole
  roleName: string
  /** 负责片区 ID（雪花字符串；换片区后随 profile 刷新） */
  areaId: string
  /** 负责片区名（由 store 按片区列表映射；后端 profile 仅返回 areaId） */
  areaName: string
  /** 入职日期 */
  hireDate: string
}

/** 后端工作人员身份摘要（ProfileVO.staffInfo，取 biz_staff 最新一条） */
interface StaffInfoVO {
  staffId: string
  staffType: number
  staffName: string | null
  /** 员工负责片区（biz_staff.area_id，换片区后随 profile 刷新） */
  areaId: string | null
  auditStatus: number
  status: number
  /** 入职日期（biz_staff.create_time 建档案日期，yyyy-MM-dd） */
  hireDate: string | null
}

/** 后端个人画像（ProfileVO，Long 序列化为字符串） */
interface ProfileVO {
  userId: string
  username: string
  nickname: string
  avatar: string
  email: string
  /** RECYCLER / SANITATION / TECHNICIAN / ADMIN */
  role: string
  /** sys_user 默认片区（员工换片区不更新此字段，勿用于员工片区展示） */
  areaId: string | null
  staffInfo: StaffInfoVO | null
  perms: string[]
}

/** 后端角色枚举 → 员工端 StaffRole */
const ROLE_MAP: Record<string, StaffRole> = {
  RECYCLER: 'RECYCLER',
  SANITATION: 'SANITATION',
  TECHNICIAN: 'TECHNICIAN',
}
const ROLE_NAME: Record<string, string> = {
  RECYCLER: '回收员',
  SANITATION: '环卫员',
  TECHNICIAN: '技术员',
}

/** 工号 + 密码登录 */
export const loginByAccount = (staffNo: string, password: string) =>
  request.post<TokenPair>('/auth/login', { username: staffNo, password, loginType: 'ACCOUNT' })

export const logout = () => request.post('/auth/logout', {}, { silent: true, skipErrorToast: true })

export async function getProfile(): Promise<StaffProfile> {
  const p = await request.get<ProfileVO>('/user/profile')
  // 员工负责片区以 staffInfo.areaId（biz_staff.area_id）为准；
  // profile.areaId 是 sys_user 默认片区，换片区不会更新，直接用它会导致显示“未分配”
  const areaId = p.staffInfo?.areaId ?? ''
  return {
    id: Number(p.userId),
    staffNo: p.username,
    name: p.nickname ?? p.username,
    email: p.email ?? '',
    avatar: p.avatar ?? '',
    role: ROLE_MAP[p.role] ?? 'RECYCLER',
    roleName: ROLE_NAME[p.role] ?? p.role,
    areaId,
    areaName: areaId ? `片区${areaId}` : '—',
    hireDate: p.staffInfo?.hireDate ?? '',
  }
}

/** 更新头像（Upgrade U1）：员工走与用户一致的 /user/avatar 端点 */
export const updateAvatar = (avatarUrl: string) => request.put('/user/avatar', { avatarUrl })

/**
 * 免登录切回用户端（C-O1 双身份互通，与 /auth/switch-staff 对称）：
 * 1. access token 缺失/已过期时先静默续期，保证 switch-user 有有效会话；
 * 2. 调 POST /auth/switch-user 用当前 STAFF 会话换回 USER 会话（后端按客户端分 Refresh
 *    Cookie/键，互不覆盖），同时种 df_refresh_USER Cookie，用户端会话可独立续期；
 * 3. 切换失败（会话失效/网络）返回 false：跳转后用户端走正常登录/Refresh 链路。
 * 中转 key 由用户端首屏 restoreFromHandoff 读取后即删（同 tab 整页导航保留 sessionStorage）。
 */
export async function handoffToUser(): Promise<boolean> {
  if (!hasToken() || isAccessTokenExpired()) {
    const pair = await refreshAccessToken()
    if (!pair) return false
  }
  try {
    const res = (await request.post('/auth/switch-user')) as unknown as { accessToken?: string }
    if (res?.accessToken) {
      sessionStorage.setItem('df_user_access', res.accessToken)
      return true
    }
  } catch {
    // 切换失败：交由调用方提示，不携带无效令牌跳转
  }
  return false
}

/**
 * 续期 access token。全局单飞（与 401 自动续期/恢复会话共享）+ 429（后端刷新锁/频控）小退避重试，
 * 避免并发刷新撞锁产生 429 被误判为登录失效。
 */
export function refreshAccessToken(): Promise<TokenPair | null> {
  return singleRefresh(async () => {
    for (let attempt = 0; ; attempt++) {
      try {
        const resp = await axios.post<Result<TokenPair>>(
          `${import.meta.env.VITE_API_BASE}/auth/refresh`, undefined,
          { withCredentials: true, headers: { 'X-Client-Type': 'STAFF' } },
        )
        if (resp.data.code === 200 && resp.data.data) {
          tokenStore.setTokens(resp.data.data)
          return resp.data.data
        }
        if (resp.data.code !== 429 || attempt >= 2) return null
      } catch (e) {
        const code = (e as { response?: { data?: { code?: number } } })?.response?.data?.code
        if (code !== 429 || attempt >= 2) return null
      }
      await new Promise((resolve) => setTimeout(resolve, 600))
    }
  })
}
