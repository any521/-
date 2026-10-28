// ============================================================
// ONNX 推理 Worker（卡顿修复 A）：session 创建 / letterbox 预处理 /
// session.run / 解码全部在 Worker 内完成，主线程只抓帧（createImageBitmap
// 转移）与渲染，推理长任务不再冻结 UI。配合 COOP/COEP（修复 B）WASM 可多线程。
// 实时档优先加载 manifest.liveFile（如 416 轻量版，修复 C）。
// ============================================================
import * as ort from 'onnxruntime-web'
import { decodeYolo, boxToSource, MANIFEST_URL, type ModelManifest, type RawDetection } from './yoloDecode'
import { configureOrtAssets } from './ortAssets'

// wasm 资产本地化（src/assets/ort/，见 ortAssets），不走 CDN
configureOrtAssets()

const ctx = self as unknown as {
  postMessage: (msg: unknown, transfer?: Transferable[]) => void
  onmessage: ((e: MessageEvent) => void) | null
}

let session: ort.InferenceSession | null = null
/** 实时档生效清单（inputSize/file 已按 live* 字段归一） */
let live: ModelManifest | null = null
let canvas: OffscreenCanvas | null = null

interface InferMsg {
  type: 'infer'
  id: number
  bmp: ImageBitmap
  videoW: number
  videoH: number
}

type WorkerMsg = InferMsg | { type: 'load'; backend?: 'webgpu' | 'wasm' }

async function load(backend: 'webgpu' | 'wasm'): Promise<void> {
  const res = await fetch(MANIFEST_URL, { cache: 'no-cache' })
  if (!res.ok) throw new Error(`manifest ${res.status}`)
  const manifest = (await res.json()) as ModelManifest
  const file = manifest.liveFile ?? manifest.file
  const inputSize = manifest.liveInputSize ?? manifest.inputSize
  const providers = backend === 'webgpu' ? ['webgpu', 'wasm'] : ['wasm']
  session = await ort.InferenceSession.create(`${import.meta.env.BASE_URL}models/${file}`, {
    executionProviders: providers,
    graphOptimizationLevel: 'all',
    logSeverityLevel: 3,
  })
  live = { ...manifest, file, inputSize }
}

/** letterbox 预处理（OffscreenCanvas，Worker 内同步绘制）→ NCHW Float32 */
function bmpToTensor(bmp: ImageBitmap, size: number): ort.Tensor | null {
  const srcW = bmp.width
  const srcH = bmp.height
  if (!srcW || !srcH) return null
  if (!canvas || canvas.width !== size) canvas = new OffscreenCanvas(size, size)
  const c2d = canvas.getContext('2d', { willReadFrequently: true }) as OffscreenCanvasRenderingContext2D | null
  if (!c2d) return null
  const scale = Math.min(size / srcW, size / srcH)
  const w = srcW * scale
  const h = srcH * scale
  c2d.fillStyle = '#727272'
  c2d.fillRect(0, 0, size, size)
  c2d.drawImage(bmp, (size - w) / 2, (size - h) / 2, w, h)
  const { data } = c2d.getImageData(0, 0, size, size)
  const plane = size * size
  const f = new Float32Array(plane * 3)
  for (let i = 0, i4 = 0; i < plane; i++, i4 += 4) {
    f[i] = data[i4]! / 255
    f[i + plane] = data[i4 + 1]! / 255
    f[i + plane * 2] = data[i4 + 2]! / 255
  }
  return new ort.Tensor('float32', f, [1, 3, size, size])
}

async function infer(msg: InferMsg): Promise<RawDetection[]> {
  if (!session || !live) return []
  const tensor = bmpToTensor(msg.bmp, live.inputSize)
  if (!tensor) return []
  const outputs = await session.run({ [session.inputNames[0]!]: tensor })
  const out = outputs[session.outputNames[0]!]
  if (!out) return []
  return decodeYolo(out, live).map((d) => ({
    ...d,
    box: boxToSource(d.box, msg.videoW, msg.videoH, live!.inputSize),
  }))
}

ctx.onmessage = async (e: MessageEvent) => {
  const msg = e.data as WorkerMsg
  if (msg.type === 'load') {
    try {
      await load(msg.backend ?? 'wasm')
      ctx.postMessage({ type: 'loaded', ok: true, version: live?.version ?? '' })
    } catch (err) {
      // 失败原因回传主线程（useEdgeDetect 记录并在降级 mock 时 console 暴露）
      ctx.postMessage({
        type: 'loaded',
        ok: false,
        error: err instanceof Error ? err.message : String(err),
      })
    }
    return
  }
  if (msg.type === 'infer') {
    try {
      const dets = await infer(msg)
      ctx.postMessage({ type: 'dets', id: msg.id, dets })
    } catch {
      ctx.postMessage({ type: 'dets', id: msg.id, dets: [] })
    } finally {
      msg.bmp.close()
    }
  }
}
