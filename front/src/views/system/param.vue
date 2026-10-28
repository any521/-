<script setup lang="ts">
// 参数设置（运营参数：垃圾投放时段/清运阈值/AI 超时等）
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageParams, saveParam, type SysParam } from '@/api/system'

const tableRef = ref<{ refresh: () => void }>()
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Partial<SysParam>>({})

function openDialog(row: SysParam) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    await saveParam(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="tableRef" :fetcher="pageParams" :init-query="{ pageSize: 50 }">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="paramName" label="参数名称" min-width="160" />
      <el-table-column prop="paramKey" label="参数键" min-width="180">
        <template #default="{ row }">
          <el-tag type="info">{{ row.paramKey }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="paramValue" label="参数值" width="200" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:param:edit'" link type="primary" @click="openDialog(row as SysParam)">修改</el-button>
        </template>
      </el-table-column>
    </TablePro>

    <FormDialog v-model="dialogVisible" title="修改参数" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="80px">
        <el-form-item label="参数名称"><el-input v-model="form.paramName" disabled /></el-form-item>
        <el-form-item label="参数键"><el-input v-model="form.paramKey" disabled /></el-form-item>
        <el-form-item label="参数值"><el-input v-model="form.paramValue" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>
