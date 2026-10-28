// ============================================================
// 弱网优化（D3）：在线状态感知 + 今日订单本地缓存 + 关键操作离线队列
// - 列表：请求失败时回退最近一次缓存，页面照常可看单
// - 接单：离线时入 localStorage 队列，恢复网络后自动重放（幂等由后端保证）
// ============================================================
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { StaffOrder } from '@/api/business'
import { request } from '@/api/request'

const ORDERS_CACHE_KEY = 'df_staff_orders_cache'
const QUEUE_KEY = 'df_staff_offline_queue'

interface QueuedAction {
  /** 目标路径（相对 baseURL） */
  url: string
  /** HTTP 方法（缺省 post；接单/完单已对齐后端 PUT） */
  method?: 'post' | 'put'
  body?: Record<string, unknown>
  /** 操作描述（同步提示用） */
  label: string
  time: string
}

// 模块级单例状态：多组件共享同一在线状态与队列
const online = ref(navigator.onLine)
const queueSize = ref(readQueue().length)
const syncing = ref(false)

function readQueue(): QueuedAction[] {
  try {
    return JSON.parse(localStorage.getItem(QUEUE_KEY) ?? '[]') as QueuedAction[]
  } catch {
    return []
  }
}

function writeQueue(queue: QueuedAction[]) {
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue))
  queueSize.value = queue.length
}

/** 今日订单缓存（弱网回退数据源） */
export function cacheOrders(list: StaffOrder[]) {
  localStorage.setItem(ORDERS_CACHE_KEY, JSON.stringify({ time: Date.now(), list }))
}

export function loadCachedOrders(): { time: number; list: StaffOrder[] } | null {
  try {
    const raw = localStorage.getItem(ORDERS_CACHE_KEY)
    return raw ? (JSON.parse(raw) as { time: number; list: StaffOrder[] }) : null
  } catch {
    return null
  }
}

/** 关键操作（接单等）离线入队 */
export function enqueueAction(action: Omit<QueuedAction, 'time'>) {
  const queue = readQueue()
  queue.push({ ...action, time: new Date().toISOString() })
  writeQueue(queue)
}

/** 网络恢复后重放队列（逐条重放，按方法分发，失败保留下次再试） */
export async function flushQueue(): Promise<{ done: number; rest: number }> {
  if (syncing.value) return { done: 0, rest: queueSize.value }
  syncing.value = true
  let done = 0
  try {
    let queue = readQueue()
    for (const action of [...queue]) {
      try {
        const opts = { silent: true, skipErrorToast: true }
        if (action.method === 'put') await request.put(action.url, action.body, opts)
        else await request.post(action.url, action.body, opts)
        queue = queue.filter((a) => a !== action)
        writeQueue(queue)
        done += 1
      } catch {
        break // 网络仍不可用，保留剩余队列
      }
    }
  } finally {
    syncing.value = false
  }
  return { done, rest: readQueue().length }
}

/** 在线状态 + 队列大小（App.vue 横幅 / 接单台使用） */
export function useOffline(onRestore?: (synced: number) => void) {
  const handleOnline = async () => {
    online.value = true
    const { done } = await flushQueue()
    if (done > 0) onRestore?.(done)
  }
  const handleOffline = () => {
    online.value = false
  }

  onMounted(() => {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return { online, queueSize, syncing }
}
