// ============================================================
// F6 端侧 AR 分类指导 composable（05_ai_platform.md）
// 实现：WebGPU → WASM 自适应跑 YOLO→ONNX（onnxruntime-web，零上传）。
// 模型资产经 /models/manifest.json 做版本管理（version/file/labels/阈值），
// 资产缺失或加载失败时自动降级本地 Mock 推理（engine='mock'），页面零改动。
// 不支持环境（无摄像头/无 WASM）→ supported=false，页面回退云端拍照（F1）。
// ============================================================
import { ref, computed, onBeforeUnmount } from 'vue'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import {
  type RawDetection,
  type InferBackend,
  detectBackend as detectInferBackend,
  boxIoU,
  adviceFor,
} from './yoloDecode'

/** 单帧识别结果（对齐 Track E 冻结的端侧出参 Schema；cam02 扩展 trackId/stable，契约 C6-1 字段名冻结） */
export interface EdgeDetection {
  /** 品类 code（对齐 biz_category） */
  category: string
  categoryName: string
  confidence: number
  /** 归一化框选区域 [x, y, w, h] */
  box: [number, number, number, number]
  /** 投放建议 */
  advice: string
  /** 帧间稳定 ID（≥1；未跟踪到的新目标首次出现即分配） */
  trackId: number
  /** 连续稳定 ≥3 帧（该目标可触发精分类） */
  stable: boolean
}

/** 解码/NMS 后的原始检测（trackId/stable 由帧间关联层 associate 分配；定义在 yoloDecode 公共层） */
export type { RawDetection }

export type EdgeBackend = InferBackend
/** 实际推理引擎：onnx=真模型 / mock=模型资产未就绪时的本地演示兜底 */
export type EdgeEngine = 'onnx' | 'mock'

