/// <reference types="vite/client" />

declare module 'virtual:svg-icons-register';

interface ImportMetaEnv {
  /** 接口基地址 */
  readonly VITE_API_BASE: string
  readonly VITE_APP_TITLE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
