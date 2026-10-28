<script setup lang="ts">
// ============================================================
// 3D 模型查看器(Track G · img2threejs 程序化模型库)
// props { model?: string } —— 品类 code/中文名/ModelKey,兜底默认模型
// 自管 renderer + OrbitControls + RAF + 完整 dispose
// ============================================================
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import {
  createModel,
  disposeGroup,
  modelData,
  resolveModelKey,
  MODEL_LABELS,
  DEFAULT_MODEL,
  type ModelKey,
} from '@/three/registry'
import { buildModelFromSpec, type ModelSpec } from '@/three/specModel'

interface Props {
  /** 品类 code('plastic')/ 中文名('塑料类')/ ModelKey;缺省走默认模型 */
  model?: string
  /** AI 构建的 3D spec（img2threejs 风格，视觉 LLM 从真实图片提取）；有效时优先于 model，无效/缺失回退预设模型库 */
  spec?: ModelSpec | null
}

const props = withDefaults(defineProps<Props>(), { model: '', spec: null })

const containerRef = ref<HTMLDivElement>()
const webglFailed = ref(false)

/** 当前生效的模型 key(用于提示条) */
const activeKey = ref<ModelKey>(resolveModelKey(props.model))
const isFallback = computed(() => !props.model)
/** spec 是否含可构建几何体（primitives/lathes/tubes 任一非空） */
const hasSpecBody = computed(
  () =>
    !!props.spec?.primitives?.length ||
    !!props.spec?.lathes?.length ||
    !!props.spec?.tubes?.length,
)
const isAiBuild = computed(() => hasSpecBody.value)
const activeLabel = computed(() => props.spec?.item || MODEL_LABELS[activeKey.value])

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let pmrem: THREE.PMREMGenerator | null = null
let envTexture: THREE.Texture | null = null
let current: THREE.Group | null = null
let stage: THREE.Group | null = null

let rafId = 0
let running = false
let clock: THREE.Clock | null = null
let elapsed = 0
let resizeObserver: ResizeObserver | null = null
let intersectionObserver: IntersectionObserver | null = null
let onVisibility: (() => void) | null = null
let resumeTimer: ReturnType<typeof setTimeout> | null = null

/** 将模型归一化到统一展示尺寸并居中 */
function frameModel(root: THREE.Group): void {
  const box = new THREE.Box3().setFromObject(root)
  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  const maxDim = Math.max(size.x, size.y, size.z) || 1
  const scale = 1.55 / maxDim
  root.scale.setScalar(scale)
  root.position.set(-center.x * scale, -center.y * scale, -center.z * scale)
}

function swapModel(key: ModelKey): void {
  if (!stage) return
  if (current) {
    stage.remove(current)
    disposeGroup(current)
    current = null
  }
  // AI spec 优先（img2threejs 重建即代码）；spec 无效时回退预设模型库
  let next: THREE.Group
  const spec = props.spec
  if (spec && hasSpecBody.value) {
    try {
      next = buildModelFromSpec(spec)
    } catch {
      next = createModel(key)
    }
  } else {
    next = createModel(key)
  }
  frameModel(next)
  stage.add(next)
  current = next
  activeKey.value = key
}

function renderLoop(): void {
  if (!running || !renderer || !scene || !camera || !controls) return
  rafId = requestAnimationFrame(renderLoop)
  const dt = clock ? clock.getDelta() : 0.016
  elapsed += dt
  if (current) modelData(current).tick?.(elapsed, dt)
  controls.update()
  renderer.render(scene, camera)
}

function startLoop(): void {
  if (running || !renderer) return
  running = true
  if (!clock) clock = new THREE.Clock()
  else clock.start()
  rafId = requestAnimationFrame(renderLoop)
}

function stopLoop(): void {
  running = false
  if (rafId) cancelAnimationFrame(rafId)
  rafId = 0
  clock?.stop()
}

