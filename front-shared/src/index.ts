// ============================================================
// @dianfei/shared 统一出口（三端通过 Vite 别名 @shared 引用）
// ============================================================

// ---- HTTP：契约类型 / 错误码 / 请求封装 ----
export * from './http/types'
export * from './http/errorCodes'
export * from './http/createRequest'
export * from './http/sharedHttp'

// ---- 共享基础数据 ----
export * from './data/baseData'

// ---- 工具 ----
export * from './utils/format'

// ---- 通用业务组件 ----
export { default as AddressPicker } from './components/AddressPicker.vue'
export { default as AddressBookSelect } from './components/AddressBookSelect.vue'
export * from './components/address'
export { default as AvatarUploader } from './components/AvatarUploader.vue'
export { default as CategorySelect } from './components/CategorySelect.vue'
export { default as OrderStatusBar } from './components/OrderStatusBar.vue'

// ---- 自定义弹窗 / 轻提示（替代原生 alert / confirm） ----
export * from './feedback'
