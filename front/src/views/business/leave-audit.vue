<script setup lang="ts">
// 请假审核（B3，G8）：独立审核页 —— 状态过滤 + 分页表格 + FormDialog 审核（通过后同事务移除区间排班）
// 对接 AdminOpsController：GET /admin/ops/leaves（待审优先排序）、PUT /admin/ops/leaves/{id}/audit
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { auditLeave, LEAVE_STATUS_TEXT, pageLeaves, type LeaveRequest } from '@/api/business'

const table = ref<{ refresh: () => void }>()

const leaveTagType: Record<number, 'warning' | 'success' | 'danger'> = { 0: 'warning', 1: 'success', 2: 'danger' }

// ---- 审核弹窗（通过/驳回 + 备注，驳回必填） ----
const dialog = ref(false)
const saving = ref(false)
const current = ref<LeaveRequest | null>(null)
const auditForm = reactive({ approved: true, remark: '' })

function openAudit(row: LeaveRequest) {
  current.value = row
  auditForm.approved = true
  auditForm.remark = ''
  dialog.value = true
}

async function submitAudit() {
  if (!current.value) return
  if (!auditForm.approved && !auditForm.remark.trim()) {
    ElMessage.warning('驳回时请填写驳回原因')
    return
  }
  saving.value = true
  try {
    await auditLeave(current.value.id, auditForm.approved, auditForm.remark.trim() || undefined)
    ElMessage.success(
      auditForm.approved ? '已通过，排班冲突已自动移除' : '已驳回',
    )
    dialog.value = false
    table.value?.refresh()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="table" :fetcher="pageLeaves">
      <template #search="{ query }">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(text, val) in LEAVE_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
      </template>
      <template #toolbar="{ refresh }">
        <el-button icon="Refresh" @click="refresh">刷新</el-button>
      </template>
      <el-table-column prop="staffId" label="员工 ID" width="130" />
      <el-table-column label="请假区间" width="220">
        <template #default="{ row }">
          {{ row.startDate }} ~ {{ row.endDate }}
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="请假事由" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="leaveTagType[row.status] ?? 'info'">{{ LEAVE_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditRemark" label="审核备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 0"
            v-permission="'business:ops:audit'"
            link
            type="primary"
            @click="openAudit(row as LeaveRequest)"
          >
            审核
          </el-button>
        </template>
      </el-table-column>
    </TablePro>

    <!-- 审核弹窗：通过/驳回 + 备注（驳回必填） -->
    <FormDialog v-model="dialog" title="请假审核" :loading="saving" @confirm="submitAudit">
      <el-form label-width="90px">
        <el-form-item label="请假区间">
          <span>{{ current?.startDate }} ~ {{ current?.endDate }}</span>
        </el-form-item>
        <el-form-item label="请假事由">
          <span>{{ current?.reason }}</span>
        </el-form-item>
        <el-form-item label="审核结论" required>
          <el-radio-group v-model="auditForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
          <div v-if="auditForm.approved" class="leave-audit__tip">通过后请假区间内已有排班将自动移除</div>
        </el-form-item>
        <el-form-item :label="auditForm.approved ? '备注' : '驳回原因'" :required="!auditForm.approved">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            :placeholder="auditForm.approved ? '审核备注（可选）' : '请填写驳回原因（必填）'"
          />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.leave-audit__tip {
  margin-top: 4px;
  color: var(--df-color-warning);
  font-size: $df-font-size-sm;
}
</style>
