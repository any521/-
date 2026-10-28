<script setup lang="ts">
// AI 供应商管理（CCSwitch 风格：协议兼容 openai/anthropic/responses/custom，密钥 AES 加密入库）
// 列表密钥脱敏；新增传明文，编辑留空表示不变更；连通性测试为轻量配置校验
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageAiProviders, saveAiProvider, removeAiProvider, testAiProvider,
  type AiProvider, type AiProviderDTO,
} from '@/api/ai'

// 厂商与协议选项（与后端 V11 种子数据对齐）
const VENDOR_OPTIONS = [
  { label: '阿里·通义千问', value: 'alibaba' },
  { label: '智谱 GLM', value: 'zhipu' },
  { label: 'DeepSeek', value: 'deepseek' },
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: '火山引擎', value: 'volcengine' },
  { label: '自定义厂商', value: 'custom' },
  { label: '内置 Mock', value: 'internal' },
]
const PROTOCOL_OPTIONS = [
  { label: 'OpenAI 兼容（/chat/completions）', value: 'openai' },
  { label: 'Anthropic（/v1/messages）', value: 'anthropic' },
  { label: '火山引擎 Responses（/responses）', value: 'responses' },
  { label: '自定义（全参数模板）', value: 'custom' },
]
const CAPABILITY_OPTIONS = ['vision', 'chat', 'reasoning']
const WEB_SEARCH_PROVIDER_OPTIONS = [
  { label: '自动（博查优先）', value: 'auto' },
  { label: '博查 Bocha（中文优化）', value: 'bocha' },
  { label: 'Tavily', value: 'tavily' },
]

// 表单提示常量（避免模板内引号/花括号转义）
const baseUrlPlaceholder = 'https://ark.cn-beijing.volces.com/api/v3'
const bodyTemplatePlaceholder = '{"model": {{model}}, "input": {{input}}, "temperature": {{temperature}}}'

const tableRef = ref<{ refresh: () => void; search: () => void }>()
const dialogVisible = ref(false)
const saving = ref(false)
const testing = ref<number | null>(null)
const form = reactive<AiProviderDTO>({})

// 模型列表 / 能力标签 / 自定义请求头 —— 动态编辑
const modelInput = ref('')
const capabilityInput = ref('')
const headerKeyInput = ref('')
const headerValueInput = ref('')

function resetForm() {
  Object.keys(form).forEach((k) => delete (form as Record<string, unknown>)[k])
  form.protocol = 'openai'
  form.vendor = 'alibaba'
  form.timeoutSeconds = 8
  form.maxTokens = 4096
  form.enabled = 1
  form.sort = 0
  form.webSearchEnabled = 1
  form.models = []
  form.capabilities = []
  form.extraHeaders = {}
  form.method = 'POST'
  form.bodyTemplate = ''
  form.responseTextPath = ''
  modelInput.value = ''
  capabilityInput.value = ''
  headerKeyInput.value = ''
  headerValueInput.value = ''
}

function openDialog(row?: AiProvider) {
  resetForm()
  if (row) {
    Object.assign(form, {
      ...row,
      // 编辑时密钥留空（不回显，不变更）；models/capabilities/extraHeaders 确保可编辑结构
      apiKey: '',
      models: row.models ? [...row.models] : [],
      capabilities: row.capabilities ? [...row.capabilities] : [],
      extraHeaders: row.extraHeaders ? { ...row.extraHeaders } : {},
    })
  }
  dialogVisible.value = true
}

function validate(): string | null {
  if (!form.name?.trim()) return '供应商名称不能为空'
  if (!form.vendor?.trim()) return '请选择厂商'
  if (!form.protocol?.trim()) return '请选择协议'
  if (!form.baseUrl?.trim()) return 'API 基址不能为空'
  // 新增时密钥二选一（internal 厂商除外）
  if (!form.id && form.vendor !== 'internal') {
    const hasKey = !!form.apiKey?.trim()
    const hasEnv = !!form.apiKeyEnv?.trim()
    if (!hasKey && !hasEnv) return '新增供应商需填写密钥或密钥环境变量名（内置 Mock 除外）'
  }
  return null
}

