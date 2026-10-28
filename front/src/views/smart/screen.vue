<script setup lang="ts">
// 数据大屏（C5 对接真实契约 DashboardOverviewVO：全量口径 KPI/近 12 月月报/片区排行 + EasyExcel 导出）
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { getDashboardOverview, DASHBOARD_EXPORT_URL, type DashboardOverview } from '@/api/business'
import { formatWeight, formatCarbon } from '@shared/utils/format'
import { tokenStore } from '@/api/token'

const overview = ref<DashboardOverview | null>(null)
let timer = 0

const kpis = computed(() => [
  { label: '今日回收单', value: overview.value?.recycleTodayOrders ?? '—' },
  { label: '累计回收单（完成）', value: overview.value ? `${overview.value.recycleTotalOrders}（${overview.value.recycleFinishedOrders}）` : '—' },
  { label: '累计回收量', value: overview.value ? formatWeight(overview.value.recycleTotalWeight) : '—' },
  { label: '累计减碳量', value: overview.value ? formatCarbon(overview.value.carbonReducedTotal) : '—' },
  { label: '今日垃圾投放(袋)', value: overview.value?.kitchenTodayBags ?? '—' },
  { label: '累计垃圾投放(袋)', value: overview.value?.kitchenTotalBags ?? '—' },
  { label: '维修工单（完成）', value: overview.value ? `${overview.value.repairTotalOrders}（${overview.value.repairFinishedOrders}）` : '—' },
  { label: '维修转回收', value: overview.value?.repairTransferredOrders ?? '—' },
])

const darkText = { color: '#a3b1c2' }

// 近 12 月月报趋势（缺月后端已补零）
const trendOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis' },
  legend: { data: ['回收单数', '回收量(kg)', '减碳量(kg)'], textStyle: darkText },
  grid: { left: 48, right: 16, top: 36, bottom: 28 },
  xAxis: { type: 'category', data: overview.value?.monthlyReport.map((m) => m.month) ?? [], axisLabel: darkText },
  yAxis: { type: 'value', axisLabel: darkText, splitLine: { lineStyle: { color: '#1c3550' } } },
  series: [
    { name: '回收单数', type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, data: overview.value?.monthlyReport.map((m) => m.recycleOrders) ?? [] },
    { name: '回收量(kg)', type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, data: overview.value?.monthlyReport.map((m) => m.recycleWeight) ?? [] },
    { name: '减碳量(kg)', type: 'line', smooth: true, data: overview.value?.monthlyReport.map((m) => m.carbonReduced) ?? [] },
  ],
  color: ['#45c986', '#4fb8d0', '#e8bc3f'],
}))

// 片区垃圾投放对比
const kitchenOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, textStyle: darkText },
  series: [
    {
      type: 'pie',
      radius: ['40%', '68%'],
      label: { color: '#a3b1c2' },
      data: overview.value?.areaRanking.map((a) => ({ name: a.areaName, value: a.kitchenBags })) ?? [],
    },
  ],
  color: ['#45c986', '#4fb8d0', '#e8bc3f', '#f29d28', '#7fd0a8', '#909399'],
}))

// 片区回收量排行
const rankOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis' },
  grid: { left: 90, right: 32, top: 16, bottom: 28 },
  xAxis: { type: 'value', axisLabel: darkText, splitLine: { lineStyle: { color: '#1c3550' } } },
  yAxis: {
    type: 'category',
    inverse: true,
    data: overview.value?.areaRanking.map((a) => a.areaName) ?? [],
    axisLabel: darkText,
  },
  series: [
    {
      type: 'bar',
      barWidth: 12,
      itemStyle: { borderRadius: 6, color: '#45c986' },
      data: overview.value?.areaRanking.map((a) => a.recycleWeight) ?? [],
    },
  ],
}))

async function load() {
  overview.value = await getDashboardOverview()
}

// 台账/月报导出（EasyExcel 文件流，需手动带 Authorization 头走 blob 下载）
const exporting = ref(false)
async function exportExcel() {
  exporting.value = true
  try {
    const resp = await fetch(DASHBOARD_EXPORT_URL, {
      headers: {
        Authorization: `Bearer ${tokenStore.getAccessToken() ?? ''}`,
        'X-Client-Type': 'ADMIN',
      },
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `dashboard-${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  load()
  // 大屏轮询（11.2：后端 1s 快照缓存，接口 silent+allowDuplicate）
  timer = window.setInterval(load, 1000)
})

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="screen">
    <div class="screen__head">
      <h2 class="screen__title">点废成金 · 城市再生资源数据大屏</h2>
      <div class="screen__actions">
        <span v-if="overview" class="screen__time">快照 {{ overview.generatedAt }}</span>
        <el-button size="small" type="primary" plain :loading="exporting" @click="exportExcel">导出台账/月报</el-button>
      </div>
    </div>
    <div class="screen__kpis">
      <div v-for="kpi in kpis" :key="kpi.label" class="screen__kpi">
        <div class="screen__kpi-value">{{ kpi.value }}</div>
        <div class="screen__kpi-label">{{ kpi.label }}</div>
      </div>
    </div>
    <div class="screen__grid">
      <div class="screen__panel screen__panel--wide">
        <h4>近 12 月回收/减碳趋势</h4>
        <EChart :option="trendOption" height="280px" />
      </div>
      <div class="screen__panel">
        <h4>片区垃圾投放占比</h4>
        <EChart :option="kitchenOption" height="280px" />
      </div>
      <div class="screen__panel screen__panel--full">
        <h4>片区回收量排行</h4>
        <EChart :option="rankOption" height="240px" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.screen {
  min-height: calc(100vh - 132px);
  margin: -#{$df-space-lg};
  padding: $df-space-xl;
  background: var(--df-bg-dark);
  color: #e6eef7;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    font-size: $df-font-size-xl;
    letter-spacing: 4px;
    background: linear-gradient(90deg, #21a666, #45c986);
    background-clip: text;
    -webkit-background-clip: text;
    color: transparent;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: $df-space-md;
  }

  &__time {
    font-size: $df-font-size-sm;
    color: #a3b1c2;
  }

  &__kpis {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: $df-space-lg;
    margin: $df-space-xl 0;
  }

  &__kpi {
    padding: $df-space-lg;
    text-align: center;
    border: 1px solid #1c3550;
    border-radius: $df-radius-md;
    background: rgba(28, 53, 80, 0.35);

    &-value {
      font-size: $df-font-size-title;
      font-weight: 700;
      color: #45c986;
    }

    &-label {
      margin-top: $df-space-xs;
      font-size: $df-font-size-sm;
      color: #a3b1c2;
    }
  }

  &__grid {
    display: grid;
    grid-template-columns: 2fr 1fr;
    gap: $df-space-lg;
  }

  &__panel {
    padding: $df-space-lg;
    border: 1px solid #1c3550;
    border-radius: $df-radius-md;
    background: rgba(28, 53, 80, 0.35);

    h4 {
      margin-bottom: $df-space-md;
      color: #e6eef7;
      font-weight: 500;
    }

    &--full {
      grid-column: 1 / -1;
    }
  }
}
</style>
