<script setup lang="ts">
// 自定义轻提示（Toast）：顶部居中堆叠 + 玻璃拟态 + 类型渐变图标 + 自动消失进度条

export type DfToastType = 'success' | 'error' | 'warning' | 'info'

export interface DfToastItem {
  id: number
  message: string
  type: DfToastType
  duration: number
}

defineProps<{
  toasts: DfToastItem[]
}>()

const emit = defineEmits<{ remove: [id: number] }>()

const typeIcon: Record<DfToastType, string> = {
  success: '<path d="M8.5 12.5l2.5 2.5 4.5-5"/><circle cx="12" cy="12" r="9"/>',
  error: '<path d="M15 9l-6 6M9 9l6 6"/><circle cx="12" cy="12" r="9"/>',
  warning: '<path d="M12 10v4"/><path d="M12 17.2h.01"/><path d="M12 3.5 22 20H2z"/>',
  info: '<path d="M12 11v5"/><path d="M12 7.8h.01"/><circle cx="12" cy="12" r="9"/>',
}

const typeLabel: Record<DfToastType, string> = {
  success: '成功',
  error: '失败',
  warning: '提示',
  info: '提示',
}
</script>

<template>
  <div class="df-toast">
    <TransitionGroup name="df-toast" tag="div" class="df-toast__stack">
      <div v-for="item in toasts" :key="item.id" class="df-toast__item" :class="`df-toast__item--${item.type}`" role="alert">
        <div class="df-toast__icon" :class="`df-toast__icon--${item.type}`">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="typeIcon[item.type]" />
        </div>
        <div class="df-toast__body">
          <div class="df-toast__msg">{{ item.message }}</div>
          <span class="df-toast__label">{{ typeLabel[item.type] }}</span>
        </div>
        <button class="df-toast__close" aria-label="关闭" @click="emit('remove', item.id)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
        <span
          class="df-toast__bar"
          :class="`df-toast__bar--${item.type}`"
          :style="{ animationDuration: `${item.duration}ms` }"
        />
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped lang="scss">
.df-toast {
  position: fixed;
  top: 18px;
  left: 0;
  right: 0;
  z-index: 2000;
  pointer-events: none;

  &__stack {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 0 16px;
  }

  &__item {
    position: relative;
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    max-width: 400px;
    padding: 14px 40px 16px 14px;
    border-radius: 16px;
    background: var(--df-glass-bg-strong, rgba(255, 255, 255, 0.85));
    backdrop-filter: blur(18px) saturate(1.4);
    -webkit-backdrop-filter: blur(18px) saturate(1.4);
    border: 1px solid var(--df-glass-border, rgba(255, 255, 255, 0.6));
    box-shadow: 0 12px 40px rgba(15, 23, 42, 0.16);
    overflow: hidden;
    pointer-events: auto;
  }

  &__icon {
    flex-shrink: 0;
    width: 38px;
    height: 38px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    color: #fff;

    svg {
      width: 22px;
      height: 22px;
    }

    &--success {
      background: var(--df-gradient-secondary, linear-gradient(135deg, #7cc47a, #5a9a6e));
      box-shadow: 0 4px 14px rgba(74, 139, 61, 0.4);
    }

    &--error {
      background: var(--df-gradient-stamp, linear-gradient(135deg, #e07a6a, #b85c4a));
      box-shadow: 0 4px 14px rgba(184, 92, 74, 0.4);
    }

    &--warning {
      background: var(--df-gradient-gold, linear-gradient(135deg, #bd8a4e, #a17340));
      box-shadow: 0 4px 14px rgba(189, 138, 78, 0.4);
    }

    &--info {
      background: var(--df-gradient-carbon, linear-gradient(135deg, #4a6b5a, #3a5a4a));
      box-shadow: 0 4px 14px rgba(74, 107, 90, 0.4);
    }
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__msg {
    font-size: 14px;
    font-weight: 600;
    line-height: 1.5;
    color: var(--df-text-primary, #1f2d3d);
    word-break: break-all;
  }

  // 成功/失败/警告文字按类型上色（浅色玻璃底上深绿/深红清晰可读，避免与主题文字色混淆）
  &__item--success &__msg { color: #22a06b; }
  &__item--error &__msg { color: #d64541; }
  &__item--warning &__msg { color: #c07a1f; }

  &__label {
    font-size: 11px;
    color: var(--df-text-secondary, #8a94a6);
  }

  &__item--success &__label { color: rgba(34, 160, 107, 0.8); }
  &__item--error &__label { color: rgba(214, 69, 65, 0.8); }
  &__item--warning &__label { color: rgba(192, 122, 31, 0.8); }

  &__close {
    position: absolute;
    top: 10px;
    right: 10px;
    width: 22px;
    height: 22px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    border: none;
    border-radius: 50%;
    background: transparent;
    color: var(--df-text-secondary, #8a94a6);
    cursor: pointer;
    transition: all 0.2s ease;

    svg {
      width: 13px;
      height: 13px;
    }

    &:hover {
      background: rgba(120, 130, 145, 0.15);
      color: var(--df-text-primary, #1f2d3d);
    }
  }

  &__bar {
    position: absolute;
    left: 0;
    bottom: 0;
    height: 3px;
    border-radius: 0 3px 3px 0;
    transform-origin: left;
    animation: df-toast-bar linear forwards;

    &--success { background: var(--df-gradient-secondary, linear-gradient(90deg, #7cc47a, #5a9a6e)); }
    &--error { background: var(--df-gradient-stamp, linear-gradient(90deg, #e07a6a, #b85c4a)); }
    &--warning { background: var(--df-gradient-gold, linear-gradient(90deg, #bd8a4e, #a17340)); }
    &--info { background: var(--df-gradient-carbon, linear-gradient(90deg, #4a6b5a, #3a5a4a)); }
  }
}

@keyframes df-toast-bar {
  from { width: 100%; }
  to { width: 0%; }
}

.df-toast-enter-active {
  transition: opacity 0.35s ease, transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.df-toast-enter-from {
  opacity: 0;
  transform: translateY(-20px) scale(0.94);
}

.df-toast-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.df-toast-leave-to {
  opacity: 0;
  transform: translateY(-14px) scale(0.95);
}

.df-toast-move {
  transition: transform 0.3s ease;
}
</style>
