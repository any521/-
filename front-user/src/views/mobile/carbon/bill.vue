<script setup lang="ts">
// 低碳账单：碳账户概览 + 月度账单曲线 + 积分/碳流水（F3 锚定标记）
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  getCarbonAccount, getCarbonBill, pageCarbonLogs,
  type CarbonAccount, type CarbonBill, type CarbonLog,
} from '@/api/business'
import { formatCarbon } from '@shared/utils/format'

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
  try {
    bill.value = await getCarbonBill(month.value)
  } catch {
    // 账单加载失败：拦截器已 Toast，此处避免未捕获异常
  }
}

/** 简易柱状图：按日碳减排相对最大值的百分比 */
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
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    logsLoading.value = false
  }
}

onMounted(async () => {
  try {
    const [a, b] = await Promise.all([getCarbonAccount(), getCarbonBill(month.value)])
    account.value = a
    bill.value = b
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page bill">
    <van-nav-bar title="低碳账单" left-arrow @click-left="router.back()" />

    <van-skeleton :loading="loading" title :row="5">
      <!-- 碳账户概览 -->
      <div class="card bill__hero">
        <div class="bill__hero-main">
          <div class="bill__hero-num">{{ formatCarbon(account?.totalCarbon ?? 0) }}</div>
          <div class="bill__hero-label">累计碳减排</div>
        </div>
        <div class="bill__hero-side">
          <div>本月 {{ formatCarbon(account?.monthCarbon ?? 0) }}</div>
          <div>积分 {{ account?.points ?? 0 }}</div>
          <div>超过 {{ account?.rankPercent ?? 100 }}% 的用户</div>
        </div>
      </div>

      <!-- 月度曲线 -->
      <div v-if="bill" class="card">
        <div class="bill__title-row">
          <div class="card__title">{{ bill.month }} 月度减排</div>
          <div class="bill__month-chips">
            <van-tag
              v-for="m in monthOptions"
              :key="m.value"
              size="large"
              :type="month === m.value ? 'primary' : 'default'"
              @click="switchMonth(m.value)"
            >
              {{ m.label }}
            </van-tag>
          </div>
        </div>
        <div class="bill__chart">
          <div v-for="d in bars" :key="d.date" class="bill__bar-wrap" :title="`${d.date} ${d.carbon}kg`">
            <div class="bill__bar" :style="{ height: `${d.pct}%` }" />
          </div>
        </div>
        <div class="bill__summary">
          <span>共 {{ formatCarbon(bill.totalCarbon) }}</span>
          <span>+{{ bill.totalPoints }} 积分</span>
          <span>≈ 植树 {{ bill.treeEquivalent }} 棵 <SvgIcon name="carbon-tree" /></span>
        </div>
      </div>
    </van-skeleton>

    <!-- 流水（F3：哈希链锚定标记） -->
    <div class="card">
      <div class="card__title">积分 · 碳流水</div>
      <van-list v-model:loading="logsLoading" :finished="logsFinished" finished-text="没有更多了" @load="loadMore">
        <div v-for="l in logs" :key="l.id" class="bill__log">
          <div class="bill__log-main">
            <div class="bill__log-title">
              {{ l.bizType }}
              <van-tag v-if="l.anchored" type="primary" plain size="medium"><SvgIcon name="nav-link" /> 已锚定</van-tag>
              <van-tag v-if="l.anchored" type="success" plain size="medium" @click="router.push(`/carbon/certificate?logId=${l.id}`)">
                <SvgIcon name="nav-check-mark" /> 可信证书
              </van-tag>
            </div>
            <div class="bill__log-desc">{{ l.desc }} · {{ l.createTime }}</div>
          </div>
          <div class="bill__log-right">
            <div :class="l.points >= 0 ? 'bill__log-points--in' : 'bill__log-points--out'">
              {{ l.points >= 0 ? '+' : '' }}{{ l.points }}
            </div>
            <div class="bill__log-carbon">{{ formatCarbon(l.carbon) }}</div>
          </div>
        </div>
      </van-list>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.bill {
  padding-top: 0;

  &__hero {
    display: flex;
    align-items: center;
    gap: $df-space-xl;
    background: var(--df-gradient-carbon);
    color: #fff;
  }

  &__hero-num {
    font-size: $df-font-size-xl;
    font-weight: 700;
  }

  &__hero-label {
    font-size: $df-font-size-xs;
    opacity: 0.85;
  }

  &__hero-side {
    font-size: $df-font-size-sm;
    line-height: 1.9;
    opacity: 0.92;
  }

  &__chart {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    height: 90px;
  }

  &__title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $df-space-md;
  }

  &__month-chips {
    display: flex;
    gap: $df-space-xs;
  }

  &__bar-wrap {
    flex: 1;
    height: 100%;
    display: flex;
    align-items: flex-end;
  }

  &__bar {
    width: 100%;
    min-height: 2px;
    border-radius: 2px 2px 0 0;
    background: var(--df-color-primary);
    opacity: 0.85;
  }

  &__summary {
    display: flex;
    justify-content: space-between;
    margin-top: $df-space-md;
    color: var(--df-text-regular);
    font-size: $df-font-size-sm;
  }

  &__log {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $df-space-md 0;
    border-bottom: 1px solid var(--df-border-color);

    &:last-child {
      border-bottom: none;
    }
  }

  &__log-title {
    font-size: $df-font-size-base;
    font-weight: 500;
  }

  &__log-desc {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__log-right {
    text-align: right;
  }

  &__log-points--in {
    color: var(--df-color-gold);
    font-weight: 600;
  }

  &__log-points--out {
    color: var(--df-text-regular);
    font-weight: 600;
  }

  &__log-carbon {
    color: var(--df-color-carbon);
    font-size: $df-font-size-xs;
  }
}
</style>
