<script setup lang="ts">
// AI 能力路由管理（CCSwitch 风格：能力 → 路由链 → 降级兜底）
// 路由链 chain 依序尝试，全部失败走 fallback 降级；供应商选项来自 ai_provider（启用）
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageAiRoutes, saveAiRoute, removeAiRoute,
  pageAiProviders,
  type AiRoute, type AiRouteDTO, type AiProvider,
} from '@/api/ai'

// 降级代号选项（与后端 V11 种子数据 + Python routing 对齐）
const FALLBACK_OPTIONS = [
  { label: '转人工审核', value: 'MANUAL_REVIEW' },
  { label: '知识库规则', value: 'KNOWLEDGE_RULE' },
  { label: '文本输入兜底', value: 'TEXT_INPUT' },
  { label: '移动平均预测', value: 'MOVING_AVERAGE' },
  { label: '基础报价', value: 'BASE_PRICE' },
  { label: '规则引擎', value: 'RULE_ENGINE' },
]
// 能力标识 → 中文（与后端 V11 种子 remark / ai-back routing.json 对齐）
const CAPABILITY_LABELS: Record<string, string> = {
  visual_verify: '视觉审核',
  repair_diagnose: '维修预诊',
  voice_parse: '语音解析',
  forecast: '回收量预测',
  pricing: '动态定价',
  agent_plan: 'Agent 规划',
}
/** 未知能力标识回退原文，保证自定义能力不丢失可读性 */
const capabilityLabel = (v?: string | null) => (v ? CAPABILITY_LABELS[v] ?? v : '—')
/** 降级代号 → 中文 */
const fallbackLabel = (v?: string | null) => FALLBACK_OPTIONS.find((o) => o.value === v)?.label ?? v ?? '—'

// 常见能力标识选项（仅作输入提示，不限制）
const CAPABILITY_HINTS = Object.entries(CAPABILITY_LABELS).map(([value, label]) => ({ label, value }))

const tableRef = ref<{ refresh: () => void; search: () => void }>()
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<AiRouteDTO>({})

// 供应商选项（启用列表，供路由链 provider 选择）
const providers = ref<AiProvider[]>([])

async function loadProviders() {
  // 拉取全部启用的供应商（不分页：pageSize 设大值）
  const res = await pageAiProviders({ pageNum: 1, pageSize: 100, keyword: '' })
  providers.value = (res.list ?? []).filter((p) => p.enabled === 1)
}

/** 根据供应商 name 取其模型列表 */
function modelsOf(providerName: string): string[] {
  return providers.value.find((p) => p.name === providerName)?.models ?? []
}

function resetForm() {
  Object.keys(form).forEach((k) => delete (form as Record<string, unknown>)[k])
  form.chain = []
  form.fallback = 'MANUAL_REVIEW'
  form.maxRetries = 1
  form.retryTemperatureDecay = 0.5
  form.enabled = 1
}

function openDialog(row?: AiRoute) {
  resetForm()
  if (row) {
    Object.assign(form, {
      ...row,
      chain: row.chain ? row.chain.map((h) => ({ ...h })) : [],
      retryTemperatureDecay: Number(row.retryTemperatureDecay),
    })
  }
  dialogVisible.value = true
}

function validate(): string | null {
  if (!form.capability?.trim()) return '能力标识不能为空'
  if (!form.chain || form.chain.length === 0) return '路由链至少需要一跳'
  for (let i = 0; i < form.chain.length; i++) {
    const hop = form.chain[i]
    if (!hop) continue
    if (!hop.provider?.trim()) return `第 ${i + 1} 跳未选择供应商`
    if (!hop.model?.trim()) return `第 ${i + 1} 跳未选择模型`
  }
  if (!form.fallback?.trim()) return '请选择降级方案'
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
    await saveAiRoute(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    saving.value = false
  }
}

async function remove(row: AiRoute) {
  await ElMessageBox.confirm(`确定删除路由「${capabilityLabel(row.capability)}」吗？`, '提示', { type: 'warning' })
  await removeAiRoute(row.id)
  ElMessage.success('删除成功')
  tableRef.value?.refresh()
}

// 路由链编辑：增删 + 上下移动
function addHop() {
  if (!form.chain) form.chain = []
  // 默认预选第一个供应商与其首个模型
  const first = providers.value[0]
  form.chain.push({
    provider: first?.name ?? '',
    model: first?.models?.[0] ?? '',
  })
}
function removeHop(idx: number) {
  form.chain?.splice(idx, 1)
}
function moveHop(idx: number, delta: -1 | 1) {
  const arr = form.chain
  if (!arr) return
  const target = idx + delta
  if (target < 0 || target >= arr.length) return
  const a = arr[idx]
  const b = arr[target]
  if (!a || !b) return
  arr[idx] = b
  arr[target] = a
}
/** 切换供应商时重置模型为该供应商首个模型 */
function onProviderChange(hop: { provider: string; model: string }) {
  const models = modelsOf(hop.provider)
  if (hop.model && models.includes(hop.model)) return
  hop.model = models[0] ?? ''
}

