<script setup lang="ts">
// PC 在线聊天 · 聊天页（onsite03）：气泡列表 + 文本/表情/语音/图片/视频 + 已读回执
// 消息数组维护为「旧→新」，历史触顶加载插入头部，WS 新帧 append 尾部
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showImagePreview, showToast } from 'vant'
import {
  pageMyConversations, pageMessages, readConversation,
  type ImMessageVO, type ImWsFrame,
} from '@/api/im'
import { request } from '@/api/request'
import {
  connect, sendFrame, onMessage as onWsMessage,
} from '@/composables/useImSocket'
import {
  startRecording, stopRecording, cancelRecording, uploadVoice, recorderSupported, setRecordTick,
} from '@/composables/useRecorder'
import { IM_STICKERS } from '@shared/data/baseData'
import { mediaUrl } from '@shared/utils/format'
import { tokenStore } from '@/api/token'
import { useUserStore } from '@/store/user'

defineOptions({ name: 'pc-im-chat' })

const route = useRoute()
const router = useRouter()
const convId = String(route.params.convId)

const peerName = ref(String(route.query.peerName ?? '工作人员'))
const bizLabel = ref('')
/** 本地消息 = 服务端 VO + 发送状态（乐观渲染：pending 发送中 / failed 失败可重试） */
interface LocalMsg extends ImMessageVO {
  pending?: boolean
  failed?: boolean
}

const messages = ref<LocalMsg[]>([])
const input = ref('')
const loading = ref(false)
const hasMore = ref(true)
const pageNum = ref(1)
const pageSize = 20

/** 滚动容器 */
const scroller = ref<HTMLElement | null>(null)
/** 已滚动到顶（触顶加载更早历史） */
let atTop = false
/** 首屏加载完成前不触发触顶加载 */
let firstLoaded = false
/** 当前播放中的语音消息 id */
const playingVoiceId = ref<string | null>(null)
let audio: HTMLAudioElement | null = null

/** 对方已读回执（收到 READ 帧后置真，作用于最后一条我方消息） */
const peerRead = ref(false)
/** 表情面板开关 */
const showStickers = ref(false)
/** 录音状态 */
const recording = ref(false)
const recordSec = ref(0)

const me = ref<string | null>(null)
/** WS 订阅注销函数 */
const offWs = ref<(() => void) | null>(null)

/** 本人资料（乐观消息的头像/名称；服务端确认后以帧内 senderAvatar 为准） */
const userStore = useUserStore()
const myName = computed(() => userStore.profile?.nickname || '')
const myAvatar = computed(() => userStore.profile?.avatar || '')

/** 头像为空/加载失败 → 灰色人形占位 */
const DEFAULT_AVATAR = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" rx="40" fill="%23ececec"/><circle cx="40" cy="32" r="13" fill="%23c6c9d0"/><path d="M15 74a25 25 0 0 1 50 0z" fill="%23c6c9d0"/></svg>'
function onAvatarError(e: Event) {
  ;(e.target as HTMLImageElement).src = DEFAULT_AVATAR
}
const avatarOf = (m: LocalMsg) => mediaUrl(m.senderAvatar, tokenStore.getAccessToken()) || DEFAULT_AVATAR

/** 本地时间 yyyy-MM-dd HH:mm（与后端 createTime 同格式，避免 toISOString 的 UTC 时差） */
function fmtNow() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 我方消息 = senderRole=USER（用户端本人必为 USER） */
const isMine = (m: ImMessageVO) => m.senderRole === 'USER'

// ---------- 会话信息 ----------
async function loadConv() {
  try {
    const list = await pageMyConversations()
    const conv = list.find((c) => c.id === convId)
    if (conv) {
      peerName.value = conv.peerName || peerName.value
      bizLabel.value = `${conv.bizType} · 订单 ${conv.bizId}`
    }
  } catch {
    // 静默，用 query 传入的名称兜底
  }
}

