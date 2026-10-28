/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口基地址 */
  readonly VITE_API_BASE: string
  readonly VITE_APP_TITLE: string
  /** 高德 JS API 2.0 Web Key（未配置时走后端 /api/map/config 下发） */
  readonly VITE_AMAP_WEB_KEY?: string
  /** 高德安全密钥（可选，与 Web Key 配对） */
  readonly VITE_AMAP_SECURITY_CODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
