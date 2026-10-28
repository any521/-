<script setup lang="ts">
// 角色管理（对接 Track A，先 Mock）
import { nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageRoles, saveRole, removeRole, listMenus, assignRoleMenus, getRoleMenuIds,
  type SysRole, type SysMenu,
} from '@/api/system'

const tableRef = ref<{ refresh: () => void }>()
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Partial<SysRole>>({})

function openDialog(row?: SysRole) {
  Object.keys(form).forEach((k) => delete form[k as keyof SysRole])
  if (row) Object.assign(form, row)
  else form.status = 1
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    await saveRole(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    saving.value = false
  }
}

async function remove(row: SysRole) {
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '提示', { type: 'warning' })
  await removeRole(row.id)
  ElMessage.success('删除成功')
  tableRef.value?.refresh()
}

// ---------------- 分配菜单（全量覆盖；父级半选必须提交，否则子树显示不完整） ----------------
const assignVisible = ref(false)
const assigning = ref(false)
const assignRole = ref<SysRole>()
const menus = ref<SysMenu[]>([])
const treeRef = ref<{
  setCheckedKeys: (keys: (string | number)[]) => void
  getCheckedKeys: () => (string | number)[]
  getHalfCheckedKeys: () => (string | number)[]
}>()

async function openAssign(row: SysRole) {
  assignRole.value = row
  menus.value = await listMenus()
  const ids = await getRoleMenuIds(row.id)
  assignVisible.value = true
  // FormDialog destroy-on-close：树在弹窗打开后挂载，nextTick 后再回显
  await nextTick()
  treeRef.value?.setCheckedKeys(ids)
}

async function submitAssign() {
  if (!assignRole.value) return
  assigning.value = true
  try {
    const checked = [...(treeRef.value?.getCheckedKeys() ?? []), ...(treeRef.value?.getHalfCheckedKeys() ?? [])]
    // 菜单 id 为雪花 Long 字符串，直接透传（Number 转换会丢精度导致分配错位）
    await assignRoleMenus(assignRole.value.id, checked.map(String))
    ElMessage.success('分配成功')
    assignVisible.value = false
  } finally {
    assigning.value = false
  }
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="tableRef" :fetcher="pageRoles">
      <template #toolbar>
        <el-button v-permission="'system:role:add'" type="primary" icon="Plus" @click="openDialog()">新增角色</el-button>
      </template>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="roleName" label="角色名称" min-width="120" />
      <el-table-column prop="roleKey" label="权限字符" min-width="120" />
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:role:edit'" link type="primary" @click="openDialog(row as SysRole)">编辑</el-button>
          <el-button v-permission="'system:role:assign'" link type="warning" @click="openAssign(row as SysRole)">分配菜单</el-button>
          <el-button v-permission="'system:role:remove'" link type="danger" @click="remove(row as SysRole)">删除</el-button>
        </template>
      </el-table-column>
    </TablePro>

    <FormDialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="80px">
        <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="权限字符"><el-input v-model="form.roleKey" placeholder="如：operator" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
    </FormDialog>

    <FormDialog v-model="assignVisible" title="分配菜单" :loading="assigning" @confirm="submitAssign">
      <el-tree
        ref="treeRef"
        :data="menus"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'title', children: 'children' }"
        style="max-height: 420px; overflow: auto"
      />
    </FormDialog>
  </div>
</template>
