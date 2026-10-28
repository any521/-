<script setup lang="ts">
// Hero 问候横幅（对齐模板 .hero-banner）
// 结构：背景色 + greet-banner.jpg（右侧铺满）+ ::before 斜向渐变遮罩
//   - 左侧 34% 为不透明表面色（保证标题/正文可读），52%→76% 渐隐露出右侧森林小人插画
//   - 日/夜版由 --df-bg-card / --df-bg-nested 自动切换，无需额外判断
// 用法：<HeroMascot title="下午好" subtitle="…"><stats/quote 等左侧内联内容</HeroMascot>
//   具名槽 #top：右上角操作区（如「新建」按钮）
import { computed, useSlots } from 'vue'

defineOptions({ name: 'HeroMascot' })

const props = withDefaults(defineProps<{
  /** 标题（左上角大字） */
  title?: string
  /** 副标题 */
  subtitle?: string
  /** 横幅最小高度 px（PC 208 / 移动 132~150） */
  height?: number
  /** 紧凑模式（内页统一 hero：更小的内边距与字号） */
  compact?: boolean
}>(), {
  title: '',
  subtitle: '',
  height: 180,
  compact: false,
})

const emit = defineEmits<{ click: [event: MouseEvent] }>()

const slots = useSlots()
const hasExtra = computed(() => Boolean(slots.default))
const hasTop = computed(() => Boolean(slots.top))

// minHeight 需带 px 单位（v-bind 数字会生成无效值）
const heightPx = computed(() => `${props.height}px`)
</script>

<template>
  <section
    class="hero-banner"
    :class="{ 'is-compact': compact }"
    @click="emit('click', $event)"
  >
    <div class="hero-banner__body">
      <h2 v-if="title" class="hero-banner__hi">{{ title }}</h2>
      <p v-if="subtitle" class="hero-banner__sub">{{ subtitle }}</p>
      <div v-if="hasExtra" class="hero-banner__extra">
        <slot />
      </div>
    </div>

    <div v-if="hasTop" class="hero-banner__top">
      <slot name="top" />
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.hero-banner {
  position: relative;
  overflow: hidden;
  border-radius: var(--df-radius-xl);
  border: 1px solid var(--df-border-color);
  box-shadow: var(--df-shadow-card);
  min-height: v-bind(heightPx);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 34px 40px;
  // 背景色兜底 + 右侧森林小人插画（编辑部 greet-banner）
  background: var(--df-bg-nested) url('/greet-banner.jpg') right center / cover no-repeat;

  // 斜向渐变遮罩：左侧不透明表面色，向右渐隐露出插画（对齐模板 100deg 34%→76%）
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: linear-gradient(
      100deg,
      var(--df-bg-card) 34%,
      color-mix(in srgb, var(--df-bg-card) 82%, transparent) 52%,
      transparent 76%
    );
  }

  &.is-compact {
    min-height: 96px;
    padding: 22px 28px;
  }
}

.hero-banner__body {
  position: relative;
  z-index: 1;
  max-width: 62%;
}

.hero-banner__hi {
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.03em;
  font-family: var(--df-font-serif);
  color: var(--df-text-primary);
  margin: 0;

  .is-compact & {
    font-size: 24px;
  }
}

.hero-banner__sub {
  color: var(--df-text-secondary);
  font-size: 14px;
  margin: 7px 0 0;
  max-width: 460px;
  line-height: 1.5;

  .is-compact & {
    font-size: 13px;
  }
}

.hero-banner__extra {
  margin-top: 22px;

  .is-compact & {
    margin-top: 14px;
  }
}

.hero-banner__top {
  position: absolute;
  top: 22px;
  right: 26px;
  z-index: 2;
  display: flex;
  gap: 10px;
  align-items: center;
}

// ---- 移动端：更紧凑的报头 ----
@media (max-width: 768px) {
  .hero-banner {
    border-radius: var(--df-radius-lg);
    padding: 22px 22px;
  }

  .hero-banner__body {
    max-width: 70%;
  }

  .hero-banner__hi {
    font-size: 26px;
  }

  .hero-banner__top {
    top: 16px;
    right: 16px;
  }
}
</style>
