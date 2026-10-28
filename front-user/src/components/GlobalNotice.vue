<script setup lang="ts">
// 全局公告浮层（挂 App.vue）：
// 悬浮叠加在顶部导航下方，不占用页面布局空间；登录后拉取最新公告标题，
// 可手动关闭（sessionStorage 记忆，同标题不再重复打扰，新公告重新弹出）
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getHomeSummary } from '@/api/business'
import { hasToken } from '@/api/token'

const route = useRoute()

const text = ref('')
const publishTime = ref('')
const dismissed = ref(false)

const isPc = computed(() => route.path.startsWith('/pc'))
const visible = computed(() => Boolean(text.value) && !dismissed.value)

function simpleHash(s: string) {
  let h = 0
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) | 0
  }
  return Math.abs(h).toString(36)
}

onMounted(async () => {
  if (!hasToken()) return
  try {
    const s = await getHomeSummary()
    if (!s?.notice) return
    // 公告身份 = 标题 + 发布时间：点击关闭仅对「同一条」生效，新发布（即使同标题）会重新弹出
    const key = `df_notice_dismiss_${simpleHash(`${s.notice}|${s.noticePublishTime ?? ''}`)}`
    if (sessionStorage.getItem(key)) return
    text.value = s.notice
    publishTime.value = s.noticePublishTime ?? ''
  } catch {
    /* 未登录/接口异常：静默，不影响页面 */
  }
})

function onDismiss() {
  if (text.value) {
    const key = `df_notice_dismiss_${simpleHash(`${text.value}|${publishTime.value ?? ''}`)}`
    sessionStorage.setItem(key, '1')
  }
  dismissed.value = true
}
</script>

<template>
  <Transition name="notice">
    <div v-if="visible" class="global-notice" :class="isPc ? 'global-notice--pc' : 'global-notice--mobile'">
      <div class="global-notice__icon">
        <SvgIcon name="nav-speech-balloon" :size="16" />
      </div>
      <p class="global-notice__text" :title="text">{{ text }}</p>
      <button class="global-notice__close" type="button" aria-label="关闭公告" @click="onDismiss">
        <SvgIcon name="nav-close" :size="14" />
      </button>
    </div>
  </Transition>
</template>

<style scoped lang="scss">
.global-notice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 12px;
  color: var(--df-text-primary);
  background: linear-gradient(135deg, color-mix(in srgb, var(--df-color-secondary) 92%, transparent) 0%, rgba(26, 95, 158, 0.92) 100%);
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: 0 8px 24px rgba(20, 60, 30, 0.18);
  backdrop-filter: blur(8px);
  z-index: 1000;

  &--pc {
    position: fixed;
    top: 84px;
    left: 50%;
    transform: translateX(-50%);
    width: min(720px, calc(100vw - 48px));
  }

  &--mobile {
    position: fixed;
    top: 54px;
    left: 12px;
    right: 12px;
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    color: #fff;
    background: rgba(255, 255, 255, 0.22);
  }

  &__text {
    flex: 1;
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #fff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__close {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    padding: 0;
    border: none;
    border-radius: 50%;
    color: rgba(255, 255, 255, 0.85);
    background: rgba(255, 255, 255, 0.18);
    cursor: pointer;
    transition: background 0.25s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.34);
    }
  }
}

.notice-enter-active,
.notice-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.notice-enter-from,
.notice-leave-to {
  opacity: 0;
  transform: translate(-50%, -12px);
}

.global-notice--mobile.notice-enter-from,
.global-notice--mobile.notice-leave-to {
  transform: translateY(-12px);
}
</style>
