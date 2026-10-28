// ============================================================
// IM WebSocket 客户端（onsite03 核心，单例共享，员工端独立副本）：
// - 连接 ws(s)://host/点废成金/api/ws/im?token=xxx（C-O3-1 通道契约；nginx 仅反代带前缀的 /点废成金/api/ws/）
// - 断线自动重连（指数退避 1s→2s→4s…≤30s）+ 30s PING 心跳、10s 判死重连
// - 帧分发：MSG/READ/SYSTEM/PONG（LOCATION 帧当前端不透出，onsite04 消费在用户端）
// - token 过期被拒时静默退避重试，前端 auth 刷新流程触发重连
// ============================================================
import { ref } from 'vue'
import { tokenStore } from '@/api/token'
import type { ImWsFrame } from '@/api/im'

type FrameHandler = (frame: ImWsFrame) => void

const WS_URL = () =>
  (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/点废成金/api/ws/im?token='

const HEARTBEAT_INTERVAL = 30_000
const HEARTBEAT_TIMEOUT = 10_000
const MAX_RETRY_DELAY = 30_000

/** 连接状态（页面可据此展示断线提示） */
export const imConnected = ref(false)

let ws: WebSocket | null = null
let retryDelay = 1_000
let retryTimer: number | undefined
let heartbeatTimer: number | undefined
let timeoutTimer: number | undefined
let lastActivityAt = 0
let manualClosed = false

/** 断线期间待发帧（连接恢复后补发，防消息丢失；上限 20 条防积压） */
const PENDING_FRAME_CAP = 20
let pendingFrames: ImWsFrame[] = []

/** 全局帧回调（页面 onMounted 注册 / onUnmounted 注销） */
const frameHandlers = new Set<FrameHandler>()

function clearTimers() {
  if (retryTimer !== undefined) {
    window.clearTimeout(retryTimer)
    retryTimer = undefined
  }
  if (heartbeatTimer !== undefined) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }
  if (timeoutTimer !== undefined) {
    window.clearTimeout(timeoutTimer)
    timeoutTimer = undefined
  }
}

function scheduleReconnect() {
  if (manualClosed) return
  clearTimers()
  retryTimer = window.setTimeout(() => {
    connect()
    retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY)
  }, retryDelay)
}

function stopHeartbeat() {
  if (heartbeatTimer !== undefined) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }
  if (timeoutTimer !== undefined) {
    window.clearTimeout(timeoutTimer)
    timeoutTimer = undefined
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = window.setInterval(() => {
    // 上次收到任何帧（含 PONG）距今 > 10s → 判死重连
    if (Date.now() - lastActivityAt > HEARTBEAT_TIMEOUT) {
      ws?.close()
      return
    }
    if (ws?.readyState === WebSocket.OPEN) {
      sendFrame({ type: 'PING' })
    }
  }, HEARTBEAT_INTERVAL)
}

function onOpen() {
  imConnected.value = true
  retryDelay = 1_000
  lastActivityAt = Date.now()
  startHeartbeat()
  // 补发断线期间暂存的帧（MSG 落库由后端完成，重连后消息不会丢）
  flushPending()
}

/** 连接就绪后按序补发暂存帧（单条失败不阻断后续） */
function flushPending() {
  if (ws?.readyState !== WebSocket.OPEN || pendingFrames.length === 0) return
  const frames = pendingFrames
  pendingFrames = []
  frames.forEach((f) => {
    try {
      ws!.send(JSON.stringify(f))
    } catch {
      // 单条发送失败忽略（心跳/重连兜底）
    }
  })
}

function handleSocketMessage(ev: MessageEvent) {
  lastActivityAt = Date.now()
  let frame: ImWsFrame | null = null
  try {
    frame = JSON.parse(ev.data as string) as ImWsFrame
  } catch {
    return
  }
  if (!frame?.type) return
  frameHandlers.forEach((cb) => cb(frame!))
}

function onClose() {
  imConnected.value = false
  stopHeartbeat()
  ws = null
  scheduleReconnect()
}

function onError() {
  // onClose 会随后触发并负责重连
  ws?.close()
}

/** 建立连接（单例：已连接/连接中直接复用；token 变化时强制重建） */
export function connect(force = false) {
  const token = tokenStore.getAccessToken()
  if (!token) return
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) && !force) {
    return
  }
  if (retryTimer !== undefined) {
    window.clearTimeout(retryTimer)
    retryTimer = undefined
  }
  manualClosed = false
  try {
    ws = new WebSocket(WS_URL() + encodeURIComponent(token))
    ws.onopen = onOpen
    ws.onmessage = handleSocketMessage
    ws.onclose = onClose
    ws.onerror = onError
  } catch {
    scheduleReconnect()
  }
}

/** 发送上行帧：已连接直接发送返回 true；未连接入待发队列返回 false（重连后自动补发） */
export function sendFrame(frame: ImWsFrame): boolean {
  if (ws?.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(frame))
    return true
  }
  if (pendingFrames.length < PENDING_FRAME_CAP) {
    pendingFrames.push(frame)
  }
  return false
}

/** 注册全局帧回调，返回注销函数（页面 onUnmounted 调用） */
export function onMessage(cb: FrameHandler): () => void {
  frameHandlers.add(cb)
  return () => frameHandlers.delete(cb)
}

/** 登出/换号时主动断开（不再重连） */
export function disconnect() {
  manualClosed = true
  clearTimers()
  stopHeartbeat()
  ws?.close()
  ws = null
  imConnected.value = false
  pendingFrames = [] // 登出/换号清空待发帧，避免补发到新账号
}
