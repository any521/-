<script setup lang="ts">
// 售后处理（C4 对接真实契约）：投诉/退款/追责/补发积分/纠纷仲裁 受理与驳回
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageComplaints, handleComplaint,
  COMPLAINT_TYPE_TEXT, COMPLAINT_STATUS_TEXT,
  type Complaint,
} from '@/api/business'

const table = ref<{ refresh: () => void }>()

const statusTagType: Record<number, 'warning' | 'success' | 'danger'> = { 0: 'warning', 1: 'success', 2: 'danger' }
const ORDER_TYPE_TEXT: Record<string, string> = { RECYCLE: '回收单', KITCHEN: '垃圾投放', REPAIR: '维修单', MALL: '兑换单' }

// ---- 处置弹窗（受理可选补发/追责积分，走碳账本同事务留痕） ----
const dialogVisible = ref(false)
const submitting = ref(false)
const current = ref<Complaint | null>(null)
const form = reactive({ approved: true, handleResult: '', compensatePoints: 0, deductPoints: 0 })

function openHandle(row: Complaint) {
  current.value = row
  Object.assign(form, { approved: true, handleResult: '', compensatePoints: 0, deductPoints: 0 })
  dialogVisible.value = true
}

async function submitHandle() {
  if (!form.handleResult.trim()) {
    ElMessage.warning('请填写处理结论')
    return
  }
  submitting.value = true
  try {
    await handleComplaint(current.value!.id, {
      approved: form.approved,
      handleResult: form.handleResult,
      compensatePoints: form.approved && form.compensatePoints > 0 ? form.compensatePoints : undefined,
      deductPoints: form.approved && form.deductPoints > 0 ? form.deductPoints : undefined,
    })
    ElMessage.success(form.approved ? '已受理处置' : '已驳回')
    dialogVisible.value = false
    table.value?.refresh()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="table" :fetcher="pageComplaints">
      <template #search="{ query }">
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(text, val) in COMPLAINT_TYPE_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(text, val) in COMPLAINT_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
      </template>
      <el-table-column label="业务单" width="110">
        <template #default="{ row }">
          <el-tag type="info">{{ ORDER_TYPE_TEXT[row.orderType] ?? row.orderType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderId" label="订单 ID" width="150" show-overflow-tooltip />
      <el-table-column label="售后类型" width="100">
        <template #default="{ row }">
          {{ COMPLAINT_TYPE_TEXT[row.type] ?? row.type }}
        </template>
      </el-table-column>
      <el-table-column prop="content" label="诉求内容" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType[row.status] ?? 'info'">{{ COMPLAINT_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handleResult" label="处理结论" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="提交时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" v-permission="'business:aftersale:handle'" link type="primary" @click="openHandle(row as Complaint)">
            处置
          </el-button>
        </template>
      </el-table-column>
    </TablePro>

    <!-- 处置弹窗 -->
    <FormDialog v-model="dialogVisible" title="售后处置" :loading="submitting" @confirm="submitHandle">
      <el-descriptions v-if="current" :column="1" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="售后类型">{{ COMPLAINT_TYPE_TEXT[current.type] ?? current.type }}</el-descriptions-item>
        <el-descriptions-item label="诉求内容">{{ current.content }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="form" label-width="90px">
        <el-form-item label="处置结果">
          <el-radio-group v-model="form.approved">
            <el-radio :value="true">受理</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理结论" required>
          <el-input v-model="form.handleResult" type="textarea" :rows="3" maxlength="200" placeholder="处理说明（必填）" />
        </el-form-item>
        <template v-if="form.approved">
          <el-form-item label="补发积分">
            <el-input-number v-model="form.compensatePoints" :min="0" :max="100000" />
            <span class="form-tip">补偿用户，走账本 REISSUE 留痕</span>
          </el-form-item>
          <el-form-item label="追责扣分">
            <el-input-number v-model="form.deductPoints" :min="0" :max="100000" />
            <span class="form-tip">责任方扣除，走账本 DEDUCT 留痕</span>
          </el-form-item>
        </template>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped>
.form-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
