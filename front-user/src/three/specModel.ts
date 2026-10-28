import * as THREE from 'three'
import { modelData } from './types'

/**
 * 前端通用 spec 执行器（img2threejs 融合的运行时版）：
 * 视觉 LLM 从真实图片提取的结构化 spec（几何体组合/颜色/材质/动画）→ THREE.Group。
 * 纯数据驱动（JSON 不是代码），无任何动态执行，安全可控；
 * spec 无效时抛错，由调用方回退预设模型库。
 */

export interface SpecPrimitive {
  type: 'box' | 'cylinder' | 'sphere' | 'cone' | 'plane'
  size?: [number, number, number]
  radius?: number
  height?: number
  pos?: [number, number, number]
  rot?: [number, number, number]
  color?: string
  roughness?: number
  metalness?: number
  transparent?: boolean
  opacity?: number
  /** 材质模拟：纸/塑料/金属/玻璃（PBR 参数 + 纤维/微噪凹凸贴图） */
  surface?: 'none' | 'paper' | 'plastic' | 'metal' | 'glass'
  /** box 六面贴图材质（+X,-X,+Y,-Y,+Z,-Z） */
  materials?: FaceMaterial[]
  /** 侧面环绕标签 */
  label?: SideLabel
}

/** 面/侧面纹理绘制元素（region 归一化 0-1，左上原点，按声明顺序绘制） */
export interface DecalItem {
  type: 'text' | 'circle' | 'rect' | 'stripe'
  text?: string
  color?: string
  region?: [number, number, number, number]
  /** 文字高度占 region 高度比例 */
  fontSize?: number
  /** rect 描边模式（条码框/标签框） */
  border?: boolean
}

/** box 单面贴图材质 */
export interface FaceMaterial {
  bg?: string
  items?: DecalItem[]
}

/** 侧面环绕标签（cylinder/cone/lathe/tube 侧面纹理） */
export interface SideLabel {
  bg?: string
  items?: DecalItem[]
}

export interface ModelSpec {
  /** 物品名（如 牛奶盒） */
  item?: string
  primitives: SpecPrimitive[]
  /** 车削体：轮廓点 [半径,高度] 绕 Y 轴旋转（瓶身/杯身曲线） */
  lathes?: LatheSpec[]
  /** 管道：路径点 + 半径（弯曲吸管/把手） */
  tubes?: TubeSpec[]
  /** 表面贴花：文字/logo/图标（CanvasTexture 绘制） */
  decals?: DecalSpec[]
  /** 整体包围盒比例 [宽,高,深]（高=1） */
  aspect?: [number, number, number]
  /** 待机动画：float 浮沉 / sway 摇摆 / spin 旋转 / none 静止 */
  tick?: 'none' | 'float' | 'sway' | 'spin'
}

export interface LatheSpec {
  points: [number, number][]
  pos?: [number, number, number]
  rot?: [number, number, number]
  color?: string
  roughness?: number
  metalness?: number
  transparent?: boolean
  opacity?: number
  surface?: 'none' | 'paper' | 'plastic' | 'metal' | 'glass'
  label?: SideLabel
}

export interface TubeSpec {
  curve: [number, number, number][]
  radius: number
  pos?: [number, number, number]
  rot?: [number, number, number]
  color?: string
  roughness?: number
  metalness?: number
  transparent?: boolean
  opacity?: number
  surface?: 'none' | 'paper' | 'plastic' | 'metal' | 'glass'
}

export interface DecalSpec {
  /** 文字内容（OCR 近似） */
  text?: string
  /** text 文字 / circle 圆形标 / rect 边框 / stripe 色条 */
  shape?: 'text' | 'circle' | 'rect' | 'stripe'
  color?: string
  /** 底色 #rrggbb 或 transparent */
  bg?: string
  pos?: [number, number, number]
  rot?: [number, number, number]
  size?: [number, number]
  /** 文字高度（模型比例） */
  fontSize?: number
}

const FALLBACK_COLOR = 0xc8c8c8