export function useEdgeDetect() {
  /** 推理后端：WebGPU 优先，降级 WASM，都不支持则 none */
  const backend = ref<EdgeBackend>('none')
  /** 端侧能力是否可用（false 时页面回退云端拍照 F1） */
  const supported = computed(() => backend.value !== 'none')
  const engine = ref<EdgeEngine>('mock')
  const modelVersion = ref('')
  const running = ref(false)
  const loading = ref(false)
  const detections = ref<EdgeDetection[]>([])
  /** 60fps 插值渲染列表（卡顿修复 D）：仅叠框用；逻辑侧（精分类/建议）仍用 detections */
  const renderDetections = ref<EdgeDetection[]>([])
  let latchedAt = 0 // 防闪烁：最近一次有效检测的 timestamp
  let lastExtrap = 0 // 上次插值渲染的估计时间（ms）
  const videoRef = ref<HTMLVideoElement | null>(null)
  /** 摄像头画面宽高比（"1280 / 720" 格式，页面据此做 aspect-fit 容器，框以源坐标系精确叠加） */
  const aspect = ref('')

  let stream: MediaStream | null = null
  let timer: ReturnType<typeof setInterval> | null = null
  // 卡顿修复 A：推理移入 Worker，主线程只抓帧与渲染
  let worker: Worker | null = null
  let workerReady = false
  let reqSeq = 0
  const pending = new Map<number, (dets: RawDetection[]) => void>()
  let inferring = false
  let raf = 0

  // ---- 帧间跟踪状态（cam02：trackId/stable，契约 C6-1） ----
    interface Tracked {
    trackId: number
    box: [number, number, number, number]
    category: string
    categoryName: string
    confidence: number
    advice: string
    age: number // 连续关联帧数
    missed: number // 连续丢失帧数
    vx: number // 归一化速度（插值外推用，卡顿修复 D）
    vy: number
    lastUpdate: number // 最近关联时间戳（performance.now）
    _prevX?: number
    _prevY?: number
  }
  let nextTrackId = 1
  let tracks = new Map<number, Tracked>()

  /** Mock 演示目标（带平移速度，模拟持续移动的物品） */
  interface MockTarget {
    box: [number, number, number, number]
    vx: number
    vy: number
    category: string
    categoryName: string
  }
  let mockTargets: MockTarget[] = []

  backend.value = detectInferBackend()

  /** 拉起推理 Worker 并加载模型资产；失败降级 mock，不阻塞开摄 */
  async function loadSession(): Promise<void> {
    if (workerReady || engine.value === 'onnx') return
    worker = new Worker(new URL('./yoloWorker.ts', import.meta.url), { type: 'module' })
    worker.onmessage = (e: MessageEvent) => {
      const m = e.data as { type: string; id?: number; dets?: RawDetection[] }
      if (m.type === 'dets' && m.id !== undefined) {
        pending.get(m.id)?.(m.dets ?? [])
        pending.delete(m.id)
      }
    }
    const ok = await new Promise<boolean>((resolve) => {
      const h = (e: MessageEvent) => {
        const m = e.data as { type: string; ok?: boolean; version?: string; error?: string }
        if (m.type !== 'loaded') return
        worker?.removeEventListener('message', h)
        if (m.ok) {
          workerReady = true
          engine.value = 'onnx'
          modelVersion.value = m.version ?? ''
        } else {
          // 记录 Worker 侧加载失败详情（路径/MIME/COOP-COEP 等），降级 mock 时 console 可见
          console.error('[edge] Worker 模型加载失败:', m.error ?? '未知错误')
        }
        resolve(!!m.ok)
      }
      worker?.addEventListener('message', h)
      worker?.postMessage({ type: 'load', backend: backend.value })
    })
    if (!ok) {
      // 模型资产未就绪 / 下载失败 / EP 初始化失败 → 本地 Mock 演示兜底
      engine.value = 'mock'
      worker.terminate()
      worker = null
    }
  }

  /**
   * 帧间关联（cam02）：对解码+NMS 后的检测做 greedy 匹配——
   * 按置信降序找同品类且 IoU 最大的现存 track（阈值 0.3，品类门控防框在不同目标间跳导致忽大忽小）；
   * 关联成功 → 位置 α=0.6 快跟随 / 尺寸 α=0.45 稳尺寸（EMA 拆分） + age++/missed=0；
   * 未匹配 track → missed++（≥3 删除）；tracks 上限 8；输出 stable = age ≥ 3。
   */
  function associate(dets: RawDetection[]): EdgeDetection[] {
    const now = performance.now()
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
        // 速度估计（EMA 平滑）：供渲染层插值外推（卡顿修复 D）
        const dt = Math.min(Math.max((now - t.lastUpdate) / 1000, 0.05), 2)
        t.vx = 0.5 * t.vx + 0.5 * ((d.box[0] - t.box[0]) / dt)
        t.vy = 0.5 * t.vy + 0.5 * ((d.box[1] - t.box[1]) / dt)
        t.lastUpdate = now
        // 位置 α=0.9 快跟随（仅保留微小平滑防抖；0.75 仍有可感知滞后）
        t.box = [
          0.1 * t.box[0] + 0.9 * d.box[0],
          0.1 * t.box[1] + 0.9 * d.box[1],
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
        const id = nextTrackId++
        tracks.set(id, {
          trackId: id,
          box: [...d.box] as [number, number, number, number],
          category: d.category,
          categoryName: d.categoryName,
          confidence: d.confidence,
          advice: d.advice,
          age: 1,
          missed: 0,
          vx: 0,
          vy: 0,
          lastUpdate: now,
        })
      }
    }
    // 未匹配的现存 track：时间制过期 + 错过计数双重判定——距上次关联超 0.6s 或错过>=3 帧即删除。
    for (const [id, t] of tracks) {
      if (matched.has(id)) continue
      t.missed++
      if (now - t.lastUpdate > 600 || t.missed >= 3) tracks.delete(id)
    }
    // 上限 8：超限删除最旧且 missed 最大者，防内存膨胀
    while (tracks.size > 8) {
      let worst: number | null = null
      let worstMissed = -1
      for (const [id, t] of tracks) {
        if (t.missed > worstMissed) {
          worstMissed = t.missed
          worst = id
        }
      }
      if (worst === null) break
      tracks.delete(worst)
    }
    // 防杂乱：仅输出连续 ≥2 帧关联的目标（滤单帧闪烁误检），按置信降序上限 4（与 NMS 单帧上限对齐）
    return snapshot(now)
  }

  /** 真推理：主线程抓帧（createImageBitmap 转移）→ Worker 内预处理+run+解码（单帧串行防积压） */
  async function inferOnnx(): Promise<RawDetection[]> {
    const video = videoRef.value
    if (!worker || !workerReady || !video || !video.videoWidth || inferring) return []
    inferring = true
    try {
      if (!aspect.value) aspect.value = `${video.videoWidth} / ${video.videoHeight}`
      const bmp = await createImageBitmap(video)
      const id = ++reqSeq
      // 3s 兜底：Worker 异常不致卡死 tick 链
      return await Promise.race([
        new Promise<RawDetection[]>((resolve) => {
          pending.set(id, resolve)
          worker!.postMessage(
            { type: 'infer', id, bmp, videoW: video.videoWidth, videoH: video.videoHeight },
            [bmp],
          )
        }),
        new Promise<RawDetection[]>((resolve) => {
          setTimeout(() => {
            if (pending.delete(id)) resolve([])
          }, 3000)
        }),
      ])
    } catch {
      return []
    } finally {
      inferring = false
    }
  }

  /** 跟踪快照：age≥2 过滤 + 置信降序上限 4；不对外推——仅输出真实关联位置。 */
  function snapshot(now: number): EdgeDetection[] {
    return [...tracks.values()]
      .filter((t) => t.age >= 2)
      .sort((a, b) => b.confidence - a.confidence)
      .slice(0, 4)
      .map((t) => ({
        category: t.category,
        categoryName: t.categoryName,
        confidence: t.confidence,
        box: t.box as [number, number, number, number],
        advice: t.advice,
        trackId: t.trackId,
        stable: t.age >= 3,
      }))
  }

  /** 渲染层插值/外推：让框实时跟随视频流（60fps），不随推理帧率跳动。
   *  仅在目标连续出现且无错过时外推；一旦 missed>=1 立即停止外推，避免移出画面后仍显示框。
   */
  function interpolateRender(now: number): EdgeDetection[] {
    const anchored = snapshot(now)
    const rendered: EdgeDetection[] = anchored.map((t) => {
      const trk = tracks.get(t.trackId)
      if (trk && trk.missed > 0) return t // 已错过 1 帧以上，不插值
      const dtSec = (now - lastExtrap) / 1000
      if (dtSec <= 0 || isNaN(dtSec)) {
        lastExtrap = now
        return t
      }
      const prevX = (t as any)._prevX ?? t.box[0]
      const prevY = (t as any)._prevY ?? t.box[1]
      const vx = (t.box[0] - prevX) / (dtSec + 1e-6)
      const vy = (t.box[1] - prevY) / (dtSec + 1e-6)
      const nextX = Math.min(Math.max(t.box[0] + vx * dtSec, 0), 1 - t.box[2])
      const nextY = Math.min(Math.max(t.box[1] + vy * dtSec, 0), 1 - t.box[3])
      ;(t as any)._prevX = t.box[0]
      ;(t as any)._prevY = t.box[1]
      lastExtrap = now
      return {
        ...t,
        box: [nextX, nextY, t.box[2], t.box[3]] as [number, number, number, number],
      }
    })
    return rendered
  }

  /** Mock 推理：随机产出 0-2 个移动目标（模型资产未就绪时的演示兜底） */
  function inferMock(): RawDetection[] {
    // 65% 概率沿用既有目标（模拟同一物品持续存在，便于观察跟踪稳定性），否则重开场景
    if (mockTargets.length === 0 || Math.random() >= 0.65) {
      const count = Math.random() < 0.25 ? 0 : Math.random() < 0.75 ? 1 : 2
      mockTargets = Array.from({ length: count }, () => {
        const cat = RECYCLE_CATEGORIES[Math.floor(Math.random() * RECYCLE_CATEGORIES.length)]!
        return {
          category: cat.code,
          categoryName: cat.name,
          box: [Math.random() * 0.5, Math.random() * 0.5, 0.25 + Math.random() * 0.2, 0.25 + Math.random() * 0.2],
          // 微小平移速度，模拟目标移动
          vx: (Math.random() - 0.5) * 0.03,
          vy: (Math.random() - 0.5) * 0.03,
        }
      })
    }
    return mockTargets.map((t) => {
      // 位置微平移 + 画面边界夹紧
      const x = Math.min(Math.max(t.box[0] + t.vx, 0.02), 1 - t.box[2] - 0.02)
      const y = Math.min(Math.max(t.box[1] + t.vy, 0.02), 1 - t.box[3] - 0.02)
      t.box = [x, y, t.box[2], t.box[3]]
      return {
        category: t.category,
        categoryName: t.categoryName,
        confidence: Number((0.6 + Math.random() * 0.39).toFixed(2)),
        box: t.box,
        advice: adviceFor(t.category, t.categoryName),
      }
    })
  }

  /** 开启摄像头 + 推理循环 */
  async function start() {
    if (!supported.value || running.value) return
    loading.value = true
    try {
      await loadSession()
      stream = await navigator.mediaDevices.getUserMedia({
        // 指定理想分辨率：默认低清流（640×480）下目标占像素太少，
        // 纸盒印纹等纹理在 letterbox 后丢失导致类别翻转（牛奶盒→塑料问题根因）
        video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
      })
      if (videoRef.value) {
        videoRef.value.srcObject = stream
        await videoRef.value.play()
      }
      running.value = true
      // 卡顿修复 D：rAF 渲染循环，按 track 速度外推框位置，推理低帧率也视觉顺滑
      const loop = () => {
        raf = requestAnimationFrame(loop)
        if (running.value) renderDetections.value = interpolateRender(performance.now())
      }
      raf = requestAnimationFrame(loop)
      // 零节流自调度链：上一帧推理完成立即排下一帧，推理耗时本身即节奏控制（onnx 帧内串行防积压）；
      // 关联+Vue 赋值放独立宏任务，避免长任务累计超 50ms
      const tick = async () => {
        if (!running.value) return
        const raw = engine.value === 'onnx' ? await inferOnnx() : inferMock()
        timer = setTimeout(() => {
          if (!running.value) return
          detections.value = associate(raw)
          timer = setTimeout(tick, 0) as unknown as ReturnType<typeof setInterval>
        }, 0) as unknown as ReturnType<typeof setInterval>
      }
      timer = setTimeout(tick, 0) as unknown as ReturnType<typeof setInterval>
    } catch {
      // 摄像头授权失败视为不支持，页面走云端拍照降级
      backend.value = 'none'
    } finally {
      loading.value = false
    }
  }

  function stop() {
    running.value = false
    detections.value = []
    renderDetections.value = []
    tracks.clear()
    mockTargets = []
    aspect.value = ''
    if (raf) {
      cancelAnimationFrame(raf)
      raf = 0
    }
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    stream?.getTracks().forEach((t) => t.stop())
    stream = null
    if (videoRef.value) videoRef.value.srcObject = null
  }

  onBeforeUnmount(() => {
    stop()
    worker?.terminate()
    worker = null
    workerReady = false
  })

  return {
    backend,
    supported,
    engine,
    modelVersion,
    running,
    loading,
    detections,
    renderDetections,
    videoRef,
    aspect,
    start,
    stop,
  }
}
