<template>
  <div class="fluid-bg">
    <!-- 降级渐变层：始终存在，WebGL 未就绪或不可用时直接显示 -->
    <div class="fluid-bg__fallback" :style="fallbackStyle"></div>
    <!-- WebGL 流体层：初始化完成后淡入，覆盖在 fallback 之上 -->
    <canvas
      ref="canvasRef"
      class="fluid-bg__canvas"
      :class="{ 'is-ready': ready }"
    ></canvas>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { initFluidSimulation } from '@/utils/fluid'
import { getSavedTheme, type ThemeMode } from '@shared/theme'

const canvasRef = ref<HTMLCanvasElement | null>(null)
const fallback = ref(false)
/** WebGL 初始化完成前不显示 canvas，初始化后淡入覆盖 fallback */
const ready = ref(false)
/** WebGL 不可用时的降级渐变（随主题，避免亮色渐变盖住深色背景） */
const fallbackStyle = ref<{ background: string }>({ background: '' })
let cleanup: (() => void) | null = null
let handleResize: (() => void) | null = null
let themeObserver: MutationObserver | null = null
let onVisibilityRef: (() => void) | null = null
let initializedOnce = false

/**
 * 各主题流体配色（与 theme.scss 同系：深色主题下用可见的主题绿，
 * 既能看出流动又不刺眼、不盖住背景；亮色主题保持品牌绿蓝）：
 * - light:   环保绿 + 科技蓝（品牌默认）
 * - dark:    近黑绿 + 深主题绿（黑底上的绿色流动）
 */
const THEME_COLORS: Record<
  ThemeMode,
  { color1: [number, number, number]; color2: [number, number, number] }
> = {
  light: {
    color1: [47 / 255, 107 / 255, 94 / 255], // #2f6b5e 墨绿主色
    color2: [61 / 255, 139 / 255, 133 / 255], // #3d8b85 青蓝绿
  },
  dark: {
    color1: [13 / 255, 24 / 255, 17 / 255], // #0d1811 近黑绿
    color2: [26 / 255, 80 / 255, 50 / 255], // #1a5032 深主题绿
  },
}

/** 按当前主题生成降级渐变（与流体配色同源） */
function themeGradient(): string {
  const { color1, color2 } = THEME_COLORS[getSavedTheme()]
  const c1 = color1.map((v) => Math.round(v * 255)).join(', ')
  const c2 = color2.map((v) => Math.round(v * 255)).join(', ')
  return `linear-gradient(135deg, rgb(${c1}) 0%, rgb(${c2}) 100%)`
}

/** 按当前主题启动/重建流体（重建前先释放旧渲染器） */
function startFluid() {
  if (!canvasRef.value) return
  if (cleanup) {
    cleanup()
    cleanup = null
  }

  let canvas = canvasRef.value
  // 只有非首次重建且旧上下文已丢失时，才需要替换 DOM 换一张新画布；
  // 首次初始化直接用 Vue 创建的 canvas，避免 DOM 替换造成的闪烁。
  if (initializedOnce) {
    // 旧画布上下文已被 dispose() 的 WEBGL_lose_context 永久丢弃，
    // 重建必须换一张新画布，否则 getContext 会拿到已丢失的旧上下文
    const next = document.createElement('canvas')
    next.className = canvas.className
    next.style.cssText = canvas.style.cssText
    // 复制全部属性（含 Vue scoped 样式的 data-v-*），否则新画布拿不到
    // .fluid-bg__canvas 的 fixed/100vw 布局样式，会退化成左上角 300x150 小方块
    for (const attr of Array.from(canvas.attributes)) {
      if (attr.name !== 'style') next.setAttribute(attr.name, attr.value)
    }
    canvas.replaceWith(next)
    canvasRef.value = next
    canvas = next
  }

  const { color1, color2 } = THEME_COLORS[getSavedTheme()]
  cleanup = initFluidSimulation(canvas, { color1, color2 })
  initializedOnce = true
  // WebGL 初始化成功后再淡入显示 canvas，避免初始透明期"背景消失"；
  // 引擎内部失败时返回 no-op 清理函数，canvas 保持透明、降级渐变可见
  ready.value = true
}

onMounted(() => {
  if (!canvasRef.value) return

  // Resize canvas to match window
  handleResize = () => {
    if (canvasRef.value) {
      canvasRef.value.width = window.innerWidth
      canvasRef.value.height = window.innerHeight
    }
  }
  window.addEventListener('resize', handleResize)
  handleResize()

  // 先按当前主题设置降级渐变（无 WebGL 时立即生效）
  fallbackStyle.value.background = themeGradient()

  // Check WebGL support
  const gl = canvasRef.value.getContext('webgl2') || canvasRef.value.getContext('webgl')
  if (!gl) {
    fallback.value = true
    return
  }

  startFluid()

  // 主题切换（PcNav 按钮）时同步重建流体配色/降级渐变，避免亮色与深色主题割裂
  themeObserver = new MutationObserver(() => {
    fallbackStyle.value.background = themeGradient()
    if (!fallback.value) startFluid()
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

  // Chrome 会对隐藏标签页丢弃 WebGL 上下文；回到前台时若上下文已丢失，
  // 换新画布重建（重建后引擎内部会自行恢复动画）
  const onVisibility = () => {
    if (document.visibilityState !== 'visible') return
    const canvas = canvasRef.value
    if (!canvas) return
    const gl = canvas.getContext('webgl2') || canvas.getContext('webgl')
    if (gl && gl.isContextLost()) {
      ready.value = false
      startFluid()
    }
  }
  document.addEventListener('visibilitychange', onVisibility)
  onVisibilityRef = onVisibility
})

onUnmounted(() => {
  if (cleanup) cleanup()
  if (themeObserver) themeObserver.disconnect()
  if (onVisibilityRef) document.removeEventListener('visibilitychange', onVisibilityRef)
  if (handleResize) window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.fluid-bg {
  width: 100vw;
  height: 100vh;
  position: fixed;
  top: 0;
  left: 0;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
}

.fluid-bg__fallback,
.fluid-bg__canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* WebGL 流体层：初始隐藏，就绪后淡入覆盖 fallback，避免背景"一闪而过" */
.fluid-bg__canvas {
  opacity: 0;
  transition: opacity 0.5s ease;
}

.fluid-bg__canvas.is-ready {
  opacity: 1;
}
</style>