/** '#rrggbb' / 'rrggbb' → number，非法回退中性灰 */
function toColor(hex?: string): number {
  const s: string = (hex ?? '').trim()
  if (!s) return FALLBACK_COLOR
  const m = /^#?([0-9a-f]{6})$/i.exec(s)
  return m ? parseInt(m[1]!, 16) : FALLBACK_COLOR
}

interface Placeable {
  pos?: [number, number, number]
  rot?: [number, number, number]
}

interface StandardMat {
  color?: string
  roughness?: number
  metalness?: number
  transparent?: boolean
  opacity?: number
  /** 材质模拟：纸/塑料/金属/玻璃 */
  surface?: 'none' | 'paper' | 'plastic' | 'metal' | 'glass'
}

/** 模块级缓存：凹凸贴图（纸纤维 / 塑料微噪），复用避免重复生成 */
let paperBump: THREE.CanvasTexture | null = null
let plasticBump: THREE.CanvasTexture | null = null

function makeBumpTexture(kind: 'paper' | 'plastic'): THREE.CanvasTexture {
  const W = 512
  const c = document.createElement('canvas')
  c.width = W
  c.height = W
  const ctx = c.getContext('2d')
  if (!ctx) throw new Error('canvas 2d 不可用')
  if (kind === 'paper') {
    // 纸盒纤维纹理：横向纤维细线 + 噪点（油墨印刷的细微凹凸感）
    ctx.fillStyle = '#808080'
    ctx.fillRect(0, 0, W, W)
    for (let i = 0; i < 1400; i++) {
      const y = Math.random() * W
      const len = 30 + Math.random() * 160
      ctx.strokeStyle = Math.random() > 0.5 ? '#8c8c8c' : '#747474'
      ctx.lineWidth = 0.4 + Math.random() * 0.9
      ctx.beginPath()
      ctx.moveTo(Math.random() * W, y)
      ctx.lineTo(Math.random() * W + len, y)
      ctx.stroke()
    }
  } else {
    // 塑料微凹凸：细密噪点
    const img = ctx.createImageData(W, W)
    for (let i = 0; i < img.data.length; i += 4) {
      const v = 128 + (Math.random() - 0.5) * 40
      img.data[i] = v
      img.data[i + 1] = v
      img.data[i + 2] = v
      img.data[i + 3] = 255
    }
    ctx.putImageData(img, 0, 0)
  }
  const tex = new THREE.CanvasTexture(c)
  tex.wrapS = THREE.RepeatWrapping
  tex.wrapT = THREE.RepeatWrapping
  return tex
}

function getBump(kind: 'paper' | 'plastic'): THREE.CanvasTexture {
  if (kind === 'paper') return (paperBump ??= makeBumpTexture('paper'))
  return (plasticBump ??= makeBumpTexture('plastic'))
}

