// ============================================================
// CAM 自动精分类（契约 C6-2/3/4/5）：稳定目标自动裁剪上传，
// 结果缓存（Map<trackId, 结果>）+ 并发队列（≤2，排 3 丢最老）
// + 隐私裁剪（仅目标区域，短边 ≤512、base64 ≤300KB、超限降采样）
// + 失败 3s 冷却降级（保留大类 + Toast，循环不中断）
// + 窗口配额（30s 自动 ≤3 次）+ 429 全局冷却 30s + Toast 去重（防刷屏）。
// 挂载点由 cam02 预留（C6-8），本模块只被 ar-scan.vue 接入。
// ============================================================
import { ref, watch, type Ref } from 'vue'
import { showToast } from 'vant'
import { BizError } from '@shared/index'
import type { EdgeDetection } from '@/composables/useEdgeDetect'
import { classifyDetail, type ClassifyDetailResult } from '@/api/business'

/** 手动框结果的固定键（页面单独展示，契约 C6-8 forceClassify(trackId=null)） */
export const MANUAL_RESULT_KEY = -1

/** 裁剪规格（契约 C6-3）：短边 ≤512px、jpeg 质量 0.85、base64 ≤300KB */
const CROP_MAX_SHORT = 512
const CROP_MAX_B64 = 300 * 1024

interface ClassifyJob {
  /** -1 = 手动框 */
  trackId: number
  box: [number, number, number, number]
  category?: string
}