// ---------- 历史分页（触顶加载上一页） ----------
async function loadHistory() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  const prevHeight = scroller.value?.scrollHeight ?? 0
  try {
    const page = await pageMessages(convId, { pageNum: pageNum.value, pageSize })
    const older = [...page.list].reverse() // 后端倒序 → 旧→新
    messages.value = [...older, ...messages.value]
    hasMore.value = pageNum.value < Number(page.pages ?? 1)
    pageNum.value += 1
    await nextTick()
    // 触顶加载后保持视口内容位置（新内容插入头部后修正滚动偏移）
    if (scroller.value && prevHeight > 0) {
      scroller.value.scrollTop = scroller.value.scrollHeight - prevHeight
    }
  } catch {
    showToast('历史加载失败')
  } finally {
    loading.value = false
  }
}

function onScroll() {
  const el = scroller.value
  if (!el || !firstLoaded) return
  if (el.scrollTop < 40 && !atTop) {
    atTop = true
    loadHistory().finally(() => {
      atTop = false
    })
  }
}

async function scrollToBottom(smooth = false) {
  await nextTick()
  const el = scroller.value
  if (el) el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
}

// ---------- 已读回执 ----------
async function markRead() {
  try {
    await readConversation(convId)
    sendFrame({ type: 'READ', convId })
  } catch {
    // 已读失败静默（不影响聊天）
  }
}

/** 下行 MSG 帧 → 本地消息对象 */
function fromFrame(frame: ImWsFrame, fallbackRole: 'USER' | 'STAFF'): LocalMsg {
  return {
    id: String(frame.msg!.id ?? Date.now()),
    convId,
    senderId: String(frame.senderId ?? ''),
    senderRole: frame.senderRole ?? fallbackRole,
    msgType: frame.msg!.msgType as ImMessageVO['msgType'],
    content: frame.msg!.content,
    duration: frame.msg!.duration ?? 0,
    createTime: frame.msg!.createTime ?? '',
    senderName: frame.senderName,
    senderAvatar: frame.senderAvatar,
  }
}

/**
 * 收到 MSG 帧：对方发的 → 入列 + 已读回执。
 * 后端只推对方不推发送者（自己消息以本地乐观渲染为准，避免重复）。
 */
function onIncoming(frame: ImWsFrame) {
  if (frame.convId !== convId) return
  if (frame.type === 'MSG' && frame.msg) {
    const serverId = String(frame.msg.id ?? '')
    const dup = serverId && messages.value.some((m) => m.id === serverId)
    if (!dup) {
      messages.value.push(fromFrame(frame, 'STAFF'))
      scrollToBottom(true)
      markRead()
    }
  } else if (frame.type === 'READ') {
    // 对端已读：作用于最后一条我方消息
    peerRead.value = frame.readerId !== me.value
  } else if (frame.type === 'SYSTEM' && frame.msg) {
    messages.value.push({
      id: `sys-${Date.now()}`,
      convId,
      senderId: '0',
      senderRole: 'STAFF',
      msgType: 'SYSTEM',
      content: frame.msg.content,
      duration: 0,
      createTime: frame.msg.createTime ?? '',
    })
    scrollToBottom(true)
  }
}

// ---------- 发送（乐观渲染 + 失败重试） ----------
function pushLocal(msg: LocalMsg) {
  messages.value.push(msg)
  scrollToBottom(true)
}

function sendText() {
  const text = input.value.trim()
  if (!text) return
  input.value = ''
  doSend({ msgType: 'TEXT', content: text, duration: 0 })
}

function sendSticker(key: string) {
  showStickers.value = false
  doSend({ msgType: 'STICKER', content: key, duration: 0 })
}

/** 图片/视频上传（隐藏 input 选择 → 先传后发） */
async function onFileRead(file: File, kind: 'IMAGE' | 'VIDEO') {
  try {
    const url = await uploadFile(file)
    if (!url) {
      showToast('上传失败')
      return
    }
    doSend({ msgType: kind, content: url, duration: 0 })
  } catch {
    showToast('上传失败，请重试')
  }
}

async function uploadFile(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file, file.name)
  const r = await request.post<{ url?: string } | string>('/file/upload?biz=im', form)
  return typeof r === 'string' ? r : r.url ?? ''
}

