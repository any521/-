<script setup lang="ts">
// Agent 驾驶舱（C5/F8 对接真实契约）：Agent 吞吐/哈希链健康 + HITL 决策审批（冻结契约单接口 pass/reason）+ 高德地图片区碳减排标注
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '@/api/request'
import {
  getAgentCockpit, pageAgentDecisions, getAgentDecision, approveDecision,
  getDashboardOverview,
  RISK_LEVEL_TEXT, DECISION_STATUS_TEXT,
  type AgentCockpit, type AgentDecision, type AreaRank,
} from '@/api/business'

const cockpit = ref<AgentCockpit | null>(null)
const tableRef = ref<{
  refresh: () => void
  search: () => void
  reset: () => void
  query: { pageNum: number; pageSize: number; status?: number }
}>()

// ---- HITL 审批分页（大屏底部全宽分页条：表格内分页关闭，翻页时同步 TablePro 内部 query） ----
const page = reactive({ pageNum: 1, pageSize: 10 })
const total = ref(0)
function syncPager(t: number) {
  total.value = t
}
function pageChange() {
  const t = tableRef.value
  if (!t) return
  t.query.pageNum = page.pageNum
  t.query.pageSize = page.pageSize
  t.refresh()
}

// ---- HITL 审批查询（状态下拉 + 查询/重置，置于分页条左上方） ----
const queryStatus = ref<number>()
function searchDecisions() {
  const t = tableRef.value
  if (!t) return
  t.query.status = queryStatus.value
  t.search()
}
function resetDecisions() {
  queryStatus.value = undefined
  const t = tableRef.value
  if (!t) return
  t.reset()
}

const riskTagType: Record<number, 'info' | 'warning' | 'danger'> = { 1: 'info', 2: 'warning', 3: 'danger' }
const statusTagType: Record<number, 'warning' | 'success' | 'danger' | 'primary' | 'info'> = {
  0: 'warning', 1: 'success', 2: 'danger', 3: 'primary', 4: 'info',
}

// ---- 片区碳减排地图（高德）：真实片区坐标 + 账本碳减排标注，替代原 3D 模拟热力网格 ----
const areaRanks = ref<Array<AreaRank & { heat: number }>>([])
const mapReady = ref(false)
const mapFailed = ref(false)
let mapInstance: any = null
let markerList: any[] = []

async function loadCockpit() {
  cockpit.value = await getAgentCockpit()
}

/** 片区数据（排行 + 地图标注共用同一真实聚合） */
async function loadAreaData() {
  const overview = await getDashboardOverview()
  const ranking = overview.areaRanking
  // 无坐标片区按片区名地理编码补齐（参考客户端 track.vue 地址→坐标用法：/map/geocode 后端 10min 缓存；失败静默降级，仅该片区不标注，不阻塞排行）
  await Promise.all(
    ranking.map(async (a) => {
      if (a.lng != null && a.lat != null) return
      try {
        const g = await request.get<{ lng?: number; lat?: number }>('/map/geocode', { address: a.areaName }, { silent: true })
        if (g && g.lng != null && g.lat != null) {
          a.lng = g.lng
          a.lat = g.lat
        }
      } catch {
        /* 地理编码失败静默 */
      }
    }),
  )
  const max = Math.max(...ranking.map((a) => a.carbonReduced ?? 0), 1)
  // 右侧排行：碳减排归一化（与地图标注同源），按碳减排降序
  areaRanks.value = ranking
    .map((a) => ({ ...a, heat: Math.round(((a.carbonReduced ?? 0) / max) * 100) }))
    .sort((x, y) => (y.carbonReduced ?? 0) - (x.carbonReduced ?? 0))
  await initMap(ranking)
}

// ---- 高德地图加载（参考用户端 useAmap.ts 单例模式：key 优先环境变量，缺省走 /api/map/config；加载 Promise 缓存，失败释放允许下次重试） ----
let amapLoadPromise: Promise<boolean> | null = null

