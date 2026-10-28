<script setup lang="ts">
// 在线聊天 · 会话列表（onsite03）：我的会话 + 未读角标 + WS 帧实时刷新
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyConversations, type ImConversationVO } from '@/api/im'
import { connect, onMessage as onWsMessage } from '@/composables/useImSocket'

defineOptions({ name: 'mobile-im-list' })

const router = useRouter()
const list = ref<ImConversationVO[]>([])
const loading = ref(false)
const refreshing = ref(false)
const offWs = ref<(() => void) | null>(null)

/** 列表摘要：STICKER 已由后端映射为 [表情] 文案 */
const previewOf = (c: ImConversationVO) => c.lastMsg || '暂无消息'

async function loadList() {
  loading.value = true
  try {
    list.value = await pageMyConversations()
  } catch {
    // 失败静默，下拉可重试
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onRefresh() {
  refreshing.value = true
  loadList()
}

/** 收到 MSG/SYSTEM 帧 → 刷新列表（当前会话未读实时可见） */
function handleFrame() {
  loadList()
}

function openChat(c: ImConversationVO) {
  router.push({ path: `/mobile/im/${c.id}`, query: { peerName: c.peerName } })
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
  <div class="page im-list">
    <van-nav-bar title="在线聊天" left-arrow @click-left="router.back()" />

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-cell
        v-for="c in list"
        :key="c.id"
        :title="c.peerName"
        :label="previewOf(c)"
        is-link
        @click="openChat(c)"
      >
        <template #icon>
          <div class="im-list__avatar">
            <!-- Upgrade U1：头像 URL 用 <img> 渲染（此前误当 SvgIcon name 使用必然失效），无则图标兜底 -->
            <img v-if="c.peerAvatar" :src="c.peerAvatar" class="im-list__avatar-img" alt="" />
            <SvgIcon v-else name="nav-user" />
          </div>
        </template>
        <template #value>
          <span class="im-list__time">{{ fmtTime(c.lastTime) }}</span>
        </template>
        <template #right-icon>
          <span class="im-list__right">
            <span v-if="c.unread > 0" class="im-list__badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
            <van-icon name="arrow" />
          </span>
        </template>
      </van-cell>
      <van-empty v-if="!loading && list.length === 0" description="暂无会话，可从订单详情发起联系" />
    </van-pull-refresh>
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
@use '@shared/styles/tokens.scss' as *;

.im-list {
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

  &__time {
    color: var(--df-text-tertiary);
    font-size: $df-font-size-xs;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
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
  }
}

.van-cell {
  &::v-deep(.van-cell__label) {
    color: var(--df-text-secondary);
  }
}
</style>
