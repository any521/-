// 站内信未读数全局共享：底栏/侧栏/消息页/我的 同源，避免「消息页已读、角标仍旧」
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getUnreadCount } from '@/api/business'
import { hasToken } from '@/api/token'
import { connect, onMessage as onWsMessage } from '@/composables/useImSocket'

const POLL_MS = 20_000
const WS_DEBOUNCE_MS = 400

export const useUnreadStore = defineStore('unread', () => {
  const count = ref(0)
  let loading = false
  let started = false
  let pollTimer: number | null = null
  let debounceTimer: number | null = null
  let offWs: (() => void) | null = null

  async function refresh() {
    if (!hasToken()) {
      count.value = 0
      return
    }
    if (loading) return
    loading = true
    try {
      count.value = await getUnreadCount()
    } catch {
      // 静默降级：保留旧值
    } finally {
      loading = false
    }
  }

  /** 防抖刷新（WS 帧/本地已读变更共用） */
  function scheduleRefresh(delay = WS_DEBOUNCE_MS) {
    if (debounceTimer !== null) window.clearTimeout(debounceTimer)
    debounceTimer = window.setTimeout(() => {
      debounceTimer = null
      refresh()
    }, delay)
  }

  /** 站内信未读 + IM 未读帧都可能影响角标（SYSTEM 为后端落库唤醒） */
  function onFrame(frame: { type: string }) {
    if (frame.type === 'MSG' || frame.type === 'READ' || frame.type === 'SYSTEM') {
      scheduleRefresh()
    }
  }

  function onVisible() {
    if (document.visibilityState === 'visible') refresh()
  }

  /** 幂等启动：App 壳层/PC 侧栏各调一次，WS + 20s 轮询 + 回前台刷一次 */
  function start() {
    if (started) return
    started = true
    connect()
    offWs = onWsMessage(onFrame)
    refresh()
    pollTimer = window.setInterval(refresh, POLL_MS)
    document.addEventListener('visibilitychange', onVisible)
  }

  function stop() {
    offWs?.()
    offWs = null
    if (pollTimer !== null) window.clearInterval(pollTimer)
    if (debounceTimer !== null) window.clearTimeout(debounceTimer)
    pollTimer = null
    debounceTimer = null
    document.removeEventListener('visibilitychange', onVisible)
    started = false
  }

  /** 本地已读一条：角标立刻减，不必等 REST 往返 */
  function decrement(n = 1) {
    count.value = Math.max(0, count.value - n)
  }

  /** 本地全部已读 */
  function clear() {
    count.value = 0
  }

  function set(n: number) {
    count.value = Math.max(0, n)
  }

  return { count, refresh, scheduleRefresh, start, stop, decrement, clear, set }
})
