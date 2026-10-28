// ============================================================
// 上传图片端侧一次性识别 composable（F6 扩展：零上传）
// 复用 yoloDecode 公共层（letterbox/解码/NMS 与 AR 实时流一致），
// 对单张静态图跑一次推理返回检测框；模型加载失败时 error 置位
// （静态场景不做 Mock 假结果，避免误导分类）。
// ============================================================
import { ref, computed, onBeforeUnmount } from 'vue'
import type * as OrtNS from 'onnxruntime-web'
import {
  type RawDetection,
  type ModelManifest,
  type InferBackend,
  detectBackend,
  loadModelSession,
  sourceToTensor,
  decodeYolo,
  boxToSource,
} from './yoloDecode'

export type StaticSource = ImageBitmap | HTMLImageElement | HTMLVideoElement

export function useStaticDetect() {
  const backend = ref<InferBackend>(detectBackend())
  /** 端侧推理能力是否可用（false 时页面提示降级） */
  const supported = computed(() => backend.value !== 'none')
  const ready = ref(false)
  const busy = ref(false)
  const modelVersion = ref('')
  /** 模型加载/推理失败原因（null=正常） */
  const error = ref<string | null>(null)

  let session: OrtNS.InferenceSession | null = null
  let ort: typeof OrtNS | null = null
  let manifest: ModelManifest | null = null

  /** 懒加载模型资产（首次 detect 时触发） */
  async function ensureSession(): Promise<boolean> {
    if (session && manifest) return true
    try {
      const loaded = await loadModelSession(backend.value)
      session = loaded.session
      manifest = loaded.manifest
      ort = loaded.ort
      modelVersion.value = loaded.manifest.version
      error.value = null
      ready.value = true
      return true
    } catch (err) {
      // 透出具体失败原因（路径/MIME/COOP-COEP 等），页面提示「模型加载失败：<原因>」
      error.value = err instanceof Error ? err.message : String(err)
      return false
    }
  }

  /** 单图推理：图像源 → 张量 → session.run → 解码（框经 letterbox 逆变换算回原图坐标系；失败置 error） */
  async function detect(source: StaticSource): Promise<RawDetection[]> {
    if (busy.value) return []
    busy.value = true
    try {
      if (!(await ensureSession()) || !session || !manifest || !ort) return []
      const srcW =
        source instanceof HTMLVideoElement
          ? source.videoWidth
          : source instanceof HTMLImageElement
            ? source.naturalWidth
            : source.width
      const srcH =
        source instanceof HTMLVideoElement
          ? source.videoHeight
          : source instanceof HTMLImageElement
            ? source.naturalHeight
            : source.height
      const tensor = await sourceToTensor(ort, source, manifest.inputSize)
      if (!tensor) return []
      const outputs = await session.run({ [session.inputNames[0]!]: tensor })
      const out = outputs[session.outputNames[0]!]
      if (!out) return []
      return decodeYolo(out, manifest).map((d) => ({
        ...d,
        box: boxToSource(d.box, srcW, srcH, manifest!.inputSize),
      }))
    } catch {
      error.value = 'infer'
      return []
    } finally {
      busy.value = false
    }
  }

  onBeforeUnmount(() => {
    session = null
    ort = null
    manifest = null
  })

  return { backend, supported, ready, busy, modelVersion, error, detect }
}
