// ============================================================
// 3D 构建任务 store（Pinia）：异步任务生命周期 + 平滑进度
// 轮询 timer 挂在 store 上：用户切走路由轮询不中断，
// 完成/失败后由全局浮窗（Build3dToast）提示，spec 供页面消费
// ============================================================
import { ref, computed } from 'vue'
import { defineStore, acceptHMRUpdate } from 'pinia'
import { submitBuild3d, getBuild3dTask, type ModelSpec, type Build3dMode } from '@/api/business'

export type Build3dStatus = 'IDLE' | 'SUBMITTING' | 'RUNNING' | 'DONE' | 'FAILED'

/** RUNNING 期间平滑进度上限（真实完成时才到 100） */
const MAX_RUNNING_PROGRESS = 88
/** 每次轮询推进量 */
const STEP = 6

export const useBuild3dStore = defineStore('build3d', () => {
  const status = ref<Build3dStatus>('IDLE')
  /** 当前任务构建模式（advanced=高质量重建 / extreme=极致渲染，三档文案区分） */
  const mode = ref<Build3dMode>('standard')
  const progress = ref(0)
  const spec = ref<ModelSpec | null>(null)
  const error = ref('')
  let timer: number | undefined
  let pollFailures = 0

  const advanced = computed(() => mode.value === 'advanced')
  const extreme = computed(() => mode.value === 'extreme')

  /** 阶段文案（页面进度条与全局浮窗共用；advanced/extreme 模式文案区分） */
  const stageText = computed(() => {
    if (status.value === 'SUBMITTING') {
      return extreme.value ? '提交极致渲染任务中…'
        : advanced.value ? '提交高质量重建任务中…' : '提交任务中…'
    }
    if (progress.value < 45) {
      return extreme.value ? 'AI 深度渲染分析中…'
        : advanced.value ? 'AI 精细化分析中…' : 'AI 分析图片中…'
    }
    return extreme.value ? '极致渲染 3D 模型中…'
      : advanced.value ? '高质量重建 3D 模型中…' : '构建 3D 模型中…'
  })

  const running = computed(() => status.value === 'SUBMITTING' || status.value === 'RUNNING')

  function stopPolling() {
    if (timer !== undefined) {
      clearInterval(timer)
      timer = undefined
    }
    pollFailures = 0
  }

  /** 停止当前任务（用户手动选品类/换图时调用）：丢弃迟到结果 */
  function stop() {
    stopPolling()
    status.value = 'IDLE'
    mode.value = 'standard'
    progress.value = 0
    spec.value = null
    error.value = ''
  }

  /** 提交新构建任务：立即进入进度展示，后台轮询；mode 默认 standard（识别链路无感） */
  async function start(photoBase64: string, buildMode: Build3dMode = 'standard') {
    stop()
    mode.value = buildMode
    status.value = 'SUBMITTING'
    progress.value = 8
    try {
      const { taskId } = await submitBuild3d(photoBase64, buildMode)
      // 提交期间被 stop（用户手动选择了品类）则放弃启动轮询
      if (status.value !== 'SUBMITTING') return
      status.value = 'RUNNING'
      progress.value = 12
      timer = window.setInterval(() => poll(taskId), 1000)
    } catch {
      // 提交失败：不阻塞任何流程，回退预设模型库
      status.value = 'FAILED'
      error.value = '任务提交失败'
    }
  }

  async function poll(taskId: string) {
    try {
      const t = await getBuild3dTask(taskId)
      pollFailures = 0
      if (t.status === 'DONE') {
        spec.value = t.spec ?? null
        status.value = 'DONE'
        progress.value = 100
        stopPolling()
      } else if (t.status === 'FAILED') {
        error.value = t.error ?? '构建失败'
        status.value = 'FAILED'
        stopPolling()
      } else {
        // RUNNING：平滑推进，上限 88%
        progress.value = Math.min(MAX_RUNNING_PROGRESS, progress.value + STEP)
      }
    } catch {
      // 查询网络抖动：连续 5 次失败才判死，避免瞬时抖动误伤
      if (++pollFailures >= 5) {
        status.value = 'FAILED'
        error.value = '查询任务状态失败'
        stopPolling()
      }
    }
  }

  return { status, mode, progress, spec, error, stageText, running, start, stop }
})

// Pinia setup store HMR：热更新时保留 state 并替换方法
if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useBuild3dStore, import.meta.hot))
}
