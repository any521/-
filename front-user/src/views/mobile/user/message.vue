<script setup lang="ts">
// 消息中心：未读数 + 分页列表（下拉刷新/上拉加载）+ 全部已读 + 详情弹层（GapFill B1 V4）
// U5：四分类 tab（全部/订单/积分/交易，前端过滤）+ 类型 tag + 交易消息跳转跳蚤页
// onsite03 合并：顶部「在线聊天」会话区块（未读红点 + WS 实时刷新），聊天统一入口在本页
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import {
  pageMessages, getUnreadCount, markMessageRead, markAllMessagesRead,
  MSG_TABS, msgGroupOf,
  type UserMessage,
} from '@/api/business'
import { pageMyConversations, getImUnreadTotal, type ImConversationVO } from '@/api/im'
import { connect, onMessage as onWsMessage } from '@/composables/useImSocket'
import { useUnreadStore } from '@/store/unread'

defineOptions({ name: 'user-message' })

const router = useRouter()
const unreadStore = useUnreadStore()
const unread = ref(0)
const list = ref<UserMessage[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const allReadBusy = ref(false)
const activeTab = ref('ALL')
let pageNum = 1

// ---- 在线聊天会话区块（onsite03 合并：未读红点 + WS 帧实时刷新） ----
const convs = ref<ImConversationVO[]>([])
const convLoading = ref(false)
const imUnread = ref(0)
let offImWs: (() => void) | null = null

const convUnreadTotal = computed(() => convs.value.reduce((s, c) => s + (c.unread || 0), 0))

// 详情弹层
const detailVisible = ref(false)
const detail = ref<UserMessage | null>(null)

/** 当前 tab 过滤后的列表（分类走前端过滤，后端 /message/list 无 msgType 参数） */
const filtered = computed(() => {
  if (activeTab.value === 'ALL') return list.value
  return list.value.filter((m) => msgGroupOf(m.msgType) === activeTab.value)
})

/** 消息类型 tag 配色（订单蓝/积分金/交易绿/未知灰） */
function groupClass(msgType: string): string {
  const g = msgGroupOf(msgType)
  if (g === '订单') return 'message__group--order'
  if (g === '积分') return 'message__group--points'
  if (g === '交易') return 'message__group--trade'
  return 'message__group--other'
}

/** 交易消息跳转目标（U5 简化规则：含「下单/到账/评价」→ 卖家 orders，含「发货/取消」→ 买家 mine，含「下架」→ publish） */
function fleaTarget(m: UserMessage): string | null {
  const c = m.content
  if (c.includes('下架')) return '/mobile/flea/publish'
  if (c.includes('下单') || c.includes('到账') || c.includes('评价')) return '/mobile/flea/orders'
  if (c.includes('发货') || c.includes('取消')) return '/mobile/flea/mine'
  return null
}

async function openItem(m: UserMessage) {
  // 先标记已读（既有语义：点击消息标记已读）
  if (m.readFlag === 0) {
    try {
      await markMessageRead(m.id)
      m.readFlag = 1
      unread.value = Math.max(0, unread.value - 1)
      // 底栏角标同步减一，无需刷新页面
      unreadStore.decrement(1)
    } catch {
      showToast('操作失败，请重试')
    }
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
  router.push({ path: `/mobile/im/${c.id}`, query: { peerName: c.peerName } })
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

async function loadMore() {
  loading.value = true
  try {
    const page = await pageMessages({ pageNum, pageSize: 10 })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function reload() {
  pageNum = 1
  finished.value = false
  loadMore()
}

function onRefresh() {
  refreshing.value = true
  reload()
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
  } catch {
    // 操作失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    allReadBusy.value = false
  }
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
  <div class="page message">
    <van-nav-bar title="消息中心" left-arrow @click-left="router.back()">
      <template #right>
        <span class="message__read-all" @click="onReadAll">全部已读</span>
      </template>
    </van-nav-bar>

    <div class="message__summary card">
      <span>未读消息 <b class="message__unread">{{ unread + convUnreadTotal }}</b> 条</span>
      <span class="message__tip">点击消息标记已读</span>
    </div>

    <!-- onsite03 合并：在线聊天会话区块（未读红点 + WS 实时刷新） -->
    <div class="card message__chat">
      <div class="message__chat-header">
        <span class="message__chat-title">在线聊天</span>
        <span v-if="convUnreadTotal > 0" class="message__chat-badge">{{ convUnreadTotal > 99 ? '99+' : convUnreadTotal }}</span>
      </div>
      <van-cell
        v-for="c in convs"
        :key="c.id"
        :title="c.peerName"
        :label="convPreview(c)"
        is-link
        @click="openChat(c)"
      >
        <template #icon>
          <div class="message__avatar">
            <img v-if="c.peerAvatar" :src="c.peerAvatar" class="message__avatar-img" alt="" />
            <SvgIcon v-else name="nav-user" />
          </div>
        </template>
        <template #value>
          <span class="message__time">{{ fmtTime(c.lastTime) }}</span>
        </template>
        <template #right-icon>
          <span class="message__chat-right">
            <span v-if="c.unread > 0" class="message__chat-badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
            <van-icon name="arrow" />
          </span>
        </template>
      </van-cell>
      <van-empty v-if="!convLoading && convs.length === 0" description="暂无会话，可从订单详情发起联系" />
    </div>

    <van-tabs v-model:active="activeTab" class="message__tabs">
      <van-tab v-for="t in MSG_TABS" :key="t.key" :name="t.key" :title="t.text" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div class="page">
          <van-cell
            v-for="m in filtered"
            :key="m.id"
            :value="m.readFlag === 1 ? '' : '未读'"
            :value-class="m.readFlag === 1 ? 'message__read' : 'message__unread-tag'"
            is-link
            @click="openItem(m)"
          >
            <template #icon>
              <span class="message__dot" :class="{ 'message__dot--unread': m.readFlag === 0 }" />
            </template>
            <template #title>
              <span class="message__line">
                <span class="message__group" :class="groupClass(m.msgType)">{{ msgGroupOf(m.msgType) || '消息' }}</span>
                <span class="message__title">{{ m.title }}</span>
              </span>
            </template>
            <template #label>
              <span class="message__content">{{ m.content }}</span>
              <span class="message__time">{{ m.createTime }}</span>
            </template>
          </van-cell>
          <van-empty v-if="finished && filtered.length === 0" description="暂无消息" />
        </div>
      </van-list>
    </van-pull-refresh>

    <!-- 消息详情弹层 -->
    <van-popup v-model:show="detailVisible" round position="bottom" :style="{ maxHeight: '60%' }">
      <div v-if="detail" class="message__detail">
        <div class="message__detail-title">{{ detail.title }}</div>
        <div class="message__detail-time">{{ detail.createTime }}</div>
        <p class="message__detail-content">{{ detail.content }}</p>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.message {
  &__read-all {
    color: var(--df-color-primary);
    font-size: $df-font-size-base;
  }

  &__summary {
    margin: $df-space-md $df-space-lg 0;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
  }

  &__unread {
    color: var(--df-color-danger);
    font-size: $df-font-size-md;
  }

  &__dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    margin-right: $df-space-sm;
    border-radius: 50%;
    background: var(--df-border-color);

    &--unread {
      background: var(--df-color-danger);
    }
  }

  &__unread-tag {
    color: var(--df-color-danger);
    font-size: $df-font-size-xs;
  }

  &__read {
    color: var(--df-text-tertiary);
    font-size: $df-font-size-xs;
  }

  &__detail {
    padding: $df-space-lg;
  }

  &__detail-title {
    font-size: $df-font-size-md;
    font-weight: 600;
  }

  &__detail-time {
    margin-top: $df-space-xs;
    color: var(--df-text-tertiary);
    font-size: $df-font-size-xs;
  }

  &__detail-content {
    margin-top: $df-space-md;
    color: var(--df-text-regular);
    font-size: $df-font-size-base;
    line-height: 1.8;
    white-space: pre-wrap;
  }

  &__tabs {
    margin: $df-space-sm $df-space-lg 0;

    :deep(.van-tabs__nav) {
      background: transparent;
    }
  }

  // onsite03 合并：在线聊天会话区块
  &__chat {
    margin: $df-space-md $df-space-lg 0;
    padding: 0;
    overflow: hidden;
  }

  &__chat-header {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    padding: $df-space-md $df-space-lg;
    border-bottom: 1px solid var(--df-border-color);
  }

  &__chat-title {
    font-size: $df-font-size-base;
    font-weight: 600;
  }

  &__chat-right {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
  }

  &__chat-badge {
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 9px;
    background: var(--df-color-danger);
    color: #fff;
    font-size: 11px;
    line-height: 1;
  }

  &__avatar {
    width: 44px;
    height: 44px;
    margin-right: $df-space-md;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 26px;
    border-radius: 50%;
    background: var(--df-fill-color-light);
    color: var(--df-color-primary);
    flex-shrink: 0;
    overflow: hidden;
  }

  &__avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &__line {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    min-width: 0;
  }

  &__group {
    flex-shrink: 0;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: $df-font-size-xs;
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

  &__title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__content {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__time {
    margin-left: $df-space-sm;
    color: var(--df-text-tertiary);
    font-size: $df-font-size-xs;
  }
}
</style>
