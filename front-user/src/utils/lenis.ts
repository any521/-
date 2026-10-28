// ============================================================
// Lenis 单例（仅 PC 端使用）
// 路由 afterEach 调用 scrollTo(0, { immediate: true }) 修复
// keep-alive 导致滚动位置错乱；prefers-reduced-motion 时跳过。
// ============================================================
import Lenis from 'lenis'

let instance: Lenis | null = null
let rafId: number | null = null

const reduced =
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

export function initLenis(): Lenis | null {
  if (instance || reduced) return instance

  instance = new Lenis({
    duration: 1.2,
    easing: (t: number) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
    orientation: 'vertical',
    smoothWheel: true,
  })

  function raf(time: number) {
    instance?.raf(time)
    rafId = requestAnimationFrame(raf)
  }
  rafId = requestAnimationFrame(raf)

  return instance
}

export function destroyLenis(): void {
  if (rafId !== null) cancelAnimationFrame(rafId)
  rafId = null
  instance?.destroy()
  instance = null
}

export function getLenis(): Lenis | null {
  return instance
}

/** 路由切换后重置滚动位置（G3 修复 keep-alive 滚动错乱） */
export function scrollToTop(): void {
  if (instance) {
    instance.scrollTo(0, { immediate: true })
  }
}