/** 按 surface 构建 PBR 材质：纸（sheen+纤维凹凸）/塑料（clearcoat+微凹凸）/金属/玻璃（transmission 半透明） */
function makeMaterial(m: StandardMat): THREE.Material {
  const surface = m.surface ?? 'none'
  const base = {
    color: toColor(m.color),
    roughness: m.roughness ?? 0.6,
    metalness: m.metalness ?? 0,
  }
  const mat = new THREE.MeshPhysicalMaterial(base)
  if (surface === 'paper') {
    // 纸盒：轻微反光 + 纤维纹理 + 油墨凹凸
    mat.roughness = m.roughness ?? 0.72
    mat.sheen = 0.5
    mat.sheenColor = new THREE.Color(0xffffff)
    mat.sheenRoughness = 0.65
    mat.bumpMap = getBump('paper')
    mat.bumpScale = 0.02
  } else if (surface === 'plastic') {
    // 塑料：微光泽 + 细密凹凸
    mat.roughness = m.roughness ?? 0.32
    mat.clearcoat = 0.3
    mat.clearcoatRoughness = 0.45
    mat.bumpMap = getBump('plastic')
    mat.bumpScale = 0.008
  } else if (surface === 'metal') {
    // 金属：高反射
    mat.metalness = m.metalness ?? 0.85
    mat.roughness = m.roughness ?? 0.35
    mat.clearcoat = 0.15
  } else if (surface === 'glass') {
    // 玻璃/透明塑料（PET 瓶、玻璃杯）：分两种光学模型
    // ① 薄壁透明件（PET 瓶/薄膜，opacity <= 0.6）：用简单透明（transparent+opacity），
    //    与 plasticBottle.ts 一致；避免 transmission 在无 GPU/软件渲染下变成黑块。
    // ② 厚玻璃（opacity > 0.6）：用 transmission 模拟折射。
    mat.metalness = 0
    const opacity = m.opacity ?? 0.85
    if (opacity <= 0.6) {
      mat.transparent = true
      mat.roughness = m.roughness ?? 0.08
      mat.clearcoat = 1.0
      mat.clearcoatRoughness = 0.05
      mat.envMapIntensity = 1.2
      mat.side = THREE.DoubleSide
      // 对接近白色/浅灰的薄壁透明件，使用纯白+更高透明度，避免软件渲染下呈现浑浊灰膜
      const c = new THREE.Color(base.color)
      const brightness = (c.r + c.g + c.b) / 3
      if (brightness > 0.82) {
        mat.color.setHex(0xffffff)
        mat.opacity = Math.max(0.12, opacity * 0.7)
      } else {
        mat.opacity = opacity
      }
    } else {
      // 厚玻璃：使用 transmission 实现真实半透明效果（参考 glass-mug-3d.html）
      mat.roughness = m.roughness ?? 0.1
      mat.transmission = Math.min(opacity, 0.95)  // opacity 映射到 transmission
      mat.ior = 1.5  // 玻璃折射率
      mat.thickness = 1.5  // 折射深度
      mat.clearcoat = 0.1
      mat.clearcoatRoughness = 0.2
      mat.specularIntensity = 0.8
      mat.transparent = true
      mat.opacity = opacity
      mat.premultipliedAlpha = true
      mat.depthWrite = false  // 防止透明物体遮挡问题
      mat.side = THREE.DoubleSide  // 双面渲染确保透明效果正确
    }
  }
  if (m.transparent && surface !== 'glass') {
    mat.transparent = true
    // 非玻璃透明件（塑料膜等）：允许半透明，但设置下限防止完全不可见
    mat.opacity = Math.max(m.opacity ?? 0.95, 0.3)
    mat.depthWrite = false  // 防止透明物体遮挡问题
  }
  return mat
}

