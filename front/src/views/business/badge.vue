<script setup lang="ts">
// 徽章管理（积分市场合并）：内置成就徽章 CRUD/上下架 + 图标封面上传（/file/upload?biz=badge）
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/request'
import {
  pageBadges, saveBadge, changeBadgeStatus,
  type BadgeRow, type BadgeSave,
} from '@/api/business'

const table = ref<{ refresh: () => void }>()

// ---- 新增/编辑 ----
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<BadgeSave>({ code: '', name: '', description: '', price: 100, sort: 0, status: 1, iconUrl: '' })

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: undefined, code: '', name: '', description: '', price: 100, sort: 0, status: 1, iconUrl: '' })
  dialogVisible.value = true
}

function openEdit(row: BadgeRow) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, code: row.code, name: row.name, description: row.description,
    price: row.price, sort: row.sort, status: row.status, iconUrl: row.iconUrl ?? '',
  })
  dialogVisible.value = true
}

// ---- 徽章图标上传（走项目 request 封装，上传成功回填 form.iconUrl） ----
async function uploadIcon(options: { file: File }) {
  const fd = new FormData()
  fd.append('file', options.file)
  const r = await request.post<{ url?: string } | string>('/file/upload?biz=badge', fd, { allowDuplicate: true })
  form.iconUrl = typeof r === 'string' ? r : r.url ?? ''
  if (form.iconUrl) ElMessage.success('徽章图标已上传')
}

function beforeIconUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return false
  }
  return true
}

async function submit() {
  if (!form.code || !form.name || !form.description || !form.price) {
    ElMessage.warning('编码、名称、描述与兑换积分必填')
    return
  }
  saving.value = true
  try {
    await saveBadge({ ...form })
    ElMessage.success(isEdit.value ? '徽章已更新' : '徽章已创建')
    dialogVisible.value = false
    table.value?.refresh()
  } finally {
    saving.value = false
  }
}

// ---- 上下架 ----
async function toggleStatus(row: BadgeRow) {
  const onSale = row.status !== 1
  await ElMessageBox.confirm(`确认${onSale ? '上架' : '下架'}「${row.name}」？下架后用户端市场不再展示`, '提示', { type: 'warning' })
  await changeBadgeStatus(row.id, onSale)
  ElMessage.success(onSale ? '已上架' : '已下架')
  table.value?.refresh()
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="table" :fetcher="pageBadges">
      <template #search="{ query }">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="徽章名称/编码" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
      </template>
      <template #toolbar>
        <el-button v-permission="'business:badge:save'" type="primary" icon="Plus" @click="openCreate">新增徽章</el-button>
      </template>
      <el-table-column label="图标" width="80">
        <template #default="{ row }">
          <el-image
            v-if="row.iconUrl" :src="row.iconUrl" :preview-src-list="[row.iconUrl]"
            fit="cover" style="width:48px;height:48px;border-radius:50%"
          />
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="编码" width="150" show-overflow-tooltip />
      <el-table-column prop="name" label="徽章名称" min-width="140" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="兑换积分" width="110">
        <template #default="{ row }">
          <span style="color: #e8bc3f; font-weight: 600">{{ row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'business:badge:save'" link type="primary" @click="openEdit(row as BadgeRow)">编辑</el-button>
          <el-button
            v-permission="'business:badge:save'" link
            :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row as BadgeRow)"
          >
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>
    </TablePro>

    <!-- 新增/编辑弹窗 -->
    <FormDialog v-model="dialogVisible" :title="isEdit ? '编辑徽章' : '新增徽章'" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="90px">
        <el-form-item label="徽章编码" required>
          <el-input v-model="form.code" maxlength="32" placeholder="如：GREEN_PIONEER（唯一，不可与现有重复）" />
        </el-form-item>
        <el-form-item label="徽章名称" required>
          <el-input v-model="form.name" maxlength="32" placeholder="如：绿动先锋" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="128" placeholder="用户端展示的成就说明" />
        </el-form-item>
        <el-form-item label="兑换积分" required>
          <el-input-number v-model="form.price" :min="1" :max="1000000" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="上架状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
        <el-form-item label="徽章图标">
          <el-upload
            :show-file-list="false" accept="image/*"
            :before-upload="beforeIconUpload" :http-request="uploadIcon"
          >
            <el-image
              v-if="form.iconUrl" :src="form.iconUrl" fit="cover"
              style="width:64px;height:64px;border-radius:50%;cursor:pointer"
            />
            <el-button v-else type="primary" plain icon="Upload">选择图片</el-button>
          </el-upload>
          <el-button v-if="form.iconUrl" link type="danger" @click="form.iconUrl = ''">清除</el-button>
          <div class="el-upload__tip">支持 jpg/png 等图片，≤5MB；上传后自动回填 URL，未上传时用户端显示默认星标</div>
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>
