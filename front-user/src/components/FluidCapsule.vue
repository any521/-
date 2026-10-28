<template>
  <div
    ref="rootRef"
    class="fluid-capsule"
    :class="[`fluid-capsule--${theme}`, { 'is-static': reducedMotion }]"
    :style="{ borderRadius: rounded }"
    @pointerenter="onPointerEnter"
    @pointerleave="onPointerLeave"
    @pointermove="onPointerMove"
  >
    <canvas
      v-if="!isLite && !webglFailed"
      ref="canvasRef"
      class="fluid-capsule__canvas"
      aria-hidden="true"
    ></canvas>
    <div v-else class="fluid-capsule__fallback" aria-hidden="true"></div>
    <div class="fluid-capsule__content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { createFluidRenderer, type FluidHandle, type RGB } from '@/utils/fluid/engine'

type ThemeName = 'eco' | 'carbon' | 'gold' | 'forest' | 'ocean' | 'dusk'

/** 6 套主题三色(RGB 0-1):主色 / 中间色 / 高光色 */
const THEME_COLORS: Record<ThemeName, [RGB, RGB, RGB]> = {
  eco: [
    [0.18, 0.42, 0.37], // #2f6b5e 品牌墨绿
    [0.44, 0.63, 0.58], // 浅墨绿
    [0.78, 0.87, 0.84], // 纸面高光
  ],
  carbon: [
    [0.24, 0.55, 0.52], // #3d8b85 青蓝绿
    [0.4, 0.68, 0.64], // 湖青
    [0.72, 0.87, 0.85], // 冰青高光
  ],
  gold: [
    [0.85, 0.65, 0.2], // #d9a534 金琥珀
    [0.96, 0.79, 0.37], // 蜜金
    [0.99, 0.92, 0.66], // 香槟高光
  ],
  forest: [
    [0.06, 0.35, 0.2], // 深林绿
    [0.16, 0.55, 0.33], // 苔绿
    [0.45, 0.78, 0.52], // 雾绿高光
  ],
  ocean: [
    [0.08, 0.55, 0.66], // 青蓝
    [0.2, 0.74, 0.83], // 湖蓝
    [0.62, 0.9, 0.93], // 泡沫高光
  ],
  dusk: [
    [0.45, 0.28, 0.65], // 暮紫
    [0.91, 0.5, 0.38], // 落橙
    [0.98, 0.76, 0.52], // 霞光高光
  ],
}

const BASE_SPEED = 0.22
const HOVER_SPEED = 0.5

const props = withDefaults(
  defineProps<{
    theme?: ThemeName
    /** 悬停加速 + 移动搅动,默认 true */
    interactive?: boolean
    /** 纯 CSS 降级模式;默认自动:matchMedia('(max-width:1023px)') 命中时为 true */
    lite?: boolean
    /** 左侧白色蒙版宽度 0..1,默认 0.45 */
    maskLeft?: number
    /** 胶囊圆角,默认 28px */
    rounded?: string
  }>(),
  {
    theme: 'eco',
    interactive: true,
    lite: undefined,
    maskLeft: 0.45,
    rounded: '28px',
  },
)

const rootRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const webglFailed = ref(false)

const isMobile = ref(
  typeof window !== 'undefined' && window.matchMedia('(max-width:1023px)').matches,
)
const reducedMotion = ref(
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches,
)

/** lite 模式:显式传参优先,否则按媒体查询自动判定 */
const isLite = computed(() => props.lite ?? isMobile.value)

let handle: FluidHandle | null = null
let io: IntersectionObserver | null = null

function setupRenderer() {
  if (isLite.value || !canvasRef.value) return
  handle = createFluidRenderer(canvasRef.value, {
    colors: THEME_COLORS[props.theme],
    baseSpeed: BASE_SPEED,
    maskLeft: props.maskLeft,
    resolutionScale: 0.5,
    dprCap: 1.5,
  })
  if (!handle) {
    // 无 WebGL:降级为 CSS 渐变
    webglFailed.value = true
    return
  }
  if (reducedMotion.value) {
    // prefers-reduced-motion:渲染一帧静态画面后暂停
    requestAnimationFrame(() => handle?.pause())
  }
}

function teardownRenderer() {
  handle?.dispose()
  handle = null
}

// 主题切换:重建渲染器(uniform 三色随主题变化)
watch(
  () => props.theme,
  () => {
    if (isLite.value) return
    teardownRenderer()
    setupRenderer()
  },
)

