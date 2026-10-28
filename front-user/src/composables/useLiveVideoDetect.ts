// ============================================================
// 上传视频实时逐帧识别 composable（F6 扩展：零上传）
// 视频播放期间按 500ms 抓帧推理（与 AR 实时流同一 yoloDecode 公共层），
// 识别框随播放实时变化；内置简化帧间跟踪（IoU>0.3 关联 + EMA 平滑 +
// ≥2 帧确认输出 + 上限 4，语义对齐 useEdgeDetect.associate 防闪烁杂乱）。
// 框经 letterbox 逆变换回视频原坐标系，页面按百分比直接叠加。
// ============================================================
import { ref, computed, onBeforeUnmount } from 'vue'
import type * as OrtNS from 'onnxruntime-web'
import {
  type RawDetection,
  type ModelManifest,
  type Box4,
  detectBackend,
  loadModelSession,
  sourceToTensor,
  decodeYolo,
  boxToSource,
  boxIoU,
} from './yoloDecode'

export interface LiveDetection extends RawDetection {
  trackId: number
  stable: boolean
}

export function useLiveVideoDetect() {
  const backend = detectBackend()
  /** 端侧推理能力是否可用（false 时页面提示降级） */
  const supported = computed(() => backend !== 'none')
  const ready = ref(false)
  const running = ref(false)
  const modelVersion = ref('')
  const detections = ref<LiveDetection[]>([])
  /** 失败原因：null=正常 / model=模型加载失败 */
  const error = ref<string | null>(null)

  let session: OrtNS.InferenceSession | null = null
  let ort: typeof OrtNS | null = null
  let manifest: ModelManifest | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  let inferring = false
  let nextTrackId = 1

  interface Tracked {
    trackId: number
    box: Box4
    category: string
    categoryName: string
    confidence: number
    advice: string
    age: number
    missed: number
  }
  let tracks = new Map<number, Tracked>()

  async function ensureSession(): Promise<boolean> {
    if (session && manifest) return true
    try {
      const loaded = await loadModelSession(backend)
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

  /** 单帧推理：当前播放帧 → 张量 → 解码 → 逆变换回视频坐标系 */
  async function detectFrame(video: HTMLVideoElement): Promise<RawDetection[]> {
    if (!session || !manifest || !ort) return []
    const tensor = await sourceToTensor(ort, video, manifest.inputSize)
    if (!tensor) return []
    const outputs = await session.run({ [session.inputNames[0]!]: tensor })
    const out = outputs[session.outputNames[0]!]
    if (!out) return []
    return decodeYolo(out, manifest).map((d) => ({
      ...d,
      box: boxToSource(d.box, video.videoWidth, video.videoHeight, manifest!.inputSize),
    }))
  }

  /** 简化帧间跟踪（语义对齐 useEdgeDetect.associate：品类门控关联/EMA 拆分平滑/≥2 帧确认/上限 4） */
  function associate(dets: RawDetection[]): LiveDetection[] {
    const matched = new Set<number>()
    for (const d of [...dets].sort((a, b) => b.confidence - a.confidence)) {
      let bestId = -1
      let bestIoU = 0.3
      for (const [id, t] of tracks) {
        if (matched.has(id) || t.category !== d.category) continue
        const iou = boxIoU(d.box, t.box)
        if (iou > bestIoU) {
          bestIoU = iou
          bestId = id
        }
      }
      if (bestId >= 0) {
        const t = tracks.get(bestId)!
        // 位置 α=0.6 快跟随 / 尺寸 α=0.45 稳尺寸（防框忽大忽小）
        t.box = [
          0.4 * t.box[0] + 0.6 * d.box[0],
          0.4 * t.box[1] + 0.6 * d.box[1],
          0.55 * t.box[2] + 0.45 * d.box[2],
          0.55 * t.box[3] + 0.45 * d.box[3],
        ]
        t.category = d.category
        t.categoryName = d.categoryName
        t.confidence = d.confidence
        t.advice = d.advice
        t.age++
        t.missed = 0
        matched.add(bestId)
      } else {
        tracks.set(nextTrackId++, {
          trackId: nextTrackId - 1,
          box: [...d.box] as Box4,
          category: d.category,
          categoryName: d.categoryName,
          confidence: d.confidence,
          advice: d.advice,
          age: 1,
          missed: 0,
        })
      }
    }
    // 未匹配 track：连续丢失 ≥3 帧删除（300ms 节拍下约 0.9s）
    for (const [id, t] of tracks) {
      if (matched.has(id)) continue
      t.missed++
      if (t.missed >= 3) tracks.delete(id)
    }
    // 防杂乱：≥2 帧确认 + 置信降序上限 4
    return [...tracks.values()]
      .filter((t) => t.age >= 2)
      .sort((a, b) => b.confidence - a.confidence)
      .slice(0, 4)
      .map((t) => ({
        category: t.category,
        categoryName: t.categoryName,
        confidence: t.confidence,
        box: [...t.box] as Box4,
        advice: t.advice,
        trackId: t.trackId,
        stable: t.age >= 3,
      }))
  }

  /** 300ms 自调度循环：仅播放中抓帧（暂停时框冻结，避免空转；收紧节流降低实时延迟感） */
  async function tick(video: HTMLVideoElement) {
    if (!running.value) return
    if (!inferring && video.readyState >= 2 && !video.paused && !video.ended) {
      inferring = true
      try {
        detections.value = associate(await detectFrame(video))
      } catch {
        // 单帧失败跳过，不中断循环
      } finally {
        inferring = false
      }
    }
    timer = setTimeout(() => tick(video), 300)
  }

  function start(video: HTMLVideoElement) {
    if (running.value) return
    error.value = null
    running.value = true
    void ensureSession()
    void tick(video)
  }

  function stop() {
    running.value = false
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    tracks.clear()
    detections.value = []
  }

  onBeforeUnmount(stop)

  return { supported, ready, running, modelVersion, detections, error, start, stop }
}
