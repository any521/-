<script setup lang="ts">
// PC 低碳账单
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  getCarbonAccount, getCarbonBill, pageCarbonLogs,
  type CarbonAccount, type CarbonBill, type CarbonLog,
} from '@/api/business'
import { formatCarbon } from '@shared/utils/format'

defineOptions({ name: 'pc-carbon-bill' })

const router = useRouter()
const account = ref<CarbonAccount | null>(null)
const bill = ref<CarbonBill | null>(null)
const logs = ref<CarbonLog[]>([])
const loading = ref(true)
const logsLoading = ref(false)
const logsFinished = ref(false)
let pageNum = 1

/** 当前账单月份 yyyy-MM（初始当月） */
const month = ref(new Date().toISOString().slice(0, 7))

/** 月份切换 chips：当月 + 前 2 个月 */
const monthOptions = Array.from({ length: 3 }, (_, i) => {
  const d = new Date()
  d.setMonth(d.getMonth() - i)
  const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  return { value, label: `${d.getMonth() + 1} 月` }
})

async function switchMonth(m: string) {
  if (m === month.value) return
  month.value = m
  bill.value = await getCarbonBill(month.value)
}

const bars = computed(() => {
  const days = bill.value?.days ?? []
  const max = Math.max(...days.map((d) => d.carbon), 0.01)
  return days.map((d) => ({ ...d, pct: Math.round((d.carbon / max) * 100) }))
})

async function loadMore() {
  logsLoading.value = true
  try {
    const page = await pageCarbonLogs({ pageNum, pageSize: 10 })
    logs.value.push(...page.list)
    logsFinished.value = logs.value.length >= page.total
    pageNum += 1
  } finally {
    logsLoading.value = false
  }
}