function ensureAmap(): Promise<boolean> {
  if ((window as unknown as { AMap?: unknown }).AMap) return Promise.resolve(true)
  if (amapLoadPromise) return amapLoadPromise
  amapLoadPromise = (async () => {
    try {
      let webKey = import.meta.env.VITE_AMAP_WEB_KEY
      let securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE
      if (!webKey) {
        const cfg = await request.get<{ webKey?: string; securityCode?: string }>('/map/config', undefined, { silent: true })
        webKey = cfg?.webKey?.trim() ?? ''
        if (!securityCode) securityCode = cfg?.securityCode?.trim() ?? ''
      }
      if (!webKey) return false
      const w = window as unknown as { _AMapSecurityConfig?: { securityJsCode?: string } }
      if (securityCode) w._AMapSecurityConfig = { securityJsCode: securityCode }
      await loadScript(`https://webapi.amap.com/maps?v=2.0&key=${webKey}`)
      return !!(window as unknown as { AMap?: unknown }).AMap
    } catch {
      return false
    } finally {
      // 失败后释放 Promise：后端恢复 / key 补齐后不再永久降级（与用户端 useAmap 一致）
      if (!(window as unknown as { AMap?: unknown }).AMap) amapLoadPromise = null
    }
  })()
  return amapLoadPromise
}

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('AMap 脚本加载失败'))
    document.head.appendChild(script)
  })
}

/** 初始化地图：每个片区一个气泡 Marker（片区名 + 碳减排量），点击弹详情，自动缩放到全片区 */
async function initMap(ranking: AreaRank[]) {
  const withCoord = ranking.filter((a) => a.lng != null && a.lat != null)
  if (!withCoord.length) {
    mapFailed.value = true // 片区均无坐标，无地图可标
    return
  }
  if (!(await ensureAmap())) {
    mapFailed.value = true // 未配置高德 Key，无法加载地图
    return
  }
  // 先置 mapReady 让 v-if 容器渲染，nextTick 后再取 DOM：容器不存在时直接 return 会永远转圈（v-if 未渲染 → mapReady 永不置位）
  mapReady.value = true
  await nextTick()
  const AMap = (window as unknown as { AMap: any }).AMap
  const container = document.getElementById('cockpit-map')
  if (!container || mapInstance) return

  mapInstance = new AMap.Map('cockpit-map', {
    zoom: 11,
    center: [Number(withCoord[0]!.lng), Number(withCoord[0]!.lat)],
    scrollWheel: true,
  })

  const max = Math.max(...ranking.map((a) => a.carbonReduced ?? 0), 1)
  markerList = withCoord.map((a) => {
    const carbon = Number(a.carbonReduced ?? 0)
    const heat = Math.round((carbon / max) * 100)
    // 热力配色与右侧排行一致：低 绿 → 高 金（CSS 内联避免 scoped 样式不作用于地图 DOM）
    const color = heat >= 80 ? '#e8bc3f' : heat >= 50 ? '#45c986' : '#2f6b5e'
    const marker = new AMap.Marker({
      position: [Number(a.lng), Number(a.lat)],
      anchor: 'bottom-center',
      content: `<div style="transform:translate(-50%,-100%);text-align:center;white-space:nowrap;">
          <div style="background:${color};color:#fff;font-size:12px;font-weight:600;padding:4px 8px;border-radius:10px;box-shadow:0 1px 4px rgba(0,0,0,.3);cursor:pointer;">
            ${a.areaName} · ${carbon.toFixed(1)} kgCO₂
          </div>
          <div style="width:0;height:0;margin:0 auto;border-left:6px solid transparent;border-right:6px solid transparent;border-top:6px solid ${color};"></div>
        </div>`,
      map: mapInstance,
    })
    marker.on('click', () => openAreaInfo(a, marker))
    return marker
  })

  mapInstance.setFitView(markerList, false, [50, 50, 50, 50])
  mapReady.value = true
}

/** 点击气泡 → 片区碳减排详情窗口 */
function openAreaInfo(a: AreaRank, marker: any) {
  const AMap = (window as unknown as { AMap: any }).AMap
  const info = new AMap.InfoWindow({
    content: `<div style="min-width:180px;padding:4px 2px;font-size:13px;">
        <div style="font-weight:600;margin-bottom:6px;">${a.areaName}</div>
        <div>碳减排：<b style="color:#45c986;">${Number(a.carbonReduced ?? 0).toFixed(1)} kgCO₂</b></div>
        <div>回收量：${(a.recycleWeight ?? 0).toLocaleString()} kg · ${a.recycleOrders ?? 0} 单</div>
        <div>垃圾投放：${(a.kitchenBags ?? 0).toLocaleString()} 袋</div>
      </div>`,
    offset: new AMap.Pixel(0, -10),
  })
  info.open(mapInstance, marker.getPosition())
}

function destroyMap() {
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
  markerList = []
}