// ---------- 文件选择（隐藏 input 触发） ----------
const fileImage = ref<HTMLInputElement | null>(null)
const fileVideo = ref<HTMLInputElement | null>(null)

function pickerImage() {
  fileImage.value?.click()
}

function pickerVideo() {
  fileVideo.value?.click()
}

function onImageChange(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  if (f) void onFileRead(f, 'IMAGE')
  el.value = '' // 允许重复选择同一文件
}

function onVideoChange(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  if (f) void onFileRead(f, 'VIDEO')
  el.value = ''
}

function doSend(msg: { msgType: string; content: string; duration: number }) {
  const local: LocalMsg = {
    id: `local-${Date.now()}`,
    convId,
    senderId: me.value ?? '',
    senderRole: 'USER',
    msgType: msg.msgType as ImMessageVO['msgType'],
    content: msg.content,
    duration: msg.duration,
    createTime: fmtNow(),
    senderName: myName.value,
    senderAvatar: myAvatar.value,
    pending: true,
  }
  pushLocal(local)
  // 后端不给自己推送确认帧（防重复）：WS 已发出即乐观确认（TCP 可靠，落库由后端先行完成）；
  // 未连接时入队暂存，保持 pending 等待重连补发，8s 后兜底清除
  if (sendFrame({ type: 'MSG', convId, msg })) {
    // 已发出：立即乐观确认，避免「发送中…」常驻（对方已收到仍显示发送中的根因）
    local.pending = false
  } else {
    window.setTimeout(() => {
      local.pending = false
    }, 8000)
  }
}

/** 重发失败消息（本地占位重推，成功后刷新历史兜底） */
function retrySend(m: LocalMsg) {
  m.failed = false
  m.pending = true
  if (sendFrame({
    type: 'MSG',
    convId,
    msg: { msgType: m.msgType, content: m.content, duration: m.duration },
  })) {
    // 已发出：立即乐观确认，与 doSend 一致避免「发送中…」常驻
    m.pending = false
  } else {
    window.setTimeout(() => {
      m.pending = false
    }, 8000)
  }
}

// ---------- 语音（按住说话） ----------
async function onVoiceStart() {
  if (!recorderSupported) {
    showToast('当前浏览器不支持录音，请使用文字消息')
    return
  }
  try {
    recording.value = true
    recordSec.value = 0
    setRecordTick((sec) => {
      recordSec.value = sec
    })
    await startRecording()
  } catch {
    recording.value = false
    showToast('无法使用麦克风，请使用文字消息')
  }
}

async function onVoiceEnd() {
  if (!recording.value) return
  recording.value = false
  try {
    const { blob, duration } = await stopRecording()
    if (duration < 1 || blob.size < 1000) {
      showToast('说话时间太短')
      return
    }
    showToast('上传中…')
    const url = await uploadVoice(blob)
    if (url) {
      doSend({ msgType: 'VOICE', content: url, duration })
    } else {
      showToast('语音上传失败')
    }
  } catch {
    showToast('录音失败，请重试')
  }
}

function onVoiceCancel() {
  if (!recording.value) return
  recording.value = false
  cancelRecording()
  showToast('已取消')
}

// ---------- 语音播放 ----------
function playVoice(m: ImMessageVO) {
  if (playingVoiceId.value === m.id) {
    audio?.pause()
    audio = null
    playingVoiceId.value = null
    return
  }
  audio?.pause()
  audio = new Audio(mediaUrl(m.content, tokenStore.getAccessToken()))
  audio.onended = () => {
    playingVoiceId.value = null
  }
  audio.onerror = () => {
    playingVoiceId.value = null
    showToast('语音播放失败')
  }
  playingVoiceId.value = m.id
  void audio.play()
}

// ---------- 表情渲染 ----------
const stickerOf = (key: string) => IM_STICKERS.find((s) => s.key === key)
/** 安全取表情 SVG 名（找不到返回空串，模板 v-if 判定隐藏） */
const stickerSvgName = (key: string) => stickerOf(key)?.svgName ?? ''

