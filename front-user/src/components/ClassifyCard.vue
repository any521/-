<script setup lang="ts">
// CAM 精分类结果卡片（契约 C6-8 纯展示组件）：物品名/品类/报价/建议/降级徽标
// 业务按钮（预约/积分）由 cam04 在页面层添加，本组件不含任何业务逻辑
import { computed } from 'vue'
import type { ClassifyDetailResult } from '@/api/business'
import { styleOf, DEFAULT_CATEGORY_STYLE } from '@/constants/categoryStyle'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'

const props = defineProps<{
  result: ClassifyDetailResult | null
  loading: boolean
}>()

const emit = defineEmits<{ (e: 'retry'): void }>()

/** 云端 8 类中文枚举 → 端侧 code（色标单一来源 C6-7；匹配不到走默认灰） */
const style = computed(() => {
  const cat = RECYCLE_CATEGORIES.find((c) => c.name === props.result?.category)
  return cat ? styleOf(cat.code) : DEFAULT_CATEGORY_STYLE
})

/** AR 识别只展示类别与可回收性，不展示报价（价格以实时报价页/下单页为准） */
const itemName = computed(() => props.result?.itemName?.trim() || '')
</script>

<template>
  <div class="cc" :class="{ 'cc--loading': loading && !result }">
    <!-- 识别中骨架 -->
    <template v-if="loading && !result">
      <div class="cc__skeleton cc__skeleton--title"></div>
      <div class="cc__skeleton cc__skeleton--line"></div>
      <div class="cc__skeleton cc__skeleton--line cc__skeleton--short"></div>
      <p class="cc__hint">识别中…</p>
    </template>

    <!-- 结果卡 -->
    <template v-else-if="result">
      <div class="cc__head">
        <span class="cc__name">{{ itemName || '识别结果' }}</span>
        <van-tag :color="style.color" text-color="#fff" size="medium">{{ style.label }}</van-tag>
        <van-tag v-if="result.degraded" plain type="warning">AI 降级，结果仅供参考</van-tag>
      </div>
      <p class="cc__meta">
        <span>置信 {{ Math.round((result.confidence ?? 0) * 100) }}%</span>
        <span v-if="result.purity != null">· 纯净度 {{ Math.round(result.purity * 100) }}%</span>
      </p>
      <p class="cc__advice">{{ result.advice }}</p>
      <van-button size="small" round plain class="cc__retry" @click="emit('retry')">重新识别</van-button>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.cc {
  padding: $df-space-md $df-space-lg;
  background: var(--df-bg-card);
  border-radius: $df-radius-md;

  &--loading {
    min-height: 120px;
  }

  &__head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: $df-space-sm;
  }

  &__name {
    font-size: $df-font-size-lg;
    font-weight: 600;
    color: var(--df-text-regular);
  }

  &__meta {
    margin: $df-space-xs 0;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__advice {
    margin: $df-space-xs 0 $df-space-sm;
    line-height: 1.7;
    font-size: $df-font-size-sm;
    color: var(--df-text-regular);
  }

  &__retry {
    display: inline-block;
  }

  &__skeleton {
    margin-bottom: $df-space-md;
    border-radius: $df-radius-sm;
    background: linear-gradient(90deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.14), rgba(255, 255, 255, 0.06));
    background-size: 200% 100%;
    animation: cc-shimmer 1.2s infinite;

    &--title {
      width: 45%;
      height: 20px;
    }

    &--line {
      width: 100%;
      height: 12px;
    }

    &--short {
      width: 70%;
    }
  }

  &__hint {
    margin: 0;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    text-align: center;
  }
}

@keyframes cc-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
</style>
