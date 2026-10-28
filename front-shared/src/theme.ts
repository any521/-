export type ThemeMode = 'light' | 'dark'

import { ref } from 'vue'

export const THEME_STORAGE_KEY = 'df_theme'

/** 主题循环顺序：切换按钮按此顺序轮转 */
export const THEME_MODES: readonly ThemeMode[] = ['light', 'dark']

/** 主题显示名（切换按钮 / 选择器文案） */
export const THEME_LABELS: Record<ThemeMode, string> = {
  light: '亮色',
  dark: '深色',
}

function isThemeMode(value: string | null): value is ThemeMode {
  return value === 'light' || value === 'dark'
}

export function getSavedTheme(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY)
    return isThemeMode(saved) ? saved : 'light'
  } catch {
    return 'light'
  }
}

/**
 * 当前主题响应式状态（模块级单例）：顶栏 / 我的页 / 选择器等所有切换入口共享，
 * 任一处切换后其余入口的图标与文案实时同步，避免各页面持有独立 ref 造成不一致。
 */
export const themeState = ref<ThemeMode>(getSavedTheme())

export function applyTheme(theme: ThemeMode, persist = true): void {
  themeState.value = theme
  document.documentElement.dataset.theme = theme
  // Element Plus 官方暗色变量靠 html.dark 类触发（admin 端已引入 dark/css-vars.css），
  // 与 data-theme 属性同步切换，保证表格/弹窗/下拉等全部组件暗色化
  document.documentElement.classList.toggle('dark', theme === 'dark')
  // 墨绿为深色系，跟随深色控件配色（滚动条 / 表单控件等）
  document.documentElement.style.colorScheme = theme === 'light' ? 'light' : 'dark'

  if (!persist) return
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // Storage may be unavailable in private or embedded browsing contexts.
  }
}

/** 切换主题并持久化（供主题切换按钮调用；刷新后由 index.html 内联脚本恢复） */
export function setTheme(theme: ThemeMode): void {
  applyTheme(theme, true)
}

/** 当前主题的下一个主题（三套主题轮转） */
export function getNextTheme(theme: ThemeMode): ThemeMode {
  return THEME_MODES[(THEME_MODES.indexOf(theme) + 1) % THEME_MODES.length] ?? 'light'
}

export function toggleTheme(): ThemeMode {
  const next = getNextTheme(getSavedTheme())
  applyTheme(next, true)
  return next
}

/** 动画进行中标志：避免快速连点叠加多个动画，动画期间再次切换直接生效 */
let revealInProgress = false

/** 清理 View Transitions 动画注入的类与变量 */
function clearRevealMarkers(): void {
  const root = document.documentElement
  root.classList.remove('df-reveal-collapse')
  root.style.removeProperty('--df-reveal-x')
  root.style.removeProperty('--df-reveal-y')
  root.style.removeProperty('--df-reveal-end')
  revealInProgress = false
}

/**
 * 主题切换收缩动画（View Transitions）：旧主题快照从屏幕四周向切换按钮圆心收缩消失，
 * 新主题完整界面（背景 + 内容）随之从四周覆盖全屏。
 * 不支持 View Transitions 时降级为纯色圆形扩散；异常时直接切换。
 */
export function applyThemeWithReveal(theme: ThemeMode, originX: number, originY: number): void {
  if (typeof document === 'undefined') {
    applyTheme(theme)
    return
  }
  const root = document.documentElement
  if (root.dataset.theme === theme) return
  if (revealInProgress) {
    applyTheme(theme)
    return
  }

  // 优先 View Transitions：收缩的是完整新主题界面（背景 + 内容），而非纯色
  if (typeof document.startViewTransition === 'function' && document.body) {
    revealInProgress = true
    const maxR = Math.hypot(
      Math.max(originX, window.innerWidth - originX),
      Math.max(originY, window.innerHeight - originY),
    )
    root.style.setProperty('--df-reveal-x', `${originX}px`)
    root.style.setProperty('--df-reveal-y', `${originY}px`)
    root.style.setProperty('--df-reveal-end', `${maxR}px`)
    root.classList.add('df-reveal-collapse')

    let transition: { finished: Promise<void>; ready: Promise<void> } | null = null
    try {
      transition = document.startViewTransition(() => {
        applyTheme(theme)
      })
    } catch {
      // startViewTransition 可能同步抛出（如正在执行中），走清理与直接切换
    }
    if (!transition) {
      clearRevealMarkers()
      applyTheme(theme)
      return
    }
    // 安全兜底：1.2s 后强制清理（防止 finished promise 未触发导致标志位卡死）
    const safetyTimer = window.setTimeout(clearRevealMarkers, 1200)
    transition.finished.then(
      () => { window.clearTimeout(safetyTimer); clearRevealMarkers() },
      () => { window.clearTimeout(safetyTimer); clearRevealMarkers() },
    )
    return
  }

  // 降级：不支持 View Transitions 时用纯色圆形扩散（旧方案）
  applyThemeWithSolidReveal(theme, originX, originY)
}

/**
 * 纯色圆形扩散（View Transitions 不可用时的降级）：从 origin 坐标向外扩散目标主题背景色，
 * 动画进行到约 80% 时真正切换主题，末尾露出新主题真实内容，动画结束移除遮罩。
 */
function applyThemeWithSolidReveal(theme: ThemeMode, originX: number, originY: number): void {
  const root = document.documentElement
  if (root.dataset.theme === theme) return
  if (typeof root.animate !== 'function' || !document.body || revealInProgress) {
    applyTheme(theme)
    return
  }

  // 读目标主题背景色：临时切换 data-theme 同步读取后立即恢复（同帧内不触发绘制，无闪烁）
  const prev = root.dataset.theme
  root.dataset.theme = theme
  let bg = ''
  try {
    bg = getComputedStyle(root).getPropertyValue('--df-bg-page').trim()
  } catch {
    // 读取失败走默认色
  }
  if (prev === undefined) delete root.dataset.theme
  else root.dataset.theme = prev

  const overlay = document.createElement('div')
  overlay.style.cssText =
    `position:fixed;inset:0;z-index:2147483647;pointer-events:none;background:${bg || '#ffffff'};`
  document.body.appendChild(overlay)
  revealInProgress = true

  const maxR = Math.hypot(
    Math.max(originX, window.innerWidth - originX),
    Math.max(originY, window.innerHeight - originY),
  )
  const DURATION = 650
  const SWITCH_AT = DURATION * 0.8

  try {
    const anim = overlay.animate(
      [
        { clipPath: `circle(0px at ${originX}px ${originY}px)` },
        { clipPath: `circle(${maxR}px at ${originX}px ${originY}px)` },
      ],
      { duration: DURATION, easing: 'cubic-bezier(0.5, 0, 0.25, 1)', fill: 'forwards' },
    )
    window.setTimeout(() => applyTheme(theme), SWITCH_AT)
    anim.finished
      .then(() => {
        overlay.remove()
        revealInProgress = false
      })
      .catch(() => {
        overlay.remove()
        revealInProgress = false
      })
  } catch {
    overlay.remove()
    revealInProgress = false
    applyTheme(theme)
  }
}