// ---------- 初始化 ----------
onMounted(async () => {
  connect()
  if (!userStore.profile) await userStore.fetchProfile()
  me.value = userStore.profile ? String(userStore.profile.id) : null
  loadConv()
  await loadHistory()
  firstLoaded = true
  await scrollToBottom()
  markRead()
  offWs.value = onWsMessage(onIncoming)
})

onUnmounted(() => {
  offWs.value?.()
  audio?.pause()
  audio = null
  cancelRecording()
})
</script>

<template>
  <div class="pc-im-chat">
    <div class="container">
      <div class="chat-header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <div class="chat-title">
          <h1 class="page-title">{{ peerName }}</h1>
          <span v-if="bizLabel" class="chat-biz">{{ bizLabel }}</span>
        </div>
      </div>

      <!-- 消息区 -->
      <div ref="scroller" class="chat-scroller" @scroll.passive="onScroll">
        <div v-if="loading" class="chat-hint">加载中…</div>
        <div v-else-if="!hasMore && messages.length > 20" class="chat-hint">没有更早的消息了</div>

        <div
          v-for="m in messages"
          :key="m.id"
          class="chat-row"
          :class="isMine(m) ? 'chat-row--mine' : 'chat-row--peer'"
        >
          <!-- 系统提示 -->
          <div v-if="m.msgType === 'SYSTEM'" class="chat-system">{{ m.content }}</div>

          <template v-else>
            <!-- 对方头像 -->
            <img v-if="!isMine(m)" class="chat-avatar" :src="avatarOf(m)" alt="" @error="onAvatarError" />

            <div class="chat-body">
              <!-- 发送人名称（对方） -->
              <span v-if="!isMine(m)" class="chat-name">{{ m.senderName || peerName }}</span>

              <!-- 文本 -->
              <div v-if="m.msgType === 'TEXT'" class="chat-bubble">{{ m.content }}</div>

              <!-- 表情包 -->
              <div v-else-if="m.msgType === 'STICKER'" class="chat-sticker">
                <SvgIcon v-if="stickerSvgName(m.content)" :name="stickerSvgName(m.content)" :size="48" />
              </div>

              <!-- 图片 -->
              <img
                v-else-if="m.msgType === 'IMAGE'"
                class="chat-image"
                :src="mediaUrl(m.content, tokenStore.getAccessToken())"
                alt="图片"
                @click="showImagePreview({ images: [mediaUrl(m.content, tokenStore.getAccessToken())] })"
              />

              <!-- 语音条 -->
              <div v-else-if="m.msgType === 'VOICE'" class="chat-voice" @click="playVoice(m)">
                <van-icon :name="playingVoiceId === m.id ? 'volume' : 'play-circle-o'" />
                <span class="chat-voice-dur">{{ m.duration }}″</span>
              </div>

              <!-- 视频 -->
              <video v-else-if="m.msgType === 'VIDEO'" class="chat-video" :src="mediaUrl(m.content, tokenStore.getAccessToken())" controls preload="metadata" />

              <!-- 发送时间 + 发送状态 -->
              <div class="chat-meta">
                <span class="chat-time">{{ m.createTime }}</span>
                <template v-if="isMine(m)">
                  <span v-if="m.pending" class="chat-state">发送中…</span>
                  <span v-else-if="m.failed" class="chat-state chat-state--fail" @click="retrySend(m)">发送失败，点击重试</span>
                  <span v-else-if="m === messages[messages.length - 1]" class="chat-state">
                    {{ peerRead ? '已读' : '未读' }}
                  </span>
                </template>
              </div>
            </div>

            <!-- 我方头像 -->
            <img v-if="isMine(m)" class="chat-avatar" :src="myAvatar || avatarOf(m)" alt="" @error="onAvatarError" />
          </template>
        </div>
      </div>

      <!-- 输入栏 -->
      <div class="chat-inputbar">
        <van-field
          v-model="input"
          placeholder="输入消息…"
          :border="false"
          @keyup.enter="sendText"
        />
        <div class="chat-tools">
          <van-icon name="smile-o" @click="showStickers = !showStickers" />
          <van-icon name="photo-o" @click="pickerImage()" />
          <van-icon name="video-o" @click="pickerVideo()" />
          <!-- 按住说话（PC 用鼠标事件） -->
          <span
            class="chat-record"
            :class="{ 'chat-record--on': recording }"
            @mousedown.prevent="onVoiceStart"
            @mouseup.prevent="onVoiceEnd"
            @mouseleave.prevent="onVoiceCancel"
          >
            {{ recording ? `${recordSec}s` : '' }}<SvgIcon v-if="!recording" name="ai-microphone" :size="22" />
          </span>
        </div>
      </div>
    </div>

    <!-- 表情面板 -->
    <van-popup v-model:show="showStickers" position="bottom" round>
      <div class="chat-stickers">
        <span v-for="s in IM_STICKERS" :key="s.key" class="chat-sticker-item" @click="sendSticker(s.key)">
          <SvgIcon :name="s.svgName" :size="32" />
        </span>
      </div>
    </van-popup>

    <!-- 隐藏的文件选择器（图片/视频，先传后发） -->
    <input id="pc-im-file-image" ref="fileImage" type="file" accept="image/*" hidden @change="onImageChange" />
    <input id="pc-im-file-video" ref="fileVideo" type="file" accept="video/*" hidden @change="onVideoChange" />
  </div>
