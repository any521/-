// ============================================================
// 语音口述转写（09：语音口述维修报告）
// Web Speech API（webkitSpeechRecognition）实时转写填入表单；
// 浏览器不支持时 supported=false，页面降级为纯手输，不阻塞主流程。
// 契约对齐 Track E ASR（后续可切云端 ASR，接口不变）。
// ============================================================
import { onBeforeUnmount, ref } from 'vue'

interface SpeechRecognitionLike {
  lang: string
  continuous: boolean
  interimResults: boolean
  start(): void
  stop(): void
  onresult: ((event: { resultIndex: number; results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }> }) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onend: (() => void) | null
}

function createRecognition(): SpeechRecognitionLike | null {
  const w = window as unknown as Record<string, unknown>
  const Ctor = (w.SpeechRecognition ?? w.webkitSpeechRecognition) as (new () => SpeechRecognitionLike) | undefined
  return Ctor ? new Ctor() : null
}

export function useDictation(onText: (text: string) => void) {
  const recognition = createRecognition()
  /** 环境是否支持语音转写（不支持则页面隐藏话筒入口） */
  const supported = recognition !== null
  const listening = ref(false)
  const error = ref('')

  if (recognition) {
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = false
    recognition.onresult = (event) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i]
        if (result?.isFinal && result[0]) onText(result[0].transcript)
      }
    }
    recognition.onerror = (event) => {
      error.value = event.error === 'not-allowed' ? '麦克风权限被拒绝，请改用手动输入' : '语音识别异常，请改用手动输入'
      listening.value = false
    }
    recognition.onend = () => {
      listening.value = false
    }
  }

  function start() {
    if (!recognition || listening.value) return
    error.value = ''
    recognition.start()
    listening.value = true
  }

  function stop() {
    recognition?.stop()
    listening.value = false
  }

  onBeforeUnmount(stop)

  return { supported, listening, error, start, stop }
}
