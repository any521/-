// ============================================================
// AI 模型配置管理 API（CCSwitch 风格：管理端动态维护多供应商 + 能力路由）
// 对接后端 AiConfigController（/api/ai/config/**），协议兼容 openai/anthropic/custom
// 密钥说明：apiKey 在列表/详情不回显（后端脱敏）；新增传明文，编辑留空表示不变更
// ============================================================
import type { PageQuery, PageResult } from '@shared/http/types'
import { request } from './request'

/** AI 供应商（列表行：apiKey 已脱敏为 null） */
export interface AiProvider {
  id: number
  /** 供应商唯一名（如 qwen-vl） */
  name: string
  /** 厂商标识：alibaba/zhipu/deepseek/openai/anthropic/custom/internal */
  vendor: string
  /** 请求协议：openai/anthropic/custom */
  protocol: string
  /** API 基址（不含请求路径） */
  baseUrl: string
  /** 请求路径；空=按协议推导 */
  requestPath: string | null
  /** 密钥（AES 加密存储；列表脱敏为 null） */
  apiKey: string | null
  /** 密钥环境变量名；非空时优先从环境变量读取 */
  apiKeyEnv: string | null
  /** 支持的模型列表 */
  models: string[] | null
  /** 能力标签：vision/chat/reasoning */
  capabilities: string[] | null
  /** 自定义请求头 */
  extraHeaders: Record<string, string> | null
  /** 请求方法：POST/GET（custom/responses 协议） */
  method: string | null
  /** 请求体 JSON 模板（custom 协议；占位符 {{model}}/{{messages}}/{{input}}/{{text}}/{{image}}/{{temperature}}） */
  bodyTemplate: string | null
  /** 响应文本提取点路径（如 output.0.content.0.text） */
  responseTextPath: string | null
  /** 调用超时（秒） */
  timeoutSeconds: number
  /** 单次输出上限 token（长结构化 JSON 如 3D spec 建议 8192） */
  maxTokens: number
  /** 0禁用 1启用 */
  enabled: 0 | 1
  /** 联网搜索服务商：bocha/tavily；空=auto（仅 name=web-search 行生效） */
  webSearchProvider: string | null
  /** 联网搜索启用开关：0停用 1启用（仅 name=web-search 行生效） */
  webSearchEnabled: 0 | 1
  /** 展示排序 */
  sort: number
  remark: string | null
  createTime: string
  updateTime: string | null
}

/** 供应商保存请求（apiKey 明文，编辑留空不变更） */
export type AiProviderDTO = Partial<Omit<AiProvider, 'apiKey'>> & {
  /** 密钥明文；新增必填（或填 apiKeyEnv），编辑留空表示不变更 */
  apiKey?: string
}

/** 路由链节点 */
export interface AiRouteChainHop {
  /** 供应商 name（引用 ai_provider.name） */
  provider: string
  /** 模型名（需在 provider.models 内） */
  model: string
}

/** AI 能力路由 */
export interface AiRoute {
  id: number
  /** 能力标识：visual_verify/repair_diagnose/voice_parse/... */
  capability: string
  /** 路由链（依序尝试） */
  chain: AiRouteChainHop[]
  /** 链耗尽后降级代号：MANUAL_REVIEW/KNOWLEDGE_RULE/... */
  fallback: string
  /** 单供应商出参校验失败重试次数 */
  maxRetries: number
  /** 重试温度衰减系数 */
  retryTemperatureDecay: number
  /** 0禁用 1启用 */
  enabled: 0 | 1
  remark: string | null
  createTime: string
  updateTime: string | null
}

/** 路由保存请求 */
export type AiRouteDTO = Partial<Omit<AiRoute, 'chain'>> & {
  chain?: AiRouteChainHop[]
}

// ---------------- 供应商 ----------------
export const pageAiProviders = (q: PageQuery) =>
  request.get<PageResult<AiProvider>>('/ai/config/provider/page', q)

export const saveAiProvider = (data: AiProviderDTO) =>
  data.id ? request.put('/ai/config/provider', data) : request.post('/ai/config/provider', data)

export const removeAiProvider = (id: number) => request.delete(`/ai/config/provider/${id}`)

/** 供应商连通性测试（轻量校验：配置完整性，非真实 HTTP 探测） */
export const testAiProvider = (id: number) =>
  request.post<string>(`/ai/config/provider/test/${id}`)

// ---------------- 路由 ----------------
export const pageAiRoutes = (q: PageQuery) =>
  request.get<PageResult<AiRoute>>('/ai/config/route/page', q)

export const saveAiRoute = (data: AiRouteDTO) =>
  data.id ? request.put('/ai/config/route', data) : request.post('/ai/config/route', data)

export const removeAiRoute = (id: number) => request.delete(`/ai/config/route/${id}`)
