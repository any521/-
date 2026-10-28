<script setup lang="ts">
// 维修工单管理（C1/C2 对接真实契约）：分页过滤 + 详情（F2 预诊/配件/科普留痕）+ 维修-回收双单溯源
import { ref } from 'vue'
import {
  pageRepairOrders, getRepairOrder, getRepairTrace,
  REPAIR_STATUS_TEXT, REPAIRABLE_TEXT,
  type RepairOrderDetail, type RepairTrace,
} from '@/api/business'

const statusTagType: Record<number, 'warning' | 'primary' | 'success' | 'info' | 'danger'> = {
  0: 'warning', 1: 'primary', 2: 'primary', 3: 'success', 4: 'info', 5: 'danger',
}
const repairableTagType: Record<number, 'info' | 'success' | 'danger'> = { 0: 'info', 1: 'success', 2: 'danger' }
const recycleStatusText: Record<number, string> = {
  0: '待接单', 1: '已接单', 2: '进行中', 3: '已完成', 4: '已取消', 5: '异常',
}

// ---- 详情弹窗 ----
const detailVisible = ref(false)
const detail = ref<RepairOrderDetail | null>(null)

async function openDetail(id: string) {
  detail.value = await getRepairOrder(id)
  detailVisible.value = true
}

// ---- 双单溯源弹窗 ----
const traceVisible = ref(false)
const trace = ref<RepairTrace | null>(null)

async function openTrace(id: string) {
  trace.value = await getRepairTrace(id)
  traceVisible.value = true
}
</script>

<template>
  <div class="app-page">
    <TablePro :fetcher="pageRepairOrders">
      <template #search="{ query }">
        <el-form-item label="工单号">
          <el-input v-model="query.orderNo" placeholder="RP 工单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(text, val) in REPAIR_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="可修复性">
          <el-select v-model="query.repairable" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(text, val) in REPAIRABLE_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
      </template>
      <el-table-column prop="orderNo" label="工单号" width="190" />
      <el-table-column prop="faultType" label="故障类型" width="110" />
      <el-table-column prop="faultDesc" label="故障描述" min-width="140" show-overflow-tooltip />
      <el-table-column label="可修复性" width="90">
        <template #default="{ row }">
          <el-tag :type="repairableTagType[row.repairable] ?? 'info'" size="small">
            {{ REPAIRABLE_TEXT[row.repairable] ?? row.repairable }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType[row.status] ?? 'info'">{{ REPAIR_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="转回收" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.recycleRef" type="danger" size="small">已联动</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button link type="warning" @click="openTrace(row.id)">溯源</el-button>
        </template>
      </el-table-column>
    </TablePro>

    <!-- 工单详情：F2 预诊 + 配件明细 + 科普留痕 -->
    <el-dialog v-model="detailVisible" title="维修工单详情" width="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.statusDesc }}</el-descriptions-item>
          <el-descriptions-item label="故障类型">{{ detail.faultType }}</el-descriptions-item>
          <el-descriptions-item label="可修复性">{{ REPAIRABLE_TEXT[detail.repairable] }}</el-descriptions-item>
          <el-descriptions-item label="故障描述" :span="2">{{ detail.faultDesc }}</el-descriptions-item>
          <el-descriptions-item label="上门地址" :span="2">{{ detail.address }}</el-descriptions-item>
          <el-descriptions-item label="上门时段" :span="2">{{ detail.appointStart }} ~ {{ detail.appointEnd }}</el-descriptions-item>
          <el-descriptions-item label="以旧换新">{{ detail.oldForNew ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="天然气配送">{{ detail.gasDelivery ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.scienceNote" label="科普留痕" :span="2">{{ detail.scienceNote }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.cancelReason" label="取消原因" :span="2">{{ detail.cancelReason }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.diagnose">
          <h4 class="repair-detail__sub">AI 预诊（F2）· 可修复概率 {{ (detail.diagnose.repairableProbability * 100).toFixed(0) }}% ·
            建议{{ detail.diagnose.suggestion === 'RECYCLE' ? '转回收' : '维修' }}</h4>
          <el-table :data="detail.diagnose.possibleFaults" size="small" border>
            <el-table-column prop="fault" label="可能故障" min-width="200" />
            <el-table-column label="置信度" width="120">
              <template #default="{ row }">{{ (row.confidence * 100).toFixed(0) }}%</template>
            </el-table-column>
          </el-table>
          <div v-if="detail.diagnose.suggestParts.length" class="repair-detail__parts">
            建议携带配件：<el-tag v-for="p in detail.diagnose.suggestParts" :key="p" size="small" class="repair-detail__tag">{{ p }}</el-tag>
          </div>
        </template>
        <el-alert v-else title="F2 预诊不可用时已降级为自由备注（AI 降级路径）" type="info" :closable="false" class="repair-detail__sub" />

        <template v-if="detail.parts.length">
          <h4 class="repair-detail__sub">配件登记</h4>
          <el-table :data="detail.parts" size="small" border>
            <el-table-column prop="partName" label="配件名称" min-width="140" />
            <el-table-column prop="partModel" label="型号" width="140" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column prop="price" label="单价(元)" width="100" />
          </el-table>
        </template>
      </template>
    </el-dialog>

    <!-- 双单溯源：维修 ↔ 回收（C2 归档，永久留存） -->
    <el-dialog v-model="traceVisible" title="维修-回收双单溯源" width="640px">
      <template v-if="trace">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="维修工单号">{{ trace.repairOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="维修状态">{{ trace.repairStatusDesc }}</el-descriptions-item>
          <el-descriptions-item label="故障类型">{{ trace.faultType }}</el-descriptions-item>
          <el-descriptions-item label="可修复性">{{ REPAIRABLE_TEXT[trace.repairable] }}</el-descriptions-item>
          <el-descriptions-item label="维修创建">{{ trace.repairCreateTime }}</el-descriptions-item>
          <el-descriptions-item label="维修完结">{{ trace.repairFinishTime ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <template v-if="trace.recycleOrderId">
          <el-divider>联动回收工单</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="回收工单号">{{ trace.recycleOrderNo }}</el-descriptions-item>
            <el-descriptions-item label="回收状态">{{ recycleStatusText[trace.recycleStatus ?? -1] ?? trace.recycleStatus }}</el-descriptions-item>
            <el-descriptions-item label="回收创建">{{ trace.recycleCreateTime }}</el-descriptions-item>
            <el-descriptions-item label="双向校验">
              <el-tag :type="trace.bidirectional ? 'success' : 'danger'" size="small">
                {{ trace.bidirectional ? 'repair_ref 双向一致' : '关联异常' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </template>
        <el-alert v-else title="该工单未发生转回收联动" type="info" :closable="false" style="margin-top: 12px" />
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.repair-detail__sub {
  margin: 14px 0 8px;
}

.repair-detail__parts {
  margin-top: 8px;
  font-size: 13px;
}

.repair-detail__tag {
  margin-right: 6px;
}
</style>
