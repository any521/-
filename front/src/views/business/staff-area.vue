<script setup lang="ts">
// 员工片区（GapFill V9 对接真实契约）：员工列表（新增/审核/启停/调片区）+ 片区列表（CRUD/启停）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageStaff, createStaff, auditStaff, changeStaffStatus, transferStaffArea,
  pageAreas, saveArea, removeArea,
  STAFF_TYPE_TEXT, STAFF_AUDIT_TEXT, AREA_STATUS_TEXT,
  type StaffRow, type StaffCreate, type AreaRow,
} from '@/api/business'

const activeTab = ref('staff')
const staffTable = ref<{ refresh: () => void }>()
const areaTable = ref<{ refresh: () => void }>()

// ---- 片区全量（搜索下拉 / 名称转换 / 新增员工与调片区下拉 共用） ----
const areas = ref<AreaRow[]>([])
const areaNameMap = computed(() => Object.fromEntries(areas.value.map((a) => [a.id, a.areaName])))
const enabledAreas = computed(() => areas.value.filter((a) => a.status === 1))
async function loadAreas() {
  const page = await pageAreas({ pageNum: 1, pageSize: 999 })
  areas.value = page.list ?? []
}
onMounted(loadAreas)

function areaName(id: string) {
  if (!id || id === '0') return '未分配'
  return areaNameMap.value[id] ?? `片区${id}（已移除）`
}

/** 行内调片区下拉的当前值：雪花 ID 全程字符串（Number() 会精度丢失）；
 *  当前片区不在启用列表时显示占位符，避免裸 ID/0 */
function rowAreaValue(row: StaffRow): string | undefined {
  const id = row.areaId
  if (!id || id === '0') return undefined
  return enabledAreas.value.some((a) => a.id === id) ? id : undefined
}

const staffTypeTagType: Record<number, 'primary' | 'success' | 'warning'> = { 1: 'primary', 2: 'success', 3: 'warning' }
const staffAuditTagType: Record<number, 'warning' | 'success' | 'danger'> = { 0: 'warning', 1: 'success', 2: 'danger' }

// ---- 员工审核 / 启停 / 调片区 ----
async function audit(row: StaffRow, approved: boolean) {
  let remark: string | undefined
  if (approved) {
    await ElMessageBox.confirm(`确认通过「${row.staffName}」的审核？通过后可上岗接单`, '员工审核', { type: 'success' })
  } else {
    const { value } = await ElMessageBox.prompt(`确认驳回「${row.staffName}」？`, '员工审核', {
      type: 'warning',
      inputPlaceholder: '驳回原因（可选）',
      inputValue: '',
    })
    remark = value || undefined
  }
  await auditStaff(row.id, approved, remark)
  ElMessage.success(approved ? '已通过，员工可上岗' : '已驳回')
  staffTable.value?.refresh()
}

async function toggleStatus(row: StaffRow) {
  const enable = row.status !== 1
  await ElMessageBox.confirm(
    enable ? `确认启用「${row.staffName}」？` : `确认禁用「${row.staffName}」？禁用后不可接单`,
    '启停员工', { type: 'warning' },
  )
  await changeStaffStatus(row.id, enable)
  ElMessage.success(enable ? '已启用' : '已禁用')
  staffTable.value?.refresh()
}

async function changeArea(row: StaffRow, areaId: string) {
  await transferStaffArea(row.id, areaId)
  ElMessage.success('已调整片区')
  staffTable.value?.refresh()
}

// ---- 新增员工（新增即进入待审核） ----
const staffDialog = ref(false)
const staffSaving = ref(false)
const staffForm = reactive({ userId: undefined as number | undefined, staffName: '', phone: '', staffType: 1, areaId: undefined as string | undefined })

function openStaffCreate() {
  Object.assign(staffForm, { userId: undefined, staffName: '', phone: '', staffType: 1, areaId: undefined })
  staffDialog.value = true
}

async function submitStaff() {
  if (!staffForm.userId || !staffForm.staffName || !staffForm.areaId) {
    ElMessage.warning('用户 ID、姓名与片区必填')
    return
  }
  staffSaving.value = true
  try {
    const payload: StaffCreate = {
      userId: staffForm.userId,
      staffName: staffForm.staffName,
      phone: staffForm.phone || undefined,
      staffType: staffForm.staffType,
      areaId: staffForm.areaId,
    }
    await createStaff(payload)
    ElMessage.success('已创建，进入待审核')
    staffDialog.value = false
    staffTable.value?.refresh()
  } finally {
    staffSaving.value = false
  }
}