/** 面/侧面纹理画布：底色 + items 按序绘制（品牌/文字/图案/条码区） */
function drawDecalCanvas(spec: FaceMaterial | SideLabel, W: number, H: number): THREE.CanvasTexture {
  const c = document.createElement('canvas')
  c.width = W
  c.height = H
  const ctx = c.getContext('2d')
  if (!ctx) throw new Error('canvas 2d 不可用')
  const bg = (spec.bg ?? '').trim().toLowerCase()
  if (bg && bg !== 'transparent') {
    ctx.fillStyle = bg
    ctx.fillRect(0, 0, W, H)
  }
  // 分层绘制（LLM items 输出顺序不可控，后画的会盖住先画的）：
  // 背景类（stripe 色条/实心 rect 色块）→ 边框/圆形标 → 文字最后，保证文字不被色块覆盖
  const layers = [
    (spec.items ?? []).filter((it) => it.type === 'stripe' || (it.type === 'rect' && !it.border)),
    (spec.items ?? []).filter((it) => it.type === 'circle' || (it.type === 'rect' && it.border)),
    (spec.items ?? []).filter((it) => it.type === 'text'),
  ]
  for (const layer of layers) {
    for (const it of layer) {
      const [rx, ry, rw, rh] = it.region ?? [0, 0, 1, 0.2]
      const x = rx * W
      const y = ry * H
      const w = rw * W
      const h = rh * H
      const color = (it.color ?? '#333333').trim()
      if (it.type === 'stripe') {
        ctx.fillStyle = color
        ctx.fillRect(x, y, w, h)
      } else if (it.type === 'circle') {
        ctx.fillStyle = color
        ctx.beginPath()
        ctx.arc(x + w / 2, y + h / 2, Math.min(w, h) / 2, 0, Math.PI * 2)
        ctx.fill()
      } else if (it.type === 'rect') {
        if (it.border) {
          ctx.strokeStyle = color
          ctx.lineWidth = Math.max(2, h * 0.08)
          ctx.strokeRect(x, y, w, h)
        } else {
          ctx.fillStyle = color
          ctx.fillRect(x, y, w, h)
        }
      } else if (it.type === 'text' && it.text) {
        // 字号先按 region 高度定，再自动缩小适配 region 宽度：长文字若超宽会溢出画布
        // 边缘被裁掉（视觉上=漏字），因此测量后逐级缩小到能放下为止
        let px = Math.max(12, Math.min(h, (it.fontSize ?? 0.6) * h))
        ctx.font = `700 ${px}px "Microsoft YaHei", "PingFang SC", sans-serif`
        while (px > 12 && ctx.measureText(it.text).width > w) {
          px -= 1
          ctx.font = `700 ${px}px "Microsoft YaHei", "PingFang SC", sans-serif`
        }
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillStyle = color
        ctx.fillText(it.text, x + w / 2, y + h / 2)
      }
    }
  }
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.anisotropy = 4
  return tex
}

/** 给网格附加侧面环绕标签（cylinder/cone/tube：横向环绕展开画布）。
 * 注意：lathe（瓶身/杯身）不再走这里，而是走 buildLatheLabelMesh，避免标签贴图覆盖整个瓶身高度被拉伸。 */
function applyLabel(mesh: THREE.Mesh, label?: SideLabel | null): void {
  if (!label) return
  const mat = mesh.material as THREE.MeshStandardMaterial
  const clone = mat.clone()
  clone.map = drawDecalCanvas(label, 1024, 256)
  clone.needsUpdate = true
  mesh.material = clone
}

/** 根据 lathe 轮廓点 [r, y]，插值得到指定高度 y 处的半径 */
function latheRadiusAt(pts: [number, number][], y: number): number {
  // 找到相邻两点
  for (let i = 0; i < pts.length - 1; i++) {
    const a = pts[i]
    const b = pts[i + 1]
    if (!a || !b) continue
    const [r1, y1] = a
    const [r2, y2] = b
    if ((y1 <= y && y <= y2) || (y2 <= y && y <= y1)) {
      if (Math.abs(y2 - y1) < 1e-6) return (r1 + r2) / 2
      return r1 + (r2 - r1) * ((y - y1) / (y2 - y1))
    }
  }
  // 超出范围：取最近端点
  const first = pts[0]
  if (first && y < first[1]) return first[0]
  const last = pts[pts.length - 1]
  return last ? last[0] : 0
}

/** 为 lathe（瓶身/杯身）构建独立标签带 mesh。
 *
 * 设计目标：
 * 1. 标签只覆盖瓶身中部的标签带区域，不覆盖整个瓶身高度 → 避免文字纵向拉伸。
 * 2. 标签带半径贴合瓶身实际轮廓（取标签带中心高度处的半径 + 小偏移），
 *    而不是用最大半径包裹整个瓶身 → 避免肩部/颈部被错误遮挡。
 * 3. 标签材质始终透明（alphaTest），只在有背景色/文字的区域显示，
 *    其余区域透明显示瓶身 → 标签带不会把整圈瓶身变成实心蓝色圆柱。
 * 4. 画布宽高比按标签带实际曲面比例计算（周长/标签带高度），文字不变形。
 */
