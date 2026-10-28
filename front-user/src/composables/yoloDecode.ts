// ============================================================
// YOLO→ONNX 解码公共层（cam02 独占域 C6-6 的共享化抽取）：
// AR 实时流（useEdgeDetect）与上传图片/视频识别
// （useStaticDetect/useLiveVideoDetect）共用同一套 预处理/解码/NMS，
// 输出契约冻结 [1, 4+C, N] YOLOv8 系格式；模型资产仍由
// /models/manifest.json 单一入口版本管理。
// ============================================================
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import type * as OrtNS from 'onnxruntime-web'
import { configureOrtAssets } from './ortAssets'

export type Box4 = [number, number, number, number]

/** 模型资产清单（/public/models/manifest.json，发版即换 version+file） */
export interface ModelManifest {
  version: string
  /** 模型文件名（相对 /models/），YOLO 导出 ONNX，输出 [1, 4+C, N] */
  file: string
  /** 方形输入边长（letterbox 缩放） */
  inputSize: number
  /** 实时档专用轻量模型文件名（相对 /models/，如 416 输入版）；缺省实时档复用 file */
  liveFile?: string
  /** 实时档输入边长；缺省复用 inputSize */
  liveInputSize?: number
  scoreThreshold: number
  iouThreshold: number
  /** 类别下标 → 品类 code（对齐 biz_category / RECYCLE_CATEGORIES） */
  labels: string[]
}

export const MANIFEST_URL = `${import.meta.env.BASE_URL}models/manifest.json`

/** 解码/NMS 后的原始检测（实时流的 trackId/stable 由帧间关联层分配） */
export interface RawDetection {
  category: string
  categoryName: string
  confidence: number
  /** 归一化框选区域 [x, y, w, h] */
  box: Box4
  /** 投放建议 */
  advice: string
}

export type InferBackend = 'webgpu' | 'wasm' | 'none'

/** 能力探测：WebGPU 优先，WASM 兜底（上传识别不依赖摄像头，单独判断算力后端） */
export function detectBackend(): InferBackend {
  if ('gpu' in navigator) return 'webgpu'
  if (typeof WebAssembly === 'object') return 'wasm'
  return 'none'
}

/** 加载模型资产（manifest → ONNX Session）；失败抛错（调用方捕获置 error 提示具体原因） */
export async function loadModelSession(backend: InferBackend): Promise<{
  session: OrtNS.InferenceSession
  manifest: ModelManifest
  ort: typeof OrtNS
}> {
  try {
    const res = await fetch(MANIFEST_URL, { cache: 'no-cache' })
    if (!res.ok) throw new Error(`manifest ${res.status}`)
    const manifest = (await res.json()) as ModelManifest
    const ort = await import('onnxruntime-web')
    // wasm 资产本地化（src/assets/ort/，见 ortAssets）：不走 CDN，配合 COOP/COEP 可开 WASM 多线程
    configureOrtAssets()
    const providers = backend === 'webgpu' ? ['webgpu', 'wasm'] : ['wasm']
    const session = await ort.InferenceSession.create(`${import.meta.env.BASE_URL}models/${manifest.file}`, {
      executionProviders: providers,
      graphOptimizationLevel: 'all',
      // 仅 Error 级日志：shape 类算子固定走 CPU 属 ORT 预期降级（EP 分配 W 级提示为正常输出），无需刷屏
      logSeverityLevel: 3,
    })
    return { session, manifest, ort }
  } catch (err) {
    // 失败原因向上抛（调用方置 error 供页面提示）：manifest 404/HTML 假 200、
    // wasm/mjs MIME 错误、COOP/COEP 缺失等都可在此暴露具体 message
    console.error('[edge] loadModelSession 失败:', err)
    throw err
  }
}

// ---- letterbox 预处理（模块级 canvas 复用，避免每帧重建） ----
let sharedCanvas: HTMLCanvasElement | null = null

/**
 * 图像源 → letterbox 方形 NCHW Float32（0-1 归一化）。
 * 源支持 ImageBitmap / HTMLImageElement / HTMLVideoElement（实时流帧、上传图片、视频抽帧共用）。
 */
export async function sourceToTensor(
  ort: typeof OrtNS,
  source: ImageBitmap | HTMLImageElement | HTMLVideoElement,
  size: number,
): Promise<OrtNS.Tensor | null> {
  const srcW =
    source instanceof HTMLVideoElement ? source.videoWidth : source instanceof HTMLImageElement ? source.naturalWidth : source.width
  const srcH =
    source instanceof HTMLVideoElement ? source.videoHeight : source instanceof HTMLImageElement ? source.naturalHeight : source.height
  if (!srcW || !srcH) return null
  if (!sharedCanvas) sharedCanvas = document.createElement('canvas')
  sharedCanvas.width = size
  sharedCanvas.height = size
  const ctx = sharedCanvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return null
  const scale = Math.min(size / srcW, size / srcH)
  const w = srcW * scale
  const h = srcH * scale
  ctx.fillStyle = '#727272'
  ctx.fillRect(0, 0, size, size)
  // createImageBitmap 在浏览器内部线程解码，主线程只等 Promise（失败回退同步 drawImage）
  try {
    const bmp = await createImageBitmap(source)
    ctx.drawImage(bmp, (size - w) / 2, (size - h) / 2, w, h)
    bmp.close()
  } catch {
    ctx.drawImage(source, (size - w) / 2, (size - h) / 2, w, h)
  }
  const { data } = ctx.getImageData(0, 0, size, size)
  const plane = size * size
  const f = new Float32Array(plane * 3)
  for (let i = 0, i4 = 0; i < plane; i++, i4 += 4) {
    f[i] = data[i4]! / 255
    f[i + plane] = data[i4 + 1]! / 255
    f[i + plane * 2] = data[i4 + 2]! / 255
  }
  return new ort.Tensor('float32', f, [1, 3, size, size])
}

