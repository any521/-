<template>
  <span :class="['status-badge', statusClass]">
    {{ statusText }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  status: number // 0=待审，1=已发布，2=已下架，3=被驳回
}

const props = defineProps<Props>()

const statusText = computed(() => {
  const map: Record<number, string> = { 0: '待审核', 1: '已发布', 2: '已下架', 3: '被驳回' }
  return map[props.status] || ''
})

const statusClass = computed(() => {
  const map: Record<number, string> = {
    0: 'status-pending',
    1: 'status-published',
    2: 'status-withdrawn',
    3: 'status-rejected'
  }
  return map[props.status] || ''
})
</script>

<style scoped>
.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-pending {
  background: color-mix(in srgb, var(--df-color-warning) 22%, transparent);
  color: var(--df-color-warning-dark, #8b9a3d);
}

.status-published {
  background: var(--df-color-secondary-light);
  color: var(--df-color-secondary-dark);
}

.status-withdrawn {
  background: var(--df-bg-nested);
  color: var(--df-text-regular);
}

.status-rejected {
  background: var(--df-color-danger-muted);
  color: var(--df-color-danger);
}
</style>