function buildLatheLabelMesh(l: LatheSpec): THREE.Mesh | null {
  if (!l.label) return null
  const pts = l.points
  if (!pts || pts.length < 2) return null

  const yMin = Math.min(...pts.map((p) => p[1]))
  const yMax = Math.max(...pts.map((p) => p[1]))
  const bottleHeight = yMax - yMin
  if (bottleHeight <= 0) return null

  // 从 label items 的 region 计算「标签带」在画布 y 上的范围（0-1，左上原点）。
  // 标签带是瓶身中部的带状区域，不应覆盖整个瓶身；用更小的 padding 并限制最大高度。
  let items = l.label.items ?? []
  let labelYStart = 0.3
  let labelYEnd = 0.7
  if (items.length > 0) {
    const yMins = items.map((it) => it.region?.[1] ?? 0.5)
    const yMaxs = items.map((it) => (it.region?.[1] ?? 0.5) + (it.region?.[3] ?? 0.2))
    labelYStart = Math.max(0, Math.min(...yMins) - 0.03)
    labelYEnd = Math.min(1, Math.max(...yMaxs) + 0.03)
  }
  // 标签带高度上限约 0.55（瓶身 55%）。
  // 当 item 纵向跨度超过上限时，整体缩放 item（保持相对位置，避免裁剪导致重叠/截断），
  // 而不是简单向中心裁剪标签带。这样所有文字/图案都能落在标签带内，正面可读。
  const MAX_LABEL_BAND = 0.55
  const BAND_PAD = 0.03
  if (labelYEnd - labelYStart > MAX_LABEL_BAND) {
    const center = (labelYStart + labelYEnd) / 2
    const contentSpan = labelYEnd - labelYStart - BAND_PAD * 2
    const targetContentSpan = MAX_LABEL_BAND - BAND_PAD * 2
    const scale = Math.max(0.5, targetContentSpan / contentSpan)
    items = items.map(({ region, ...rest }) => {
      const [rx, ry, rw, rh] = region ?? [0, 0.5, 1, 0.2]
      return { ...rest, region: [rx, center + (ry - center) * scale, rw, rh * scale] as [number, number, number, number] }
    })
    // 根据缩放后的 item 重新计算标签带范围（缩放后 region 必有值，`?.` 仅兜底类型）
    const sYmins = items.map((it) => it.region?.[1] ?? 0.5)
    const sYmaxs = items.map((it) => (it.region?.[1] ?? 0.5) + (it.region?.[3] ?? 0.2))
    labelYStart = Math.max(0, Math.min(...sYmins) - BAND_PAD)
    labelYEnd = Math.min(1, Math.max(...sYmaxs) + BAND_PAD)
    // 兜底：仍可能因边界余量略微超上限，再次夹紧
    if (labelYEnd - labelYStart > MAX_LABEL_BAND) {
      const c2 = (labelYStart + labelYEnd) / 2
      labelYStart = Math.max(0, c2 - MAX_LABEL_BAND / 2)
      labelYEnd = Math.min(1, labelYStart + MAX_LABEL_BAND)
    }
  }
  if (labelYEnd <= labelYStart) return null

  // 画布 y 范围 → 瓶身 y 坐标（画布 y=0 顶部对应瓶身 yMax，y=1 底部对应 yMin）
  const labelBottleYTop = yMax - labelYStart * bottleHeight
  const labelBottleYBottom = yMax - labelYEnd * bottleHeight
  const labelHeight = Math.max(0.02, labelBottleYTop - labelBottleYBottom)
  const labelCenterY = (labelBottleYTop + labelBottleYBottom) / 2

  // 标签带半径：取标签带中心高度处的瓶身实际半径 + 小偏移（防 z-fighting，同时避免缝隙）
  const baseRadius = latheRadiusAt(pts as [number, number][], labelCenterY)
  if (baseRadius <= 0) return null
  const labelRadius = baseRadius * 1.003

  // 标签圆柱：开放端（openEnded=true），与瓶身同轴。
  // thetaStart 经实测校准：让 canvas x=0.5（标签文字通常水平居中）落在 +Z 正面中央。
  // 默认值使主视觉偏向右侧；π 让 front 视角下标签居中，避免文字偏到侧面/背面。
  const labelGeo = new THREE.CylinderGeometry(
    labelRadius, labelRadius, labelHeight, 64, 1, true,
    Math.PI,
  )

  // 画布宽高比按实际曲面比例计算：周长 / 标签带高度
  const circumference = 2 * Math.PI * baseRadius
  const surfaceAspect = circumference / labelHeight
  // 高度固定 256，宽度按周长比例；避免过高导致文字被纵向压缩
  const canvasH = 256
  const canvasW = Math.max(512, Math.min(2048, Math.round(canvasH * surfaceAspect)))

  // 重映射 items 的 region：从「整瓶画布 (labelYStart~labelYEnd)」→「标签带画布 (0~1)」
  const bandHeight = labelYEnd - labelYStart
  const remappedLabel: SideLabel = {
    bg: l.label.bg,
    items: items.map((it) => {
      const rx = it.region?.[0] ?? 0
      const ry = it.region?.[1] ?? 0.5
      const rw = it.region?.[2] ?? 1
      const rh = it.region?.[3] ?? 0.2
      const newRy = bandHeight > 0 ? (ry - labelYStart) / bandHeight : 0
      const newRh = bandHeight > 0 ? rh / bandHeight : 1
      const clampedRy = Math.max(0, Math.min(0.95, newRy))
      const clampedRh = Math.max(0.05, Math.min(1 - clampedRy, newRh))
      return { ...it, region: [rx, clampedRy, rw, clampedRh] as [number, number, number, number] }
    }),
  }

  const bg = (l.label.bg ?? '').trim().toLowerCase()
  // 透明背景：空、transparent、none，或 #rrggbbaa / #rgba 的 alpha=0
  const isTransparentBg = bg === '' || bg === 'transparent' || bg === 'none' || /^#([0-9a-f]{8}|[0-9a-f]{4})$/.test(bg) && (() => {
    const hex = bg.slice(1)
    if (hex.length === 8) return parseInt(hex.slice(6, 8), 16) === 0
    return parseInt(hex.slice(3, 4), 16) === 0
  })()

  // 标签材质：
  // - 透明背景：alphaTest 裁剪空白区域，只保留文字/色块，depthWrite=false 避免遮挡透明瓶身。
  // - 有色/不透明背景：按真实印刷标签处理，不透明（opacity=1.0, depthWrite=true），
  //   确保标签能盖住后面的透明瓶身/液体，文字对比度清晰。
  // 使用 MeshBasicMaterial：标签印刷颜色应忠实还原，避免深色在软件渲染/弱光下变成黑块。
  const labelMat = isTransparentBg
    ? new THREE.MeshBasicMaterial({
        map: drawDecalCanvas(remappedLabel, canvasW, canvasH),
        side: THREE.DoubleSide,
        transparent: true,
        alphaTest: 0.05,
        depthWrite: false,
        opacity: 1.0,
      })
    : new THREE.MeshBasicMaterial({
        map: drawDecalCanvas(remappedLabel, canvasW, canvasH),
        side: THREE.DoubleSide,
        transparent: false,
        opacity: 1.0,
        depthWrite: true,
      })

  const labelMesh = new THREE.Mesh(labelGeo, labelMat)
  labelMesh.name = 'lathe-label'
  const px = l.pos?.[0] ?? 0
  const py = l.pos?.[1] ?? 0
  const pz = l.pos?.[2] ?? 0
  // CylinderGeometry 中心在原点 → position.y = 瓶身 pos.y + 标签带中心 y
  labelMesh.position.set(px, py + labelCenterY, pz)
  return labelMesh
}

