<script setup lang="ts">
// ============================================================
// HeatCity3D（D1）：Three.js 3D 回收热力柱状网格（Agent 驾驶舱）
// 对接 C 的 F8 驾驶舱接口 heatGrid（x/y 片区网格坐标，v 热度 0-100）
// ============================================================
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

const props = withDefaults(
  defineProps<{ grid: { x: number; y: number; v: number }[]; height?: string }>(),
  { height: '420px' },
)

const el = ref<HTMLDivElement>()

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let barsGroup: THREE.Group | null = null
let rafId = 0
let observer: ResizeObserver | null = null

// 热度 → 颜色：低 绿(品牌) → 高 金
const lowColor = new THREE.Color('#2f6b5e')
const highColor = new THREE.Color('#e8bc3f')

function buildBars(grid: { x: number; y: number; v: number }[]) {
  if (!scene) return
  if (barsGroup) {
    scene.remove(barsGroup)
    barsGroup.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose()
        ;(obj.material as THREE.Material).dispose()
      }
    })
  }
  barsGroup = new THREE.Group()
  const size = 10 // 网格 10x10，居中摆放
  for (const cell of grid) {
    const h = Math.max(cell.v / 10, 0.1)
    const geo = new THREE.BoxGeometry(0.7, h, 0.7)
    const color = lowColor.clone().lerp(highColor, cell.v / 100)
    const mat = new THREE.MeshStandardMaterial({ color, transparent: true, opacity: 0.92 })
    const bar = new THREE.Mesh(geo, mat)
    bar.position.set(cell.x - size / 2 + 0.5, h / 2, cell.y - size / 2 + 0.5)
    barsGroup.add(bar)
  }
  scene.add(barsGroup)
}

onMounted(() => {
  const container = el.value!
  scene = new THREE.Scene()
  scene.background = new THREE.Color('#1d3a31')

  camera = new THREE.PerspectiveCamera(50, container.clientWidth / container.clientHeight, 0.1, 200)
  camera.position.set(10, 11, 12)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.setSize(container.clientWidth, container.clientHeight)
  container.appendChild(renderer.domElement)

  scene.add(new THREE.AmbientLight(0xffffff, 0.7))
  const dirLight = new THREE.DirectionalLight(0xffffff, 1.4)
  dirLight.position.set(8, 16, 8)
  scene.add(dirLight)

  // 地面网格线（片区坐标参考）
  const gridHelper = new THREE.GridHelper(10, 10, 0x1f4d6e, 0x123048)
  scene.add(gridHelper)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.autoRotate = true
  controls.autoRotateSpeed = 0.8
  controls.maxPolarAngle = Math.PI / 2.2

  buildBars(props.grid)

  const animate = () => {
    rafId = requestAnimationFrame(animate)
    controls?.update()
    renderer?.render(scene!, camera!)
  }
  animate()

  observer = new ResizeObserver(() => {
    if (!renderer || !camera) return
    camera.aspect = container.clientWidth / container.clientHeight
    camera.updateProjectionMatrix()
    renderer.setSize(container.clientWidth, container.clientHeight)
  })
  observer.observe(container)
})

watch(() => props.grid, buildBars)

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  observer?.disconnect()
  controls?.dispose()
  renderer?.dispose()
  renderer?.domElement.remove()
  renderer = null
  scene = null
  camera = null
})
</script>

<template>
  <div ref="el" class="heat-city" :style="{ height }" />
</template>

<style scoped>
.heat-city {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}
</style>