function initScene(): boolean {
  const container = containerRef.value
  if (!container) return false

  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  } catch {
    return false
  }

  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75))
  renderer.setSize(container.clientWidth, container.clientHeight)
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  container.appendChild(renderer.domElement)

  scene = new THREE.Scene()

  // 环境光照(RoomEnvironment + PMREM,替代手动灯光)
  pmrem = new THREE.PMREMGenerator(renderer)
  envTexture = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  scene.environment = envTexture

  camera = new THREE.PerspectiveCamera(
    40,
    container.clientWidth / Math.max(container.clientHeight, 1),
    0.1,
    60,
  )
  camera.position.set(0, 0.55, 3.4)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.autoRotate = true
  controls.autoRotateSpeed = 1.1
  controls.minDistance = 1.6
  controls.maxDistance = 8
  controls.maxPolarAngle = Math.PI * 0.62
  controls.target.set(0, 0, 0)
  // 用户拖拽时暂停自动旋转,松手 1.6s 后恢复
  controls.addEventListener('start', () => {
    if (!controls) return
    controls.autoRotate = false
    if (resumeTimer) clearTimeout(resumeTimer)
  })
  controls.addEventListener('end', () => {
    resumeTimer = setTimeout(() => {
      if (controls) controls.autoRotate = true
    }, 1600)
  })

  stage = new THREE.Group()
  scene.add(stage)
  swapModel(resolveModelKey(props.model))

  // 容器尺寸自适应(替代 window resize 监听)
  resizeObserver = new ResizeObserver(() => {
    if (!renderer || !camera || !containerRef.value) return
    const w = containerRef.value.clientWidth
    const h = containerRef.value.clientHeight
    if (w === 0 || h === 0) return
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h)
  })
  resizeObserver.observe(container)

  // 离屏暂停
  intersectionObserver = new IntersectionObserver(
    (entries) => {
      const visible = entries[0]?.isIntersecting ?? true
      if (visible && !document.hidden) startLoop()
      else stopLoop()
    },
    { threshold: 0.05 },
  )
  intersectionObserver.observe(container)

  // 后台标签页暂停
  onVisibility = () => {
    if (document.hidden) stopLoop()
    else startLoop()
  }
  document.addEventListener('visibilitychange', onVisibility)

  startLoop()
  return true
}

watch(
  () => [props.model, props.spec],
  () => {
    if (!stage) return
    swapModel(resolveModelKey(props.model))
  },
)

onMounted(() => {
  if (!initScene()) webglFailed.value = true
})

onBeforeUnmount(() => {
  stopLoop()
  if (resumeTimer) clearTimeout(resumeTimer)
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  if (onVisibility) document.removeEventListener('visibilitychange', onVisibility)
  controls?.dispose()
  if (current) {
    disposeGroup(current)
    current = null
  }
  envTexture?.dispose()
  pmrem?.dispose()
  if (renderer) {
    renderer.dispose()
    renderer.forceContextLoss()
    renderer.domElement.remove()
    renderer = null
  }
  scene = null
  camera = null
  controls = null
  stage = null
})
</script>

<template>
  <div class="three-viewer">
    <div v-if="webglFailed" class="viewer-fallback">
      <SvgIcon name="common-cube" :size="48" color="var(--df-text-secondary)" />
      <p>当前设备不支持 3D 预览</p>
    </div>
    <template v-else>
      <div ref="containerRef" class="viewer-canvas" />
      <div class="viewer-chip">
        <SvgIcon name="common-cube" :size="14" color="var(--df-color-primary)" />
        <span>{{ activeLabel }} · 3D 纪念模型</span>
        <em v-if="isFallback">默认</em>
        <em v-if="isAiBuild">AI 构建</em>
      </div>
      <div class="viewer-hint">拖拽旋转 · 滚轮缩放</div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.three-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 300px;
  border-radius: var(--df-radius-lg);
  overflow: hidden;
  background:
    radial-gradient(ellipse 90% 70% at 50% 30%, rgba(47, 107, 94, 0.10), transparent 65%),
    radial-gradient(ellipse 70% 55% at 50% 85%, rgba(61, 139, 133, 0.08), transparent 60%);
}

.viewer-canvas {
  position: absolute;
  inset: 0;

  :deep(canvas) {
    display: block;
    width: 100%;
    height: 100%;
    touch-action: none;
  }
}

.viewer-chip {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: var(--df-radius-round);
  background: var(--df-glass-bg-strong);
  backdrop-filter: blur(8px);
  border: 1px solid var(--df-glass-border);
  font-size: var(--df-font-size-sm);
  color: var(--df-text-primary);
  pointer-events: none;

  em {
    font-style: normal;
    font-size: var(--df-font-size-xs);
    color: var(--df-color-gold);
    border: 1px solid currentColor;
    border-radius: var(--df-radius-sm);
    padding: 0 4px;
  }
}

.viewer-hint {
  position: absolute;
  bottom: 10px;
  right: 14px;
  font-size: var(--df-font-size-xs);
  color: var(--df-text-secondary);
  pointer-events: none;
}

.viewer-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
  min-height: 300px;
  color: var(--df-text-secondary);
  background: var(--df-bg-page);
}
</style>
