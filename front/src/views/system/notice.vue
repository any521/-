<script setup lang="ts">
// 公告管理（对接 Track A，先 Mock）
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageNotices, saveNotice, removeNotice, type SysNotice } from '@/api/system'

const tableRef = ref<{ refresh: () => void }>()
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Partial<SysNotice>>({})

function targetText(target?: string): string {
  return (
    { ALL: '全部用户', USER: '用户端', STAFF: '员工端', ADMIN: '管理端' }[target ?? 'ALL'] ?? '全部用户'
  )
}

function openDialog(row?: SysNotice) {
  Object.keys(form).forEach((k) => delete form[k as keyof SysNotice])
  if (row) Object.assign(form, row)
  else Object.assign(form, { type: '通知', status: 1, target: 'ALL' })
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    await saveNotice(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    saving.value = false
  }
}

async function remove(row: SysNotice) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', { type: 'warning' })
  await removeNotice(row.id)
  ElMessage.success('删除成功')
  tableRef.value?.refresh()
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="tableRef" :fetcher="pageNotices">
      <template #toolbar>
        <el-button v-permission="'system:notice:add'" type="primary" icon="Plus" @click="openDialog()">发布公告</el-button>
      </template>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.type === '公告' ? 'warning' : 'primary'">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标端" width="90">
        <template #default="{ row }">
          {{ targetText(row.target) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已发布' : '已关闭' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="发布人" width="100" />
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:notice:edit'" link type="primary" @click="openDialog(row as SysNotice)">编辑</el-button>
          <el-button v-permission="'system:notice:remove'" link type="danger" @click="remove(row as SysNotice)">删除</el-button>
        </template>
      </el-table-column>
    </TablePro>

    <FormDialog v-model="dialogVisible" :title="form.id ? '编辑公告' : '发布公告'" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="60px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="通知">通知</el-radio>
            <el-radio value="公告">公告</el-radio>
          </el-radio-group>
          <span class="form-tip">通知发送到各端「消息中心」，公告全局展示最新一条</span>
        </el-form-item>
        <el-form-item label="目标端">
          <el-select v-model="form.target" style="width: 100%">
            <el-option label="全部用户" value="ALL" />
            <el-option label="用户端" value="USER" />
            <el-option label="员工端" value="STAFF" />
            <el-option label="管理端" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="通知正文（发送到消息中心）/ 公告正文" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped>
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
