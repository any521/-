<script setup lang="ts">
// 垃圾投放与清运任务（B4）：投放记录只读查看；清运任务由管理员发布（片区/日期/袋数）+ 列表 + 改派
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageKitchenRecords, pageKitchenTasks, publishKitchenTask, reassignKitchenTask,
  pageAreas, pageStaff, listKitchenVehicles,
  KITCHEN_TASK_STATUS_TEXT,
  type KitchenRecord, type KitchenTaskRow, type KitchenVehicle,
} from '@/api/business'

const auditTagType = {
  待审核: 'warning',
  已通过: 'success',
  已复核: 'primary',
  已驳回: 'danger',
} as const

const activeTab = ref('records')
const taskTable = ref<{ refresh: () => void }>()

// ---- 投放记录 ----
const detailVisible = ref(false)
const current = ref<KitchenRecord | null>(null)

function openDetail(row: KitchenRecord) {
  current.value = row
  detailVisible.value = true
}

// ---- 清运任务：基础数据（片区/车辆/环卫员映射） ----
const areas = ref<{ id: string; areaName: string; status: number }[]>([])
const vehicles = ref<KitchenVehicle[]>([])
const sanitationMap = ref<Record<string, string>>({})

const vehicleNoOf = (id?: string | null) => (id ? vehicles.value.find((v) => v.id === id)?.vehicleNo : undefined)
const staffNameOf = (id?: string | null) => (id ? sanitationMap.value[id] : undefined)

async function loadBase() {
  try {
    const [areaPage, vehs, staffPage] = await Promise.all([
      pageAreas({ pageNum: 1, pageSize: 999 }),
      listKitchenVehicles().catch(() => [] as KitchenVehicle[]),
      pageStaff({ pageNum: 1, pageSize: 999, staffType: 2 }),
    ])
    areas.value = areaPage.list ?? []
    vehicles.value = vehs
    sanitationMap.value = Object.fromEntries(
      (staffPage.list ?? []).map((s) => [s.id, s.staffName]),
    )
  } catch {
    // 基础数据加载失败：下拉为空仍可发布（后端会校验），不阻塞页面
  }
}
onMounted(loadBase)

// ---- 发布清运任务 ----
const publishVisible = ref(false)
const publishing = ref(false)
const publishForm = reactive({ areaId: undefined as string | undefined, taskDate: '', totalBags: 100 })

function openPublish() {
  Object.assign(publishForm, { areaId: undefined, taskDate: '', totalBags: 100 })
  publishVisible.value = true
}

async function submitPublish() {
  if (!publishForm.areaId) {
    ElMessage.warning('请选择片区')
    return
  }
  if (!publishForm.taskDate) {
    ElMessage.warning('请选择清运日期')
    return
  }
  if (!publishForm.totalBags || publishForm.totalBags < 1) {
    ElMessage.warning('清运袋数至少 1 袋')
    return
  }
  publishing.value = true
  try {
    const task = await publishKitchenTask({
      areaId: publishForm.areaId,
      taskDate: publishForm.taskDate,
      totalBags: publishForm.totalBags,
    })
    ElMessage.success(`已发布清运任务（${task.taskDate} 共 ${task.totalBags} 袋）`)
    publishVisible.value = false
    taskTable.value?.refresh()
  } finally {
    publishing.value = false
  }
}

// ---- 改派（仅待执行：可改车辆/环卫员） ----
async function reassign(row: KitchenTaskRow) {
  if (row.status !== 0) return
  const pick = await import('element-plus').then(({ ElMessageBox }) =>
    ElMessageBox.prompt(
      `为 ${row.areaName ?? `片区${row.areaId}`} ${row.taskDate} 的清运任务改派环卫员（可选，留空不改）`,
      '改派环卫员',
      { inputPlaceholder: '环卫员姓名', inputValue: staffNameOf(row.staffId) ?? '' },
    ).catch(() => null),
  )
  if (!pick) return
  const name = (pick.value ?? '').trim()
  const staff = Object.entries(sanitationMap.value).find(([, n]) => n === name)
  if (name && !staff) {
    ElMessage.warning('未找到该环卫员，请确认姓名')
    return
  }
  await reassignKitchenTask(row.id, undefined, staff?.[0])
  ElMessage.success('已改派')
  taskTable.value?.refresh()
}

const disabledDate = (d: Date) => d.getTime() < Date.now() - 86400000
const taskStatusTag: Record<number, 'warning' | 'success'> = { 0: 'warning', 1: 'success' }
const activeAreas = computed(() => areas.value.filter((a) => a.status === 1))
</script>

