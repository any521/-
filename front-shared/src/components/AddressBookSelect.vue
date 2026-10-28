<script setup lang="ts">
// ============================================================
// 地址簿下拉选择器（address-book 正式版配套组件）：
// 预约回收/维修等场景「从地址簿选地址」——点击弹出地址列表，选中即回填
// AddressValue（含片区/经纬度）；底部「管理地址簿」由页面路由跳转（emit open-book）。
// 纯原生自绘（df 设计令牌），不依赖 vant/宿主 UI 库；地址为空展示引导空态。
// ============================================================
import { computed, ref } from 'vue'
import type { AddressValue } from './address'
import { addressTextOf } from './address'

/** 地址簿条目（结构兼容各端 AddressBookEntry；front-shared 不依赖各端私有 API） */
interface AddressBookOption extends AddressValue {
  id?: string
  tag?: string
  /** 0非默认 1默认 */
  isDefault?: number
}

const props = withDefaults(
  defineProps<{
    modelValue: AddressValue | null
    /** 地址簿列表；null=加载中/失败 */
    addresses?: AddressBookOption[] | null
    /** 选择框占位文案 */
    placeholder?: string
  }>(),
  { addresses: null, placeholder: '请选择地址簿中的地址' },
)
const emit = defineEmits<{
  (e: 'update:modelValue', v: AddressValue): void
  (e: 'open-book'): void
}>()

const open = ref(false)

const currentText = computed(() => (props.modelValue ? addressTextOf(props.modelValue) : ''))

/** 仅展示非默认标签（家不展示，默认条目有独立角标） */
const tagOf = (a: AddressBookOption) => (a.tag && a.tag !== '家' ? a.tag : '')

function select(a: AddressBookOption) {
  emit('update:modelValue', {
    areaCode: a.areaCode ?? '',
    areaName: a.areaName ?? '',
    province: a.province ?? '',
    city: a.city ?? '',
    district: a.district ?? '',
    detail: a.detail ?? '',
    lng: a.lng ?? null,
    lat: a.lat ?? null,
  })
  open.value = false
}

function openBook() {
  open.value = false
  emit('open-book')
}
</script>

<template>
  <div class="df-address-book-select">
    <!-- 选择框：当前地址 / 占位 -->
    <button
      type="button"
      class="df-address-book-select__trigger"
      :class="{ 'is-empty': !currentText }"
      @click="open = true"
    >
      <span v-if="currentText" class="df-address-book-select__text">{{ currentText }}</span>
      <span v-else class="df-address-book-select__placeholder">{{ placeholder }}</span>
      <span class="df-address-book-select__caret">▾</span>
    </button>

    <!-- 弹层：地址列表 + 管理入口 -->
    <teleport to="body">
      <div v-if="open" class="df-address-book-select__mask" @click.self="open = false">
        <div class="df-address-book-select__panel">
          <div class="df-address-book-select__head">
            <span>选择地址</span>
            <button type="button" class="df-address-book-select__close" @click="open = false">×</button>
          </div>

          <div v-if="!addresses || addresses.length === 0" class="df-address-book-select__empty">
            {{ addresses ? '地址簿暂无地址，请先添加' : '地址加载中…' }}
          </div>

          <div v-else class="df-address-book-select__list">
            <button
              v-for="a in addresses"
              :key="a.id ?? addressTextOf(a)"
              type="button"
              class="df-address-book-select__item"
              :class="{ 'is-active': currentText === addressTextOf(a) }"
              @click="select(a)"
            >
              <span v-if="tagOf(a) || a.isDefault" class="df-address-book-select__badges">
                <em v-if="tagOf(a)" class="df-address-book-select__tag">{{ tagOf(a) }}</em>
                <em v-if="a.isDefault" class="df-address-book-select__tag is-default">默认</em>
              </span>
              <span class="df-address-book-select__item-text">{{ addressTextOf(a) }}</span>
            </button>
          </div>

          <button type="button" class="df-address-book-select__manage" @click="openBook">管理地址簿</button>
        </div>
      </div>
    </teleport>
  </div>
</template>

<style scoped lang="scss">
@use '../styles/tokens.scss' as *;

.df-address-book-select {
  // flex 容器中占满剩余宽度且允许收缩：否则长地址（nowrap）会撑破屏幕
  flex: 1;
  min-width: 0;

  &__trigger {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    width: 100%;
    padding: $df-space-sm $df-space-md;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-card);
    text-align: left;
    cursor: pointer;

    &:hover,
    &:focus-visible {
      outline: none;
      border-color: var(--df-color-primary);
    }
  }

  &__text {
    flex: 1;
    min-width: 0;
    font-size: $df-font-size-base;
    color: var(--df-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__placeholder {
    flex: 1;
    font-size: $df-font-size-base;
    color: var(--df-text-placeholder);
  }

  &__caret {
    flex-shrink: 0;
    font-size: 10px;
    color: $df-text-placeholder;
  }

  &__mask {
    position: fixed;
    inset: 0;
    z-index: 3000;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.4);
    padding: $df-space-md;
  }

  &__panel {
    display: flex;
    flex-direction: column;
    width: 100%;
    max-width: 480px;
    max-height: 70vh;
    background: var(--df-bg-card);
    border-radius: $df-radius-md;
    overflow: hidden;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $df-space-sm $df-space-md;
    border-bottom: 1px solid var(--df-border-color);
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__close {
    border: none;
    background: none;
    font-size: 20px;
    line-height: 1;
    color: var(--df-text-secondary);
    cursor: pointer;
  }

  &__empty {
    padding: $df-space-lg $df-space-md;
    text-align: center;
    font-size: $df-font-size-sm;
    color: var(--df-text-placeholder);
  }

  &__list {
    flex: 1;
    overflow-y: auto;
    padding: $df-space-sm;
    display: flex;
    flex-direction: column;
    gap: $df-space-xs;
  }

  &__item {
    display: flex;
    align-items: flex-start;
    gap: $df-space-sm;
    padding: $df-space-sm $df-space-md;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-page);
    text-align: left;
    cursor: pointer;

    &:active {
      border-color: var(--df-color-primary);
    }

    &.is-active {
      border-color: var(--df-color-primary);
      background: var(--df-color-primary-light);
    }
  }

  &__badges {
    display: flex;
    flex-direction: column;
    gap: $df-space-xs;
    flex-shrink: 0;
  }

  &__tag {
    display: inline-block;
    padding: 1px 8px;
    border-radius: $df-radius-round;
    background: var(--df-color-primary-light);
    font-size: $df-font-size-sm;
    font-style: normal;
    color: var(--df-color-primary-dark);

    &.is-default {
      background: var(--df-color-gold);
      color: #fff;
    }
  }

  &__item-text {
    flex: 1;
    min-width: 0;
    font-size: $df-font-size-sm;
    line-height: 1.5;
    color: var(--df-text-regular);
    word-break: break-all;
  }

  &__manage {
    margin: $df-space-sm;
    padding: $df-space-sm;
    border: 1px dashed var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-page);
    font-size: $df-font-size-sm;
    color: var(--df-color-primary-dark);
    cursor: pointer;

    &:active {
      border-color: var(--df-color-primary);
    }
  }
}
</style>
