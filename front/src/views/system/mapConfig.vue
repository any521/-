<script setup lang="ts">
// 地图配置管理：高德 Web Key / Server Key 专属配置页
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageParams, saveParam, type SysParam } from '@/api/system'

const loading = ref(false)
const saving = ref(false)
const form = reactive({
  webKey: { id: 0, paramName: '高德Web端Key（JS API）', paramKey: 'amap.web_key', paramValue: '', remark: '前端 JS API 2.0 用，经 /api/map/config 下发' },
  serverKey: { id: 0, paramName: '高德服务端Key（Web服务）', paramKey: 'amap.server_key', paramValue: '', remark: '后端代理高德 REST 用，禁止下发前端' },
  securityCode: { id: 0, paramName: '高德安全密钥', paramKey: 'amap.security_code', paramValue: '', remark: '控制台「安全密钥 jscode」，与 key 配对；后端 REST 追加 jscode、前端 JS API 注入 securityJsCode（V37）' },
})

async function loadConfig() {
  loading.value = true
  try {
    const page = await pageParams({ pageNum: 1, pageSize: 100, keyword: 'amap' })
    for (const item of page.list ?? []) {
      if (item.paramKey === 'amap.web_key') {
        Object.assign(form.webKey, item)
      } else if (item.paramKey === 'amap.server_key') {
        Object.assign(form.serverKey, item)
      } else if (item.paramKey === 'amap.security_code') {
        Object.assign(form.securityCode, item)
      }
    }
  } finally {
    loading.value = false
  }
}

async function submit() {
  saving.value = true
  try {
    await saveParam(form.webKey)
    await saveParam(form.serverKey)
    await saveParam(form.securityCode)
    ElMessage.success('保存成功，配置已热更新')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <div class="app-page">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>高德地图配置</span>
          <el-button type="primary" :loading="saving" @click="submit">保存配置</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="160px" style="max-width: 600px">
        <el-divider content-position="left">Web 端配置（JS API 2.0）</el-divider>
        <el-form-item label="Web Key">
          <el-input v-model="form.webKey.paramValue" placeholder="请输入高德 Web 端 Key" show-password />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.webKey.remark" type="textarea" :rows="2" disabled />
        </el-form-item>

        <el-divider content-position="left">服务端配置（Web 服务 API）</el-divider>
        <el-form-item label="Server Key">
          <el-input v-model="form.serverKey.paramValue" placeholder="请输入高德服务端 Key" show-password />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.serverKey.remark" type="textarea" :rows="2" disabled />
        </el-form-item>

        <el-divider content-position="left">安全密钥（jscode / securityJsCode）</el-divider>
        <el-form-item label="安全密钥">
          <el-input v-model="form.securityCode.paramValue" placeholder="请输入高德控制台「安全密钥 jscode」" show-password />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.securityCode.remark" type="textarea" :rows="2" disabled />
        </el-form-item>
        <div class="form-tip">高德新版要求 key 与安全密钥配对：本密钥将作为后端 REST 的 jscode 参数、前端 JS API 2.0 的 securityJsCode 注入。</div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>