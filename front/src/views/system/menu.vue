<script setup lang="ts">
// 菜单管理（树形表格 CRUD，权限码与后端注解一一对应）
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMenus, saveMenu, removeMenu, type SysMenu, type MenuForm } from '@/api/system'

const menus = ref<SysMenu[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<MenuForm>({ parentId: '0', menuType: 2, sort: 0, visible: 1, status: 1, menuName: '' })
// 弹窗内父级只读显示名（根目录 / 父菜单 title）
const parentTitle = ref('')

async function load() {
  loading.value = true
  try {
    menus.value = await listMenus()
  } catch (e) {
    ElMessage.warning('菜单列表刷新失败，请稍后手动刷新')
    throw e
  } finally {
    loading.value = false
  }
}

function findTitle(list: SysMenu[], id: string): string {
  for (const m of list) {
    if (m.id === id) return m.title
    if (m.children?.length) {
      const t = findTitle(m.children, id)
      if (t) return t
    }
  }
  return ''
}

function resetForm() {
  Object.assign(form, { parentId: '0', menuType: 2, sort: 0, visible: 1, status: 1, menuName: '' })
  const clearKeys = ['id', 'path', 'component', 'perm', 'icon'] as const
  clearKeys.forEach((k) => delete form[k as keyof MenuForm])
  parentTitle.value = ''
}

// 菜单类型显示名（1 目录 / 2 菜单 / 3 按钮）
const TYPE_TEXT: Record<number, string> = { 1: '目录', 2: '菜单', 3: '按钮' }
function menuTypeText(type: number): string {
  return TYPE_TEXT[type] ?? '菜单'
}

function openDialog(row?: SysMenu, parent?: SysMenu) {
  resetForm()
  if (row) {
    // 编辑：树行 → MenuDTO 字段映射
    form.id = row.id
    form.parentId = row.parentId
    form.menuName = row.title
    form.path = row.path
    form.component = row.component
    form.perm = row.perms
    form.icon = row.icon
    form.sort = row.orderNum
    form.menuType = row.menuType ?? 2
    form.visible = row.visible ?? 1
    form.status = row.status ?? 1
    parentTitle.value = row.parentId && row.parentId !== '0' ? findTitle(menus.value, row.parentId) : '根目录'
  } else if (parent) {
    // 新增子级（按钮级不可加子，按钮行不渲染新增按钮）
    form.parentId = parent.id
    parentTitle.value = parent.title
  }
  dialogVisible.value = true
}

async function submit() {
  if (!form.menuName?.trim()) {
    ElMessage.warning('请输入菜单名称')
    return
  }
  if (form.menuType === 3 && !form.perm?.trim()) {
    ElMessage.warning('按钮类型需填写权限标识')
    return
  }
  saving.value = true
  try {
    await saveMenu(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    try {
      await load()
    } catch {
      // 刷新失败不阻断保存流程
    }
  } finally {
    saving.value = false
  }
}

async function remove(row: SysMenu) {
  await ElMessageBox.confirm(`确定删除菜单「${row.title}」吗？`, '提示', { type: 'warning' })
  try {
    await removeMenu(row.id)
    ElMessage.success('删除成功')
    // 立即从响应式列表移除（优化体验）+ 重新拉取最新数据；刷新失败时保留本地已删除结果，避免陈旧列表误导
    menus.value = menus.value.filter(m => m.id !== row.id && !hasChildId(row.id, m.children))
    try {
      await load()
    } catch {
      // 刷新失败：本地列表已移除该行，保持现状
    }
  } catch (e: any) {
    if (!e.message?.includes('不允许删除')) {
      throw e
    }
  }
}

// 递归检查是否有子菜单包含目标 ID
function hasChildId(targetId: string, children?: SysMenu[]): boolean {
  if (!children?.length) return false
  return children.some(child => child.id === targetId || hasChildId(targetId, child.children))
}

onMounted(load)
</script>

<template>
  <div class="app-page app-page--flow">
    <div class="app-card">
      <div style="margin-bottom: 16px">
        <el-button v-permission="'system:menu:add'" type="primary" icon="Plus" @click="openDialog()">新增菜单</el-button>
      </div>
      <el-table v-loading="loading" :data="menus" row-key="id" default-expand-all>
        <el-table-column prop="title" label="菜单名称" min-width="180">
          <template #default="{ row }">
            <el-icon v-if="row.icon" style="vertical-align: -2px; margin-right: 6px">
              <component :is="row.icon" />
            </el-icon>
            {{ row.title }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.menuType === 3 ? 'info' : row.menuType === 2 ? 'success' : 'warning'" size="small">
              {{ menuTypeText(row.menuType ?? 2) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由地址" min-width="120" />
        <el-table-column prop="component" label="组件路径" min-width="140" />
        <el-table-column prop="perms" label="权限标识" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.perms" type="info" size="small">{{ row.perms }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.menuType !== 3" v-permission="'system:menu:add'" link type="primary" @click="openDialog(undefined, row as SysMenu)">新增</el-button>
            <el-button v-permission="'system:menu:edit'" link type="primary" @click="openDialog(row as SysMenu)">编辑</el-button>
            <el-button v-permission="'system:menu:remove'" link type="danger" @click="remove(row as SysMenu)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <FormDialog v-model="dialogVisible" :title="form.id ? '编辑菜单' : '新增菜单'" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="90px">
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.menuName" placeholder="如：回收订单" />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.menuType">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="父级">
          <el-input :model-value="parentTitle || '根目录'" disabled />
        </el-form-item>
        <el-form-item v-if="form.menuType === 2" label="路由地址">
          <el-input v-model="form.path" placeholder="如：recycle" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 2" label="组件路径">
          <el-input v-model="form.component" placeholder="views 下相对路径，如 system/user" />
        </el-form-item>
        <el-form-item label="权限标识" :required="form.menuType === 3">
          <el-input v-model="form.perm" placeholder="如：system:menu:add" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 3" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标组件名，如 User" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="显示">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>
