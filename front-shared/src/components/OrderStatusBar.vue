<script setup lang="ts">
// 订单状态条（D0 通用业务组件）：按回收订单状态机渲染步骤条
import { computed } from 'vue'
import { OrderStatus, ORDER_STATUS_TEXT } from '../http/types'

const props = defineProps<{ status: OrderStatus }>()

/** 正常流转步骤（取消/异常单独展示终态） */
const NORMAL_FLOW = [OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.PROCESSING, OrderStatus.FINISHED]

const isTerminatedAbnormally = computed(
  () => props.status === OrderStatus.CANCELED || props.status === OrderStatus.ABNORMAL,
)

const steps = computed(() =>
  NORMAL_FLOW.map((s, i) => ({
    label: ORDER_STATUS_TEXT[s],
    done: !isTerminatedAbnormally.value && NORMAL_FLOW.indexOf(props.status) >= i,
  })),
)
</script>

<template>
  <div class="df-order-status">
    <template v-if="isTerminatedAbnormally">
      <span class="df-order-status__terminal" :class="status === 5 ? 'is-abnormal' : 'is-canceled'">
        {{ ORDER_STATUS_TEXT[status] }}
      </span>
    </template>
    <template v-else>
      <div v-for="(s, i) in steps" :key="i" class="df-order-status__step" :class="{ 'is-done': s.done }">
        <span class="df-order-status__dot" />
        <span class="df-order-status__label">{{ s.label }}</span>
        <span v-if="i < steps.length - 1" class="df-order-status__line" />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '../styles/tokens.scss' as *;

.df-order-status {
  display: flex;
  align-items: center;

  &__step {
    display: flex;
    align-items: center;
    flex: 1;

    &:last-child { flex: 0; }
  }

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: $df-border-color;
    flex-shrink: 0;
  }

  &__label {
    margin: 0 $df-space-xs;
    font-size: $df-font-size-xs;
    color: $df-text-secondary;
    white-space: nowrap;
  }

  &__line {
    flex: 1;
    height: 2px;
    background: $df-border-color;
    margin-right: $df-space-xs;
  }

  &__step.is-done {
    .df-order-status__dot { background: $df-color-primary; }
    .df-order-status__label { color: $df-color-primary-dark; }
    .df-order-status__line { background: $df-color-primary; }
  }

  &__terminal {
    padding: 2px $df-space-md;
    border-radius: $df-radius-round;
    font-size: $df-font-size-sm;

    &.is-canceled { background: $df-bg-page; color: $df-text-secondary; }
    &.is-abnormal { background: #fdeaea; color: $df-color-danger; }
  }
}
</style>
