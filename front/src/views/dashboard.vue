<script setup lang="ts">
// 工作台（D1 静态路由页）：核心指标卡 + 近 7 日趋势
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { useUserStore } from '@/store/user'
import { getDashboardSummary, type DashboardSummary } from '@/api/business'
import { formatWeight, formatCarbon } from '@shared/utils/format'

const userStore = useUserStore()
const summary = ref<DashboardSummary | null>(null)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

// 报刊日期行
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})

const cards = computed(() => [
  { label: '今日回收订单', value: summary.value?.todayOrders ?? '—', icon: 'Box', color: '#2f6b5e' },
  { label: '今日回收量', value: summary.value ? formatWeight(summary.value.todayWeight) : '—', icon: 'TrendCharts', color: '#3d8b85' },
  { label: '今日减碳量', value: summary.value ? formatCarbon(summary.value.todayCarbon) : '—', icon: 'Sunny', color: '#e8bc3f' },
  { label: '平台用户总数', value: summary.value?.totalUsers ?? '—', icon: 'User', color: '#f29d28' },
])

const trendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['订单数', '回收量(kg)', '减碳量(g)'] },
  grid: { left: 48, right: 24, top: 40, bottom: 28 },
  xAxis: { type: 'category', data: summary.value?.weekTrend.map((t) => t.date) ?? [] },
  yAxis: { type: 'value' },
  series: [
    { name: '订单数', type: 'line', smooth: true, data: summary.value?.weekTrend.map((t) => t.orders) ?? [] },
    { name: '回收量(kg)', type: 'line', smooth: true, data: summary.value?.weekTrend.map((t) => t.weight) ?? [] },
    { name: '减碳量(g)', type: 'bar', data: summary.value?.weekTrend.map((t) => t.carbon) ?? [] },
  ],
  color: ['#2f6b5e', '#3d8b85', '#e8bc3f'],
}))

onMounted(async () => {
  summary.value = await getDashboardSummary()
})
</script>

<template>
  <div class="app-page app-page--flow">
    <div class="app-card dashboard__welcome">
      <div class="dashboard__welcome-top">
        <h2 class="font-serif">{{ greeting }}，{{ userStore.nickname }}</h2>
        <span class="dashboard__date">{{ today }}</span>
      </div>
      <p>欢迎使用点废成金管理驾驶舱，今日平台运行正常。</p>
    </div>
    <el-row :gutter="16" class="dashboard__cards">
      <el-col v-for="card in cards" :key="card.label" :span="6">
        <div class="app-card dashboard__card">
          <el-icon :size="36" :color="card.color"><component :is="card.icon" /></el-icon>
          <div>
            <div class="dashboard__card-value">{{ card.value }}</div>
            <div class="dashboard__card-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>
    <div class="app-card">
      <h3 class="dashboard__chart-title">近 7 日运营趋势</h3>
      <EChart :option="trendOption" height="340px" />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.dashboard__welcome {
  h2 {
    margin: 0;
    font-size: $df-font-size-lg;
    color: var(--df-color-title);
  }

  &-top {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: $df-space-md;
  }

  .dashboard__date {
    font-family: $df-font-serif;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
    letter-spacing: 0.04em;
  }

  p {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }
}

.dashboard__cards {
  margin-top: $df-space-lg;
  margin-bottom: $df-space-lg;
}

.dashboard__card {
  display: flex;
  align-items: center;
  gap: $df-space-lg;

  &-value {
    font-size: $df-font-size-xl;
    font-weight: 600;
  }

  &-label {
    margin-top: $df-space-xs;
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
  }
}

.dashboard__chart-title {
  margin-bottom: $df-space-md;
  font-size: $df-font-size-md;
  font-family: $df-font-serif;
  color: var(--df-color-title);
  letter-spacing: 0.03em;
}
</style>
