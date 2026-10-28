<script setup lang="ts">
// 自定义确认/提示弹窗：毛玻璃遮罩 + 弹簧入场卡片 + 类型渐变图标 + 渐变主按钮
import { onBeforeUnmount, onMounted } from 'vue'

export type DfDialogKind = 'confirm' | 'alert'
export type DfDialogTone = 'warning' | 'success' | 'error' | 'info'

const props = withDefaults(
  defineProps<{
    kind?: DfDialogKind
    tone?: DfDialogTone
    title?: string
    message: string
    confirmText?: string
    cancelText?: string
    danger?: boolean
    resolve: (value: boolean) => void
  }>(),
  {
    kind: 'confirm',
    tone: 'warning',
    title: '提示',
    confirmText: '确定',
    cancelText: '取消',
    danger: false,
  },
)

const toneIcon: Record<DfDialogTone, string> = {
  warning: '<path d="M12 10v4"/><path d="M12 17.2h.01"/><path d="M12 3.5 22 20H2z"/>',
  success: '<path d="M8.5 12.5l2.5 2.5 4.5-5"/><circle cx="12" cy="12" r="9"/>',
  error: '<path d="M15 9l-6 6M9 9l6 6"/><circle cx="12" cy="12" r="9"/>',
  info: '<path d="M12 11v5"/><path d="M12 7.8h.01"/><circle cx="12" cy="12" r="9"/>',
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') props.resolve(false)
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="df-dialog" role="dialog" aria-modal="true">
    <div class="df-dialog__mask" @click="resolve(false)" />
    <div class="df-dialog__card">
      <div class="df-dialog__icon" :class="`df-dialog__icon--${tone}`">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="toneIcon[tone]" />
      </div>
      <h3 class="df-dialog__title">{{ title }}</h3>
      <p class="df-dialog__message">{{ message }}</p>
      <div class="df-dialog__actions">
        <button v-if="kind === 'confirm'" class="df-dialog__btn df-dialog__btn--ghost" @click="resolve(false)">
          {{ cancelText }}
        </button>
        <button class="df-dialog__btn df-dialog__btn--primary" :class="{ 'df-dialog__btn--danger': danger }" @click="resolve(true)">
          {{ confirmText }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.df-dialog {
  position: fixed;
  inset: 0;
  z-index: 2001;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;

  &__mask {
    position: absolute;
    inset: 0;
    background: rgba(15, 23, 42, 0.45);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
    animation: df-dialog-mask-in 0.25s ease both;
  }

  &__card {
    position: relative;
    width: 100%;
    max-width: 400px;
    padding: 32px 28px 24px;
    background: var(--df-bg-card, #ffffff);
    border: 1px solid var(--df-border-color, #e4e9f0);
    border-radius: 20px;
    box-shadow: 0 24px 64px rgba(15, 23, 42, 0.28);
    text-align: center;
    animation: df-dialog-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  }

  &__icon {
    width: 64px;
    height: 64px;
    margin: 0 auto 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 20px;
    color: #fff;

    svg {
      width: 30px;
      height: 30px;
    }

    &--warning {
      background: var(--df-gradient-gold, linear-gradient(135deg, #bd8a4e, #a17340));
      box-shadow: 0 10px 24px rgba(189, 138, 78, 0.38);
    }

    &--success {
      background: var(--df-gradient-secondary, linear-gradient(135deg, #7cc47a, #5a9a6e));
      box-shadow: 0 10px 24px rgba(74, 139, 61, 0.38);
    }

    &--error {
      background: var(--df-gradient-stamp, linear-gradient(135deg, #e07a6a, #b85c4a));
      box-shadow: 0 10px 24px rgba(184, 92, 74, 0.38);
    }

    &--info {
      background: var(--df-gradient-carbon, linear-gradient(135deg, #4a6b5a, #3a5a4a));
      box-shadow: 0 10px 24px rgba(74, 107, 90, 0.38);
    }
  }

  &__title {
    margin: 0 0 10px;
    font-size: 18px;
    font-weight: 700;
    color: var(--df-text-primary, #1f2d3d);
  }

  &__message {
    margin: 0;
    font-size: 14px;
    line-height: 1.8;
    color: var(--df-text-regular, #465264);
    white-space: pre-line;
    word-break: break-all;
  }

  &__actions {
    display: flex;
    gap: 12px;
    margin-top: 26px;
  }

  &__btn {
    flex: 1;
    padding: 12px 0;
    font-size: 15px;
    font-weight: 600;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.25s ease;

    &:active {
      transform: scale(0.97);
    }

    &--ghost {
      color: var(--df-text-regular, #465264);
      background: transparent;
      border: 2px solid var(--df-border-color, #e4e9f0);

      &:hover {
        border-color: var(--df-color-primary, #21a666);
        color: var(--df-color-primary, #21a666);
      }
    }

    &--primary {
      color: #fff;
      border: 2px solid transparent;
      background: linear-gradient(135deg, var(--df-color-primary, #21a666), var(--df-color-primary-dark, #178a53));

      &:hover {
        box-shadow: 0 6px 20px rgba(33, 166, 102, 0.4);
        transform: translateY(-1px);
      }
    }

    &--danger {
      background: var(--df-gradient-stamp, linear-gradient(135deg, #e07a6a, #b85c4a));

      &:hover {
        box-shadow: 0 6px 20px rgba(184, 92, 74, 0.4);
      }
    }
  }
}

@keyframes df-dialog-mask-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes df-dialog-pop {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.88);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
