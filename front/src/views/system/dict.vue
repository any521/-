<script setup lang="ts">
// 字典管理（品类/状态等配置化数据：类型 CRUD + 展开行内字典项 CRUD）
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageDicts, saveDictType, removeDictType, saveDictData, removeDictData,
  type SysDict, type DictItem, type DictTypeForm, type DictDataForm,
} from '@/api/system'

const tableRef = ref<{ refresh: () => void }>()
const typeDialogVisible = ref(false)
const dataDialogVisible = ref(false)
const saving = ref(false)
const typeForm = reactive<DictTypeForm>({ dictType: '', dictName: '', remark: '', status: 1 })
const dataForm = reactive<DictDataForm>({ dictType: '', dictLabel: '', dictValue: '', sort: 0, remark: '', status: 1 })

// ---------------- 类型层 CRUD ----------------

function openTypeDialog(row?: SysDict) {
  Object.assign(typeForm, { dictType: '', dictName: '', remark: '', status: 1 })
  delete typeForm.id
  if (row) {
    typeForm.id = row.id
    typeForm.dictType = row.dictType
    typeForm.dictName = row.dictName
    typeForm.remark = row.remark
    typeForm.status = row.status ?? 1
  }
  typeDialogVisible.value = true
}

async function submitType() {
  if (!typeForm.dictName?.trim()) {
    ElMessage.warning('请输入字典名称')
    return
  }
  if (!typeForm.dictType?.trim()) {
    ElMessage.warning('请输入字典类型')
    return
  }
  saving.value = true
  try {
    await saveDictType(typeForm)
    ElMessage.success('保存成功')
    typeDialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    saving.value = false
  }
}

async function removeType(row: SysDict) {
  await ElMessageBox.confirm(`确定删除字典类型「${row.dictName}」吗？`, '提示', { type: 'warning' })
  await removeDictType(row.id)
  ElMessage.success('删除成功')
  tableRef.value?.refresh()
}

// ---------------- 数据项层 CRUD（展开行内） ----------------

function openDataDialog(row?: DictItem, dictType?: string) {
  Object.assign(dataForm, { dictLabel: '', dictValue: '', sort: 0, remark: '', status: 1 })
  delete dataForm.id
  dataForm.dictType = dictType ?? ''
  if (row) {
    dataForm.id = row.id
    dataForm.dictLabel = row.label
    dataForm.dictValue = row.value
    dataForm.sort = row.sort
  }
  dataDialogVisible.value = true
}

async function submitData() {
  if (!dataForm.dictLabel?.trim()) {
    ElMessage.warning('请输入字典标签')
    return
  }
  if (!dataForm.dictValue?.trim()) {
    ElMessage.warning('请输入字典值')
    return
  }
  saving.value = true
  try {
    await saveDictData(dataForm)
    ElMessage.success('保存成功')
    dataDialogVisible.value = false
    // items 由 pageDicts 逐类型补拉组装，删除/新增后整页重拉（简单可靠）
    tableRef.value?.refresh()
  } finally {
    saving.value = false
  }
}

async function removeData(row: SysDict, item: DictItem) {
  if (item.id == null) return
  await ElMessageBox.confirm(`确定删除字典项「${item.label}」吗？`, '提示', { type: 'warning' })
  await removeDictData(item.id)
  ElMessage.success('删除成功')
  tableRef.value?.refresh()
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="tableRef" :fetcher="pageDicts">
      <template #toolbar>
        <el-button v-permission="'system:dict:add'" type="primary" icon="Plus" @click="openTypeDialog()">新增类型</el-button>
      </template>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div style="padding: 8px 48px">
            <div style="margin-bottom: 8px">
              <el-button v-permission="'system:dict:add'" size="small" type="primary" plain icon="Plus" @click="openDataDialog(undefined, (row as SysDict).dictType)">新增字典项</el-button>
            </div>
            <el-tag v-for="item in (row as SysDict).items" :key="item.value" style="margin-right: 8px" type="success">
              {{ item.label }}（{{ item.value }}）
              <el-button v-if="item.id != null" v-permission="'system:dict:remove'" link type="danger" size="small" style="margin-left: 4px" @click="removeData(row as SysDict, item)">删除</el-button>
            </el-tag>
            <el-empty v-if="!(row as SysDict).items.length" description="暂无字典项" :image-size="60" />
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="dictName" label="字典名称" min-width="140" />
      <el-table-column prop="dictType" label="字典类型" min-width="160">
        <template #default="{ row }">
          <el-tag type="info">{{ (row as SysDict).dictType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="字典项数" width="100">
        <template #default="{ row }">{{ (row as SysDict).items.length }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:dict:edit'" link type="primary" @click="openTypeDialog(row as SysDict)">编辑</el-button>
          <el-button v-permission="'system:dict:remove'" link type="danger" @click="removeType(row as SysDict)">删除</el-button>
        </template>
      </el-table-column>
    </TablePro>

    <!-- 类型表单弹窗 -->
    <FormDialog v-model="typeDialogVisible" :title="typeForm.id ? '编辑类型' : '新增类型'" :loading="saving" @confirm="submitType">
      <el-form :model="typeForm" label-width="80px">
        <el-form-item label="字典名称" required>
          <el-input v-model="typeForm.dictName" placeholder="如：回收品类" />
        </el-form-item>
        <el-form-item label="字典类型" required>
          <el-input v-model="typeForm.dictType" placeholder="小写字母/数字/下划线，2-64 位，如 recycle_category" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
    </FormDialog>

    <!-- 字典项表单弹窗 -->
    <FormDialog v-model="dataDialogVisible" :title="dataForm.id ? '编辑字典项' : '新增字典项'" :loading="saving" @confirm="submitData">
      <el-form :model="dataForm" label-width="80px">
        <el-form-item label="字典标签" required>
          <el-input v-model="dataForm.dictLabel" placeholder="如：金属" />
        </el-form-item>
        <el-form-item label="字典值" required>
          <el-input v-model="dataForm.dictValue" placeholder="如：METAL" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>
