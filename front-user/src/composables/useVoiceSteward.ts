// ============================================================
// F7 语音碳管家 composable（05_ai_platform.md）
// ASR：端侧 MediaRecorder 录音 → AudioContext 解码重采样 16k 单声道
// WAV base64 → 云端 /api/ai/voice/asr（ai-back vosk 本地离线转写）。
// 浏览器 Web Speech API（Chrome 系走 Google 服务器）国内不可达，已弃用。
// 意图解析：POST /api/ai/voice/parse；TTS：speechSynthesis。
// ============================================================
import { ref, onBeforeUnmount } from 'vue'
import { showToast } from 'vant'
import { aiVoiceParse, aiVoiceAsr, aiVoiceSessionHistory, type VoiceParseResult } from '@/api/business'
import { uploadFile } from '@/api/file'
import { mediaUrl } from '@shared/utils/format'
import { tokenStore } from '@/api/token'

export interface StewardMessage {
  role: 'user' | 'steward'
  text: string
  /** 用户消息可携带图片缩略图（上传识别回显，base64）；管家消息可携带识别后图片（标注图，base64） */
  image?: string
  /** 用户消息的照片持久化 URL（/api/file/preview/voice/...，上传成功才存在；展示优先于 image） */
  imageUrl?: string
  /** 管家消息可携带意图（页面据此渲染快捷跳转） */
  intent?: VoiceParseResult['intent']
  slots?: Record<string, string>
}

