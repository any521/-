<script setup lang="ts">
// PC 消息中心：未读数 + 分页列表 + 全部已读 + 详情弹层（GapFill B1 V4）
// U5：四分类 tab（全部/订单/积分/交易，前端过滤）+ 类型 tag + 交易消息跳转跳蚤页
// onsite03 合并：顶部「在线聊天」会话区块（未读红点 + WS 实时刷新），聊天统一入口在本页
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import {
  pageMessages, getUnreadCount, markMessageRead, markAllMessagesRead,
  MSG_TABS, msgGroupOf,
  type UserMessage,
} from '@/api/business'
import { pageMyConversations, getImUnreadTotal, type ImConversationVO } from '@/api/im'
import { connect, onMessage as onWsMessage } from '@/composables/useImSocket'
import { useUnreadStore } from '@/store/unread'

defineOptions({ name: 'pc-user-message' })

const router = useRouter()
const unreadStore = useUnreadStore()
const unread = ref(0)
const list = ref<UserMessage[]>([])
const loading = ref(false)
const finished = ref(false)
const allReadBusy = ref(false)
const activeTab = ref('ALL')
let pageNum = 1

// 详情弹层
const detailVisible = ref(false)
const detail = ref<UserMessage | null>(null)

// ---- 在线聊天会话区块（onsite03 合并：未读红点 + WS 帧实时刷新） ----
const convs = ref<ImConversationVO[]>([])
const convLoading = ref(false)
const imUnread = ref(0)
let offImWs: (() => void) | null = null

const convUnreadTotal = computed(() => convs.value.reduce((s, c) => s + (c.unread || 0), 0))

async function loadConvs() {
  convLoading.value = true
  try {
    convs.value = await pageMyConversations()
  } catch {
    // 失败静默，WS 帧/下次进入再刷
  } finally {
    convLoading.value = false
  }
}

async function loadImUnread() {
  try {
    imUnread.value = await getImUnreadTotal()
  } catch {
    imUnread.value = 0
  }
}

/** 收到 MSG/SYSTEM 帧 → 刷新会话列表（未读实时可见） */
function handleImFrame() {
  loadConvs()
  loadImUnread()
}

function openChat(c: ImConversationVO) {
  router.push({ path: `/pc/im/${c.id}`, query: { peerName: c.peerName } })
}

// 会话摘要：非文本类型后端已映射为 [图片] 等文案
const convPreview = (c: ImConversationVO) => c.lastMsg || '暂无消息'

