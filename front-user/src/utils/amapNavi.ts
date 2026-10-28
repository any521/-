// ============================================================
// 高德导航工具（用户端）：
// - 移动端：androidamap:// / iosamap:// 唤起高德地图 APP；
//   未安装时 2.5s 兜底打开高德网页导航
// - PC 端：uri.amap.com/navigation 网页导航
// - 统一驾车模式 mode=car + policy=1（距离优先，规划最近路线）
// ============================================================
import { isMobile } from './device'

const fallbackUrl = (lng: number, lat: number, name: string) => {
  const title = encodeURIComponent(name || '目的地')
  return `https://uri.amap.com/navigation?to=${lng},${lat},${title}&mode=car&policy=1&src=点废成金`
}

/** 高德导航（真实调用）：移动端唤起 APP，PC 打开网页导航 */
export function openAmapNavigation(lng: number, lat: number, name: string) {
  if (!lng || !lat) return
  const title = encodeURIComponent(name || '目的地')
  if (isMobile()) {
    // 移动端优先唤起高德 APP（iOS/Android 双 scheme）
    const scheme = /iP(hone|ad|od)/i.test(navigator.userAgent)
      ? `iosamap://navi?sourceApplication=点废成金&poiname=${title}&lat=${lat}&lon=${lng}&dev=0`
      : `androidamap://navi?sourceApplication=点废成金&poiname=${title}&lat=${lat}&lon=${lng}&dev=0`
    window.location.href = scheme
    // 未安装 APP 时 scheme 静默失败：页面可见时兜底打开网页导航
    const timer = window.setTimeout(() => {
      window.open(fallbackUrl(lng, lat, name), '_blank')
    }, 2500)
    document.addEventListener(
      'visibilitychange',
      () => {
        if (document.hidden) window.clearTimeout(timer)
      },
      { once: true },
    )
  } else {
    window.open(fallbackUrl(lng, lat, name), '_blank')
  }
}
