// ============================================================
// 三端统一请求封装（09_frontend.md §9.5，D0 共享基建）
// 能力：Token 注入 / X-Client-Type / 401 自动续期 / 重复请求取消
//       全局 Loading / Result 拆解 / 错误码提示
// ============================================================
import axios from 'axios'
import type { AxiosAdapter, AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ErrorCode, messageOf } from './errorCodes'
import type { ClientType, Result, TokenPair } from './types'
import { singleRefresh } from './singleRefresh'

/** 各端注入的令牌存取实现（admin 存 Pinia，H5 存 localStorage） */
export interface TokenStore {
  getAccessToken(): string | null
  getRefreshToken(): string | null
  setTokens(pair: TokenPair): void
  clear(): void
}

export interface RequestOptions {
  baseURL: string
  clientType: ClientType
  tokenStore: TokenStore
  /** 401 且续期失败后的回调（跳登录页） */
  onUnauthorized: () => void
  /** 错误提示（admin 用 ElMessage，H5 用 Vant Toast） */
  toast: (message: string) => void
  /** 全局 Loading 开关（可选） */
  loading?: { show: () => void; hide: () => void }
  /** 刷新令牌接口路径（相对 baseURL），默认 /auth/refresh */
  refreshUrl?: string
  /** 自定义适配器（可选，测试/代理场景注入） */
  adapter?: AxiosAdapter
  timeout?: number
}

/** 请求级配置扩展 */
export interface DfRequestConfig extends AxiosRequestConfig {
  /** 跳过全局 Loading */
  silent?: boolean
  /** 跳过重复请求取消（如轮询） */
  allowDuplicate?: boolean
  /** 业务失败时不自动 toast，由调用方处理 */
  skipErrorToast?: boolean
}

export interface DfRequest {
  raw: AxiosInstance
  get<T>(url: string, params?: Record<string, unknown>, config?: DfRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: DfRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: DfRequestConfig): Promise<T>
  delete<T>(url: string, params?: Record<string, unknown>, config?: DfRequestConfig): Promise<T>
}

/** 业务错误（code ≠ 200 时抛出） */
export class BizError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}

function requestKey(config: AxiosRequestConfig): string {
  return [config.method, config.url, JSON.stringify(config.params ?? {}), JSON.stringify(config.data ?? {})].join('&')
}

