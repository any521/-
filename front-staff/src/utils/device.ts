// ============================================================
// 设备判断（PC / 移动双端分流）：UA 命中手机/平板特征视为移动端，
// 桌面 UA 且窗口宽度 ≥ 1024 视为 PC（与用户端 front-user 口径一致）
// ============================================================
const MOBILE_UA =
  /(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i

/** 是否为移动端（UA 命中或窗口过窄） */
export const isMobile = () =>
  MOBILE_UA.test(navigator.userAgent) || window.innerWidth < 1024

/** 是否为 PC 端 */
export const isPC = () => !MOBILE_UA.test(navigator.userAgent) && window.innerWidth >= 1024
