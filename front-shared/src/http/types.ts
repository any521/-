// ============================================================
// 公共契约类型（Sprint 0 冻结，memory.md 全局约定）
// ============================================================

/** 统一响应体（决策 D4 + C20 修订：400/401/403/429 使用真实 HTTP 状态码，其余业务码仍 HTTP 200，语义由 code 承载）
 *  消息字段兼容两种命名：Mock/前端用 message，后端 Result.java 用 msg（联调期兼容，取先非空者） */
export interface Result<T = unknown> {
  code: number
  message?: string
  msg?: string
  data: T
}

/** 分页请求参数 */
export interface PageQuery {
  pageNum: number
  pageSize: number
  [key: string]: unknown
}

/** 分页响应结构（后端 PageVO：total/pages/list；Mock 另带 pageNum/pageSize，均可选兼容） */
export interface PageResult<T = unknown> {
  total: number
  pageNum?: number
  pageSize?: number
  /** 总页数（后端 PageVO 字段） */
  pages?: number
  list: T[]
}

/** 多端识别头（12_api.md §12.1） */
export type ClientType = 'USER' | 'STAFF' | 'ADMIN'

/** 登录令牌对（决策 D3：双 Token） */
export interface TokenPair {
  accessToken: string
  /** 浏览器端刷新令牌存 HttpOnly Cookie，响应中可为空；非浏览器客户端可继续使用 body。 */
  refreshToken?: string | null
  /** access token 有效期（秒） */
  expiresIn: number
}

/** 回收订单状态机（memory.md 业务规则） */
export enum OrderStatus {
  PENDING = 0, // 待接单
  ACCEPTED = 1, // 已接单
  PROCESSING = 2, // 进行中
  FINISHED = 3, // 已完成
  CANCELED = 4, // 已取消
  ABNORMAL = 5, // 异常
}

export const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  [OrderStatus.PENDING]: '待接单',
  [OrderStatus.ACCEPTED]: '已接单',
  [OrderStatus.PROCESSING]: '进行中',
  [OrderStatus.FINISHED]: '已完成',
  [OrderStatus.CANCELED]: '已取消',
  [OrderStatus.ABNORMAL]: '异常',
}
