<script setup lang="ts">
// PC 在线聊天 · 会话列表（onsite03）：我的会话 + 未读角标 + WS 帧实时刷新
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyConversations, type ImConversationVO } from '@/api/im'
import { connect, onMessage as onWsMessage } from '@/composables/useImSocket'

defineOptions({ name: 'pc-im-list' })

const router = useRouter()
const list = ref<ImConversationVO[]>([])
const loading = ref(false)
const offWs = ref<(() => void) | null>(null)

/** 列表摘要：STICKER 已由后端映射为 [表情] 文案 */
const previewOf = (c: ImConversationVO) => c.lastMsg || '暂无消息'

async function loadList() {
  loading.value = true
  try {
    list.value = await pageMyConversations()
  } catch {
    // 失败静默，可点击刷新重试
  } finally {
    loading.value = false
  }
}

/** 收到 MSG/SYSTEM 帧 → 刷新列表（当前会话未读实时可见） */
function handleFrame() {
  loadList()
}

function openChat(c: ImConversationVO) {
  router.push({ path: `/pc/im/${c.id}`, query: { peerName: c.peerName } })
}

onMounted(() => {
  connect()
  offWs.value = onWsMessage(handleFrame)
  loadList()
})

onUnmounted(() => {
  offWs.value?.()
})
</script>

<template>
  <div class="pc-im-list">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">在线聊天</h1>
      </div>

      <div class="section-card">
        <div v-if="loading" class="loading">加载中…</div>
        <div
          v-for="c in list"
          :key="c.id"
          class="conv-row"
          @click="openChat(c)"
        >
          <div class="conv-avatar">
            <!-- Upgrade U1：头像 URL 用 <img> 渲染（此前误当 SvgIcon name 使用必然失效），无则图标兜底 -->
            <img v-if="c.peerAvatar" :src="c.peerAvatar" class="conv-avatar-img" alt="" />
            <SvgIcon v-else name="nav-user" :size="22" />
          </div>
          <div class="conv-main">
            <div class="conv-name">{{ c.peerName }}</div>
            <div class="conv-preview">{{ previewOf(c) }}</div>
          </div>
          <div class="conv-side">
            <span v-if="c.unread > 0" class="conv-badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
            <span class="conv-time">{{ fmtTime(c.lastTime) }}</span>
          </div>
        </div>
        <div v-if="!loading && list.length === 0" class="empty">暂无会话，可从订单详情发起联系</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
// 时间展示：今天 HH:mm，其他 MM-DD HH:mm
function fmtTime(t?: string | null): string {
  if (!t) return ''
  const d = new Date(String(t).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(t).slice(5, 16)
  const pad = (n: number) => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const now = new Date()
  return d.toDateString() === now.toDateString() ? hm : `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
}
</script>

<style scoped lang="scss">
.pc-im-list {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 1000px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 48px;
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

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin: 0;
}

.section-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 12px 28px;
  box-shadow: var(--df-shadow-card);
}

.conv-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 0;
  border-bottom: 1px solid var(--df-border-color);
  cursor: pointer;
  transition: all 0.3s ease;

  &:last-child { border-bottom: none; }

  &:hover {
    background: color-mix(in srgb, var(--df-color-secondary) 4%, transparent);
    padding-left: 12px;
    border-radius: 10px;
  }
}

.conv-avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--df-fill-color-light);
  color: var(--df-color-primary);
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

.conv-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 4px;
}

.conv-preview {
  font-size: 13px;
  color: var(--df-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-side {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.conv-time {
  font-size: 12px;
  color: var(--df-text-tertiary);
}

.conv-badge {
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

.loading {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 15px;
}

.empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-tertiary);
  font-size: 14px;
}
</style>