export function createRequest(options: RequestOptions): DfRequest {
  const {
    baseURL, clientType, tokenStore, onUnauthorized, toast, loading,
    refreshUrl = '/auth/refresh', adapter, timeout = 15000,
  } = options

  const instance = axios.create({ baseURL, timeout, ...(adapter ? { adapter } : {}) })

  // ---- 重复请求取消：pending 池 ----
  const pending = new Map<string, AbortController>()
  // ---- 全局 Loading 计数 ----
  let loadingCount = 0
  const showLoading = () => { if (loading && loadingCount++ === 0) loading.show() }
  const hideLoading = () => { if (loading && --loadingCount <= 0) { loadingCount = 0; loading.hide() } }

  // ---- 401 续期：全局单飞（与路由守卫/恢复会话共享）+ 429 小退避重试 ----
  async function tryRefresh(): Promise<boolean> {
    const pair = await singleRefresh(async () => {
      const rt = tokenStore.getRefreshToken()
      for (let attempt = 0; ; attempt++) {
        try {
          // 用裸 axios 防止拦截器递归；refreshUrl 为相对路径，经 baseURL 拼接
          const resp = await axios.post<Result<TokenPair>>(
            refreshUrl,
            rt ? { refreshToken: rt } : undefined,
            { baseURL, withCredentials: true, headers: { 'X-Client-Type': clientType }, ...(adapter ? { adapter } : {}) },
          )
          if (resp.data.code === ErrorCode.OK && resp.data.data) {
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
    return !!pair
  }

  /** 401 统一处理：续期成功后重放一次，失败则清令牌并回调跳登录（成功分支与 HTTP 401 分支共用） */
  async function retryAfterRefresh(
    instance: AxiosInstance,
    cfg: InternalAxiosRequestConfig & DfRequestConfig,
  ): Promise<unknown> {
    if (cfg.headers.get('X-Retried')) return undefined
    const ok = await tryRefresh()
    if (!ok) {
      tokenStore.clear()
      onUnauthorized()
      return undefined
    }
    cfg.headers.set('X-Retried', '1')
    return instance.request(cfg)
  }

  // ---- 请求拦截 ----
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const cfg = config as InternalAxiosRequestConfig & DfRequestConfig
    // Token 注入 + 多端识别头
    const token = tokenStore.getAccessToken()
    if (token) cfg.headers.set('Authorization', `Bearer ${token}`)
    cfg.headers.set('X-Client-Type', clientType)
    // 重复请求取消
    if (!cfg.allowDuplicate) {
      const key = requestKey(cfg)
      pending.get(key)?.abort()
      const controller = new AbortController()
      cfg.signal = controller.signal
      pending.set(key, controller)
    }
    if (!cfg.silent) showLoading()
    return cfg
  })

  // ---- 响应拦截：拆解 Result，code≠200 抛 BizError ----
  // 拦截器会把后端 Result 拆成业务 data；Axios 类型声明默认要求返回 AxiosResponse，
  // 这里显式放宽拦截器注册类型，避免污染 DfRequest 的业务数据契约。
  instance.interceptors.response.use(
    (async (response: AxiosResponse) => {
      const cfg = response.config as InternalAxiosRequestConfig & DfRequestConfig
      if (!cfg.allowDuplicate) pending.delete(requestKey(cfg))
      if (!cfg.silent) hideLoading()

      const result = response.data as Result
      if (result.code === ErrorCode.OK) return result.data as never

      // 401 → 自动续期后重放一次
      if (result.code === ErrorCode.UNAUTHORIZED) {
        const retried = await retryAfterRefresh(instance, cfg)
        if (retried) return retried
      }
      const msg = messageOf(result.code, result.message ?? result.msg)
      if (!cfg.skipErrorToast) toast(msg)
      throw new BizError(result.code, msg)
    }) as any,
    async (error) => {
      const cfg = (error.config ?? {}) as InternalAxiosRequestConfig & DfRequestConfig
      if (!cfg.allowDuplicate) pending.delete(requestKey(cfg))
      if (!cfg.silent) hideLoading()
      // 被取消的重复请求静默处理
      if (axios.isCancel(error)) return new Promise(() => {})
      // C20：后端 400/401/403/429 返回真实 HTTP 状态码 + Result body，这里解析还原 BizError
      const body = error.response?.data as Result | undefined
      if (body && typeof body.code === 'number') {
        if (body.code === ErrorCode.UNAUTHORIZED && !cfg.headers.get('X-Retried')) {
          const retried = await retryAfterRefresh(instance, cfg)
          if (retried) return retried
        }
        const msg = messageOf(body.code, body.message ?? body.msg)
        if (!cfg.skipErrorToast) toast(msg)
        throw new BizError(body.code, msg)
      }
      const msg = error.message?.includes('timeout') ? '网络超时，请稍后重试' : '网络异常，请检查网络连接'
      if (!cfg.skipErrorToast) toast(msg)
      return Promise.reject(error)
    },
  )

  return {
    raw: instance,
    // axios 新版泛型推导与拦截器拆包不兼容，统一断言为业务数据类型
    get: <T>(url: string, params?: Record<string, unknown>, config?: DfRequestConfig) =>
      instance.get(url, { params, ...config }) as Promise<T>,
    post: <T>(url: string, data?: unknown, config?: DfRequestConfig) =>
      instance.post(url, data, config) as Promise<T>,
    put: <T>(url: string, data?: unknown, config?: DfRequestConfig) =>
      instance.put(url, data, config) as Promise<T>,
    delete: <T>(url: string, params?: Record<string, unknown>, config?: DfRequestConfig) =>
      instance.delete(url, { params, ...config }) as Promise<T>,
  }
}