// ---- 片区新增/编辑/启停/删除 ----
const areaDialog = ref(false)
const areaSaving = ref(false)
const areaForm = reactive({ id: undefined as string | undefined, areaName: '', address: '', lng: undefined as number | undefined, lat: undefined as number | undefined, cleanThreshold: 100, householdCount: 0 })

function openAreaCreate() {
  Object.assign(areaForm, { id: undefined, areaName: '', address: '', lng: undefined, lat: undefined, cleanThreshold: 100, householdCount: 0 })
  areaDialog.value = true
}

function openAreaEdit(row: AreaRow) {
  Object.assign(areaForm, {
    id: row.id,
    areaName: row.areaName,
    address: row.address ?? '',
    lng: row.lng ?? undefined,
    lat: row.lat ?? undefined,
    cleanThreshold: row.cleanThreshold ?? 100,
    householdCount: row.householdCount ?? 0,
  })
  areaDialog.value = true
}

async function submitArea() {
  if (!areaForm.areaName) {
    ElMessage.warning('片区名称必填')
    return
  }
  areaSaving.value = true
  try {
    await saveArea({
      id: areaForm.id,
      areaName: areaForm.areaName,
      address: areaForm.address || undefined,
      lng: areaForm.lng,
      lat: areaForm.lat,
      cleanThreshold: areaForm.cleanThreshold,
      householdCount: areaForm.householdCount,
    }, areaForm.id)
    ElMessage.success(areaForm.id ? '片区已保存' : '片区已创建')
    areaDialog.value = false
    areaTable.value?.refresh()
    loadAreas()
  } finally {
    areaSaving.value = false
  }
}

async function toggleAreaStatus(row: AreaRow) {
  const status = row.status === 1 ? 0 : 1
  await saveArea({
    id: row.id,
    areaName: row.areaName,
    address: row.address ?? undefined,
    cleanThreshold: row.cleanThreshold ?? undefined,
    householdCount: row.householdCount ?? undefined,
    status,
  }, row.id)
  ElMessage.success(status === 1 ? '已启用' : '已停用')
  areaTable.value?.refresh()
  loadAreas()
}

async function removeAreaRow(row: AreaRow) {
  await ElMessageBox.confirm(`确认删除片区「${row.areaName}」？删除后不可恢复`, '删除片区', { type: 'warning' })
  await removeArea(row.id)
  ElMessage.success('已删除')
  areaTable.value?.refresh()
  loadAreas()
}
</script>

