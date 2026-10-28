<script setup lang="ts">
// PC 地址簿：省市区 + 详细地址 + 定位 + 片区，预约回收/维修/商城/跳蚤统一取用
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import AddressPicker from '@shared/components/AddressPicker.vue'
import { addressTextOf, type AddressValue } from '@shared/components/address'
import {
  listAddresses, saveAddress, updateAddress, removeAddress, setDefaultAddress,
  type AddressBookEntry,
} from '@/api/addressBook'

defineOptions({ name: 'pc-address-book' })

const router = useRouter()

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
  await removeAddress(e.id)
  showSuccessToast('已删除')
  await load()
}

async function onSetDefault(e: AddressBookEntry) {
  await setDefaultAddress(e.id)
  showSuccessToast('已设为默认地址')
  await load()
}
</script>

<template>
  <div class="pc-address-book">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">地址簿</h1>
        <button class="add-btn" @click="openCreate">
          <SvgIcon name="common-plus" :size="16" /> 新增地址
        </button>
      </div>

      <div v-if="!loading && list.length === 0" class="empty">
        <SvgIcon name="nav-location-pin" :size="48" />
        <p>还没有地址，点击右上角「新增地址」添加常用地址</p>
      </div>

      <div v-else class="list">
        <div v-for="e in list" :key="e.id" class="card">
          <div class="card__head">
            <span class="card__tag">{{ e.tag }}</span>
            <span v-if="e.contactName" class="card__contact">{{ e.contactName }}</span>
            <span v-if="e.contactPhone" class="card__contact">{{ e.contactPhone }}</span>
            <span v-if="e.isDefault === 1" class="card__default">默认</span>
          </div>
          <p class="card__text">{{ addressTextOf(e) }}</p>
          <div class="card__ops">
            <button v-if="e.isDefault !== 1" type="button" class="op" @click="onSetDefault(e)">设为默认</button>
            <button type="button" class="op" @click="openEdit(e)">编辑</button>
            <button type="button" class="op is-danger" @click="onDelete(e)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <van-popup v-model:show="showEditor" class="editor" round>
      <div class="editor__title">{{ editingId ? '编辑地址' : '新增地址' }}</div>
      <div class="editor__form">
        <div class="form-row">
          <label class="form-label">联系人</label>
          <input v-model="contactName" class="form-input" placeholder="选填" maxlength="32" />
        </div>
        <div class="form-row">
          <label class="form-label">联系电话</label>
          <input v-model="contactPhone" class="form-input" placeholder="选填" maxlength="20" />
        </div>
        <div class="form-row form-row--block">
          <label class="form-label">地址</label>
          <AddressPicker v-model="form" locatable :saved="list" />
        </div>
        <div class="form-row">
          <label class="form-label">标签</label>
          <div class="tags">
            <button
              v-for="t in TAGS"
              :key="t"
              type="button"
              class="tag-chip"
              :class="{ 'is-active': tag === t }"
              @click="tag = t"
            >
              {{ t }}
            </button>
          </div>
        </div>
        <div class="form-row">
          <label class="form-label">设为默认</label>
          <van-switch v-model="asDefault" size="20" />
        </div>
      </div>
      <div class="editor__submit">
        <button class="submit-btn" :disabled="saving" @click="onSave">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.pc-address-book {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 860px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: 1px solid var(--df-border-color);
  border-radius: 8px;
  background: var(--df-bg-card);
  color: var(--df-text-regular);
  font-size: 14px;
  cursor: pointer;

  &:hover { color: var(--df-color-primary); border-color: var(--df-color-primary); }
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin: 0;
}

.add-btn {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;

  &:hover { opacity: 0.9; }
}

.empty {
  padding: 80px 0;
  text-align: center;
  color: var(--df-text-tertiary);
}

.list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}

.card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 20px;
  box-shadow: var(--df-shadow-card);

  &__head {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__tag {
    padding: 2px 10px;
    border-radius: 999px;
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
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--df-color-primary);
    color: #fff;
    font-size: 12px;
  }

  &__text {
    margin: 12px 0 0;
    color: var(--df-text-primary);
    font-size: 14px;
    line-height: 1.6;
  }

  &__ops {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 14px;
  }

  .op {
    padding: 5px 14px;
    border: 1px solid var(--df-border-color);
    border-radius: 999px;
    background: transparent;
    color: var(--df-text-regular);
    font-size: 13px;
    cursor: pointer;

    &.is-danger {
      color: var(--df-color-danger);
      border-color: rgba(240, 77, 77, 0.4);
    }

    &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
  }
}

.editor {
  width: min(640px, 92vw);
  max-height: 88vh;
  overflow-y: auto;
  padding: 24px;

  &__title {
    text-align: center;
    font-size: 18px;
    font-weight: 700;
    color: var(--df-text-primary);
    margin-bottom: 16px;
  }

  &__submit {
    margin-top: 20px;
  }
}

.form-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--df-border-color);

  &--block {
    align-items: flex-start;
    flex-direction: column;
  }
}

.form-label {
  flex-shrink: 0;
  width: 76px;
  color: var(--df-text-regular);
  font-size: 14px;
}

.form-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--df-text-primary);
  font-size: 14px;

  &::placeholder { color: var(--df-text-tertiary); }
}

.tags {
  display: flex;
  gap: 10px;
}

.tag-chip {
  padding: 4px 14px;
  border: 1px solid var(--df-border-color);
  border-radius: 999px;
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

.submit-btn {
  width: 100%;
  padding: 12px 0;
  border: none;
  border-radius: 10px;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;

  &:hover { opacity: 0.9; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
</style>