/** box 六面贴图：给定面用材质纹理，缺省面复用主体材质 */
function buildBoxMaterials(p: SpecPrimitive, base: THREE.MeshStandardMaterial): THREE.Material[] {
  const mats: THREE.Material[] = []
  for (let i = 0; i < 6; i++) {
    const fm = p.materials?.[i]
    const hasItems = (fm?.items?.length ?? 0) > 0
    const hasBg = fm?.bg && fm.bg.trim().toLowerCase() !== 'transparent'
    if (fm && (hasItems || hasBg)) {
      const m = base.clone()
      m.map = drawDecalCanvas(fm, 512, 512)
      m.needsUpdate = true
      mats.push(m)
    } else {
      mats.push(base)
    }
  }
  return mats
}

/** 贴花 canvas 绘制：底色/色条/圆形标/边框 + 居中文字（内容与颜色取自真实图片） */
function makeDecalTexture(d: DecalSpec): THREE.CanvasTexture {
  const W = 512
  const H = 256
  const c = document.createElement('canvas')
  c.width = W
  c.height = H
  const ctx = c.getContext('2d')
  if (!ctx) throw new Error('canvas 2d 不可用')
  const shape = d.shape ?? 'text'
  const bg = (d.bg ?? '').trim().toLowerCase()
  if (bg && bg !== 'transparent') {
    ctx.fillStyle = bg
    ctx.fillRect(0, 0, W, H)
  }
  const color = (d.color ?? '#333333').trim()
  if (shape === 'circle') {
    // 圆形 logo 标：实心圆 + 反白文字
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(W / 2, H / 2, Math.min(W, H) * 0.38, 0, Math.PI * 2)
    ctx.fill()
  } else if (shape === 'rect') {
    ctx.strokeStyle = color
    ctx.lineWidth = Math.max(4, W * 0.02)
    ctx.strokeRect(W * 0.06, H * 0.14, W * 0.88, H * 0.72)
  } else if (shape === 'stripe') {
    // 色条（标签带/瓶盖环/装饰带）
    ctx.fillStyle = color
    ctx.fillRect(0, 0, W, H * 0.34)
  }
  const text = d.text?.trim()
  if (text) {
    const sizeH = d.size?.[1] ?? 0.15
    // fontSize 为模型比例 → 换算为画布像素（0.9 系数防溢出）
    const px = Math.min(H * 0.85, Math.max(16, Math.round(((d.fontSize ?? 0.06) / sizeH) * H * 0.9)))
    ctx.font = `600 ${px}px "Microsoft YaHei", "PingFang SC", sans-serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    // 圆形标内文字反白，其余用主题色
    ctx.fillStyle = shape === 'circle' ? '#ffffff' : color
    ctx.fillText(text, W / 2, shape === 'stripe' ? H * 0.17 : H / 2)
  }
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.anisotropy = 4
  return tex
}

function buildLatheGeometry(l: LatheSpec): THREE.BufferGeometry | null {
  const pts = l.points.map(([r, y]) => new THREE.Vector2(r, y))
  if (pts.length < 2) return null
  return new THREE.LatheGeometry(pts, 48)
}

function buildTubeGeometry(t: TubeSpec): THREE.BufferGeometry | null {
  const path = t.curve.map(([x, y, z]) => new THREE.Vector3(x, y, z))
  if (path.length < 2) return null
  return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(path), 48, t.radius, 12)
}

function place(mesh: THREE.Object3D, p: Placeable) {
  if (p.pos) mesh.position.set(p.pos[0] ?? 0, p.pos[1] ?? 0, p.pos[2] ?? 0)
  if (p.rot) mesh.rotation.set(p.rot[0] ?? 0, p.rot[1] ?? 0, p.rot[2] ?? 0)
}

function buildGeometry(p: SpecPrimitive): THREE.BufferGeometry | null {
  switch (p.type) {
    case 'box': {
      const s = p.size ?? [0.5, 0.5, 0.5]
      return new THREE.BoxGeometry(s[0] ?? 0.5, s[1] ?? 0.5, s[2] ?? 0.5)
    }
    case 'cylinder': {
      const r = p.radius ?? 0.25
      return new THREE.CylinderGeometry(r, r, p.height ?? 1, 32)
    }
    case 'sphere':
      return new THREE.SphereGeometry(p.radius ?? 0.5, 32, 24)
    case 'cone':
      return new THREE.ConeGeometry(p.radius ?? 0.25, p.height ?? 1, 32)
    case 'plane': {
      const s = p.size ?? [1, 1, 0]
      return new THREE.PlaneGeometry(s[0] ?? 1, s[1] ?? 1)
    }
    default:
      return null
  }
}

/** spec → THREE.Group（spec 为空/无有效几何体时抛错，调用方回退预设模型库） */
export function buildModelFromSpec(spec: ModelSpec): THREE.Group {
  const hasBody =
    (spec?.primitives?.length ?? 0) > 0 ||
    (spec?.lathes?.length ?? 0) > 0 ||
    (spec?.tubes?.length ?? 0) > 0
  if (!spec || !hasBody) {
    throw new Error('3d spec 为空')
  }
  const root = new THREE.Group()
  root.name = `spec-${spec.item ?? 'model'}`
  const parts = new THREE.Group()
  parts.name = 'body'
  root.add(parts)

  spec.primitives?.forEach((p, i) => {
    const geo = buildGeometry(p)
    if (!geo) return
    const mat = makeMaterial(p)
    const mesh = new THREE.Mesh(geo, p.type === 'box' && p.materials?.length ? buildBoxMaterials(p, mat as THREE.MeshStandardMaterial) : mat)
    mesh.name = `part-${i}`
    place(mesh, p)
    if (p.type !== 'box') applyLabel(mesh, p.label)
    parts.add(mesh)
  })

  spec.lathes?.forEach((l, i) => {
    const geo = buildLatheGeometry(l)
    if (!geo) return
    const mesh = new THREE.Mesh(geo, makeMaterial(l))
    mesh.name = `lathe-${i}`
    place(mesh, l)
    parts.add(mesh)
    // 标签作为独立带状 mesh（ CylinderGeometry 包裹在瓶身外侧）：
    // 避免标签贴图直接覆盖整个瓶身高度导致文字纵向拉伸失真；
    // 画布宽高比按实际曲面比例计算，文字不变形。
    const labelMesh = buildLatheLabelMesh(l)
    if (labelMesh) parts.add(labelMesh)
  })

  spec.tubes?.forEach((t, i) => {
    const geo = buildTubeGeometry(t)
    if (!geo) return
    const mesh = new THREE.Mesh(geo, makeMaterial(t))
    mesh.name = `tube-${i}`
    place(mesh, t)
    parts.add(mesh)
  })

  spec.decals?.forEach((d, i) => {
    const s = d.size ?? [0.4, 0.15]
    const mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(s[0] ?? 0.4, s[1] ?? 0.15),
      new THREE.MeshStandardMaterial({
        map: makeDecalTexture(d),
        transparent: true,
        roughness: 0.7,
        // 偏移深度缓冲防与表面 z-fighting
        polygonOffset: true,
        polygonOffsetFactor: 1,
        polygonOffsetUnits: 1,
      }),
    )
    mesh.name = `decal-${i}`
    place(mesh, d)
    parts.add(mesh)
  })

  if (parts.children.length === 0) {
    throw new Error('3d spec 无有效几何体')
  }

  modelData(root).label = spec.item || 'AI 构建模型'
  const tick = spec.tick ?? 'float'
  if (tick !== 'none') {
    // 待机动画（img2threejs animation-ready 理念）：float 浮沉 / sway 摇摆 / spin 旋转
    modelData(root).tick = (t) => {
      if (tick === 'spin') {
        parts.rotation.y = t * 0.6
      } else if (tick === 'sway') {
        parts.rotation.z = Math.sin(t * 1.2) * 0.08
      } else {
        parts.position.y = Math.sin(t * 1.15) * 0.035
        parts.rotation.z = Math.sin(t * 0.85) * 0.035
        parts.rotation.x = Math.cos(t * 0.7) * 0.02
      }
    }
  }
  return root
}
