// ============================================================
// 通用工具（D0）
// ============================================================

/** 金额格式化：分转元保留两位（Mock 阶段直接元） */
export function formatMoney(yuan: number): string {
  return `¥${yuan.toFixed(2)}`
}

/** 重量格式化 */
export function formatWeight(kg: number): string {
  return kg >= 1000 ? `${(kg / 1000).toFixed(2)} t` : `${kg.toFixed(1)} kg`
}

/** 碳减排量格式化 */
export function formatCarbon(kgCO2: number): string {
  return kgCO2 >= 1000 ? `${(kgCO2 / 1000).toFixed(2)} tCO₂` : `${kgCO2.toFixed(2)} kgCO₂`
}

/** 手机号脱敏 */
export function maskPhone(phone: string): string {
  return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

/**
 * IM 媒体直链：私密目录（im/repair/recycle/kitchen）预览需登录（C11），
 * `<img>/<video>/<audio>` 无法携带 Authorization Header，故将 access_token 附到 query。
 * 公开目录（avatar/product/diy/flea/common）已匿名放行，无需追加。
 */
export function mediaUrl(url: string | null | undefined, token: string | null | undefined): string {
  if (!url || !token) return url ?? ''
  if (!url.startsWith('/api/file/preview/')) return url
  if (url.startsWith('/api/file/preview/avatar/')
      || url.startsWith('/api/file/preview/product/')
      || url.startsWith('/api/file/preview/diy/')
      || url.startsWith('/api/file/preview/flea/')
      || url.startsWith('/api/file/preview/common/')) {
    return url
  }
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}access_token=${encodeURIComponent(token)}`
}

/** 简单防抖 */
export function debounce<T extends (...args: never[]) => void>(fn: T, wait = 300) {
  let timer: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), wait)
  }
}

/** 生成 Mock 订单号（雪花占位：前缀 + 日期 + 序列） */
export function mockOrderNo(prefix = 'RO'): string {
  const d = new Date()
  const date = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  return `${prefix}${date}${String(Math.floor(Math.random() * 1000000)).padStart(6, '0')}`
}
