<script setup lang="ts">
// 排班运维（C4 对接真实契约）：排班 CRUD 防冲突 + 请假审核（批准自动移除区间排班）+ 车辆/设备运维登记
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/request'
import type { PageResult } from '@shared/http/types'
import {
  pageSchedules, createSchedule, deleteSchedule,
  pageLeaves, auditLeave,
  pageMaintenances, registerMaintenance,
  pageAreas,
  SHIFT_TEXT, SCHEDULE_BIZ_TEXT, LEAVE_STATUS_TEXT, MAINT_TARGET_TEXT, STAFF_TYPE_TEXT,
  type StaffSchedule, type LeaveRequest, type StaffRow, type AreaRow,
} from '@/api/business'

const activeTab = ref('schedules')
const scheduleTable = ref<{ refresh: () => void }>()
const leaveTable = ref<{ refresh: () => void }>()
const maintTable = ref<{ refresh: () => void }>()

const leaveTagType: Record<number, 'warning' | 'success' | 'danger'> = { 0: 'warning', 1: 'success', 2: 'danger' }

// ---- 排班新增/删除（后端防冲突：同员工同日同班次唯一，请假区间禁排） ----
const scheduleDialog = ref(false)
const scheduleSaving = ref(false)
const scheduleForm = reactive({ staffId: undefined as string | undefined, workDate: '', shiftType: 1, bizType: 0, supportAreaId: undefined as string | undefined, remark: '' })

// ---- 可排班员工（在岗：审核通过 + 未禁用）与片区下拉 ----
const staffOptions = ref<StaffRow[]>([])
const areaOptions = ref<AreaRow[]>([])

async function loadScheduleOptions() {
  try {
    const [staffRes, areaRes] = await Promise.all([
      request.get<PageResult<StaffRow>>('/admin/staff', {
        pageNum: 1, pageSize: 100, auditStatus: 1, status: 1,
      }),
      pageAreas({ pageNum: 1, pageSize: 999 }),
    ])
    // 后端仅按参数过滤，前端再筛在岗（status=1）保证与回收员改派同口径
    staffOptions.value = (staffRes.list ?? []).filter((s) => s.status === 1)
    areaOptions.value = areaRes.list ?? []
  } catch (e) {
    console.warn('[schedule] 排班选项加载失败:', e)
  }
}

function openScheduleCreate() {
  Object.assign(scheduleForm, { staffId: undefined, workDate: '', shiftType: 1, bizType: 0, supportAreaId: undefined, remark: '' })
  loadScheduleOptions()
  scheduleDialog.value = true
}

async function submitSchedule() {
  if (!scheduleForm.staffId || !scheduleForm.workDate) {
    ElMessage.warning('员工与排班日期必填')
    return
  }
  if (scheduleForm.bizType === 2 && !scheduleForm.supportAreaId) {
    ElMessage.warning('跨片区支援须选择支援片区')
    return
  }
  scheduleSaving.value = true
  try {
    await createSchedule({
      staffId: scheduleForm.staffId,
      workDate: scheduleForm.workDate,
      shiftType: scheduleForm.shiftType,
      bizType: scheduleForm.bizType,
      supportAreaId: scheduleForm.bizType === 2 ? scheduleForm.supportAreaId : undefined,
      remark: scheduleForm.remark || undefined,
    })
    ElMessage.success('排班已创建')
    scheduleDialog.value = false
    scheduleTable.value?.refresh()
  } finally {
    scheduleSaving.value = false
  }
}

async function removeSchedule(row: StaffSchedule) {
  await ElMessageBox.confirm(`确认删除 ${row.workDate} 的排班？`, '提示', { type: 'warning' })
  await deleteSchedule(row.id)
  ElMessage.success('已删除')
  scheduleTable.value?.refresh()
}

// ---- 请假审核（批准后同事务移除区间排班） ----
async function audit(row: LeaveRequest, approved: boolean) {
  const { value } = await ElMessageBox.prompt(
    approved
      ? `批准 ${row.startDate} ~ ${row.endDate} 请假？区间内已有排班将自动移除`
      : '驳回该请假申请？',
    '请假审核',
    { inputPlaceholder: '审核备注（可选）', inputValue: '' },
  )
  await auditLeave(row.id, approved, value || undefined)
  ElMessage.success(approved ? '已批准' : '已驳回')
  leaveTable.value?.refresh()
}

// ---- 运维登记 ----
const maintDialog = ref(false)
const maintSaving = ref(false)
const maintForm = reactive({ targetType: 1, targetNo: '', content: '', cost: undefined as number | undefined, maintDate: '' })

function openMaintCreate() {
  Object.assign(maintForm, { targetType: 1, targetNo: '', content: '', cost: undefined, maintDate: '' })
  maintDialog.value = true
}

async function submitMaint() {
  if (!maintForm.targetNo || !maintForm.content || !maintForm.maintDate) {
    ElMessage.warning('对象编号、维保内容与日期必填')
    return
  }
  maintSaving.value = true
  try {
    await registerMaintenance({ ...maintForm })
    ElMessage.success('运维记录已登记')
    maintDialog.value = false
    maintTable.value?.refresh()
  } finally {
    maintSaving.value = false
  }
}
</script>