onMounted(async () => {
  try {
    const [a, b] = await Promise.all([getCarbonAccount(), getCarbonBill(month.value)])
    account.value = a
    bill.value = b
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="pc-bill">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">低碳账单</h1>
      </div>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else>
        <!-- 碳账户概览 -->
        <div class="hero-card">
          <div class="hero-main">
            <div class="hero-num">{{ formatCarbon(account?.totalCarbon ?? 0) }}</div>
            <div class="hero-label">累计碳减排 (kgCO₂)</div>
          </div>
          <div class="hero-side">
            <div>本月 {{ formatCarbon(account?.monthCarbon ?? 0) }}</div>
            <div>积分 {{ account?.points ?? 0 }}</div>
            <div>超过 {{ account?.rankPercent ?? 100 }}% 的用户</div>
          </div>
        </div>

        <!-- 月度曲线 -->
        <div v-if="bill" class="section-card">
          <div class="section-head">
            <h3 class="section-title">{{ bill.month }} 月度减排</h3>
            <div class="month-chips">
              <button
                v-for="m in monthOptions"
                :key="m.value"
                class="month-chip"
                :class="{ 'month-chip--active': month === m.value }"
                @click="switchMonth(m.value)"
              >
                {{ m.label }}
              </button>
            </div>
          </div>
          <div class="chart">
            <div v-for="d in bars" :key="d.date" class="bar-wrap" :title="`${d.date} ${d.carbon}kg`">
              <div class="bar" :style="{ height: `${d.pct}%` }" />
            </div>
          </div>
          <div class="summary">
            <span>共 {{ formatCarbon(bill.totalCarbon) }}</span>
            <span>+{{ bill.totalPoints }} 积分</span>
            <span>≈ 植树 {{ bill.treeEquivalent }} 棵</span>
          </div>
        </div>

        <!-- 流水 -->
        <div class="section-card">
          <h3 class="section-title">积分 · 碳流水</h3>
          <div v-if="logsFinished && logs.length === 0" class="empty">暂无记录</div>
          <div v-for="l in logs" :key="l.id" class="log-item">
            <div class="log-main">
              <div class="log-title">
                {{ l.bizType }}
                <span v-if="l.anchored" class="log-anchor">已锚定</span>
                <span v-if="l.anchored" class="log-cert" @click="router.push(`/pc/carbon/certificate?logId=${l.id}`)">可信证书 ›</span>
              </div>
              <div class="log-desc">{{ l.desc }} · {{ l.createTime }}</div>
            </div>
            <div class="log-right">
              <div :class="l.points >= 0 ? 'log-points--in' : 'log-points--out'">
                {{ l.points >= 0 ? '+' : '' }}{{ l.points }}
              </div>
              <div class="log-carbon">{{ formatCarbon(l.carbon) }}</div>
            </div>
          </div>
          <button v-if="!logsFinished && logs.length > 0" class="load-more" :disabled="logsLoading" @click="loadMore">
            {{ logsLoading ? '加载中…' : '加载更多' }}
          </button>
          <p v-else-if="logsFinished && logs.length > 0" class="no-more">没有更多了</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-bill {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container { max-width: 800px; margin: 0 auto; }

.header {
  display: flex; align-items: center; gap: 16px; margin-bottom: 48px;
}

.back-btn {
  display: flex; align-items: center; gap: 6px; padding: 8px 16px;
  font-size: 14px; color: var(--df-text-regular); background: var(--df-bg-card);
  border: 2px solid var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
}

.page-title { font-size: 32px; font-weight: 700; color: var(--df-text-primary); margin: 0; }

.hero-card {
  display: flex; align-items: center; gap: 32px;
  background: var(--df-gradient-carbon);
  border-radius: 16px; padding: 36px; margin-bottom: 24px; color: #fff;
}

.hero-num { font-size: 36px; font-weight: 700; }
.hero-label { margin-top: 4px; font-size: 14px; opacity: 0.85; }
.hero-side { font-size: 15px; line-height: 2; opacity: 0.92; }

.section-card {
  background: var(--df-bg-card); border-radius: 16px; padding: 28px; margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.section-title { font-size: 18px; font-weight: 600; color: var(--df-text-primary); margin: 0; }

.section-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px; margin-bottom: 20px;
}

.month-chips { display: flex; gap: 8px; }

.month-chip {
  padding: 6px 14px; font-size: 14px; color: var(--df-text-regular);
  background: var(--df-bg-card); border: 2px solid var(--df-border-color);
  border-radius: 20px; cursor: pointer; transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
  &--active {
    border-color: var(--df-color-primary); color: #fff; background: var(--df-color-primary);
  }
}

.chart { display: flex; align-items: flex-end; gap: 2px; height: 100px; }
.bar-wrap { flex: 1; height: 100%; display: flex; align-items: flex-end; }
.bar {
  width: 100%; min-height: 2px; border-radius: 2px 2px 0 0;
  background: var(--df-color-primary); opacity: 0.85;
}

.summary {
  display: flex; justify-content: space-between; margin-top: 16px;
  color: var(--df-text-regular); font-size: 14px;
}

.log-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 0; border-bottom: 1px solid var(--df-border-color);
  &:last-child { border-bottom: none; }
}

.log-title { font-size: 15px; font-weight: 500; color: var(--df-text-primary); }
.log-anchor { margin-left: 8px; font-size: 12px; color: var(--df-color-primary); font-weight: 400; }
.log-cert {
  margin-left: 12px; font-size: 12px; color: var(--df-color-primary); font-weight: 500;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}
.log-desc { margin-top: 4px; color: var(--df-text-secondary); font-size: 13px; }
.log-right { text-align: right; }
.log-points--in { color: var(--df-color-gold); font-weight: 600; }
.log-points--out { color: var(--df-text-regular); font-weight: 600; }
.log-carbon { color: var(--df-color-carbon); font-size: 13px; }

.loading, .empty { text-align: center; padding: 60px 0; color: var(--df-text-secondary); font-size: 16px; }

.load-more {
  width: 100%; padding: 12px; margin-top: 16px; font-size: 15px; font-weight: 500;
  color: var(--df-color-primary); background: transparent;
  border: 2px solid var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover:not(:disabled) { border-color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.no-more { text-align: center; color: var(--df-text-secondary); font-size: 14px; margin: 16px 0 0; }
</style>