</template>

<style scoped lang="scss">
.pc-im-chat {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
  display: flex;
  justify-content: center;
}

.container {
  width: 100%;
  max-width: 1000px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 72px - 128px);
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 14px;
  color: var(--df-text-regular);
  background: var(--df-bg-card);
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }
}

.chat-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin: 0;
}

.chat-biz {
  font-size: 13px;
  color: var(--df-text-tertiary);
}

.chat-scroller {
  flex: 1;
  overflow-y: auto;
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 24px;
  box-shadow: var(--df-shadow-card);
}

.chat-hint {
  text-align: center;
  color: var(--df-text-tertiary);
  font-size: 12px;
  padding: 8px 0;
}

.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 16px;

  &--mine {
    flex-direction: row-reverse;
  }
}

.chat-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  flex-shrink: 0;
  object-fit: cover;
  background: var(--df-fill-color-light);
}

.chat-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 76%;
}

.chat-row--mine .chat-body {
  align-items: flex-end;
}

.chat-name {
  font-size: 12px;
  color: var(--df-text-tertiary);
  margin: 0 4px 2px;
}

.chat-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  padding: 0 4px;
}

.chat-time {
  font-size: 11px;
  color: var(--df-text-tertiary);
}

.chat-bubble {
  max-width: 100%;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--df-fill-color-light);
  color: var(--df-text-regular);
  font-size: 15px;
  line-height: 1.5;
  word-break: break-all;
}

.chat-row--mine .chat-bubble {
  background: var(--df-color-primary);
  color: #fff;
}

.chat-sticker {
  font-size: 42px;
  line-height: 1;
}

.chat-image {
  max-width: 55%;
  max-height: 240px;
  border-radius: 10px;
  display: block;
}

.chat-video {
  max-width: 55%;
  max-height: 240px;
  border-radius: 10px;
}

.chat-voice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}

.chat-row--peer .chat-voice {
  background: var(--df-fill-color-light);
  color: var(--df-text-regular);
}

.chat-voice-dur {
  font-size: 12px;
}

.chat-state {
  font-size: 11px;
  color: var(--df-text-tertiary);

  &--fail {
    color: var(--df-color-danger);
    cursor: pointer;
  }
}

.chat-system {
  align-self: center;
  padding: 4px 10px;
  border-radius: 6px;
  background: var(--df-fill-color-light);
  color: var(--df-text-tertiary);
  font-size: 12px;
}

.chat-inputbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 16px;
  background: var(--df-bg-card);
  border-radius: 16px;
  box-shadow: var(--df-shadow-card);
}

.chat-tools {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 22px;
  color: var(--df-text-secondary);
  padding-left: 8px;
}

.chat-record {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 18px;
  user-select: none;
  cursor: pointer;

  &--on {
    background: var(--df-color-danger);
  }
}

.chat-stickers {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  padding: 24px;

  &-item {
    font-size: 32px;
    text-align: center;
    cursor: pointer;
  }
}
</style>
