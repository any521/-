<script setup lang="ts">
// 预约时间选择器（回收/维修 × 移动/PC 四页统一）：
// 日期 → van-calendar 日历弹窗；时段 → 横向滑动菜单（scroll-snap 左右滑动选择）
import { ref, computed } from 'vue'
import { TIME_SLOTS } from '@shared/index'

const props = defineProps<{ date: string; timeSlot: string }>()
const emit = defineEmits<{
  (e: 'update:date', v: string): void
  (e: 'update:timeSlot', v: string): void
}>()

const showCal = ref(false)
/** 未来 7 天可约（与原日期 chips 范围一致） */
const minDate = new Date(Date.now() + 86400000)
const maxDate = new Date(Date.now() + 7 * 86400000)

const dateText = computed(() => {
  if (!props.date) return ''
  const d = new Date(`${props.date}T00:00:00`)
  if (Number.isNaN(d.getTime())) return props.date
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日（周${week}）`
})

const defaultDate = computed(() => {
  if (props.date) {
    const d = new Date(`${props.date}T00:00:00`)
    if (!Number.isNaN(d.getTime())) return d
  }
  return minDate
})

function onConfirm(d: Date) {
  const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  emit('update:date', iso)
  showCal.value = false
}
</script>

<template>
  <div class="appoint">
    <div class="appoint__label">日期</div>
    <div class="appoint__date-field" role="button" @click="showCal = true">
      <span v-if="date" class="appoint__date-text">{{ dateText }}</span>
      <span v-else class="appoint__placeholder">点击选择预约日期</span>
      <SvgIcon name="common-arrow-right" :size="16" />
    </div>
    <van-calendar
      v-model:show="showCal"
      title="选择预约日期"
      :min-date="minDate"
      :max-date="maxDate"
      :default-date="defaultDate"
      @confirm="onConfirm"
    />

    <div class="appoint__label">时段（左右滑动选择）</div>
    <div class="appoint__slide">
      <div
        v-for="s in TIME_SLOTS"
        :key="s"
        class="appoint__slide-item"
        :class="{ active: timeSlot === s }"
        role="button"
        @click="emit('update:timeSlot', s)"
      >
        {{ s }}
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.appoint {
  &__label {
    margin: $df-space-sm 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__date-field {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-card);
    cursor: pointer;
    font-size: $df-font-size-md;
    color: var(--df-text-primary);

    &:hover {
      border-color: var(--df-color-primary);
    }
  }

  &__placeholder {
    color: var(--df-text-secondary);
  }

  &__slide {
    display: flex;
    gap: $df-space-sm;
    overflow-x: auto;
    scroll-snap-type: x mandatory;
    -webkit-overflow-scrolling: touch;
    padding-bottom: 4px;
  }

  &__slide-item {
    flex: none;
    scroll-snap-align: start;
    padding: 10px 22px;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-card);
    cursor: pointer;
    font-size: $df-font-size-sm;
    white-space: nowrap;
    color: var(--df-text-primary);

    &.active {
      border-color: var(--df-color-primary);
      background: var(--df-color-primary-light);
      color: var(--df-color-primary-dark);
      font-weight: 600;
    }
  }
}
</style>
