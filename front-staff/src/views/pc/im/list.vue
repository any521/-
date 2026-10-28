<script setup lang="ts">
// PC 端在线聊天 · 会话列表（onsite03）：我的会话 + 未读角标 + WS 帧实时刷新
// 会话由用户从订单发起后自动出现（员工端不主动创建）
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
    // 失败静默，可点击刷新
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

<template>
  <div class="pc-page im-list">
    <div class="pc-page__header">
      <h1 class="pc-page__title">在线聊天</h1>
      <p class="pc-page__sub">会话由用户从订单发起后自动出现，收到新消息实时刷新</p>
      <van-button size="small" round plain type="primary" icon="replay" class="im-list__refresh" @click="loadList">
        刷新
      </van-button>
    </div>

    <div v-if="!loading && list.length === 0" class="pc-card">
      <van-empty description="暂无会话，用户从订单发起联系后自动出现" />
    </div>

    <div v-else class="im-list__grid">
      <div
        v-for="c in list"
        :key="c.id"
        class="pc-card im-list__item"
        @click="openChat(c)"
      >
        <div class="im-list__avatar">
          <!-- 头像 URL 用 <img> 渲染（此前误当 SvgIcon name 使用必然失效），无则图标兜底 -->
          <img v-if="c.peerAvatar" :src="c.peerAvatar" class="im-list__avatar-img" alt="" />
          <SvgIcon v-else name="nav-user" />
        </div>
        <div class="im-list__info">
          <div class="im-list__head">
            <span class="im-list__name">{{ c.peerName }}</span>
            <span class="im-list__time">{{ fmtTime(c.lastTime) }}</span>
          </div>
          <div class="im-list__preview">
            <span class="im-list__text">{{ previewOf(c) }}</span>
            <span v-if="c.unread > 0" class="im-list__badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.im-list {
  &__refresh {
    margin-left: auto;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: $df-space-lg;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: $df-space-md;
    cursor: pointer;
    transition: all 0.25s ease;

    &:hover {
      box-shadow: var(--df-shadow-float);
      transform: translateY(-2px);
    }
  }

  &__avatar {
    width: 52px;
    height: 52px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 30px;
    border-radius: 50%;
    background: var(--df-fill-color-light);
    color: var(--df-color-primary);
    flex-shrink: 0;
    overflow: hidden;
  }

  &__avatar-img {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__name {
    font-weight: 600;
    font-size: $df-font-size-md;
  }

  &__time {
    color: var(--df-text-tertiary);
    font-size: $df-font-size-xs;
  }

  &__preview {
    margin-top: 4px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: $df-space-sm;
  }

  &__text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__badge {
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
    flex-shrink: 0;
  }
}
</style>
