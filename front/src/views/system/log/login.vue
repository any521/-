<script setup lang="ts">
// 登录日志（B3，菜单 108 修复白屏）：查询表单（用户名/客户端/状态/时间范围）+ 分页表格，满屏展示
// 复用 operlog.vue 的 TablePro 模式；后端 LoginLogQueryDTO：username/clientType/status/beginTime/endTime
import type { PageQuery } from '@shared/http/types'
import { pageLoginLogs } from '@/api/system'

const CLIENT_TYPE_TEXT: Record<string, string> = { USER: '用户端', STAFF: '员工端', ADMIN: '管理端' }

/** 查询包装：el-date-picker 区间 → beginTime/endTime（时间范围 00:00:00 ~ 23:59:59） */
async function fetchLoginLogs(q: PageQuery) {
  const params: PageQuery = { ...q }
  const range = params.timeRange as [string, string] | undefined
  delete params.timeRange
  if (range?.length === 2) {
    params.beginTime = `${range[0]} 00:00:00`
    params.endTime = `${range[1]} 23:59:59`
  }
  return pageLoginLogs(params)
}
</script>

<template>
  <div class="app-page">
    <TablePro :fetcher="fetchLoginLogs">
      <template #search="{ query }">
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="模糊查询" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="客户端">
          <el-select v-model="query.clientType" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="(text, val) in CLIENT_TYPE_TEXT" :key="val" :label="text" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 100px">
            <el-option label="成功" :value="0" />
            <el-option label="失败" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="query.timeRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
      </template>
      <template #toolbar="{ refresh }">
        <el-button icon="Refresh" @click="refresh">刷新</el-button>
      </template>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column label="客户端" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ CLIENT_TYPE_TEXT[row.clientType] ?? row.clientType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="loginIp" label="登录 IP" width="150" />
      <el-table-column prop="userAgent" label="浏览器 UA" min-width="260" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">
            {{ row.status === 0 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.msg ?? '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="loginTime" label="登录时间" width="180" />
    </TablePro>
  </div>
</template>