<template>
  <div class="app-page">
    <el-tabs v-model="activeTab">
      <!-- 投放记录 -->
      <el-tab-pane label="投放记录" name="records">
        <TablePro :fetcher="pageKitchenRecords">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="areaName" label="片区" width="120" />
          <el-table-column prop="userName" label="用户" width="100" />
          <el-table-column prop="bags" label="袋数" width="80" />
          <el-table-column label="AI 审核（F1）" width="130">
            <template #default="{ row }">
              <el-tag :type="auditTagType[row.aiAuditResult as keyof typeof auditTagType]">{{ row.aiAuditResult }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="points" label="积分" width="80" />
          <el-table-column prop="createTime" label="投放时间" width="170" />
          <el-table-column label="操作" min-width="120">
            <template #default="{ row }">
              <el-button v-permission="'business:kitchen:list'" link type="primary" @click="openDetail(row as KitchenRecord)">
                查看
              </el-button>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>

      <!-- 清运任务 -->
      <el-tab-pane label="清运任务" name="tasks">
        <TablePro ref="taskTable" :fetcher="pageKitchenTasks">
          <template #search="{ query }">
            <el-form-item label="清运日期">
              <el-date-picker v-model="query.taskDate" type="date" value-format="YYYY-MM-DD" placeholder="全部" style="width: 150px" />
            </el-form-item>
            <el-form-item label="片区">
              <el-select v-model="query.areaId" placeholder="全部" clearable style="width: 150px">
                <el-option v-for="a in areas" :key="a.id" :label="a.areaName" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
                <el-option v-for="(text, val) in KITCHEN_TASK_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="'business:kitchen:save'" type="primary" icon="Plus" @click="openPublish">发布清运任务</el-button>
          </template>
          <el-table-column prop="taskDate" label="清运日期" width="120" />
          <el-table-column prop="areaName" label="片区" width="130">
            <template #default="{ row }">{{ row.areaName ?? `片区${row.areaId}` }}</template>
          </el-table-column>
          <el-table-column prop="totalBags" label="袋数" width="80" />
          <el-table-column label="派车" width="110">
            <template #default="{ row }">
              <span v-if="vehicleNoOf(row.vehicleId)">{{ vehicleNoOf(row.vehicleId) }}</span>
              <span v-else style="color: var(--el-text-color-placeholder)">未派车</span>
            </template>
          </el-table-column>
          <el-table-column label="环卫员" width="110">
            <template #default="{ row }">
              <span v-if="staffNameOf(row.staffId)">{{ staffNameOf(row.staffId) }}</span>
              <span v-else style="color: var(--el-text-color-placeholder)">未指派</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="taskStatusTag[row.status] ?? 'info'">{{ KITCHEN_TASK_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalWeight" label="实收重量(kg)" width="110">
            <template #default="{ row }">{{ row.totalWeight ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 0"
                v-permission="'business:kitchen:save'"
                link type="primary"
                @click="reassign(row as KitchenTaskRow)"
              >
                改派
              </el-button>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>
    </el-tabs>

    <!-- 投放记录详情 -->
    <FormDialog v-model="detailVisible" title="垃圾投放详情" @confirm="detailVisible = false">
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="片区">{{ current.areaName }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ current.userName }}</el-descriptions-item>
          <el-descriptions-item label="袋数">{{ current.bags }}</el-descriptions-item>
          <el-descriptions-item label="应得积分">{{ current.points }}</el-descriptions-item>
          <el-descriptions-item label="投放时间">{{ current.createTime }}</el-descriptions-item>
          <el-descriptions-item label="AI 审核状态">
            <el-tag :type="auditTagType[current.aiAuditResult as keyof typeof auditTagType]">
              {{ current.aiAuditResult }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="photo-wrap">
          <div class="photo-title">投放照片</div>
          <el-image
            v-if="current.photoUrl"
            :src="current.photoUrl"
            :preview-src-list="[current.photoUrl]"
            :initial-index="0"
            fit="cover"
            preview-teleported
            class="photo-img"
          />
          <el-text v-else type="info">无照片</el-text>
        </div>
        <div class="risk-tip">
          <el-text type="info" size="small">
            审核通过后积分自动到账；驳回由工作人员在员工端处置
          </el-text>
        </div>
      </template>
    </FormDialog>

    <!-- 发布清运任务 -->
    <FormDialog v-model="publishVisible" title="发布清运任务" :loading="publishing" @confirm="submitPublish">
      <el-form :model="publishForm" label-width="100px">
        <el-form-item label="片区" required>
          <el-select v-model="publishForm.areaId" placeholder="请选择清运片区" style="width: 100%">
            <el-option v-for="a in activeAreas" :key="a.id" :label="a.areaName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="清运日期" required>
          <el-date-picker
            v-model="publishForm.taskDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择清运日期"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="清运袋数" required>
          <el-input-number v-model="publishForm.totalBags" :min="1" :max="99999" style="width: 100%" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped>
.photo-wrap {
  margin-top: 14px;
}
.photo-title {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.photo-img {
  width: 200px;
  height: 200px;
  border-radius: 6px;
}
.risk-tip {
  margin-top: 16px;
  text-align: right;
}
</style>
