// ============================================================
// 自定义弹窗/轻提示（替换浏览器原生 alert / confirm / prompt）
// dfToast —— 顶部堆叠轻提示（自动消失）
// dfConfirm —— 确认弹窗（Promise<boolean>）
// dfAlert —— 纯提示弹窗（“知道了”单按钮）
// ============================================================
import { createApp, reactive, h } from 'vue'
import DfToast, { type DfToastItem, type DfToastType } from './components/DfToast.vue'
import DfDialog, { type DfDialogKind, type DfDialogTone } from './components/DfDialog.vue'

export type { DfToastType } from './components/DfToast.vue'
export type { DfDialogKind, DfDialogTone } from './components/DfDialog.vue'

export interface DfConfirmOptions {
  kind?: DfDialogKind
  tone?: DfDialogTone
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  /** 主按钮使用警示红渐变（删除类操作） */
  danger?: boolean
}

// ---------------- Toast ----------------

const toastState = reactive<{ list: DfToastItem[] }>({ list: [] })
let toastApp: ReturnType<typeof createApp> | null = null
let toastSeq = 0

function ensureToastApp() {
  if (toastApp) return
  const host = document.createElement('div')
  host.setAttribute('data-df-toast-host', '')
  document.body.appendChild(host)
  toastApp = createApp({
    render: () => h(DfToast, {
      toasts: toastState.list,
      onRemove: (id: number) => {
        toastState.list = toastState.list.filter((t) => t.id !== id)
      },
    }),
  })
  toastApp.mount(host)
}

/** 轻提示：顶部玻璃卡片自动消失 */
export function dfToast(message: string, type: DfToastType = 'info', duration = 3200): void {
  ensureToastApp()
  const id = ++toastSeq
  toastState.list.push({ id, message, type, duration })
  // duration 后移除：留 350ms 播放离场动画（TransitionGroup leave）
  window.setTimeout(() => {
    toastState.list = toastState.list.filter((t) => t.id !== id)
  }, duration + 350)
}

// ---------------- Dialog ----------------

/** 确认弹窗：resolve(true) 确认 / resolve(false) 取消 */
export function dfConfirm(options: DfConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    const host = document.createElement('div')
    host.setAttribute('data-df-dialog-host', '')
    document.body.appendChild(host)
    const app = createApp(DfDialog, {
      ...options,
      resolve: (value: boolean) => {
        app.unmount()
        host.remove()
        resolve(value)
      },
    })
    app.mount(host)
  })
}

/** 纯提示弹窗：单按钮「知道了」 */
export function dfAlert(message: string, options: Omit<DfConfirmOptions, 'message'> = {}): Promise<boolean> {
  return dfConfirm({
    kind: 'alert',
    title: '提示',
    confirmText: '知道了',
    ...options,
    message,
  })
}
