<script setup lang="ts">
// 地址簿（address-book 正式版）：省市区 + 详细地址 + 定位 + 片区，预约回收/维修/商城/跳蚤统一取用
import { onMounted, ref } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import AddressPicker from '@shared/components/AddressPicker.vue'
import { addressTextOf, type AddressValue } from '@shared/components/address'
import {
  listAddresses, saveAddress, updateAddress, removeAddress, setDefaultAddress,
  type AddressBookEntry,
} from '@/api/addressBook'

defineOptions({ name: 'address-book' })

const list = ref<AddressBookEntry[]>([])
const loading = ref(false)

const showEditor = ref(false)
const editingId = ref('')
const saving = ref(false)
const form = ref<AddressValue>({ areaCode: '', areaName: '', province: '', city: '', district: '', detail: '' })
const contactName = ref('')
const contactPhone = ref('')
const tag = ref('家')
const asDefault = ref(false)

const TAGS = ['家', '公司', '学校']

async function load() {
  loading.value = true
  try {
    list.value = await listAddresses()
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = ''
  form.value = { areaCode: '', areaName: '', province: '', city: '', district: '', detail: '' }
  contactName.value = ''
  contactPhone.value = ''
  tag.value = '家'
  asDefault.value = list.value.length === 0
  showEditor.value = true
}

function openEdit(e: AddressBookEntry) {
  editingId.value = e.id
  form.value = { ...e }
  contactName.value = e.contactName ?? ''
  contactPhone.value = e.contactPhone ?? ''
  tag.value = e.tag || '家'
  asDefault.value = e.isDefault === 1
  showEditor.value = true
}

async function onSave() {
  if ((!form.value.areaCode && !form.value.province) || !form.value.detail.trim()) {
    showToast('请填写完整地址（省市区或片区 + 详细地址）')
    return
  }
  saving.value = true
  try {
    const extra = { contactName: contactName.value, contactPhone: contactPhone.value, tag: tag.value, isDefault: asDefault.value ? 1 : 0 }
    if (editingId.value) await updateAddress(editingId.value, form.value, extra)
    else await saveAddress(form.value, extra)
    showSuccessToast('地址已保存')
    showEditor.value = false
    await load()
  } catch {
    // 保存失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    saving.value = false
  }
}

async function onDelete(e: AddressBookEntry) {
  try {
    await showConfirmDialog({ title: '删除地址', message: `确定删除「${addressTextOf(e)}」吗？` })
  } catch {
    return
  }
  try {
    await removeAddress(e.id)
    showSuccessToast('已删除')
    await load()
  } catch {
    // 删除失败：拦截器已 Toast，此处避免未捕获异常
  }
}

async function onSetDefault(e: AddressBookEntry) {
  try {
    await setDefaultAddress(e.id)
    showSuccessToast('已设为默认地址')
    await load()
  } catch {
    // 设置失败：拦截器已 Toast，此处避免未捕获异常
  }
}
</script>

<template>
  <div class="page address-book">
    <van-nav-bar title="地址簿" left-arrow @click-left="$router.back()" />

    <van-empty v-if="!loading && list.length === 0" description="还没有地址，点击下方按钮新增" />

    <div v-else class="address-book__list">
      <div v-for="e in list" :key="e.id" class="address-book__item">
        <div class="address-book__head">
          <span class="address-book__tag">{{ e.tag }}</span>
          <span v-if="e.contactName" class="address-book__contact">{{ e.contactName }}</span>
          <span v-if="e.contactPhone" class="address-book__contact">{{ e.contactPhone }}</span>
          <span v-if="e.isDefault === 1" class="address-book__default">默认</span>
        </div>
        <p class="address-book__text">{{ addressTextOf(e) }}</p>
        <div class="address-book__ops">
          <button v-if="e.isDefault !== 1" type="button" class="address-book__op" @click="onSetDefault(e)">设为默认</button>
          <button type="button" class="address-book__op" @click="openEdit(e)">编辑</button>
          <button type="button" class="address-book__op is-danger" @click="onDelete(e)">删除</button>
        </div>
      </div>
    </div>

    <div class="address-book__add">
      <van-button round block type="primary" icon="plus" @click="openCreate">新增地址</van-button>
    </div>

    <van-popup v-model:show="showEditor" position="bottom" round class="address-book__editor">
      <div class="address-book__editor-title">{{ editingId ? '编辑地址' : '新增地址' }}</div>
      <van-cell-group inset>
        <van-field v-model="contactName" label="联系人" placeholder="选填" maxlength="32" />
        <van-field v-model="contactPhone" type="tel" label="联系电话" placeholder="选填" maxlength="20" />
      </van-cell-group>
      <div class="address-book__picker">
        <AddressPicker v-model="form" locatable :saved="list" />
      </div>
      <van-cell-group inset class="address-book__tag-group">
        <van-cell title="标签" label="选填，默认「家」">
          <template #right-icon>
            <div class="address-book__tags">
              <button
                v-for="t in TAGS"
                :key="t"
                type="button"
                class="address-book__tag-chip"
                :class="{ 'is-active': tag === t }"
                @click="tag = t"
              >
                {{ t }}
              </button>
            </div>
          </template>
        </van-cell>
        <van-cell title="设为默认地址">
          <template #right-icon>
            <van-switch v-model="asDefault" size="20" />
          </template>
        </van-cell>
      </van-cell-group>
      <div class="address-book__editor-submit">
        <van-button round block type="primary" :loading="saving" @click="onSave">保存</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.address-book {
  &__list {
    display: flex;
    flex-direction: column;
    gap: $df-space-sm;
    padding: $df-space-md;
  }

  &__item {
    padding: $df-space-md;
    background: var(--df-bg-card);
    border-radius: $df-radius-lg;
    box-shadow: var(--df-shadow-card);
  }

  &__head {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
  }

  &__tag {
    padding: 1px 8px;
    border-radius: $df-radius-round;
    background: var(--df-color-primary-light);
    color: var(--df-color-primary-dark);
    font-size: 12px;
  }

  &__contact {
    color: var(--df-text-regular);
    font-size: 13px;
  }

  &__default {
    margin-left: auto;
    padding: 1px 8px;
    border-radius: $df-radius-round;
    background: var(--df-color-primary);
    color: #fff;
    font-size: 12px;
  }

  &__text {
    margin: $df-space-sm 0 0;
    color: var(--df-text-primary);
    font-size: 14px;
    line-height: 1.6;
  }

  &__ops {
    display: flex;
    justify-content: flex-end;
    gap: $df-space-sm;
    margin-top: $df-space-sm;
  }

  &__op {
    padding: 4px 12px;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-round;
    background: transparent;
    color: var(--df-text-regular);
    font-size: 13px;
    cursor: pointer;

    &.is-danger {
      color: var(--df-color-danger);
      border-color: rgba(240, 77, 77, 0.4);
    }
  }

  &__add {
    margin: $df-space-md;
  }

  &__editor {
    max-height: 86vh;
    overflow-y: auto;
    padding-bottom: calc(env(safe-area-inset-bottom) + 16px);

    &-title {
      padding: $df-space-md;
      text-align: center;
      font-size: 16px;
      font-weight: 600;
      color: var(--df-text-primary);
    }

    &-submit {
      margin: $df-space-md;
    }
  }

  &__picker {
    padding: $df-space-md;
  }

  &__tag-group {
    margin: 0 $df-space-md;
  }

  &__tags {
    display: flex;
    gap: $df-space-xs;
  }

  &__tag-chip {
    padding: 3px 10px;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-round;
    background: transparent;
    font-size: 13px;
    color: var(--df-text-regular);
    cursor: pointer;

    &.is-active {
      border-color: var(--df-color-primary);
      color: var(--df-color-primary-dark);
      background: var(--df-color-primary-light);
    }
  }
}
</style>