<template>
  <div class="app-page">
    <el-tabs v-model="activeTab">
      <!-- 员工排班 -->
      <el-tab-pane label="员工排班" name="schedules">
        <TablePro ref="scheduleTable" :fetcher="pageSchedules">
          <template #search="{ query }">
            <el-form-item label="班次">
              <el-select v-model="query.shiftType" placeholder="全部" clearable style="width: 110px">
                <el-option v-for="(text, val) in SHIFT_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="'business:ops:add'" type="primary" icon="Plus" @click="openScheduleCreate">新增排班</el-button>
          </template>
          <el-table-column prop="staffId" label="员工 ID" width="120" />
          <el-table-column prop="areaId" label="片区 ID" width="110" />
          <el-table-column prop="workDate" label="日期" width="120" />
          <el-table-column label="班次" width="90">
            <template #default="{ row }">
              <el-tag :type="row.shiftType === 1 ? 'primary' : 'warning'">{{ SHIFT_TEXT[row.shiftType] ?? row.shiftType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="业务类型" width="120">
            <template #default="{ row }">
              {{ SCHEDULE_BIZ_TEXT[row.bizType] ?? row.bizType }}
            </template>
          </el-table-column>
          <el-table-column prop="supportAreaId" label="支援片区" width="100" />
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'business:ops:delete'" link type="danger" @click="removeSchedule(row as StaffSchedule)">删除</el-button>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>

      <!-- 请假审核 -->
      <el-tab-pane label="请假审核" name="leaves">
        <TablePro ref="leaveTable" :fetcher="pageLeaves">
          <template #search="{ query }">
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
                <el-option v-for="(text, val) in LEAVE_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </template>
          <el-table-column prop="staffId" label="员工 ID" width="120" />
          <el-table-column prop="startDate" label="开始日期" width="120" />
          <el-table-column prop="endDate" label="结束日期" width="120" />
          <el-table-column prop="reason" label="请假事由" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="leaveTagType[row.status] ?? 'info'">{{ LEAVE_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="auditRemark" label="审核备注" min-width="140" show-overflow-tooltip />
          <el-table-column prop="createTime" label="申请时间" width="170" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-button v-permission="'business:ops:audit'" link type="success" @click="audit(row as LeaveRequest, true)">批准</el-button>
                <el-button v-permission="'business:ops:audit'" link type="danger" @click="audit(row as LeaveRequest, false)">驳回</el-button>
              </template>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>

      <!-- 运维登记 -->
      <el-tab-pane label="运维登记" name="maintenances">
        <TablePro ref="maintTable" :fetcher="pageMaintenances">
          <template #toolbar>
            <el-button v-permission="'business:ops:add'" type="primary" icon="Plus" @click="openMaintCreate">登记维保</el-button>
          </template>
          <el-table-column label="对象类型" width="110">
            <template #default="{ row }">
              <el-tag type="info">{{ MAINT_TARGET_TEXT[row.targetType] ?? row.targetType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetNo" label="对象编号" width="140" />
          <el-table-column prop="content" label="维保内容" min-width="220" show-overflow-tooltip />
          <el-table-column prop="cost" label="费用(元)" width="100" />
          <el-table-column prop="maintDate" label="维保日期" width="120" />
          <el-table-column prop="operatorId" label="登记人 ID" width="120" />
          <el-table-column prop="createTime" label="登记时间" width="170" />
        </TablePro>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增排班弹窗 -->
    <FormDialog v-model="scheduleDialog" title="新增排班" :loading="scheduleSaving" @confirm="submitSchedule">
      <el-form :model="scheduleForm" label-width="100px">
        <el-form-item label="员工" required>
          <el-select v-model="scheduleForm.staffId" filterable placeholder="选择在岗员工（可搜索）" style="width: 240px">
            <el-option
              v-for="s in staffOptions" :key="s.id"
              :label="`${s.staffName}（${STAFF_TYPE_TEXT[s.staffType] ?? s.staffType}）${s.phone ?? ''}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排班日期" required>
          <el-date-picker v-model="scheduleForm.workDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="班次">
          <el-radio-group v-model="scheduleForm.shiftType">
            <el-radio v-for="(text, val) in SHIFT_TEXT" :key="val" :value="Number(val)">{{ text }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="scheduleForm.bizType" style="width: 180px">
            <el-option v-for="(text, val) in SCHEDULE_BIZ_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="scheduleForm.bizType === 2" label="支援片区" required>
          <el-select v-model="scheduleForm.supportAreaId" filterable placeholder="选择支援片区" style="width: 240px">
            <el-option v-for="a in areaOptions" :key="a.id" :label="a.areaName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="scheduleForm.remark" maxlength="100" placeholder="可选" />
        </el-form-item>
      </el-form>
    </FormDialog>

    <!-- 运维登记弹窗 -->
    <FormDialog v-model="maintDialog" title="登记维保" :loading="maintSaving" @confirm="submitMaint">
      <el-form :model="maintForm" label-width="100px">
        <el-form-item label="对象类型">
          <el-radio-group v-model="maintForm.targetType">
            <el-radio v-for="(text, val) in MAINT_TARGET_TEXT" :key="val" :value="Number(val)">{{ text }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="对象编号" required>
          <el-input v-model="maintForm.targetNo" maxlength="50" placeholder="车牌号 / 设备编号" />
        </el-form-item>
        <el-form-item label="维保内容" required>
          <el-input v-model="maintForm.content" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
        <el-form-item label="费用(元)">
          <el-input-number v-model="maintForm.cost" :min="0" :max="1000000" :precision="2" />
        </el-form-item>
        <el-form-item label="维保日期" required>
          <el-date-picker v-model="maintForm.maintDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>