async function submit() {
  const err = validate()
  if (err) {
    ElMessage.warning(err)
    return
  }
  saving.value = true
  try {
    await saveAiProvider(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    saving.value = false
  }
}

async function remove(row: AiProvider) {
  await ElMessageBox.confirm(`确定删除供应商「${row.name}」吗？关联路由将失效。`, '提示', { type: 'warning' })
  await removeAiProvider(row.id)
  ElMessage.success('删除成功')
  tableRef.value?.refresh()
}

async function testRow(row: AiProvider) {
  testing.value = row.id
  try {
    const msg = await testAiProvider(row.id)
    ElMessage.success(msg)
  } finally {
    testing.value = null
  }
}

// 模型标签增删
function addModel() {
  const v = modelInput.value.trim()
  if (!v) return
  if (!form.models) form.models = []
  if (form.models.includes(v)) {
    ElMessage.warning('模型已存在')
    return
  }
  form.models.push(v)
  modelInput.value = ''
}
function removeModel(idx: number) {
  form.models?.splice(idx, 1)
}

// 能力标签增删
function addCapability() {
  const v = capabilityInput.value.trim()
  if (!v) return
  if (!form.capabilities) form.capabilities = []
  if (form.capabilities.includes(v)) {
    ElMessage.warning('能力已存在')
    return
  }
  form.capabilities.push(v)
  capabilityInput.value = ''
}
function removeCapability(idx: number) {
  form.capabilities?.splice(idx, 1)
}

// 自定义请求头增删
function addHeader() {
  const k = headerKeyInput.value.trim()
  const v = headerValueInput.value.trim()
  if (!k) return
  if (!form.extraHeaders) form.extraHeaders = {}
  form.extraHeaders[k] = v
  headerKeyInput.value = ''
  headerValueInput.value = ''
}
function removeHeader(key: string) {
  if (form.extraHeaders) delete form.extraHeaders[key]
}
</script>

<template>
  <div class="app-page">
    <TablePro ref="tableRef" :fetcher="pageAiProviders" :init-query="{ keyword: '' }">
      <template #search="{ query }">
        <el-form-item>
          <el-input
            v-model="query.keyword"
            placeholder="名称 / 厂商 / 备注"
            clearable
            style="width: 220px"
            @keyup.enter="tableRef?.search()"
          />
        </el-form-item>
      </template>
      <template #toolbar>
        <el-button v-permission="'system:ai:provider:list'" type="primary" icon="Plus" @click="openDialog()">
          新增供应商
        </el-button>
      </template>

      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="120">
        <template #default="{ row }">
          <el-tag>{{ row.name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="vendor" label="厂商" width="110">
        <template #default="{ row }">
          {{ VENDOR_OPTIONS.find((o) => o.value === row.vendor)?.label ?? row.vendor }}
        </template>
      </el-table-column>
      <el-table-column prop="protocol" label="协议" width="100">
        <template #default="{ row }">
          <el-tag type="info" effect="plain">{{ row.protocol }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="baseUrl" label="API 基址" min-width="220" show-overflow-tooltip />
      <el-table-column label="模型/能力" min-width="200">
        <template #default="{ row }">
          <div class="cell-tags">
            <el-tag v-for="m in row.models ?? []" :key="m" size="small" type="success">{{ m }}</el-tag>
            <el-tag v-for="c in row.capabilities ?? []" :key="c" size="small" type="warning">{{ c }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="密钥来源" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.apiKeyEnv" size="small" type="primary">环境变量</el-tag>
          <el-tag v-else-if="row.vendor === 'internal'" size="small" type="info">内置</el-tag>
          <el-tag v-else size="small">加密入库</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="timeoutSeconds" label="超时(秒)" width="90" align="center" />
      <el-table-column prop="maxTokens" label="输出上限" width="100" align="center">
        <template #default="{ row }">
          {{ row.maxTokens?.toLocaleString() ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="70" align="center" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'danger'">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:ai:provider:list'" link type="success" :loading="testing === row.id" @click="testRow(row as AiProvider)">
            测试
          </el-button>
          <el-button v-permission="'system:ai:provider:list'" link type="primary" @click="openDialog(row as AiProvider)">
            编辑
          </el-button>
          <el-button v-permission="'system:ai:provider:list'" link type="danger" @click="remove(row as AiProvider)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </TablePro>

    <FormDialog v-model="dialogVisible" :title="form.id ? '编辑供应商' : '新增供应商'" width="720px" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="120px">
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="供应商名称" required>
          <el-input v-model="form.name" placeholder="唯一标识，如 qwen-vl" />
        </el-form-item>
        <el-form-item label="厂商" required>
          <el-select v-model="form.vendor" placeholder="选择厂商" style="width: 100%">
            <el-option v-for="o in VENDOR_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="协议" required>
          <el-select v-model="form.protocol" placeholder="选择请求协议" style="width: 100%">
            <el-option v-for="o in PROTOCOL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 基址" required>
          <el-input v-model="form.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
        </el-form-item>
        <el-form-item label="请求路径">
          <el-input v-model="form.requestPath" placeholder="留空按协议推导（openai→/chat/completions, anthropic→/v1/messages）" />
        </el-form-item>

        <el-divider content-position="left">密钥配置</el-divider>
        <el-form-item label="密钥">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不变更' : '明文录入，后端 AES 加密入库'"
          />
        </el-form-item>
        <el-form-item label="密钥环境变量">
          <el-input v-model="form.apiKeyEnv" placeholder="如 QWEN_API_KEY；填写后优先从环境变量读取" />
        </el-form-item>

        <el-divider content-position="left">联网搜索（web-search 专用行）</el-divider>
        <el-form-item label="搜索服务商">
          <el-select v-model="form.webSearchProvider" placeholder="auto：博查优先，无则 Tavily" clearable style="width: 100%">
            <el-option v-for="o in WEB_SEARCH_PROVIDER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div class="empty-hint">仅对名称 <code>web-search</code> 的供应商行生效；密钥在上方「密钥配置」填写</div>
        </el-form-item>
        <el-form-item label="搜索启用">
          <el-switch v-model="form.webSearchEnabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          <div class="empty-hint">客服 Agent 与语音助手价格行情将使用该搜索配置；停用后静默降级（不联网）</div>
        </el-form-item>

        <el-divider content-position="left">模型与能力</el-divider>
        <el-form-item label="模型列表">
          <div class="tag-editor">
            <div class="tag-editor__tags">
              <el-tag v-for="(m, i) in form.models ?? []" :key="i" closable @close="removeModel(i)">{{ m }}</el-tag>
            </div>
            <div class="tag-editor__input">
              <el-input v-model="modelInput" placeholder="模型名，如 qwen-vl-max" @keyup.enter="addModel" />
              <el-button icon="Plus" @click="addModel">添加</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="能力标签">
          <div class="tag-editor">
            <div class="tag-editor__tags">
              <el-tag v-for="(c, i) in form.capabilities ?? []" :key="i" type="warning" closable @close="removeCapability(i)">{{ c }}</el-tag>
            </div>
            <div class="tag-editor__input">
              <el-select v-model="capabilityInput" placeholder="选择或输入能力" allow-create filterable clearable style="flex: 1" @change="addCapability">
                <el-option v-for="c in CAPABILITY_OPTIONS" :key="c" :label="c" :value="c" />
              </el-select>
            </div>
          </div>
        </el-form-item>

        <el-divider content-position="left">高级配置</el-divider>
        <el-form-item v-if="form.protocol === 'responses'" label="协议说明">
          <div class="protocol-tip">
            已内置火山引擎 Responses 协议：POST {{ baseUrlPlaceholder }}/responses，请求体
            <code v-pre>{"model","input","temperature"}</code>（多模态自动转换为
            <code>input_image</code>/<code>input_text</code>），响应取 <code>output[0].content[0].text</code>。
            仅需填写 API 基址（如 https://ark.cn-beijing.volces.com/api/v3）与密钥即可使用。
          </div>
        </el-form-item>
        <template v-if="form.protocol === 'custom'">
          <el-form-item label="请求方法">
            <el-select v-model="form.method" style="width: 200px">
              <el-option label="POST" value="POST" />
              <el-option label="GET" value="GET" />
            </el-select>
          </el-form-item>
          <el-form-item label="Body 模板">
            <div style="width: 100%">
              <el-input
                v-model="form.bodyTemplate"
                type="textarea"
                :rows="6"
                :placeholder="bodyTemplatePlaceholder"
              />
              <div class="empty-hint">
                占位符（替换时按 JSON 编码注入）：<code v-pre>{{model}}</code> 模型名、<code v-pre>{{messages}}</code>
                消息数组、<code v-pre>{{input}}</code> Responses 输入数组、<code v-pre>{{text}}</code> 纯文本拼接、
                <code v-pre>{{image}}</code> 首张图片 URL、<code v-pre>{{temperature}}</code> 温度。留空则回退 OpenAI 兼容模板。
              </div>
            </div>
          </el-form-item>
          <el-form-item label="响应文本路径">
            <el-input
              v-model="form.responseTextPath"
              placeholder="点路径，如 output.0.content.0.text / choices.0.message.content；留空默认 choices.0.message.content"
            />
          </el-form-item>
        </template>
        <el-form-item label="自定义请求头">
          <div class="header-editor">
            <div class="header-editor__list">
              <div v-for="(v, k) in form.extraHeaders ?? {}" :key="k" class="header-row">
                <el-tag closable @close="removeHeader(k as string)">{{ k }}: {{ v }}</el-tag>
              </div>
              <span v-if="!form.extraHeaders || Object.keys(form.extraHeaders).length === 0" class="empty-hint">
                custom 协议或需追加认证头时使用
              </span>
            </div>
            <div class="header-editor__input">
              <el-input v-model="headerKeyInput" placeholder="Header 名" style="flex: 1" />
              <el-input v-model="headerValueInput" placeholder="Header 值" style="flex: 1" />
              <el-button icon="Plus" @click="addHeader">添加</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="超时秒数">
          <el-input-number v-model="form.timeoutSeconds" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="输出上限">
          <el-input-number v-model="form.maxTokens" :min="512" :max="32768" :step="1024" />
          <div class="empty-hint">单次输出上限 token；长结构化 JSON（3D spec / 诊断）建议 8192，避免截断导致解析失败</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="供应商说明" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped lang="scss">
.cell-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-editor {
  width: 100%;

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 8px;
    min-height: 24px;
  }

  &__input {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.header-editor {
  width: 100%;

  &__list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 8px;
    min-height: 24px;
  }

  &__input {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.empty-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.protocol-tip {
  width: 100%;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 6px;

  code {
    padding: 1px 5px;
    font-size: 12px;
    color: var(--el-color-primary);
    background: var(--el-fill-color);
    border-radius: 4px;
  }
}

.empty-hint code {
  padding: 1px 5px;
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-fill-color);
  border-radius: 4px;
}
</style>
