<script setup lang="ts">
// PC 端我的绩效：月度单量/重量/金额/碳减排 + 片区排名 + 近 7 日单量柱图
// 柱图为轻量 SVG（员工端不引 ECharts 控体积），联调期数据源切 B 线统计接口
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatCarbon, formatMoney, formatWeight } from '@shared/utils/format'
import { getPerformance, type Performance } from '@/api/business'

defineOptions({ name: 'pc-performance' })

const router = useRouter()

const perf = ref<Performance | null>(null)
/** 月份选择（近 6 个月） */
const month = ref(new Date().toISOString().slice(0, 7))
const monthOptions = Array.from({ length: 6 }, (_, i) => {
  const d = new Date()
  d.setMonth(d.getMonth() - i)
  return d.toISOString().slice(0, 7)
})

/** 近 7 日柱图（SVG）：按最大值归一化 */
const bars = computed(() => {
  const days = perf.value?.days ?? []
  const max = Math.max(...days.map((d) => d.orders), 1)
  const barW = 46
  const width = Math.max(days.length * (barW + 32), 560)
  const gap = (width - days.length * barW) / (days.length + 1)
  return days.map((d, i) => {
    const h = Math.round((d.orders / max) * 140)
    return {
      ...d,
      x: Math.round(gap + i * (barW + gap)),
      y: 170 - h,
      w: barW,
      h,
    }
  })
})

const chartWidth = computed(() => Math.max((perf.value?.days.length ?? 7) * 78, 560))

async function load() {
  perf.value = await getPerformance(month.value)
}

watch(month, load)
onMounted(load)
</script>

<template>
  <div class="pc-page perf">
    <div class="pc-page__header">
      <button class="pc-back" @click="router.back()">← 返回</button>
      <h1 class="pc-page__title">我的绩效</h1>
      <p class="pc-page__sub">月度单量 · 重量 · 金额 · 碳减排</p>
    </div>

    <template v-if="perf">
      <!-- 月份切换 -->
      <div class="pc-card perf__month">
        <span
          v-for="m in monthOptions"
          :key="m"
          class="perf__month-item"
          :class="{ 'is-active': m === month }"
          @click="month = m"
        >
          {{ m.slice(5) }}月
        </span>
      </div>

      <!-- 核心指标 -->
      <div class="pc-card perf__grid">
        <div class="perf__cell">
          <div class="perf__num">{{ perf.orders }}</div>
          <div class="perf__label">完成单数</div>
        </div>
        <div class="perf__cell">
          <div class="perf__num">{{ formatWeight(perf.weight) }}</div>
          <div class="perf__label">核验总重</div>
        </div>
        <div class="perf__cell">
          <div class="perf__num">{{ formatMoney(perf.amount) }}</div>
          <div class="perf__label">经手金额</div>
        </div>
        <div class="perf__cell">
          <div class="perf__num perf__num--carbon">{{ formatCarbon(perf.carbon) }}</div>
          <div class="perf__label">带动碳减排</div>
        </div>
      </div>

      <!-- 排名 / 好评 -->
      <div class="pc-card perf__rank">
        <div class="perf__rank-item">
          <van-icon name="medal-o" color="#d9a534" size="26" />
          片区排名 <b>第 {{ perf.rank }} 名</b>
        </div>
        <div class="perf__rank-item">
          <van-icon name="like-o" color="#f04d4d" size="26" />
          好评率 <b>{{ perf.praiseRate ? perf.praiseRate + '%' : '—' }}</b>
        </div>
      </div>

      <!-- 近 7 日单量 -->
      <div class="pc-card">
        <div class="perf__chart-title">近 7 日单量</div>
        <svg :viewBox="`0 0 ${chartWidth} 200`" class="perf__chart">
          <g v-for="b in bars" :key="b.date">
            <rect :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="6" fill="#2f6b5e" opacity="0.85" />
            <text :x="b.x + b.w / 2" :y="b.y - 6" text-anchor="middle" font-size="13" fill="#465264">
              {{ b.orders }}
            </text>
            <text :x="b.x + b.w / 2" y="192" text-anchor="middle" font-size="12" fill="#8a94a6">
              {{ b.date }}
            </text>
          </g>
        </svg>
      </div>
    </template>
    <van-skeleton v-else title :row="8" />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.pc-back {
  border: none;
  background: transparent;
  color: var(--df-text-secondary);
  font-size: $df-font-size-base;
  cursor: pointer;
  padding: 4px 0;

  &:hover {
    color: var(--df-color-primary);
  }
}

.perf {
  &__month {
    display: flex;
    gap: $df-space-sm;
    padding-top: $df-space-md;
    padding-bottom: $df-space-md;
  }

  &__month-item {
    padding: 6px 20px;
    border-radius: $df-radius-round;
    background: transparent;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    cursor: pointer;
    transition: all 0.25s ease;

    &:hover {
      color: var(--df-color-primary);
    }

    &.is-active {
      background: var(--df-color-primary);
      color: #fff;
      font-weight: 600;

      &:hover {
        color: #fff;
      }
    }
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: $df-space-lg;
    text-align: center;
  }

  &__cell {
    padding: $df-space-md;
    background: var(--df-fill-color-light);
    border-radius: $df-radius-md;
  }

  &__num {
    font-size: $df-font-size-xl;
    font-weight: 700;

    &--carbon { color: var(--df-color-carbon); }
  }

  &__label {
    margin-top: 4px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__rank {
    display: flex;
    justify-content: space-around;
  }

  &__rank-item {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
    font-size: $df-font-size-base;

    b { font-size: $df-font-size-lg; }
  }

  &__chart-title {
    font-size: $df-font-size-md;
    font-weight: 600;
    margin-bottom: $df-space-md;
  }

  &__chart {
    width: 100%;
  }
}
</style>
