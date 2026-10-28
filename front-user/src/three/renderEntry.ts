// ============================================================
// 服务端渲染页构建专用入口（Track G · extreme 渲染闭环）
// 产物=ai-back/app/services/render3d/render_page/spec-executor.iife.js
// （由 ai-back/app/services/render3d/build_render_page.ps1 用 esbuild 打包；
//   本文件仅服务端渲染设施使用，不参与前端应用构建产物）
// 页面暴露 window.renderSpec(specJson) → string[]（3 张 512×512 PNG dataURL）
// 灯光/背景/归一化与 ThreeViewer.vue 对齐（ACES toneMapping + RoomEnvironment + maxDim→1.55）
// 纯数据驱动（JSON spec，非代码），无任何动态执行，无代码注入面
// ============================================================
import * as THREE from 'three'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { buildModelFromSpec, type ModelSpec } from './specModel'

const VIEW_SIZE = 512
/** 与 ThreeViewer 默认相机位距一致（position (0, 0.55, 3.4) 的模长） */
const CAM_DIST = Math.sqrt(0.55 * 0.55 + 3.4 * 3.4)

/** 固定视角：front +Z / three-quarter (1,0.8,1) / top +Y（方向向量，实际距离=模长归一化×CAM_DIST） */
const VIEWS: ReadonlyArray<{ name: string; dir: [number, number, number]; up?: [number, number, number] }> = [
  { name: 'front', dir: [0, 0.55, 3.4] },
  { name: 'three-quarter', dir: [1, 0.8, 1] },
  // top 视角 up 取 -Z：避免相机方向与默认 up(0,1,0) 平行导致 lookAt 退化
  { name: 'top', dir: [0, 1, 0.0001], up: [0, 0, -1] },
]

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let pmrem: THREE.PMREMGenerator | null = null
let envTexture: THREE.Texture | null = null
let stage: THREE.Group | null = null

function ensureScene(): boolean {
  if (renderer && scene && camera && stage) return true
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  } catch {
    return false
  }
  renderer.setPixelRatio(1)
  renderer.setSize(VIEW_SIZE, VIEW_SIZE)
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  document.body.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  // 环境光照（RoomEnvironment + PMREM），与 ThreeViewer 一致
  pmrem = new THREE.PMREMGenerator(renderer)
  envTexture = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  scene.environment = envTexture

  camera = new THREE.PerspectiveCamera(40, 1, 0.1, 60)
  stage = new THREE.Group()
  scene.add(stage)
  return true
}

/** 归一化到统一展示尺寸并居中（与 ThreeViewer.frameModel 一致） */
function frameModel(root: THREE.Group): void {
  const box = new THREE.Box3().setFromObject(root)
  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  const maxDim = Math.max(size.x, size.y, size.z) || 1
  const scale = 1.55 / maxDim
  root.scale.setScalar(scale)
  root.position.set(-center.x * scale, -center.y * scale, -center.z * scale)
}

/** 释放模型几何/材质（GPU 内存），复用同一 renderer 渲染多轮 */
function disposeRoot(root: THREE.Object3D): void {
  root.traverse((o) => {
    const mesh = o as THREE.Mesh
    if (mesh.geometry) mesh.geometry.dispose()
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    mats.forEach((m) => {
      if (!m) return
      const sm = m as THREE.MeshStandardMaterial
      sm.map?.dispose()
      sm.bumpMap?.dispose()
      m.dispose()
    })
  })
}

function normToCamDist(dir: [number, number, number]): [number, number, number] {
  const l = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]) || 1
  return [(dir[0] / l) * CAM_DIST, (dir[1] / l) * CAM_DIST, (dir[2] / l) * CAM_DIST]
}

/** 渲染 spec → 3 张 PNG dataURL（front/three-quarter/top）；spec 无效抛错由上层兜底 */
function renderSpec(specJson: string): string[] {
  if (!ensureScene() || !renderer || !scene || !camera || !stage) {
    throw new Error('WebGL 初始化失败')
  }
  const r = renderer
  const sc = scene
  const cam = camera
  const st = stage
  const spec = JSON.parse(specJson) as ModelSpec
  const root = buildModelFromSpec(spec) // spec 无有效几何体抛错 → 上层按渲染失败处理
  frameModel(root)
  st.add(root)
  try {
    return VIEWS.map((v) => {
      const [x, y, z] = normToCamDist(v.dir)
      cam.position.set(x, y, z)
      if (v.up) cam.up.set(v.up[0], v.up[1], v.up[2])
      else cam.up.set(0, 1, 0)
      cam.lookAt(0, 0, 0)
      r.render(sc, cam)
      return r.domElement.toDataURL('image/png')
    })
  } finally {
    st.remove(root)
    disposeRoot(root)
  }
}

declare global {
  interface Window {
    renderSpec: (specJson: string) => string[]
  }
}
window.renderSpec = renderSpec
