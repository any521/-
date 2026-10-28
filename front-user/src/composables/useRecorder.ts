// ============================================================
// 语音录制（onsite03）：MediaRecorder(audio/webm) + Blob 收集
// - start()/stop()：返回 { blob, duration }（duration 秒，向上取整，至少 1）
// - upload(blob)：POST /api/file/upload?biz=im（FormData）→ 文件 URL
// - 兼容降级：无 MediaRecorder → supported=false，页面引导发文字
// ============================================================
import { request } from '@/api/request'

export interface RecordResult {
  blob: Blob
  duration: number
}

/** 当前浏览器是否支持录音（不支持时页面降级引导文字输入） */
export const recorderSupported = typeof MediaRecorder !== 'undefined'

let mediaRecorder: MediaRecorder | null = null
let chunks: Blob[] = []
let startTime = 0
let timer: number | undefined
let durationSec = 0
/** 录制中回调（实时秒数，用于「按住说话」进度提示） */
let onTick: ((sec: number) => void) | undefined

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

/** 开始录音（需用户手势触发）；无权限/异常抛错由调用方 Toast */
export async function startRecording(): Promise<void> {
  if (!recorderSupported) return
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  chunks = []
  startTime = Date.now()
  durationSec = 0
  mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' })
  mediaRecorder.ondataavailable = (e) => {
    if (e.data.size > 0) chunks.push(e.data)
  }
  mediaRecorder.start()
  timer = window.setInterval(() => {
    durationSec = Math.max(1, Math.round((Date.now() - startTime) / 1000))
    onTick?.(durationSec)
  }, 1000)
}

/** 停止录音 → 释放麦克风 → 返回 { blob, duration }（时长 <1s 仍按 1s） */
export async function stopRecording(): Promise<RecordResult> {
  return new Promise((resolve, reject) => {
    const recorder = mediaRecorder
    if (!recorder) {
      reject(new Error('未在录制'))
      return
    }
    stopTimer()
    onTick = undefined
    durationSec = Math.max(1, Math.round((Date.now() - startTime) / 1000))
    recorder.onstop = () => {
      // 释放麦克风轨道
      recorder.stream.getTracks().forEach((t) => t.stop())
      const blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' })
      mediaRecorder = null
      resolve({ blob, duration: durationSec })
    }
    recorder.onerror = () => {
      mediaRecorder = null
      reject(new Error('录音失败'))
    }
    recorder.stop()
  })
}

/** 取消录音（不产出 blob），释放麦克风 */
export function cancelRecording() {
  stopTimer()
  onTick = undefined
  mediaRecorder?.stream.getTracks().forEach((t) => t.stop())
  try {
    mediaRecorder?.stop()
  } catch {
    // 未启动忽略
  }
  mediaRecorder = null
}

/** 录音实时秒数回调（startRecording 前设置，start 后每秒触发） */
export function setRecordTick(cb: (sec: number) => void) {
  onTick = cb
}

/** 上传语音文件（复用通用上传 biz=im）→ /api/file/preview/** URL */
export async function uploadVoice(blob: Blob): Promise<string> {
  const form = new FormData()
  form.append('file', blob, `voice-${Date.now()}.webm`)
  const r = await request.post<{ url?: string } | string>('/file/upload?biz=im', form)
  // 后端返回 { url } 或裸字符串均兼容
  return typeof r === 'string' ? r : r.url ?? ''
}
