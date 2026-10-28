// ============================================================
// useReveal — 滚动触发的渐入动画（IntersectionObserver）
// 用法: <div v-reveal> 或 const { el } = useReveal()
// 共享 CSS 见 styles/index.scss .reveal / .reveal--up
// ============================================================
import { ref, type Directive, type DirectiveBinding, type ObjectDirective } from 'vue'

/** 默认阈值 */
const DEFAULT_THRESHOLD = 0.15

/** 是否 reduced-motion（全局判定一次，后续监听变化） */
let reduced = false
if (typeof window !== 'undefined') {
  reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', (e) => {
    reduced = e.matches
  })
}

function observe(el: HTMLElement, threshold: number = DEFAULT_THRESHOLD) {
  if (reduced) {
    // 无动画偏好：直接显示
    el.classList.add('is-revealed')
    return
  }
  const io = new IntersectionObserver(
    ([entry]) => {
      if (entry?.isIntersecting) {
        el.classList.add('is-revealed')
        io.unobserve(el)
      }
    },
    { threshold },
  )
  io.observe(el)
  // 存引用以便 cleanup
  ;(el as any)._revealIO = io
}

function unobserve(el: HTMLElement) {
  const io = (el as any)._revealIO as IntersectionObserver | undefined
  io?.unobserve(el)
}

/**
 * v-reveal 指令:元素进入视口时添加 .is-revealed 类
 * 用法: <div v-reveal> 或 <div v-reveal="0.1">（自定义阈值）
 */
export const vReveal: ObjectDirective<HTMLElement, number | undefined> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<number | undefined>) {
    observe(el, binding.value ?? DEFAULT_THRESHOLD)
  },
  unmounted(el: HTMLElement) {
    unobserve(el)
  },
}

/**
 * useReveal — 组合式 API 用法
 * 返回 { el, isRevealed }，在模板中绑定 el 到 ref
 *
 * @example
 * const { el } = useReveal(0.1)
 * <div :ref="el">内容</div>
 */
export function useReveal(threshold: number = DEFAULT_THRESHOLD) {
  const isRevealed = ref(false)

  function el(node: HTMLElement | null) {
    if (!node) return
    if (reduced) {
      isRevealed.value = true
      return
    }
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          isRevealed.value = true
          io.unobserve(node)
        }
      },
      { threshold },
    )
    io.observe(node)
  }

  return { el, isRevealed }
}