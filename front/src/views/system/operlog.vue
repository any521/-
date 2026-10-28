<script setup lang="ts">
// 操作日志（审计查询，只读）：分页表格，满屏展示（TablePro 自动占满可用高度）
import { pageOperLogs } from '@/api/system'
</script>

<template>
  <div class="app-page">
    <TablePro :fetcher="pageOperLogs">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="操作模块" width="160" show-overflow-tooltip />
      <el-table-column label="请求方式" width="100">
        <template #default="{ row }">
          <el-tag :type="row.method === 'DELETE' ? 'danger' : row.method === 'POST' ? 'primary' : 'warning'" size="small">
            {{ row.method }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operName" label="操作人" width="110" />
      <el-table-column prop="operIp" label="操作 IP" width="150" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costTime" label="耗时(ms)" width="100" />
      <el-table-column prop="operTime" label="操作时间" width="180" />
    </TablePro>
  </div>
</template>