const pendingTotal = computed(() =>
  Object.values(cockpit.value?.pendingByRisk ?? {}).reduce((sum, n) => sum + n, 0),
)

// ---- HITL 审批（冻结契约：单接口 pass/reason，驳回必填原因；高风险双人复核由后端强制异人） ----
const approveDialog = ref(false)
const approving = ref(false)
const current = ref<AgentDecision | null>(null)
const approveForm = ref({ pass: true, reason: '' })

function openApprove(row: AgentDecision, pass: boolean) {
  current.value = row
  approveForm.value = { pass, reason: '' }
  approveDialog.value = true
}

async function submitApprove() {
  if (!approveForm.value.pass && !approveForm.value.reason.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  approving.value = true
  try {
    await approveDecision(current.value!.id, approveForm.value.pass, approveForm.value.reason || undefined)
    ElMessage.success(approveForm.value.pass ? '已审批通过' : '已驳回')
    approveDialog.value = false
    tableRef.value?.refresh()
    loadCockpit()
  } finally {
    approving.value = false
  }
}

// ---- 决策单详情（证据链/哈希链留痕） ----
const detailVisible = ref(false)
const detail = ref<AgentDecision | null>(null)

async function openDetail(id: string) {
  detail.value = await getAgentDecision(id)
  detailVisible.value = true
}

function prettyJson(raw: string | null) {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

onMounted(() => {
  loadCockpit()
  loadAreaData()
})

onBeforeUnmount(destroyMap)
</script>

<template>
  <div class="app-page cockpit">
    <!-- Agent 吞吐卡片 -->
    <el-row :gutter="16" class="cockpit__agents">
      <el-col v-for="agent in cockpit?.agents ?? []" :key="agent.agentId" :span="6">
        <div class="app-card cockpit__agent">
          <div class="cockpit__agent-head">
            <span class="cockpit__agent-name">{{ agent.agentId }}</span>
            <el-tag :type="agent.failed > 0 ? 'danger' : agent.pending > 0 ? 'warning' : 'success'" size="small">
              {{ agent.failed > 0 ? '有失败' : agent.pending > 0 ? '待审批' : '正常' }}
            </el-tag>
          </div>
          <div class="cockpit__agent-role">
            累计 {{ agent.total }} · 已执行 {{ agent.executed }} · 失败 {{ agent.failed }}
          </div>
          <div class="cockpit__agent-actions">今日决策 <b>{{ agent.todayCount }}</b> 单 · 待审 <b>{{ agent.pending }}</b></div>
        </div>
      </el-col>
    </el-row>

    <!-- 哈希链健康 + 待审风险分布 -->
    <div class="app-card cockpit__chain">
      <el-space wrap :size="24">
        <span>
          决策哈希链
          <el-tag :type="cockpit?.chainValid ? 'success' : 'danger'" size="small">
            {{ cockpit?.chainValid ? '完整可信' : '校验失败' }}
          </el-tag>
        </span>
        <span>链长 <b>{{ cockpit?.chainLength ?? 0 }}</b></span>
        <span class="cockpit__hash">链尾哈希 {{ cockpit?.lastDecisionHash ?? '—' }}</span>
        <el-divider direction="vertical" />
        <span>待审批 <b>{{ pendingTotal }}</b> 单：</span>
        <el-tag v-for="(count, level) in cockpit?.pendingByRisk ?? {}" :key="level" :type="riskTagType[Number(level)] ?? 'info'" size="small">
          {{ RISK_LEVEL_TEXT[Number(level)] ?? level }}风险 {{ count }}
        </el-tag>
      </el-space>
    </div>

    <div class="cockpit__row">
      <!-- 3D 热力大屏（Three.js）：左侧 70%，占满剩余屏幕高度 -->
      <div class="cockpit__col cockpit__col--3d">
        <div class="app-card cockpit__heat-card">
          <div class="cockpit__panel-head">
            <h4>片区碳减排地图</h4>
            <span class="cockpit__meta">气泡标注片区累计碳减排量 kgCO₂ · 点击查看详情</span>
          </div>
          <div class="cockpit__heat">
            <div v-if="mapReady" id="cockpit-map" class="cockpit__map" />
            <el-empty
              v-else-if="mapFailed" description="地图不可用：未配置高德 Web Key 或片区无坐标（系统-地图配置可维护）"
              :image-size="80"
            />
            <div v-else v-loading="true" class="cockpit__map" />
          </div>
        </div>
      </div>
      <!-- HITL 决策流审批：右侧 30% -->
      <div class="cockpit__col cockpit__col--table">
        <!-- 对应地图标注的片区排行 -->
        <div class="app-card cockpit__areas">
          <div class="cockpit__panel-head">
            <h4>片区碳减排排行</h4>
            <span class="cockpit__meta">对应地图标注 · 按碳减排量排序</span>
          </div>
          <div v-for="a in areaRanks" :key="a.areaId" class="cockpit__area">
            <div class="cockpit__area-head">
              <span class="cockpit__area-name">{{ a.areaName }}</span>
              <span class="cockpit__area-heat">{{ Number(a.carbonReduced ?? 0).toFixed(1) }} kgCO₂</span>
            </div>
            <div class="cockpit__area-track">
              <div class="cockpit__area-bar" :style="{ width: `${a.heat}%` }" />
            </div>
            <div class="cockpit__area-meta">
              回收 {{ (a.recycleWeight ?? 0).toLocaleString() }} kg · 订单 {{ a.recycleOrders ?? 0 }} · 垃圾投放 {{ (a.kitchenBags ?? 0).toLocaleString() }} 袋
            </div>
          </div>
        </div>
        <TablePro ref="tableRef" :fetcher="pageAgentDecisions" :pagination="false" :search="false" :on-loaded="syncPager">
          <template #search="{ query }">
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
                <el-option v-for="(text, val) in DECISION_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </template>
          <el-table-column prop="agentId" label="Agent" width="110" show-overflow-tooltip />
          <el-table-column prop="actionType" label="动作" min-width="130" show-overflow-tooltip />
          <el-table-column label="风险" width="70">
            <template #default="{ row }">
              <el-tag :type="riskTagType[row.riskLevel] ?? 'info'" size="small">{{ RISK_LEVEL_TEXT[row.riskLevel] ?? row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType[row.status] ?? 'info'" size="small">{{ DECISION_STATUS_TEXT[row.status] ?? row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="info" @click="openDetail(row.id)">详情</el-button>
              <template v-if="row.status === 0">
                <el-button v-permission="'smart:decision:approve'" link type="success" @click="openApprove(row as AgentDecision, true)">通过</el-button>
                <el-button v-permission="'smart:decision:approve'" link type="danger" @click="openApprove(row as AgentDecision, false)">驳回</el-button>
              </template>
            </template>
          </el-table-column>
        </TablePro>
      </div>
    </div>

    <!-- HITL 审批页脚：查询区 + 分页条（整页左下角） -->
    <div class="cockpit__footer">
      <el-form inline class="cockpit__query" @submit.prevent="searchDecisions">
        <el-form-item label="状态">
          <el-select v-model="queryStatus" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(text, val) in DECISION_STATUS_TEXT" :key="val" :label="text" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="searchDecisions">查询</el-button>
          <el-button icon="Refresh" @click="resetDecisions">重置</el-button>
        </el-form-item>
      </el-form>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50]"
        :total="total"
        @change="pageChange"
      />
    </div>

    <!-- HITL 审批弹窗 -->
    <FormDialog v-model="approveDialog" :title="approveForm.pass ? '审批通过' : '审批驳回'" :loading="approving" @confirm="submitApprove">
      <el-alert
        v-if="current?.riskLevel === 3" type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="高风险决策需双人复核：首审通过后仍为待审批，须另一管理员二次通过方可执行"
      />
      <el-descriptions v-if="current" :column="1" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="Agent">{{ current.agentId }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ current.actionType }}</el-descriptions-item>
        <el-descriptions-item label="目标">{{ current.targetUri }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">{{ RISK_LEVEL_TEXT[current.riskLevel] ?? current.riskLevel }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px">
        <el-form-item :label="approveForm.pass ? '备注' : '驳回原因'" :required="!approveForm.pass">
          <el-input v-model="approveForm.reason" type="textarea" :rows="3" maxlength="200" :placeholder="approveForm.pass ? '可选' : '必填'" />
        </el-form-item>
      </el-form>
    </FormDialog>

    <!-- 决策单详情弹窗 -->
    <el-dialog v-model="detailVisible" title="决策单详情（证据/哈希链留痕）" width="720px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="Agent">{{ detail.agentId }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detail.actionType }}</el-descriptions-item>
        <el-descriptions-item label="目标" :span="2">{{ detail.targetUri }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskTagType[detail.riskLevel] ?? 'info'" size="small">{{ RISK_LEVEL_TEXT[detail.riskLevel] ?? detail.riskLevel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType[detail.status] ?? 'info'" size="small">{{ DECISION_STATUS_TEXT[detail.status] ?? detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="首审">{{ detail.firstApprover ?? '—' }} {{ detail.firstApproveTime ?? '' }}</el-descriptions-item>
        <el-descriptions-item label="二审">{{ detail.secondApprover ?? '—' }} {{ detail.secondApproveTime ?? '' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.rejectReason" label="驳回原因" :span="2">{{ detail.rejectReason }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.executeResult" label="执行结果" :span="2">{{ detail.executeResult }} {{ detail.executeTime ?? '' }}</el-descriptions-item>
        <el-descriptions-item label="决策哈希" :span="2"><code class="cockpit__hash">{{ detail.decisionHash }}</code></el-descriptions-item>
        <el-descriptions-item label="前链哈希" :span="2"><code class="cockpit__hash">{{ detail.prevHash ?? '—（创世）' }}</code></el-descriptions-item>
      </el-descriptions>
      <template v-if="detail">
        <h4 class="cockpit__json-title">决策参数</h4>
        <pre class="cockpit__json">{{ prettyJson(detail.paramsJson) }}</pre>
        <h4 class="cockpit__json-title">证据链</h4>
        <pre class="cockpit__json">{{ prettyJson(detail.evidencesJson) }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.cockpit__agents {
  flex-shrink: 0;
}

.cockpit__chain {
  margin-top: $df-space-lg;
  flex-shrink: 0;

  b {
    color: var(--df-color-primary);
  }
}

// 主区域：3D（70%）+ HITL 审批（30%）占满剩余屏幕高度
.cockpit__row {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: $df-space-lg;
  margin-top: $df-space-lg;
}

.cockpit__col {
  min-width: 0;
  display: flex;
  flex-direction: column;

  &--3d {
    flex: 7;
  }

  &--table {
    flex: 3;
    gap: $df-space-lg;

    > .table-pro {
      height: auto;
      flex: 1;
      min-height: 0;
    }
  }
}

// 页脚：查询区与分页条水平排列，整体靠左（大屏驾驶舱左下角）
.cockpit__footer {
  position: relative;
  flex-shrink: 0;
  margin-top: $df-space-lg;
  padding: $df-space-sm 0 $df-space-xs;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: $df-space-lg;
}

.cockpit__query {
  flex-shrink: 0;
  margin-bottom: 0;
}

.cockpit__heat-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.cockpit__heat {
  flex: 1;
  min-height: 0;
}

.cockpit__map {
  width: 100%;
  height: 100%;
  border-radius: $df-radius-sm;
  background: var(--el-fill-color-light);
}

.cockpit__hash {
  font-family: monospace;
  font-size: $df-font-size-sm;
  color: var(--df-text-secondary);
  word-break: break-all;
}

.cockpit__agent {
  &-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &-name {
    font-weight: 600;
  }

  &-role {
    margin-top: $df-space-sm;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
  }

  &-actions {
    margin-top: $df-space-sm;
    font-size: $df-font-size-sm;

    b {
      color: var(--df-color-primary);
    }
  }
}

.cockpit__areas {
  flex-shrink: 0;
}

.cockpit__area {
  + .cockpit__area {
    margin-top: $df-space-md;
  }

  &-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
  }

  &-name {
    font-weight: 600;
  }

  &-heat {
    font-family: monospace;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
  }

  &-track {
    height: 8px;
    margin-top: $df-space-xs;
    border-radius: 4px;
    background: var(--el-fill-color-light);
    overflow: hidden;
  }

  // 与 3D 热力配色一致：低 绿 → 高 金
  &-bar {
    height: 100%;
    border-radius: 4px;
    background: linear-gradient(90deg, #45c986, #e8bc3f);
  }

  &-meta {
    margin-top: $df-space-xs;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
  }
}

.cockpit__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $df-space-md;
  flex-shrink: 0;

  h4 {
    font-weight: 500;
  }
}

.cockpit__meta {
  font-size: $df-font-size-sm;
  color: var(--df-text-secondary);
}

.cockpit__json-title {
  margin: $df-space-md 0 $df-space-xs;
  font-weight: 500;
}

.cockpit__json {
  max-height: 200px;
  overflow: auto;
  padding: $df-space-sm;
  background: var(--el-fill-color-light);
  border-radius: $df-radius-sm;
  font-size: $df-font-size-sm;
}
</style>