/** 时间展示：今天 HH:mm，其他 MM-DD HH:mm */
function fmtTime(t?: string | null): string {
  if (!t) return ''
  const d = new Date(String(t).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(t).slice(5, 16)
  const pad = (n: number) => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const now = new Date()
  return d.toDateString() === now.toDateString() ? hm : `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
}

/** 当前 tab 过滤后的列表（分类走前端过滤，后端 /message/list 无 msgType 参数） */
const filtered = computed(() => {
  if (activeTab.value === 'ALL') return list.value
  return list.value.filter((m) => msgGroupOf(m.msgType) === activeTab.value)
})

/** 消息类型 tag 配色（订单蓝/积分金/交易绿/未知灰） */
function groupClass(msgType: string): string {
  const g = msgGroupOf(msgType)
  if (g === '订单') return 'group--order'
  if (g === '积分') return 'group--points'
  if (g === '交易') return 'group--trade'
  return 'group--other'
}

/** 交易消息跳转目标（U5 简化规则：含「下单/到账/评价」→ 卖家 orders，含「发货/取消」→ 买家 mine，含「下架」→ publish） */
function fleaTarget(m: UserMessage): string | null {
  const c = m.content
  if (c.includes('下架')) return '/pc/flea/publish'
  if (c.includes('下单') || c.includes('到账') || c.includes('评价')) return '/pc/flea/orders'
  if (c.includes('发货') || c.includes('取消')) return '/pc/flea/mine'
  return null
}

async function loadMore() {
  loading.value = true
  try {
    const page = await pageMessages({ pageNum, pageSize: 10 })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  try {
    unread.value = await getUnreadCount()
  } catch {
    unread.value = 0
  }
}

async function onReadAll() {
  if (allReadBusy.value) return
  allReadBusy.value = true
  try {
    await markAllMessagesRead()
    showSuccessToast('已全部标记为已读')
    list.value.forEach((m) => (m.readFlag = 1))
    unread.value = 0
    unreadStore.clear()
  } finally {
    allReadBusy.value = false
  }
}

async function openItem(m: UserMessage) {
  // 先标记已读（既有语义：点击消息标记已读）
  if (m.readFlag === 0) {
    await markMessageRead(m.id)
    m.readFlag = 1
    unread.value = Math.max(0, unread.value - 1)
    unreadStore.decrement(1)
  }
  // 交易消息按内容关键词跳转对应跳蚤页（U4 路由就绪后生效），其余打开详情
  const target = msgGroupOf(m.msgType) === '交易' ? fleaTarget(m) : null
  if (target) {
    router.push(target)
    return
  }
  detail.value = m
  detailVisible.value = true
}

onMounted(() => {
  loadUnread()
  loadMore()
  loadConvs()
  loadImUnread()
  // onsite03 合并：消息中心长驻 WS 订阅（会话/未读实时刷新）
  connect()
  offImWs = onWsMessage(handleImFrame)
})

onUnmounted(() => {
  offImWs?.()
})
</script>

<template>
  <div class="pc-message">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">消息中心</h1>
      </div>

      <div class="section-card">
        <div class="summary">
          <span>未读消息 <b class="unread">{{ unread + convUnreadTotal }}</b> 条</span>
          <button class="read-all" :disabled="allReadBusy" @click="onReadAll">全部已读</button>
        </div>
      </div>

      <!-- onsite03 合并：在线聊天会话区块（未读红点 + WS 实时刷新） -->
      <div class="section-card">
        <div class="chat-header">
          <span class="chat-title">在线聊天</span>
          <span v-if="convUnreadTotal > 0" class="chat-badge">{{ convUnreadTotal > 99 ? '99+' : convUnreadTotal }}</span>
        </div>
        <div
          v-for="c in convs"
          :key="c.id"
          class="conv-item"
          @click="openChat(c)"
        >
          <div class="conv-avatar">
            <img v-if="c.peerAvatar" :src="c.peerAvatar" class="conv-avatar-img" alt="" />
            <SvgIcon v-else name="nav-user" :size="24" />
          </div>
          <div class="conv-main">
            <div class="conv-title">
              {{ c.peerName }}
              <span class="conv-time">{{ fmtTime(c.lastTime) }}</span>
            </div>
            <div class="conv-content">{{ convPreview(c) }}</div>
          </div>
          <span v-if="c.unread > 0" class="chat-badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
        </div>
        <div v-if="!convLoading && convs.length === 0" class="empty">暂无会话，可从订单详情发起联系</div>
      </div>

      <div class="section-card">

        <div class="tabs">
          <button
            v-for="t in MSG_TABS"
            :key="t.key"
            class="tab"
            :class="{ 'tab--active': activeTab === t.key }"
            @click="activeTab = t.key"
          >{{ t.text }}</button>
        </div>

        <div v-if="finished && filtered.length === 0" class="empty">暂无消息</div>
        <div
          v-for="m in filtered"
          :key="m.id"
          class="msg-item"
          :class="{ 'msg-item--unread': m.readFlag === 0 }"
          @click="openItem(m)"
        >
          <span class="dot" :class="{ 'dot--unread': m.readFlag === 0 }" />
          <div class="msg-main">
            <div class="msg-title">
              <span class="group" :class="groupClass(m.msgType)">{{ msgGroupOf(m.msgType) || '消息' }}</span>
              {{ m.title }}
            </div>
            <div class="msg-content">{{ m.content }}</div>
            <div class="msg-time">{{ m.createTime }}</div>
          </div>
          <span v-if="m.readFlag === 0" class="msg-tag">未读</span>
        </div>

        <div v-if="!finished" class="load-more">
          <button :disabled="loading" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</button>
        </div>
      </div>
    </div>

    <!-- 消息详情弹层 -->
    <div v-if="detailVisible" class="detail-mask" @click="detailVisible = false">
      <div class="detail-panel" @click.stop>
        <div class="detail-title">{{ detail?.title }}</div>
        <div class="detail-time">{{ detail?.createTime }}</div>
        <p class="detail-content">{{ detail?.content }}</p>
        <button class="detail-close" @click="detailVisible = false">关闭</button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-message {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 700px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: 1px solid var(--df-border-color);
  border-radius: 8px;
  background: var(--df-bg-card);
  color: var(--df-text-regular);
  font-size: 14px;
  cursor: pointer;

  &:hover { color: var(--df-color-primary); border-color: var(--df-color-primary); }
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin: 0;
}

.section-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 28px;
  margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--df-border-color);
  color: var(--df-text-secondary);
  font-size: 14px;
}

// onsite03 合并：在线聊天会话区块
.chat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--df-border-color);
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.chat-badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--df-color-danger);
  color: #fff;
  font-size: 12px;
  line-height: 1;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--df-border-color);
  cursor: pointer;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--df-bg-fill); }
}

.conv-avatar {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--df-fill-color-light);
  color: var(--df-color-primary);
  flex-shrink: 0;
  overflow: hidden;
}

.conv-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.conv-main {
  flex: 1;
  min-width: 0;
}

.conv-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--df-text-primary);
  font-size: 15px;
}

.conv-time {
  color: var(--df-text-tertiary);
  font-size: 13px;
  font-weight: 400;
}

.conv-content {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--df-text-secondary);
  font-size: 13px;
}

.tabs {
  display: flex;
  gap: 8px;
  padding: 16px 0 4px;
}

.tab {
  padding: 6px 20px;
  border: 1px solid var(--df-border-color);
  border-radius: 20px;
  background: var(--df-bg-card);
  color: var(--df-text-secondary);
  font-size: 14px;
  cursor: pointer;

  &:hover { color: var(--df-color-primary); border-color: var(--df-color-primary); }

  &--active {
    background: var(--df-color-primary);
    border-color: var(--df-color-primary);
    color: #fff;
  }
}

.unread {
  color: var(--df-color-danger);
  font-size: 18px;
}

.read-all {
  padding: 6px 16px;
  border: 1px solid var(--df-color-primary);
  border-radius: 8px;
  background: transparent;
  color: var(--df-color-primary);
  font-size: 14px;
  cursor: pointer;

  &:hover { background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.msg-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 0;
  border-bottom: 1px solid var(--df-border-color);
  cursor: pointer;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--df-bg-fill); }

  &--unread .msg-title { font-weight: 600; }
}

.dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--df-border-color);
  flex-shrink: 0;

  &--unread { background: var(--df-color-danger); }
}

.msg-main {
  flex: 1;
}

.msg-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--df-text-primary);
  font-size: 15px;
}

.group {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;

  &--order {
    color: #3b7cff;
    background: rgba(59, 124, 255, 0.12);
  }

  &--points {
    color: #d48806;
    background: rgba(212, 136, 6, 0.12);
  }

  &--trade {
    color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 12%, transparent);
  }

  &--other {
    color: var(--df-text-tertiary);
    background: var(--df-bg-fill);
  }
}

.msg-content {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--df-text-secondary);
  font-size: 13px;
}

.msg-time {
  margin-top: 4px;
  color: var(--df-text-tertiary);
  font-size: 13px;
}

.msg-tag {
  color: var(--df-color-danger);
  font-size: 12px;
}

.empty {
  padding: 48px 0;
  text-align: center;
  color: var(--df-text-tertiary);
}

.load-more {
  text-align: center;
  padding-top: 16px;

  button {
    padding: 8px 32px;
    border: 1px solid var(--df-border-color);
    border-radius: 8px;
    background: var(--df-bg-card);
    color: var(--df-text-regular);
    cursor: pointer;

    &:hover { color: var(--df-color-primary); }
    &:disabled { opacity: 0.6; cursor: not-allowed; }
  }
}

.detail-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.detail-panel {
  width: 480px;
  max-width: 90vw;
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-float);
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.detail-time {
  margin-top: 8px;
  color: var(--df-text-tertiary);
  font-size: 13px;
}

.detail-content {
  margin-top: 20px;
  color: var(--df-text-regular);
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.detail-close {
  margin-top: 24px;
  width: 100%;
  padding: 10px 0;
  border: none;
  border-radius: 8px;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}
</style>
