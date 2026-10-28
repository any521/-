// ============================================================
// 用户端 IM API（onsite03 独占新建，禁止追加到 business.ts）
// 契约：onsite_overview.md C-O3-2（会话/消息 REST）
// ============================================================
import type { PageQuery, PageResult } from '@shared/http/types'
import { request } from './request'

/** 会话出参（peer 为对方昵称/头像，unread 为本人侧未读；雪花 ID 全程字符串防精度丢失） */
export interface ImConversationVO {
  id: string
  bizType: string
  bizId: string
  peerName: string
  peerAvatar?: string
  lastMsg: string
  lastTime: string
  unread: number
}

/** 消息出参（Long 已由后端序列化为字符串，ID 保持字符串传递） */
export interface ImMessageVO {
  id: string
  convId: string
  senderId: string
  senderRole: 'USER' | 'STAFF'
  /** 发送者昵称（后端填充，气泡展示） */
  senderName?: string
  /** 发送者头像（/api/file/preview/avatar/**，空则占位） */
  senderAvatar?: string
  msgType: 'TEXT' | 'IMAGE' | 'STICKER' | 'VOICE' | 'VIDEO' | 'SYSTEM'
  content: string
  duration: number
  createTime: string
}

/** WS 帧（C-O3-3 冻结协议，上下行同构；ID 字符串防精度丢失） */
export interface ImWsFrame {
  type: 'MSG' | 'READ' | 'PING' | 'PONG' | 'TYPING' | 'SYSTEM' | 'LOCATION'
  convId?: string
  /** MSG 载荷（msgType/content/duration 冻结；下行附加 id/createTime 供幂等渲染） */
  msg?: { msgType: string; content: string; duration: number; id?: string; createTime?: string }
  senderId?: string
  senderRole?: 'USER' | 'STAFF'
  senderName?: string
  senderAvatar?: string
  location?: { lng: number; lat: number }
  readerId?: string
}

/** 创建/复用会话（未接单时后端 400「订单尚未接单，暂无法发起聊天」） */
export const createConversation = (bizType: string, bizId: string) =>
  request.post<ImConversationVO>('/im/conversations', { bizType, bizId })

export const pageMyConversations = () => request.get<ImConversationVO[]>('/im/conversations')

export const pageMessages = (convId: string, q: PageQuery) =>
  request.get<PageResult<ImMessageVO>>(`/im/conversations/${convId}/messages`, q)

export const readConversation = (convId: string) => request.put(`/im/conversations/${convId}/read`)

export const getImUnreadTotal = async (): Promise<number> => {
  const n = await request.get<number | string>('/im/unread-total')
  return Number(n ?? 0)
}
