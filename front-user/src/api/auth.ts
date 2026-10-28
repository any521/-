// ============================================================
// 认证 API（全量对接真实后端 Track A）
// 验证码 → POST /api/user/verify-code（VerifyCodeDTO(email)，163 真实邮箱发送随机 6 位）
// 注册   → POST /api/user/register（RegisterDTO(email, verifyCode, password)，已注册则忽略）
// 登录   → POST /api/auth/login（LoginDTO(username, password)，后端仅支持账密）
// 个人信息 → GET /api/user/profile（ProfileVO），在本层适配为用户端 UserProfile
// 续期   → POST /api/auth/refresh（RefreshDTO(refreshToken)，路由守卫在 access 过期时主动调用）
// 邮箱验证码登录桥：验证码注册 + 派生固定密码账密登录，页面零改
// ============================================================
import axios from 'axios'
import { showToast } from 'vant'
import type { Result, TokenPair } from '@shared/http/types'
import { BizError } from '@shared/http/createRequest'
import { request } from './request'
import { tokenStore } from './token'
import { singleRefresh } from '@shared/http/singleRefresh'

export interface UserProfile {
  id: number
  nickname: string
  email: string
  avatar: string
  /** 工作人员身份摘要（C-O1 双身份契约；未申请为 null） */
  staffInfo: StaffInfo | null
}

/** 后端 ProfileVO.staffInfo（biz_staff 最新一条，Long 序列化为字符串） */
export interface StaffInfo {
  staffId: string
  /** 1回收员 2环卫员 3技术员 */
  staffType: number
  staffName: string
  areaId: string | null
  /** 0待审核 1已通过 2已驳回 */
  auditStatus: number
}

/** 后端个人画像（ProfileVO，Long 序列化为字符串） */
interface ProfileVO {
  userId: string
  username: string
  nickname: string
  avatar: string
  email: string
  role: string
  areaId: string | null
  staffInfo: StaffInfo | null
  perms: string[]
}

/** 发送邮箱验证码（后端 /user/verify-code，163 SMTP 真实邮箱发送随机 6 位） */
export const sendEmailCode = (email: string) => request.post('/user/verify-code', { email })

/** 发送邮箱验证码登录（新入口：一次请求搞定；邮箱内部 normalize 为小写 + trim） */
export const loginByEmailCode = (email: string, code: string): Promise<TokenPair> => {
  return request.post<TokenPair>('/auth/login/code', { email: email.trim().toLowerCase(), verifyCode: code })
}

/** 邮箱验证码登录桥的派生密码（约定：验证码注册时固化） */
const derivedPassword = (email: string) => `Df@${email.split('@')[0]?.slice(-6) ?? 'mail'}1`

/**
 * 邮箱验证码登录桥（弃用，仅兼容旧调用处）：派生密码模式
 * @deprecated 改用 loginByEmailCode 直接验证码登录（不依赖派生密码，避免锁定）
 */
export async function loginByEmail(email: string, code: string): Promise<TokenPair> {
  const password = derivedPassword(email)
  try {
    await request.post('/user/register', { email, verifyCode: code, password }, { skipErrorToast: true })
  } catch (e) {
    // 已注册则继续走登录；验证码过期/错误等失败必须明确提示，不再静默
    if (e instanceof BizError && e.message.includes('已注册')) {
      return request.post<TokenPair>('/auth/login', { username: email, password })
    }
    showToast(e instanceof BizError ? e.message : '操作失败，请重试')
    throw e
  }
  return request.post<TokenPair>('/auth/login', { username: email, password })
}

/** 账密登录（后端 LoginDTO{username, password}，username 支持邮箱或用户名） */
export const loginByPassword = (username: string, password: string) =>
  request.post<TokenPair>('/auth/login', { username, password })

/**
 * 设置密码（B6）：登录态 + 邮箱验证码二次校验（验证码发到当前登录邮箱，
 * 替代旧密码证明持有邮箱；验证码注册的派生密码用户不可知，故不校验旧密码）
 */
export const setPassword = (verifyCode: string, newPassword: string) =>
  request.put('/user/password', { verifyCode, newPassword })

export const logout = () => request.post('/auth/logout', {}, { silent: true, skipErrorToast: true })

export async function getProfile(): Promise<UserProfile> {
  const p = await request.get<ProfileVO>('/user/profile')
  return {
    id: Number(p.userId),
    nickname: p.nickname ?? p.username,
    email: p.email ?? '',
    avatar: p.avatar ?? '',
    staffInfo: p.staffInfo ?? null,
  }
}

/**
 * 更新头像（Upgrade U1）：上传后把 url 提交到 /user/avatar，返回最新画像。
 * 调用方拿到 ProfileVO 后按需刷新本地 userStore.profile。
 */
export const updateAvatar = (avatarUrl: string) => request.put<ProfileVO>('/user/avatar', { avatarUrl })

/**
 * 修改显示昵称（n7）：提交后返回最新画像，调用方按需刷新本地 userStore.profile。
 */
export const updateNickname = (nickname: string) => request.put<ProfileVO>('/user/nickname', { nickname })

/**
 * 用 refresh token 续期 access token（路由守卫在 access 过期时主动调用）。
 * 裸 axios 调用，不走 request 拦截器，避免 401 递归触发 tryRefresh。
 * 全局单飞：路由守卫 / 个人中心恢复会话 / 401 自动续期 共用同一次刷新，
 * 避免并发刷新撞后端刷新锁产生 429 被误判为登录失效；429 属瞬时状态，小退避重试后仍失败才返回 null。
 * 成功时同步写入 tokenStore 并返回新令牌对，失败返回 null（由调用方决定跳登录）。
 */
export function refreshAccessToken(): Promise<TokenPair | null> {
  return singleRefresh(async () => {
    for (let attempt = 0; ; attempt++) {
      try {
        const resp = await axios.post<Result<TokenPair>>(
          `${import.meta.env.VITE_API_BASE}/auth/refresh`,
          undefined,
          {
            withCredentials: true,
            headers: { 'X-Client-Type': 'USER' },
          },
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
