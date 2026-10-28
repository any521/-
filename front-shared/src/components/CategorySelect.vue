<script setup lang="ts">
// 品类多选（D0 通用业务组件）：网格卡片多选，展示当日报价
import { computed } from 'vue'
import { RECYCLE_CATEGORIES, type RecycleCategory } from '../data/baseData'

const props = withDefaults(
  defineProps<{
    modelValue: string[]
    /** 自定义品类源（默认共享字典） */
    categories?: RecycleCategory[]
    /** 是否展示报价 */
    showPrice?: boolean
  }>(),
  { categories: () => RECYCLE_CATEGORIES, showPrice: true },
)
const emit = defineEmits<{ (e: 'update:modelValue', v: string[]): void }>()

const selected = computed(() => new Set(props.modelValue))

function toggle(code: string) {
  const next = new Set(selected.value)
  if (next.has(code)) next.delete(code)
  else next.add(code)
  emit('update:modelValue', [...next])
}
</script>

<template>
  <div class="df-category-select">
    <button
      v-for="c in categories"
      :key="c.code"
      type="button"
      class="df-category-select__item"
      :class="{ 'is-active': selected.has(c.code) }"
      @click="toggle(c.code)"
    >
      <span class="df-category-select__icon"><SvgIcon :name="c.icon" :size="24" /></span>
      <span class="df-category-select__name">{{ c.name }}</span>
      <span v-if="showPrice" class="df-category-select__price">¥{{ c.price.toFixed(2) }}/kg</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
@use '../styles/tokens.scss' as *;

.df-category-select {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: $df-space-sm;

  &__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $df-space-xs;
    padding: $df-space-md $df-space-xs;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-card);
    cursor: pointer;
    transition: all 0.15s;

    &.is-active {
      border-color: var(--df-color-primary);
      background: var(--df-color-primary-light);
    }
  }

  &__icon { color: var(--df-color-primary); }
  &__name { font-size: $df-font-size-sm; color: var(--df-text-primary); }
  &__price { font-size: $df-font-size-xs; color: var(--df-color-gold); }
}
</style>