<template>
  <div class="app-page">
    <el-tabs v-model="activeTab">
      <!-- 员工列表 -->
      <el-tab-pane label="员工列表" name="staff">
        <TablePro ref="staffTable" :fetcher="pageStaff">
          <template #search="{ query }">
            <el-form-item label="员工类型">
              <el-select v-model="query.staffType" placeholder="全部" clearable style="width: 120px">
                <el-option v-for="(text, val) in STAFF_TYPE_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
            <el-form-item label="片区">
              <el-select v-model="query.areaId" placeholder="全部" clearable style="width: 150px">
                <el-option v-for="a in areas" :key="a.id" :label="a.areaName" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="审核状态">
              <el-select v-model="query.auditStatus" placeholder="全部" clearable style="width: 120px">
                <el-option v-for="(text, val) in STAFF_AUDIT_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="'business:staff:save'" type="primary" icon="Plus" @click="openStaffCreate">新增员工</el-button>
          </template>
          <el-table-column prop="staffName" label="姓名" width="110" />
          <el-table-column prop="phone" label="电话" width="130" />
          <el-table-column label="员工类型" width="100">
            <template #default="{ row }">
              <el-tag :type="staffTypeTagType[row.staffType] ?? 'info'">{{ STAFF_TYPE_TEXT[row.staffType] ?? row.staffType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="片区" width="130">
            <template #default="{ row }">{{ areaName(row.areaId) }}</template>
          </el-table-column>
          <el-table-column label="审核状态" width="100">
            <template #default="{ row }">
              <el-tag :type="staffAuditTagType[row.auditStatus] ?? 'info'">{{ STAFF_AUDIT_TEXT[row.auditStatus] ?? row.auditStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请理由" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.applyReason" style="color: var(--el-text-color-regular)">{{ row.applyReason }}</span>
              <span v-else style="color: var(--el-text-color-placeholder)">—</span>
            </template>
          </el-table-column>
          <el-table-column label="驳回原因" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.auditRemark" style="color: var(--el-color-danger)">{{ row.auditRemark }}</span>
              <span v-else style="color: var(--el-text-color-placeholder)">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <template v-if="row.auditStatus === 0">
                <el-button v-permission="'business:staff:save'" link type="success" @click="audit(row as StaffRow, true)">通过</el-button>
                <el-button v-permission="'business:staff:save'" link type="danger" @click="audit(row as StaffRow, false)">驳回</el-button>
              </template>
              <template v-else-if="row.auditStatus === 1">
                <el-button v-permission="'business:staff:save'" link type="warning" @click="toggleStatus(row as StaffRow)">
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-select
                  v-permission="'business:staff:save'"
                  :model-value="rowAreaValue(row as StaffRow)" placeholder="待分配片区"
                  size="small" style="width: 110px"
                  @change="(v: string) => changeArea(row as StaffRow, v)"
                >
                  <el-option v-for="a in enabledAreas" :key="a.id" :label="a.areaName" :value="a.id" />
                </el-select>
              </template>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>

      <!-- 片区列表 -->
      <el-tab-pane label="片区列表" name="areas">
        <TablePro ref="areaTable" :fetcher="pageAreas">
          <template #search="{ query }">
            <el-form-item label="名称">
              <el-input v-model="query.keyword" placeholder="片区名称模糊搜索" clearable style="width: 200px" />
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="'business:area:save'" type="primary" icon="Plus" @click="openAreaCreate">新增片区</el-button>
          </template>
          <el-table-column prop="areaName" label="片区名称" width="150" />
          <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
          <el-table-column label="中心坐标" width="170">
            <template #default="{ row }">
              <span v-if="row.lng != null && row.lat != null" style="font-family: monospace; font-size: 12px">
                {{ Number(row.lng).toFixed(5) }}, {{ Number(row.lat).toFixed(5) }}
              </span>
              <span v-else style="color: var(--el-text-color-placeholder)">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="cleanThreshold" label="清运阈值(袋)" width="120" />
          <el-table-column prop="householdCount" label="户数" width="90" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 1"
                inline-prompt active-text="启用" inactive-text="停用"
                @change="toggleAreaStatus(row as AreaRow)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'business:area:save'" link type="primary" @click="openAreaEdit(row as AreaRow)">编辑</el-button>
              <el-button v-permission="'business:area:save'" link type="danger" @click="removeAreaRow(row as AreaRow)">删除</el-button>
            </template>
          </el-table-column>
        </TablePro>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增员工弹窗 -->
    <FormDialog v-model="staffDialog" title="新增员工" :loading="staffSaving" @confirm="submitStaff">
      <el-form :model="staffForm" label-width="100px">
        <el-form-item label="用户 ID" required>
          <el-input-number v-model="staffForm.userId" :min="1" :controls="false" placeholder="关联系统用户 ID" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="staffForm.staffName" maxlength="20" placeholder="员工姓名" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="staffForm.phone" maxlength="20" placeholder="联系电话（可选）" />
        </el-form-item>
        <el-form-item label="员工类型">
          <el-select v-model="staffForm.staffType" style="width: 180px">
            <el-option v-for="(text, val) in STAFF_TYPE_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="片区" required>
          <el-select v-model="staffForm.areaId" placeholder="选择启用中的片区" style="width: 180px">
            <el-option v-for="a in enabledAreas" :key="a.id" :label="a.areaName" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </FormDialog>

    <!-- 片区新增/编辑弹窗 -->
    <FormDialog v-model="areaDialog" :title="areaForm.id ? '编辑片区' : '新增片区'" :loading="areaSaving" @confirm="submitArea">
      <el-form :model="areaForm" label-width="100px">
        <el-form-item label="片区名称" required>
          <el-input v-model="areaForm.areaName" maxlength="50" placeholder="片区名称" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="areaForm.address" maxlength="100" placeholder="片区地址（可选）" />
        </el-form-item>
        <el-form-item label="中心坐标">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input-number v-model="areaForm.lng" :min="-180" :max="180" :precision="6" :controls="false" placeholder="经度 lng" style="flex: 1" />
            <el-input-number v-model="areaForm.lat" :min="-90" :max="90" :precision="6" :controls="false" placeholder="纬度 lat" style="flex: 1" />
          </div>
          <span style="margin-left: 8px; color: #909399">驾驶舱地图标注定位（可选，如 120.1536, 30.2875）</span>
        </el-form-item>
        <el-form-item label="清运阈值">
          <el-input-number v-model="areaForm.cleanThreshold" :min="1" :max="100000" placeholder="袋" />
          <span style="margin-left: 8px; color: #909399">袋（达到即生成清运任务）</span>
        </el-form-item>
        <el-form-item label="户数">
          <el-input-number v-model="areaForm.householdCount" :min="0" :max="1000000" placeholder="户" />
          <span style="margin-left: 8px; color: #909399">户（风控户均基数）</span>
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>
