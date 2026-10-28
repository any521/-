<script setup lang="ts">
// 配件登记（技术员）：维修用件动态列表，登记后返回维修报告
// 后端无独立配件端点（parts 并入检修报告 DTO）：本页存本地草稿，随报告合并提交
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { formatMoney } from '@shared/utils/format'
import {
  getStaffOrderDetail, saveRepairPartsDraft, loadRepairPartsDraft,
  type StaffOrder, type RepairPart,
} from '@/api/business'

const route = useRoute()
const router = useRouter()

const orderId = String(route.params.id)
const order = ref<StaffOrder | null>(null)
const submitting = ref(false)

/** 配件行（字符串便于输入框绑定，提交时转数值） */
interface PartRow {
  name: string
  count: string
  price: string
}

const rows = ref<PartRow[]>([{ name: '', count: '1', price: '' }])

/** 常用配件快捷填入（联调期切配件字典） */
const COMMON_PARTS = ['保险管', '电源板', '电容', '加热管', '温控器', '电机碳刷']

function addRow(name = '') {
  rows.value.push({ name, count: '1', price: '' })
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
}

function quickFill(name: string) {
  // 首个空行填入，否则新增一行
  const empty = rows.value.find((r) => !r.name)
  if (empty) empty.name = name
  else addRow(name)
}

const validParts = computed<RepairPart[]>(() =>
  rows.value
    .filter((r) => r.name.trim() && Number(r.count) > 0)
    .map((r) => ({ name: r.name.trim(), count: Number(r.count), price: Number(r.price) || 0 })),
)

const totalFee = computed(() => validParts.value.reduce((s, p) => s + p.price * p.count, 0))

async function onSubmit() {
  submitting.value = true
  try {
    // 存本地草稿，提交检修报告时合并进 parts 字段（后端契约）
    saveRepairPartsDraft(orderId, validParts.value)
    showSuccessToast('配件已登记，随检修报告一并提交')
    router.replace(`/repair/${orderId}/report`)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  order.value = await getStaffOrderDetail(orderId)
  // 回显已存草稿（报告页返回修改场景）
  const draft = loadRepairPartsDraft(orderId)
  if (draft.length) {
    rows.value = draft.map((p) => ({ name: p.name, count: String(p.count), price: String(p.price || '') }))
  }
})
</script>

<template>
  <div class="page">
    <van-nav-bar title="配件登记" left-arrow @click-left="router.back()" />

    <template v-if="order">
      <div class="card">
        <div class="card__title">{{ order.title }}</div>
        <div class="parts__meta">{{ order.orderNo }} · {{ order.customerName }}</div>
      </div>

      <div class="card">
        <div class="card__title">常用配件</div>
        <div class="parts__quick">
          <van-tag
            v-for="name in COMMON_PARTS"
            :key="name"
            round
            size="large"
            type="primary"
            plain
            @click="quickFill(name)"
          >
            {{ name }}
          </van-tag>
        </div>
      </div>

      <div v-for="(row, i) in rows" :key="i" class="card parts__row">
        <div class="parts__row-head">
          <span>配件 {{ i + 1 }}</span>
          <van-icon v-if="rows.length > 1" name="delete-o" color="#f04d4d" @click="removeRow(i)" />
        </div>
        <van-field v-model="row.name" label="名称" placeholder="配件名称" />
        <van-field v-model="row.count" type="digit" label="数量" placeholder="1" />
        <van-field v-model="row.price" type="number" label="单价" placeholder="0.00" suffix="元" />
      </div>

      <van-button icon="plus" block plain type="primary" size="small" @click="addRow()">
        添加配件
      </van-button>

      <div class="parts__total">
        配件合计：<b>{{ formatMoney(totalFee) }}</b>（{{ validParts.length }} 项）
      </div>

      <van-button
        round
        block
        type="primary"
        :disabled="!validParts.length"
        :loading="submitting"
        @click="onSubmit"
      >
        确认登记
      </van-button>
    </template>
    <van-skeleton v-else title :row="8" />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.parts__meta {
  color: var(--df-text-secondary);
  font-size: $df-font-size-sm;
}

.parts__quick {
  display: flex;
  flex-wrap: wrap;
  gap: $df-space-sm;
}

.parts__row-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $df-space-sm;
  font-weight: 600;
  font-size: $df-font-size-sm;
}

.parts__total {
  margin: $df-space-md 0;
  text-align: right;
  font-size: $df-font-size-sm;

  b {
    color: var(--df-color-gold);
    font-size: $df-font-size-md;
  }
}
</style>