/**
 * letterbox 逆变换：模型输出的归一化框（letterbox 方形坐标系）→ 原图坐标系归一化框。
 * 上传图片/视频识别用于把检测框精确叠回原始媒体（实时流无此需求）。
 */
export function boxToSource(box: Box4, srcW: number, srcH: number, size: number): Box4 {
  if (!srcW || !srcH) return box
  const scale = Math.min(size / srcW, size / srcH)
  const dw = (size - srcW * scale) / 2
  const dh = (size - srcH * scale) / 2
  const clamp = (v: number) => Math.min(Math.max(v, 0), 1)
  const x = clamp((box[0] * size - dw) / scale / srcW)
  const y = clamp((box[1] * size - dh) / scale / srcH)
  const w = clamp((box[2] * size) / scale / srcW)
  const h = clamp((box[3] * size) / scale / srcH)
  return [x, y, Math.min(w, 1 - x), Math.min(h, 1 - y)]
}

/** 两归一化框 IoU */
export function boxIoU(a: Box4, b: Box4): number {
  const x1 = Math.max(a[0], b[0])
  const y1 = Math.max(a[1], b[1])
  const x2 = Math.min(a[0] + a[2], b[0] + b[2])
  const y2 = Math.min(a[1] + a[3], b[1] + b[3])
  const inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1)
  const union = a[2] * a[3] + b[2] * b[3] - inter
  return union > 0 ? inter / union : 0
}

/** 两归一化框交集占较小框面积比（包含度） */
export function boxIoS(a: Box4, b: Box4): number {
  const x1 = Math.max(a[0], b[0])
  const y1 = Math.max(a[1], b[1])
  const x2 = Math.min(a[0] + a[2], b[0] + b[2])
  const y2 = Math.min(a[1] + a[3], b[1] + b[3])
  const inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1)
  const smaller = Math.min(a[2] * a[3], b[2] * b[3])
  return smaller > 0 ? inter / smaller : 0
}

/** IoU 非极大值抑制（+包含抑制：嵌套小框被大框压制，防同一目标多框杂乱） */
export function nms(cands: RawDetection[], iouThr: number, maxKeep = 4): RawDetection[] {
  const kept: RawDetection[] = []
  for (const c of cands.sort((a, b) => b.confidence - a.confidence)) {
    const overlap = kept.some((k) => boxIoU(c.box, k.box) > iouThr || boxIoS(c.box, k.box) > 0.8)
    if (!overlap) kept.push(c)
    if (kept.length >= maxKeep) break
  }
  return kept
}

export function adviceFor(code: string, name: string): string {
  // kitchen 专属文案（对齐业务规则：厨余 20:00-23:00 投放、按袋计积分；cam01 补字典后生效）
  if (code === 'kitchen') return '识别为「厨余垃圾」，不可回收，请于 20:00-23:00 时段投放至厨余桶，按袋计积分'
  // other 为端侧检测独占类（不进业务字典，避免污染三端业务 UI）
  if (code === 'other') return '识别为「其他垃圾」，不可回收，请投入其他垃圾桶'
  const cat = RECYCLE_CATEGORIES.find((c) => c.code === code)
  if (!cat) return `识别为「${name}」，请按提示分类投放`
  // AR 识别只展示类别与可回收性，不展示内置参考价（价格以实时报价页/下单页为准）
  return `识别为「${name}」，可回收，请投入可回收桶`
}

/** 解码 YOLOv8 系输出 [1, 4+C, N]（cx,cy,w,h 为 letterbox 像素坐标）→ 归一化检测框 */
export function decodeYolo(output: OrtNS.Tensor, m: ModelManifest): RawDetection[] {
  const data = output.data as Float32Array
  const [, rows, cols] = output.dims as number[]
  const numClasses = (rows ?? 4) - 4
  const n = cols ?? 0
  const cands: RawDetection[] = []
  const n2 = 2 * n
  const n3 = 3 * n
  for (let i = 0; i < n; i++) {
    let best = 0
    let bestCls = -1
    // 类别置信行偏移递增（避免每候选重复乘法）
    for (let c = 0, off = 4 * n; c < numClasses; c++, off += n) {
      const s = data[off + i]!
      if (s > best) {
        best = s
        bestCls = c
      }
    }
    if (best < m.scoreThreshold || bestCls < 0) continue
    const code = m.labels[bestCls] ?? 'unknown'
    const cat = RECYCLE_CATEGORIES.find((x) => x.code === code)
    const cx = data[i]! / m.inputSize
    const cy = data[n + i]! / m.inputSize
    const w = data[n2 + i]! / m.inputSize
    const h = data[n3 + i]! / m.inputSize
    // 检测独占类中文名（other 等不进业务字典）
    const name = cat?.name ?? (code === 'other' ? '其他垃圾' : code)
    cands.push({
      category: code,
      categoryName: name,
      confidence: Number(best.toFixed(2)),
      box: [cx - w / 2, cy - h / 2, w, h],
      advice: adviceFor(code, name),
    })
  }
  return nms(cands, m.iouThreshold)
}
