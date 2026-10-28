<template>
  <svg
    :style="styleValue"
    aria-hidden="true"
    class="svg-icon"
  >
    <use :xlink:href="symbolId" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CSSProperties } from 'vue'

interface Props {
  /** 图标名称（文件名，kebab-case） */
  name: string
  /** 图标颜色，默认继承当前文本色 */
  color?: string
  /** 图标尺寸，支持数字（px）或字符串（如 '2rem'） */
  size?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  color: 'currentColor',
  size: 24,
})

const symbolId = computed(() => `#icon-${props.name}`)

const sizeValue = computed(() => {
  return typeof props.size === 'number' ? `${props.size}px` : props.size
})

// 图标源为 Lucide 风格线性图标（fill="none" + stroke="currentColor"），
// 颜色/线宽通过 CSS 注入，保证 <use> 引用 sprite 后能正确继承
const styleValue = computed<CSSProperties>(() => ({
  width: sizeValue.value,
  height: sizeValue.value,
  fill: 'none',
  stroke: props.color,
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
}))
</script>

<style scoped>
.svg-icon {
  display: inline-block;
  vertical-align: middle;
}
</style>
