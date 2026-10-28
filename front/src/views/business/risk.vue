<script setup lang="ts">
// 风控预警（C4 对接真实契约）：告警复核（解冻发放/驳回扣除）+ 恶意刷单检测
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageRiskAlerts, reviewRiskAlert, brushCheck,
  RISK_STATUS_TEXT,
  type RiskAlert, type BrushRisk,
} from '@/api/business'

const activeTab = ref('alerts')
const alertTable = ref<{ refresh: () => void }>()

const statusTagType: Record<number, 'warning' | 'success' | 'danger'> = { 0: 'warning', 1: 'success', 2: 'danger' }

// ---- 告警复核：通过=解冻发放，驳回=违规扣除 ----
async function review(row: RiskAlert, pass: boolean) {
  await ElMessageBox.confirm(
    pass
      ? `确认通过复核？将解冻并发放 ${row.frozenPoints} 冻结积分`
      : `确认驳回？冻结的 ${row.frozenPoints} 积分将按违规扣除`,
    `告警复核 · ${row.reason}`,
    { type: pass ? 'success' : 'warning' },
  )
  await reviewRiskAlert(row.id, pass)
  ElMessage.success(pass ? '已通过，积分已解冻发放' : '已驳回，积分已扣除')
  alertTable.value?.refresh()
}

// ---- 恶意刷单检测（brush-check 高频兑换聚合） ----
const brushForm = reactive({ hours: 24, threshold: 5 })
const brushLoading = ref(false)
const brushList = ref<BrushRisk[]>([])
const brushChecked = ref(false)

async function runBrushCheck() {
  brushLoading.value = true
  try {
    brushList.value = await brushCheck(brushForm.hours, brushForm.threshold)
    brushChecked.value = true
  } finally {
    brushLoading.value = false
  }
}
</script>

<template>
  <div class="app-page">
    <el-tabs v-model="activeTab">
      <!-- 告警复核 -->
      <el-tab-pane label="告警复核" name="alerts">
        <TablePro ref="alertTable" :fetcher="pageRiskAlerts">
          <template #search="{ query }">
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
                <el-option v-for="(text, val) in RISK_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </template>
          <el-table-column prop="bizType" label="业务类型" width="110" />
          <el-table-column prop="bizId" label="业务单 ID" width="150" show-overflow-tooltip />
          <el-table-column prop="userId" label="用户 ID" width="120" show-overflow-tooltip />
          <el-table-column prop="reason" label="告警原因" min-width="200" show-overflow-tooltip />
          <el-table-column label="冻结积分" width="100">
            <template #default="{ row }">
              <span style="color: #e8bc3f; font-weight: 600">{{ row.frozenPoints }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType[row.status] ?? 'info'">{{ RISK_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="触发时间" width="170" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-button v-permission="'business:risk:audit'" link type="success" @click="review(row as RiskAlert, true)">通过</el-button>
                <el-button v-permission="'business:risk:audit'" link type="danger" @click="review(row as RiskAlert, false)">驳回</el-button>
              </template>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>

      <!-- 刷单检测 -->
      <el-tab-pane label="刷单检测" name="brush">
        <el-form inline>
          <el-form-item label="时间窗(小时)">
            <el-input-number v-model="brushForm.hours" :min="1" :max="720" />
          </el-form-item>
          <el-form-item label="兑换次数阈值">
            <el-input-number v-model="brushForm.threshold" :min="2" :max="100" />
          </el-form-item>
          <el-form-item>
            <el-button v-permission="'business:risk:audit'" type="primary" :loading="brushLoading" @click="runBrushCheck">
              执行检测
            </el-button>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="brushChecked && !brushList.length" type="success" :closable="false"
          title="未检出高频兑换风险用户" show-icon
        />
        <el-table v-else :data="brushList" border stripe>
          <el-table-column prop="userId" label="用户 ID" width="160" />
          <el-table-column prop="orderCount" label="窗口内兑换次数" width="140" />
          <el-table-column prop="totalPoints" label="累计消耗积分" width="140" />
          <el-table-column label="风险提示" min-width="200">
            <template #default="{ row }">
              <el-tag type="danger">高频兑换 · {{ row.orderCount }} 次 / {{ brushForm.hours }}h</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