export function useVoiceSteward() {
  /** ASR 是否可用：需麦克风权限 + MediaRecorder（false 时页面仅提供文本输入） */
  const asrSupported = ref(
    typeof navigator !== 'undefined' &&
      !!navigator.mediaDevices?.getUserMedia &&
      typeof MediaRecorder !== 'undefined',
  )
  const listening = ref(false)
  const parsing = ref(false)

  const GREETING = '您好，我是碳管家 carbon-sprout 可以说"帮我约个废纸回收"或"查查我的积分"'
  const messages = ref<StewardMessage[]>([])

  // 历史回显：会话轨迹由后端 biz_voice_session 逐轮持久化，
  // 刷新/重进页面后对话一直可见；无历史时展示开场白；加载失败不阻塞
  aiVoiceSessionHistory()
    .then((turns) => {
      const initial: StewardMessage[] = turns.length
        ? turns.flatMap((t) => [
            { role: 'user', text: t.userText, imageUrl: t.imageUrl },
            { role: 'steward', text: t.replyText },
          ])
        : [{ role: 'steward', text: GREETING }]
      // 加载期间已发送的消息保留在后（防竞态丢消息）
      messages.value = [...initial, ...messages.value]
    })
    .catch(() => {
      messages.value = [{ role: 'steward', text: GREETING }]
    })

  let mediaRecorder: MediaRecorder | null = null
  let mediaStream: MediaStream | null = null
  let chunks: Blob[] = []
  let recordStartAt = 0
  let autoStopTimer: number | null = null
  let disposed = false

  /** TTS 播报（适老化场景重要输出通道），不支持时静默 */
  function speak(text: string) {
    if (!('speechSynthesis' in window)) return
    const utter = new SpeechSynthesisUtterance(text)
    utter.lang = 'zh-CN'
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utter)
  }

  /** 文本 → 意图解析 → 管家回复（语音与文本输入共用） */
  async function send(text: string) {
    const content = text.trim()
    if (!content || parsing.value) return
    messages.value.push({ role: 'user', text: content })
    parsing.value = true
    try {
      const result = await aiVoiceParse(content)
      messages.value.push({ role: 'steward', text: result.reply, intent: result.intent, slots: result.slots })
      speak(result.reply)
    } catch {
      // AI 不可用（3001）：降级固定话术，不阻塞对话
      messages.value.push({ role: 'steward', text: '管家开小差了，您可以直接在首页手动操作哦' })
    } finally {
      parsing.value = false
    }
  }

  /** 图片压缩至 ≤300KB（canvas 阶梯质量，对齐 /ai/classify 契约 C6-3 隐私铁律） */
  async function compressImage(file: File): Promise<string> {
    const url = URL.createObjectURL(file)
    try {
      const img = await new Promise<HTMLImageElement>((resolve, reject) => {
        const el = new Image()
        el.onload = () => resolve(el)
        el.onerror = reject
        el.src = url
      })
      const scale = Math.min(1, 1080 / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(img.width * scale))
      canvas.height = Math.max(1, Math.round(img.height * scale))
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('canvas 不可用')
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      for (const q of [0.85, 0.7, 0.55, 0.4]) {
        const dataUrl = canvas.toDataURL('image/jpeg', q)
        const b64Len = dataUrl.length - dataUrl.indexOf(',') - 1
        if (b64Len <= 300 * 1024) return dataUrl
      }
      return canvas.toDataURL('image/jpeg', 0.4)
    } finally {
      URL.revokeObjectURL(url)
    }
  }

  /** 图片消息展示地址：优先持久化 URL（voice 私有目录，需追加 access_token），回退本次会话的 base64 */
  function imageOf(m: StewardMessage): string {
    if (m.imageUrl) return mediaUrl(m.imageUrl, tokenStore.getAccessToken()) || m.image || ''
    return m.image ?? ''
  }
  
  /** 图片上传识别：压缩 → 照片持久化（MinIO）→ 语音管家图片扫描（YOLO 标注图 + LLM+RAG 答复）→ 识别后图片回显（失败降级固定话术） */
  async function uploadImage(file: File) {
    if (file.size > 5 * 1024 * 1024) {
      messages.value.push({ role: 'steward', text: '图片不能超过 5MB，请换一张试试哦' })
      return
    }
    parsing.value = true
    const b64 = await compressImage(file)
    // 照片持久化：原图上传文件服务（biz=voice 私有目录，dev 走 MinIO），URL 随会话轨迹落库；
    // 上传失败不阻塞识别，仅本次会话可见（刷新后不保留），并向用户说明
    let imageUrl: string | undefined
    try {
      const uploaded = await uploadFile(file, 'voice')
      imageUrl = typeof uploaded === 'string' ? uploaded : uploaded.url
    } catch {
      messages.value.push({ role: 'steward', text: '照片保存失败，本次仍可识别，但刷新后不保留' })
    }
    messages.value.push({ role: 'user', text: `[图片] ${file.name}`, image: b64, imageUrl })
    try {
      // 走语音管家统一入口：图片扫描（识别后图片回传）+ LLM+RAG 知识库作答
      const r = await aiVoiceParse('识别这张图片里的可回收物', b64, imageUrl)
      const annotated = r.annotatedImage
        ? `data:image/jpeg;base64,${r.annotatedImage}`
        : undefined
      const detText = (r.detections ?? []).length
        ? `\n检测到 ${r.detections!.length} 个目标：${r.detections!.map((d) => d.className).join('、')}`
        : ''
      messages.value.push({
        role: 'steward',
        text: r.reply + detText,
        image: annotated,
        intent: r.intent,
      })
      speak(`识别完成：${(r.detections ?? []).map((d) => d.className).join('、') || '图片已识别'}`)
    } catch {
      messages.value.push({ role: 'steward', text: '图片识别暂时不可用，您可以打字问我或去首页手动操作哦' })
    } finally {
      parsing.value = false
    }
  }

  /** 开始录音（按住说话）：申请麦克风 → MediaRecorder 采集；拒绝/失败降级提示 */
  async function startListen() {
    if (listening.value || !asrSupported.value) return
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      showToast('无法访问麦克风，请检查浏览器权限设置')
      return
    }
    mediaStream = stream
    chunks = []
    mediaRecorder = new MediaRecorder(stream)
    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data)
    }
    mediaRecorder.onstop = () => {
      void handleRecorded()
    }
    recordStartAt = Date.now()
    mediaRecorder.start()
    listening.value = true
    // 兜底：最长 10s 自动停止（防长按忘松/权限弹窗吞事件），避免一直占用麦克风
    autoStopTimer = window.setTimeout(() => stopListen(), 10_000)
  }

  /** 结束录音（松开）：<400ms 视为误触不停止，续录至 3.5s 兜底自动停（防按钮一闪而过无音频） */
  function stopListen() {
    if (!listening.value || !mediaRecorder) return
    if (Date.now() - recordStartAt < 400) {
      clearTimeout(autoStopTimer ?? undefined)
      autoStopTimer = window.setTimeout(() => stopListen(), 3500)
      return
    }
    clearTimeout(autoStopTimer ?? undefined)
    listening.value = false
    if (mediaRecorder.state !== 'inactive') mediaRecorder.stop()
  }

  /** 录音结束：blob → AudioContext 解码 → 16k 单声道 PCM → WAV base64 → 云端 ASR → send() */
  async function handleRecorded() {
    const stream = mediaStream
    const recorder = mediaRecorder
    mediaStream = null
    mediaRecorder = null
    stream?.getTracks().forEach((t) => t.stop())
    if (disposed || chunks.length === 0) return
    try {
      const blob = new Blob(chunks, { type: recorder?.mimeType || 'audio/webm' })
      const text = await asrFromBlob(blob)
      if (!text) {
        showToast('没有听清，请再说一遍')
        return
      }
      await send(text)
    } catch {
      showToast('语音识别失败，请稍后重试或直接输入文字')
    }
  }

  /** Blob → WAV 16k 单声道 16bit base64（AudioContext 解码，兼容 webm/mp4/ogg）→ 云端转写文本 */
  async function asrFromBlob(blob: Blob): Promise<string> {
    const ctx = new AudioContext()
    try {
      const buf = await ctx.decodeAudioData(await blob.arrayBuffer())
      return await aiVoiceAsr(arrayBufferToBase64(encodeWav(resampleToPcm16(buf))))
    } finally {
      void ctx.close()
    }
  }

  /** 解码结果 → 16k 单声道 Int16 PCM（多声道混音取平均，线性插值重采样） */
  function resampleToPcm16(buf: AudioBuffer): Int16Array {
    const src = buf.getChannelData(0)
    const targetLen = Math.max(1, Math.round((buf.length * 16000) / buf.sampleRate))
    const out = new Int16Array(targetLen)
    for (let i = 0; i < targetLen; i++) {
      const s = Math.min(src.length - 1, Math.floor((i * buf.sampleRate) / 16000))
      const v = src[s] ?? 0
      out[i] = v < 0 ? Math.max(-32768, Math.round(v * 32768)) : Math.min(32767, Math.round(v * 32767))
    }
    return out
  }

  /** PCM → WAV 容器（44 字节头 + 16bit 单声道，vosk 要求 16000Hz/1ch/16bit） */
  function encodeWav(pcm: Int16Array): ArrayBuffer {
    const buf = new ArrayBuffer(44 + pcm.length * 2)
    const view = new DataView(buf)
    const writeStr = (off: number, s: string) => {
      for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i))
    }
    writeStr(0, 'RIFF')
    view.setUint32(4, 36 + pcm.length * 2, true)
    writeStr(8, 'WAVE')
    writeStr(12, 'fmt ')
    view.setUint32(16, 16, true)
    view.setUint16(20, 1, true)
    view.setUint16(22, 1, true)
    view.setUint32(24, 16000, true)
    view.setUint32(28, 16000 * 2, true)
    view.setUint16(32, 2, true)
    view.setUint16(34, 16, true)
    writeStr(36, 'data')
    view.setUint32(40, pcm.length * 2, true)
    new Int16Array(buf, 44).set(pcm)
    return buf
  }

  function arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer)
    let binary = ''
    const chunk = 0x8000
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
    }
    return btoa(binary)
  }

  onBeforeUnmount(() => {
    disposed = true
    stopListen()
    if (mediaStream) {
      mediaStream.getTracks().forEach((t) => t.stop())
      mediaStream = null
    }
    if ('speechSynthesis' in window) window.speechSynthesis.cancel()
  })

  return { asrSupported, listening, parsing, messages, send, uploadImage, imageOf, startListen, stopListen, speak }
}