// ---- 交互:悬停加速 + 移动搅动 ----
let lastX = 0
let lastY = 0
let lastT = 0

function pointerNdc(e: PointerEvent): [number, number] {
  const rect = rootRef.value?.getBoundingClientRect()
  if (!rect || rect.width === 0 || rect.height === 0) return [0, 0]
  const nx = ((e.clientX - rect.left) / rect.width) * 2 - 1
  const ny = ((e.clientY - rect.top) / rect.height) * 2 - 1
  return [nx, ny]
}

function onPointerEnter(e: PointerEvent) {
  if (!props.interactive || reducedMotion.value) return
  handle?.setSpeed(HOVER_SPEED)
  lastX = e.clientX
  lastY = e.clientY
  lastT = performance.now()
}

function onPointerLeave() {
  if (!props.interactive || reducedMotion.value) return
  handle?.setSpeed(BASE_SPEED)
}

function onPointerMove(e: PointerEvent) {
  if (!props.interactive || reducedMotion.value || !handle) return
  const now = performance.now()
  const dt = Math.max(now - lastT, 1)
  const dist = Math.hypot(e.clientX - lastX, e.clientY - lastY)
  const velocity = dist / dt // px / ms
  lastX = e.clientX
  lastY = e.clientY
  lastT = now
  // 指针位移速度 → 搅动强度(引擎内部封顶约 4x baseSpeed)
  const strength = Math.min(velocity * 0.8, 2)
  if (strength <= 0) return
  const [nx, ny] = pointerNdc(e)
  handle.stir(nx, ny, strength)
}

onMounted(() => {
  setupRenderer()

  // 离屏 pause / 入屏 resume
  if (rootRef.value && typeof IntersectionObserver !== 'undefined') {
    io = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        if (!entry || !handle) return
        if (entry.isIntersecting) {
          if (!reducedMotion.value) handle.resume()
        } else {
          handle.pause()
        }
      },
      { threshold: 0.01 },
    )
    io.observe(rootRef.value)
  }
})

onUnmounted(() => {
  io?.disconnect()
  io = null
  teardownRenderer()
})
</script>

<style scoped>
.fluid-capsule {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  border: 1px solid var(--df-glass-border);
  box-shadow: var(--df-glass-shadow);
}

.fluid-capsule__canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  display: block;
}

/* 玻璃高光:顶部线性渐变高光带 + 内阴影(位于 canvas 之上、内容之下) */
.fluid-capsule::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.38) 0%,
    rgba(255, 255, 255, 0.08) 28%,
    rgba(255, 255, 255, 0) 45%
  );
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    inset 0 -10px 20px rgba(0, 0, 0, 0.08);
}

.fluid-capsule__content {
  position: relative;
  z-index: 1;
}

/* ---- lite / 无 WebGL 降级:CSS 渐变 + 缓慢背景位漂移 ---- */
.fluid-capsule__fallback {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-size: 220% 220%;
  animation: capsule-drift 18s ease-in-out infinite alternate;
}

.is-static .fluid-capsule__fallback {
  animation: none;
}

.fluid-capsule--eco .fluid-capsule__fallback {
  background-image: linear-gradient(120deg, #2f6b5e, #6fa093 45%, #c8ddd6);
}

.fluid-capsule--carbon .fluid-capsule__fallback {
  background-image: linear-gradient(120deg, #3d8b85, #5faaa4 45%, #b9dcd8);
}

.fluid-capsule--gold .fluid-capsule__fallback {
  background-image: linear-gradient(120deg, #d9a534, #f5c95e 45%, #fceba8);
}

.fluid-capsule--forest .fluid-capsule__fallback {
  background-image: linear-gradient(120deg, #0f5933, #298c54 45%, #73c785);
}

.fluid-capsule--ocean .fluid-capsule__fallback {
  background-image: linear-gradient(120deg, #148ca8, #33bdd4 45%, #9ee5ee);
}

.fluid-capsule--dusk .fluid-capsule__fallback {
  background-image: linear-gradient(120deg, #7347a6, #e88061 45%, #fac285);
}

@keyframes capsule-drift {
  0% {
    background-position: 0% 30%;
  }
  50% {
    background-position: 60% 70%;
  }
  100% {
    background-position: 100% 40%;
  }
}
</style>
