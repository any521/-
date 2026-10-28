// ============================================================
// 统一错误码表（12_api.md §12.4，Sprint 0 冻结）
// ============================================================

export const ErrorCode = {
  /** 成功 */
  OK: 200,
  /** 参数错误 */
  BAD_PARAM: 400,
  /** 未认证：Token 缺失或过期 */
  UNAUTHORIZED: 401,
  /** 无权限：角色不匹配或数据越权 */
  FORBIDDEN: 403,
  /** 限流：请求过于频繁 / MCP 超频 */
  RATE_LIMITED: 429,
  /** 订单状态不允许 */
  ORDER_STATE_INVALID: 2003,
  /** 积分不足 */
  POINTS_NOT_ENOUGH: 2006,
  /** AI 服务不可用（触发降级） */
  AI_UNAVAILABLE: 3001,
  /** 结构化输出校验失败 */
  AI_SCHEMA_INVALID: 3002,
  /** 系统异常 */
  SYSTEM_ERROR: 5000,
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/** 错误码 → 用户可读提示 */
export const ERROR_MESSAGE: Record<number, string> = {
  [ErrorCode.BAD_PARAM]: '请求参数有误，请检查后重试',
  [ErrorCode.UNAUTHORIZED]: '登录已过期，请重新登录',
  [ErrorCode.FORBIDDEN]: '您没有权限执行此操作',
  [ErrorCode.RATE_LIMITED]: '操作太频繁啦，请稍后再试',
  [ErrorCode.ORDER_STATE_INVALID]: '当前订单状态不允许该操作',
  [ErrorCode.POINTS_NOT_ENOUGH]: '碳积分余额不足',
  [ErrorCode.AI_UNAVAILABLE]: 'AI 服务暂不可用，已为您转入人工/兜底流程',
  [ErrorCode.AI_SCHEMA_INVALID]: 'AI 返回异常，请重试或改用手动方式',
  [ErrorCode.SYSTEM_ERROR]: '系统开小差了，请稍后重试',
}

/** 取错误提示文案（未知码给通用提示）
 *  401 统一显示"登录已过期"，避免后端 refreshToken 相关文案让用户困惑；
 *  其他错误码优先用后端返回的具体 msg，缺省才回退到本地默认文案 */
export function messageOf(code: number, fallback?: string): string {
  if (code === ErrorCode.UNAUTHORIZED) return ERROR_MESSAGE[code] ?? '登录已过期，请重新登录'
  return fallback ?? ERROR_MESSAGE[code] ?? `请求失败（${code}）`
}