onMounted(loadProviders)
</script>

<template>
  <div class="app-page">
    <TablePro ref="tableRef" :fetcher="pageAiRoutes" :init-query="{ keyword: '' }">
      <template #search="{ query }">
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="能力标识 / 备注" clearable style="width: 220px" @keyup.enter="tableRef?.search()" />
        </el-form-item>
      </template>
      <template #toolbar>
        <el-button v-permission="'system:ai:route:list'" type="primary" icon="Plus" @click="openDialog()">
          新增路由
        </el-button>
      </template>

      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="capability" label="能力标识" min-width="150">
        <template #default="{ row }">
          <el-tag type="success">{{ capabilityLabel(row.capability) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="路由链（依序尝试）" min-width="320">
        <template #default="{ row }">
          <div class="chain-flow">
            <template v-for="(hop, i) in row.chain ?? []" :key="i">
              <el-tag size="small" type="primary">{{ hop.provider }} · {{ hop.model }}</el-tag>
              <span v-if="Number(i) < (row.chain?.length ?? 0) - 1" class="chain-arrow">→</span>
            </template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="降级方案" width="140">
        <template #default="{ row }">
          <el-tag size="small" type="warning">{{ fallbackLabel(row.fallback) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="maxRetries" label="重试次数" width="90" align="center" />
      <el-table-column label="温度衰减" width="90" align="center">
        <template #default="{ row }">{{ Number(row.retryTemperatureDecay).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'danger'">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:ai:route:list'" link type="primary" @click="openDialog(row as AiRoute)">编辑</el-button>
          <el-button v-permission="'system:ai:route:list'" link type="danger" @click="remove(row as AiRoute)">删除</el-button>
        </template>
      </el-table-column>
    </TablePro>

    <FormDialog v-model="dialogVisible" :title="form.id ? '编辑路由' : '新增路由'" width="780px" :loading="saving" @confirm="submit">
      <el-form :model="form" label-width="120px">
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="能力标识" required>
          <el-select
            v-model="form.capability"
            placeholder="选择或输入能力标识"
            allow-create
            filterable
            default-first-option
            style="width: 100%"
          >
            <el-option v-for="c in CAPABILITY_HINTS" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="降级方案" required>
          <el-select v-model="form.fallback" placeholder="选择降级代号" style="width: 100%">
            <el-option v-for="o in FALLBACK_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">路由链（依序尝试，全部失败走降级）</el-divider>
        <el-form-item label="路由链" required>
          <div class="chain-editor">
            <div v-for="(hop, idx) in form.chain ?? []" :key="idx" class="chain-row">
              <span class="chain-idx">{{ idx + 1 }}</span>
              <el-select
                v-model="hop.provider"
                placeholder="供应商"
                style="width: 180px"
                @change="onProviderChange(hop)"
              >
                <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.name" />
              </el-select>
              <el-select v-model="hop.model" placeholder="模型" style="width: 200px" :disabled="!hop.provider">
                <el-option v-for="m in modelsOf(hop.provider)" :key="m" :label="m" :value="m" />
              </el-select>
              <el-button-group>
                <el-button icon="ArrowUp" :disabled="idx === 0" @click="moveHop(idx, -1)" />
                <el-button icon="ArrowDown" :disabled="idx === (form.chain?.length ?? 0) - 1" @click="moveHop(idx, 1)" />
              </el-button-group>
              <el-button icon="Delete" type="danger" @click="removeHop(idx)" />
            </div>
            <el-button v-if="(form.chain?.length ?? 0) === 0 || providers.length === 0" type="primary" plain icon="Plus" @click="addHop">
              {{ providers.length === 0 ? '无可用供应商，请先到供应商页新增' : '添加首跳' }}
            </el-button>
            <el-button v-else type="primary" plain icon="Plus" @click="addHop">追加一跳</el-button>
          </div>
        </el-form-item>

        <el-divider content-position="left">重试与降级参数</el-divider>
        <el-form-item label="重试次数">
          <el-input-number v-model="form.maxRetries" :min="0" :max="5" />
          <span class="form-hint">单供应商出参校验失败的重试次数（0=不重试）</span>
        </el-form-item>
        <el-form-item label="温度衰减">
          <el-input-number v-model="form.retryTemperatureDecay" :min="0" :max="1" :step="0.1" :precision="2" />
          <span class="form-hint">重试时 temperature 乘以此系数（0~1）</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="路由说明" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<style scoped lang="scss">
.chain-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;

  .chain-arrow {
    color: var(--el-text-color-placeholder);
    margin: 0 2px;
  }
}

.chain-editor {
  width: 100%;
}

.chain-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;

  .chain-idx {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--el-color-primary);
    color: #fff;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
}

.form-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  margin-left: 12px;
}
</style>
