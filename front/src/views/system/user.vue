<script setup lang="ts">
// 用户管理（对接 Track A，先 Mock）
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, saveUser, removeUser, auditUserRealName, type SysUser } from '@/api/system'
import TablePro from '@/components/TablePro.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

// 是否目标用户是 ADMIN 角色且当前也是 ADMIN
const isAdmin = (role: string | undefined) => role === 'ADMIN'


// 注：泛型组件取 InstanceType 会报错，按暴露接口声明
const tableRef = ref<{ refresh: () => void; search: () => void; query: Record<string, any> }>()
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Partial<SysUser>>({})

// ---- 实名审核（GapFill B1）：仅待审(1)行显示审核入口 ----
const auditVisible = ref(false)
const auditSaving = ref(false)
const auditRow = ref<SysUser | null>(null)
const auditForm = reactive({ pass: true, remark: '' })

/** 实名状态 → tag/文案（0未认证 1待审 2已通过 3已驳回） */
const REAL_NAME_META: Record<number, { type: 'info' | 'warning' | 'success' | 'danger'; text: string }> = {
  0: { type: 'info', text: '未认证' },
  1: { type: 'warning', text: '待审' },
  2: { type: 'success', text: '已通过' },
  3: { type: 'danger', text: '已驳回' },
}

// ---- 实名审核分类（待审核单独分类，便于集中审核；默认停在待审核） ----
const auditTab = ref('pending')
const REAL_NAME_TABS: Record<string, number | undefined> = {
  all: undefined,
  pending: 1,
  approved: 2,
  rejected: 3,
}

function onAuditTabChange() {
  tableRef.value!.query.realNameStatus = REAL_NAME_TABS[auditTab.value]
  tableRef.value!.search()
}

function openAudit(row: SysUser) {
  auditRow.value = row
  auditForm.pass = true
  auditForm.remark = ''
  auditVisible.value = true
}

async function submitAudit() {
  if (!auditRow.value) return
  if (!auditForm.pass && !auditForm.remark.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  auditSaving.value = true
  try {
    await auditUserRealName(auditRow.value.id, auditForm.pass, auditForm.remark.trim() || undefined)
    ElMessage.success(auditForm.pass ? '审核已通过' : '已驳回')
    auditVisible.value = false
    tableRef.value?.refresh()
  } finally {
    auditSaving.value = false
  }
}

function openDialog(row?: SysUser) {
  Object.keys(form).forEach((k) => delete form[k as keyof SysUser])
  if (row) Object.assign(form, row)
  else form.status = 1
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    const initialPassword = await saveUser(form)
    dialogVisible.value = false
    tableRef.value?.refresh()
    if (initialPassword) {
      // C5 安全加固：新增用户返回一次性随机初始密码，弹窗提示管理员转告用户后立即修改
      await ElMessageBox.alert(
        `初始密码：<b style="color:#e6a23c">${initialPassword}</b><br>请转告用户首次登录后立即修改。`,
        '用户创建成功',
        { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' },
      )
    } else {
      ElMessage.success('保存成功')
    }
  } finally {
    saving.value = false
  }
}

async function remove(row: SysUser) {
  await ElMessageBox.confirm(`确定删除用户「${row.nickname}」吗？`, '提示', { type: 'warning' })
  await removeUser(row.id)
  ElMessage.success('删除成功')
  tableRef.value?.refresh()
}
</script>

<template>
  <div class="app-page">
    <!-- 实名分类 tabs：仅作筛选分类条（内容区不承载组件），表格区独占剩余高度 -->
    <el-tabs v-model="auditTab" class="user-tabs" @tab-change="onAuditTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="待审核" name="pending" />
      <el-tab-pane label="已通过" name="approved" />
      <el-tab-pane label="已驳回" name="rejected" />
    </el-tabs>
    <div class="user-table-wrap">
      <TablePro ref="tableRef" :fetcher="pageUsers" :init-query="{ realNameStatus: 1 }">
        <template #search="{ query }">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="用户名 / 昵称" clearable style="width: 200px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" value="1" />
              <el-option label="停用" value="0" />
            </el-select>
          </el-form-item>
        </template>
        <template #toolbar>
          <el-button v-permission="'system:user:add'" type="primary" icon="Plus" @click="openDialog()">新增用户</el-button>
        </template>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="头像" width="80">
          <template #default="{ row }">
            <!-- Upgrade U1：头像 URL 用 <img> 展示（el-avatar），无则 UserFilled 图标兜底 -->
            <el-avatar :size="40" :src="row.avatar ?? ''">
              <UserFilled />
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="110" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column prop="email" label="邮箱" min-width="150" />
        <el-table-column prop="roleName" label="角色" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实名" width="100">
          <template #default="{ row }">
            <el-tooltip :content="row.realNameStatus === 3 ? row.realNameRemark : ''" placement="top">
              <el-tag :type="REAL_NAME_META[row.realNameStatus ?? 0]?.type ?? 'info'">
                {{ REAL_NAME_META[row.realNameStatus ?? 0]?.text ?? '未认证' }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:user:edit'" link type="primary" @click="openDialog(row as SysUser)">
              编辑
            </el-button>
            <el-button
              v-if="row.realNameStatus === 1"
              v-permission="'system:user:audit'"
              link
              type="warning"
              @click="openAudit(row as SysUser)"
            >
              审核
            </el-button>
            <!-- 禁止删除/封禁管理员账号（普通用户可删除） -->
            <el-button 
              v-if="!isAdmin(row.role)" 
              v-permission="'system:user:remove'" 
              link 
              type="danger" 
              @click="remove(row as SysUser)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </TablePro>
    </div>

    <FormDialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="如：user@163.com" /></el-form-item>
        <!-- 禁止修改管理员角色和状态 -->
        <el-form-item 
          v-if="!isAdmin(form.role) || !form.id" 
          label="角色"
        ><el-input v-model="form.roleName" placeholder="如：运营专员" /></el-form-item>
        <el-form-item 
          v-if="!isAdmin(form.role) || !form.id" 
          label="状态"
        ><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
    </FormDialog>

    <FormDialog v-model="auditVisible" title="实名审核" :loading="auditSaving" @confirm="submitAudit">
      <el-form :model="auditForm" label-width="80px">
        <el-form-item label="用户">
          <span>{{ auditRow?.nickname }}（{{ auditRow?.email }}）</span>
        </el-form-item>
        <el-form-item label="审核结论">
          <el-radio-group v-model="auditForm.pass">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!auditForm.pass" label="驳回原因">
          <el-input v-model="auditForm.remark" type="textarea" :rows="2" maxlength="255" placeholder="必填，将展示给用户" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped lang="scss">
// 覆盖全局「页内 tabs 占满剩余高度」约定（.app-page > .el-tabs { flex: 1 }）：
// 本页 tabs 仅为实名状态筛选条，内容区不承载组件，需收缩为导航条高度；
// 表格通过包裹层占满剩余（TablePro 根样式不受影响，避免高度叠加溢出重叠）。
.app-page > .user-tabs {
  flex: none;
  display: block;
  min-height: 0;
}

.user-table-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
