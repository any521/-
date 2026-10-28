<script setup lang="ts">
// 我的绩效：月度单量/重量/金额/碳减排 + 片区排名 + 近 7 日单量柱图
// 柱图为轻量 SVG（员工端不引 ECharts 控体积），联调期数据源切 B 线统计接口
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatCarbon, formatMoney, formatWeight } from '@shared/utils/format'
import { getPerformance, type Performance } from '@/api/business'

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
  const barW = 22
  const gap = (300 - days.length * barW) / (days.length + 1)
  return days.map((d, i) => {
    const h = Math.round((d.orders / max) * 90)
    return {
      ...d,
      x: Math.round(gap + i * (barW + gap)),
      y: 110 - h,
      w: barW,
      h,
    }
  })
})

async function load() {
  perf.value = await getPerformance(month.value)
}

watch(month, load)
onMounted(load)
</script>

<template>
  <div class="page perf">
    <van-nav-bar title="我的绩效" left-arrow @click-left="router.back()" />

    <template v-if="perf">
      <!-- 月份切换 -->
      <div class="card perf__month">
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
      <div class="card perf__grid">
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
      <div class="card perf__rank">
        <div class="perf__rank-item">
          <van-icon name="medal-o" color="#d9a534" size="22" />
          片区排名 <b>第 {{ perf.rank }} 名</b>
        </div>
        <div class="perf__rank-item">
          <van-icon name="like-o" color="#f04d4d" size="22" />
          好评率 <b>{{ perf.praiseRate ? perf.praiseRate + '%' : '—' }}</b>
        </div>
      </div>

      <!-- 近 7 日单量 -->
      <div class="card">
        <div class="card__title">近 7 日单量</div>
        <svg viewBox="0 0 300 130" class="perf__chart">
          <g v-for="b in bars" :key="b.date">
            <rect :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="4" fill="#2f6b5e" opacity="0.85" />
            <text :x="b.x + b.w / 2" :y="b.y - 4" text-anchor="middle" font-size="10" fill="#465264">
              {{ b.orders }}
            </text>
            <text :x="b.x + b.w / 2" y="124" text-anchor="middle" font-size="9" fill="#8a94a6">
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

.perf {
  &__month {
    display: flex;
    gap: $df-space-sm;
    overflow-x: auto;
    padding-top: $df-space-md;
    padding-bottom: $df-space-md;
  }

  &__month-item {
    flex-shrink: 0;
    padding: 4px 14px;
    border-radius: $df-radius-round;
    background: var(--df-bg-page);
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;

    &.is-active {
      background: var(--df-color-primary);
      color: #fff;
      font-weight: 600;
    }
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: $df-space-lg;
    text-align: center;
  }

  &__num {
    font-size: $df-font-size-lg;
    font-weight: 700;

    &--carbon { color: var(--df-color-carbon); }
  }

  &__label {
    margin-top: 2px;
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
    font-size: $df-font-size-sm;

    b { font-size: $df-font-size-md; }
  }

  &__chart {
    width: 100%;
  }
}
</style>
