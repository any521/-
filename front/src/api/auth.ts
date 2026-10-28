// ============================================================
// 认证与个人信息（已对齐 Track A 真实契约：/auth/login + /user/profile）
// userinfo 与动态菜单同源 GET /api/user/profile（ProfileVO），单飞缓存
// ============================================================
import axios from 'axios'
import type { Result, TokenPair } from '@shared/http/types'
import { request } from './request'
import { tokenStore } from './token'
import { singleRefresh } from '@shared/http/singleRefresh'

export interface LoginForm {
  username: string
  password: string
  captcha?: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  roles: string[]
  /** 按钮级权限码，'*:*:*' 表示超管 */
  permissions: string[]
}

/** 动态菜单节点（登录后拉取，前端 addRoute 注册） */
export interface MenuNode {
  id: number
  parentId: number
  title: string
  path: string
  /** views 下组件路径，如 system/user；目录节点为空 */
  component?: string
  icon?: string
  children?: MenuNode[]
}

// ---------- 后端出参形状（Track A 冻结契约，Long 序列化为字符串） ----------

/** 后端菜单树节点（MenuTreeVO） */
export interface MenuTreeVO {
  id: string
  parentId: string
  menuName: string
  /** 1 目录 / 2 菜单 / 3 按钮 */
  menuType: number
  path: string
  component: string
  perm: string
  icon: string
  sort: number
  /** 0 隐藏 / 1 显示 */
  visible: number
  /** 0 停用 / 1 启用 */
  status: number
  children?: MenuTreeVO[]
}

/** 后端当前用户画像（ProfileVO：基本信息 + 权限码 + 菜单树） */
export interface ProfileVO {
  userId: string
  username: string
  nickname: string
  avatar: string
  email: string
  /** USER / RECYCLER / SANITATION / TECHNICIAN / ADMIN */
  role: string
  areaId: string | null
  perms: string[]
  menus: MenuTreeVO[]
}

// profile 单飞缓存：getUserInfo / getMenus 共享一次请求，登录登出时失效
let profileCache: Promise<ProfileVO> | null = null
function fetchProfile(): Promise<ProfileVO> {
  if (!profileCache) {
    profileCache = request.get<ProfileVO>('/user/profile').catch((e) => {
      profileCache = null
      throw e
    })
  }
  return profileCache
}

export async function login(form: LoginForm): Promise<void> {
  const pair = await request.post<TokenPair>('/auth/login', form)
  tokenStore.setTokens(pair)
  profileCache = null
}

export async function getUserInfo(): Promise<UserInfo> {
  const p = await fetchProfile()
  return {
    id: Number(p.userId),
    username: p.username,
    nickname: p.nickname ?? p.username,
    avatar: p.avatar ?? '',
    roles: [p.role],
    permissions: p.perms ?? [],
  }
}

/** 目录/菜单可见节点（过滤按钮与停用/隐藏项） */
const keepMenu = (m: MenuTreeVO) => m.menuType !== 3 && m.visible !== 0 && m.status !== 0

function toMenuNode(m: MenuTreeVO): MenuNode {
  const children = (m.children ?? []).filter(keepMenu).map(toMenuNode)
  return {
    id: Number(m.id),
    parentId: Number(m.parentId),
    title: m.menuName,
    path: m.path,
    component: m.component || undefined,
    icon: m.icon || undefined,
    ...(children.length ? { children } : {}),
  }
}

export async function getMenus(): Promise<MenuNode[]> {
  const p = await fetchProfile()
  return (p.menus ?? []).filter(keepMenu).map(toMenuNode)
}

export async function logout(): Promise<void> {
  try {
    await request.post('/auth/logout', {}, { silent: true, skipErrorToast: true })
  } catch (e: unknown) {
    // 登出即使失败也要清理本地状态，避免下次请求用失效的 token
    console.warn('Logout API 调用失败:', (e as any)?.message || '未知错误')
  } finally {
    tokenStore.clear()
    sessionStorage.removeItem('df_admin_at') // 清除 session-access-token
    profileCache = null
  }
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
          { withCredentials: true, headers: { 'X-Client-Type': 'ADMIN' } },
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