export function useAutoClassify(detections: Ref<EdgeDetection[]>, videoRef: Ref<HTMLVideoElement | null>) {
  /** trackId → 精分类结果（缓存；-1 为手动框结果，契约 C6-4） */
  const results = ref(new Map<number, ClassifyDetailResult>())
  /** 有排队/执行中任务（操作条 loading 骨架） */
  const queueing = ref(false)

  const queue: ClassifyJob[] = []
  let inflight = 0
  let canvas: HTMLCanvasElement | null = null
  /** trackId → 上次失败时间（3s 冷却，防止循环刷请求，C6-5） */
  const failedAt = new Map<number, number>()
  /** trackId → 最后出现在画面时间（离开 ≥3s 清理缓存，允许重识别，C6-4） */
  const lastSeen = new Map<number, number>()

  /** 全局窗口配额：30s 内自动触发 ≤3 次，超出静默跳过（手动重识别不受限） */
  const AUTO_WINDOW_MS = 30_000
  const AUTO_MAX_PER_WINDOW = 3
  let autoWindowStart = 0
  let autoCount = 0
  /** 429 全局冷却：触频控后 30s 内自动触发全部静默跳过，不再刷请求 */
  const RATE_LIMIT_COOLDOWN_MS = 30_000
  let rateLimitedAt = 0
  /** Toast 去重：同类提示 5s 内只弹一次（多目标并发失败/频控防刷屏） */
  const TOAST_DEDUP_MS = 5_000
  let lastToastAt = 0
  function toastOnce(msg: string) {
    const now = Date.now()
    if (now - lastToastAt < TOAST_DEDUP_MS) return
    lastToastAt = now
    showToast(msg)
  }

  /** 裁剪目标区域 → base64（短边 ≤512 只缩不放；≤300KB，质量/尺寸递降重试） */
  function cropBase64(box: [number, number, number, number]): string | null {
    const v = videoRef.value
    if (!v?.videoWidth || !v.videoHeight) return null
    const vw = v.videoWidth
    const vh = v.videoHeight
    // 归一化框 → 视频原始帧像素（夹紧边界，最小 8px）
    let x = Math.round(box[0] * vw)
    let y = Math.round(box[1] * vh)
    let w = Math.round(box[2] * vw)
    let h = Math.round(box[3] * vh)
    x = Math.max(0, Math.min(x, vw - 1))
    y = Math.max(0, Math.min(y, vh - 1))
    w = Math.max(8, Math.min(w, vw - x))
    h = Math.max(8, Math.min(h, vh - y))
    if (!canvas) canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) return null
    // 两轮降采样（首轮短边 ≤512，次轮 0.6×）× 四档质量，保证 ≤300KB 或放弃（C6-3）
    const short = Math.min(w, h)
    const base = Math.min(1, CROP_MAX_SHORT / short)
    for (const scale of [base, base * 0.6]) {
      const cw = Math.max(8, Math.round(w * scale))
      const ch = Math.max(8, Math.round(h * scale))
      canvas.width = cw
      canvas.height = ch
      ctx.drawImage(v, x, y, w, h, 0, 0, cw, ch)
      for (const q of [0.85, 0.7, 0.55, 0.4]) {
        const url = canvas.toDataURL('image/jpeg', q)
        const b64Len = url.length - url.indexOf(',') - 1
        if (b64Len <= CROP_MAX_B64) return url
      }
    }
    return null
  }

  /** 入队：FIFO 最多 3，超出丢弃最老（C6-4 并发 ≤2） */
  function enqueue(job: ClassifyJob) {
    if (queue.length >= 3) queue.shift()
    queue.push(job)
    void pump()
  }

  /** 失败冷却：保留大类 + Toast 提示，3s 内该目标不再触发（C6-5） */
  function markFailed(trackId: number) {
    failedAt.set(trackId, Date.now())
    results.value.delete(trackId)
    toastOnce('精分类暂不可用，已保留大类识别')
  }

  async function pump() {
    if (inflight >= 2 || queue.length === 0) return
    const job = queue.shift()!
    inflight++
    queueing.value = true
    try {
      const crop = cropBase64(job.box)
      if (!crop) {
        // 裁剪失败（无帧/超限压缩不动）：按失败降级处理，不阻塞队列
        markFailed(job.trackId)
        return
      }
      const r = await classifyDetail(crop, job.category, { skipErrorToast: true })
      results.value.set(job.trackId, r)
      failedAt.delete(job.trackId)
    } catch (e) {
      // 频控（429）：后端 30s 滑动窗口限流，命中后全局冷却 30s 静默降级，
      // 由本层去重 Toast 一次（共享请求层 skipErrorToast 已关闭自动弹窗）
      if (e instanceof BizError && e.code === 429) {
        rateLimitedAt = Date.now()
        toastOnce('识别太频繁了，已暂停自动识别，稍后自动恢复')
        return
      }
      markFailed(job.trackId)
    } finally {
      inflight--
      if (queue.length === 0 && inflight === 0) queueing.value = false
      void pump()
    }
  }

  /** 自动触发 + 缓存清理（每 500ms detections 刷新时评估，C6-4） */
  watch(detections, (list) => {
    const now = Date.now()
    const alive = new Set(list.map((d) => d.trackId))
    for (const d of list) {
      lastSeen.set(d.trackId, now)
      // 触发条件：稳定 ≥3 帧 && 无缓存 && 不在失败冷却中
      if (!d.stable || results.value.has(d.trackId)) continue
      const failedAtTs = failedAt.get(d.trackId)
      if (failedAtTs !== undefined && now - failedAtTs < 3000) continue
      // 频控全局冷却：静默跳过（不弹窗不刷请求），等窗口恢复自动重试
      if (now - rateLimitedAt < RATE_LIMIT_COOLDOWN_MS) continue
      // 30s 窗口配额：自动触发累计 ≤3 次，超出静默跳过（手动重识别不受限）
      if (now - autoWindowStart >= AUTO_WINDOW_MS) {
        autoWindowStart = now
        autoCount = 0
      }
      if (autoCount >= AUTO_MAX_PER_WINDOW) continue
      autoCount++
      enqueue({ trackId: d.trackId, box: d.box, category: d.category })
    }
    // 离开画面 ≥3s → 清理缓存/冷却记录（目标重现才允许重识别）
    for (const id of [...results.value.keys(), ...failedAt.keys()]) {
      if (alive.has(id)) continue
      const since = lastSeen.get(id) ?? now
      if (now - since >= 3000) {
        results.value.delete(id)
        failedAt.delete(id)
        lastSeen.delete(id)
      }
    }
  })

  /**
   * 手动兜底入口（契约 C6-8）：
   * - trackId 有值 → 强制覆盖缓存重识别（「重新识别」/点按锁定发起）；
   * - trackId 为 null + box → 手动框直接裁剪识别，结果存 MANUAL_RESULT_KEY（页面单独展示）。
   */
  function forceClassify(trackId: number | null, box?: [number, number, number, number]) {
    if (trackId !== null) {
      results.value.delete(trackId)
      failedAt.delete(trackId)
      const det = detections.value.find((d) => d.trackId === trackId)
      const b = (box ?? det?.box) as [number, number, number, number] | undefined
      if (b) enqueue({ trackId, box: b, category: det?.category })
    } else if (box) {
      enqueue({ trackId: MANUAL_RESULT_KEY, box })
    }
  }

  return { results, queueing, forceClassify }
}
