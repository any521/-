<script setup lang="ts">
// 首页：报刊编辑部版面 —— 报头 / 今日头条（碳积分）/ 分类索引 / 今日报价 / 铅笔批注
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { getHomeSummary, getPrices, getPriceMeta, type HomeSummary, type CategoryPrice, type PriceMeta } from '@/api/business'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import { formatCarbon } from '@shared/utils/format'
import { useUserStore } from '@/store/user'
import HeroMascot from '@/components/HeroMascot.vue'
import { vReveal } from '@/composables/useReveal'

defineOptions({ name: 'home' })

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const refreshing = ref(false)
const summary = ref<HomeSummary | null>(null)
const prices = ref<CategoryPrice[]>([])
const priceMeta = ref<PriceMeta>({})

/** 报价展示列表（与 PC 端首页一致）：全 8 品类内置兜底，接口命中用真实价，未命中标注「内置」 */
const displayList = computed(() =>
  RECYCLE_CATEGORIES.map((cat) => {
    const hit = prices.value.find((p) => p.code === cat.code || p.name === cat.name)
    return {
      code: cat.code,
      name: cat.name,
      icon: cat.icon,
      price: hit && hit.price > 0 ? hit.price : cat.price,
      builtin: !hit || !(hit.price > 0),
      trend: hit?.trend ?? 0,
      updateTime: hit?.updateTime ?? priceMeta.value.lastSyncTime,
      city: hit?.city || priceMeta.value.city || '当地',
    }
  }),
)

// 报头日期（报刊发行样式）
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})

const entries = [
  { icon: 'recycle-recycle-symbol', label: '可回收预约', to: '/recycle/book' },
  { icon: 'kitchen-leafy-green', label: '垃圾投放', to: '/kitchen/drop' },
  { icon: 'repair-wrench', label: '维修预约', to: '/repair/book' },
  { icon: 'carbon-sprout', label: '低碳账单', to: '/carbon/bill' },
  { icon: 'ai-camera', label: 'AR 分类', to: '/ar-scan' },
  { icon: 'ai-microphone', label: '语音管家', to: '/voice-steward' },
  { icon: 'nav-money-bag', label: '实时报价', to: '/prices' },
  { icon: 'nav-books', label: '科普专栏', to: '/science' },
  // onsite04：回收站点地图（G4）
  { icon: 'nav-map', label: '回收站点', to: '/map/stations' },
]

async function load() {
  try {
    const [s, p, m] = await Promise.all([getHomeSummary(), getPrices(), getPriceMeta()])
    summary.value = s
    prices.value = p
    priceMeta.value = m ?? {}
  } catch {
    // API 失败时保持初始值，不阻塞页面渲染
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await load()
}

onMounted(load)

// KeepAlive 恢复自愈：首次挂载失败（静默 catch）后空数据会被缓存，切回时自动重试
onActivated(() => {
  if (!loading.value && prices.value.length === 0 && !summary.value) load()
})
</script>

<template>
  <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
    <div class="page home">
      <!-- 报头：刊名 + 发行日期 -->
      <header class="masthead home__masthead reveal" v-reveal>
        <div class="masthead__top">
          <h1 class="masthead__name">点废成金 · 生活报</h1>
          <p class="masthead__slogan font-hand">让每一次分类都被看见</p>
        </div>
        <div class="masthead__meta">
          <span class="masthead__date">{{ today }}</span>
          <span>碳积分可信记账</span>
        </div>
      </header>

      <!-- 今日 hero：森林小人插画(右侧背景) + 左侧问候/今日一句
           对齐模板 .hero-banner：左侧不透明表面色可读，右侧渐隐露出插画 -->
      <div class="home__headline reveal" v-reveal>
        <HeroMascot
          title="下午好"
          subtitle="欢迎回到森林日报"
          :height="150"
        >
          <p class="hero-quote-line font-hand">“保持节奏，像林间溪水稳稳流淌。” —— 今日箴言</p>
        </HeroMascot>
      </div>

      <!-- 快捷索引：分类广告栏版式（单元格带内边框） -->
      <div class="paper-card home__entries">
        <div class="paper-card__head">
          <h4>快捷索引</h4>
          <span class="paper-card__more">点开即达</span>
        </div>
        <div class="home__entries-grid">
          <div
            v-for="e in entries"
            :key="e.to"
            class="home__entry"
            @click="router.push(e.to)"
          >
            <div class="home__entry-icon-wrap">
              <SvgIcon class="home__entry-icon" :name="e.icon" />
            </div>
            <span class="home__entry-text">{{ e.label }}</span>
          </div>
        </div>
      </div>

      <!-- 今日报价（与 PC 端首页一致：全 8 品类内置兜底 + 真实价覆盖 + 城市与更新时间） -->
      <div class="paper-card home__prices">
        <div class="paper-card__head">
          <h4>
            <SvgIcon name="nav-money-bag" :size="16" /> 今日报价
          </h4>
          <span class="paper-card__more" @click="router.push('/prices')">全部 ›</span>
        </div>
        <p v-if="!loading && displayList.length" class="home__prices-meta">
          {{ priceMeta.city || '当地' }} · 更新于 {{ priceMeta.lastSyncTime || displayList[0]?.updateTime || '—' }}（每天 08:05 自动同步）
        </p>
        <van-skeleton :loading="loading" :row="6">
          <div class="home__price-list">
            <div
              v-for="p in displayList"
              :key="p.code"
              class="home__price-item"
              @click="router.push('/prices')"
            >
              <div class="home__price-left">
                <SvgIcon class="home__price-icon" :name="p.icon" />
                <span class="home__price-name">
                  {{ p.name }}
                  <span v-if="p.builtin" class="home__price-tag">内置</span>
                </span>
              </div>
              <div class="home__price-right">
                <span class="home__price-value">¥{{ p.price.toFixed(2) }}<span class="home__price-unit">/kg</span></span>
                <span
                  v-if="!p.builtin"
                  class="home__price-trend"
                  :class="p.trend > 0 ? 'up' : (p.trend < 0 ? 'down' : 'flat')"
                >
                  <template v-if="p.trend > 0">↑ {{ Math.abs(p.trend) }}%</template>
                  <template v-else-if="p.trend < 0">↓ {{ Math.abs(p.trend) }}%</template>
                  <template v-else>—</template>
                </span>
                <span v-else class="home__price-trend builtin">内置参考价</span>
              </div>
              <p v-if="!p.builtin" class="home__price-meta">
                {{ p.city }} · {{ p.updateTime || '—' }} 更新
              </p>
            </div>
          </div>
        </van-skeleton>
      </div>

      <!-- 适老化提示（铅笔批注） -->
      <div v-if="!userStore.elderMode" class="home__elder-tip" @click="userStore.toggleElderMode()">
        <SvgIcon name="nav-elder" />
        <span class="font-hand">家里有长辈？点这里开启大字体适老模式</span>
      </div>
    </div>
  </van-pull-refresh>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.home {
  &__masthead {
    margin-top: $df-space-xs;
  }

  &__headline {
    margin-bottom: $df-space-md;
  }

  // 今日一句：hero 左侧内联手写批注
  .hero-quote-line {
    font-size: 15px;
    color: var(--df-text-secondary);
    margin: 0;
    line-height: 1.6;
  }

  // 分类索引：报纸分类广告栏（单元格内边框）
  &__entries {
    padding: 0;
    overflow: hidden;

    .paper-card__head {
      padding: $df-space-md $df-space-lg;
      margin: 0;
    }
  }

  &__entries-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    border-top: 1px solid var(--df-border-color);
  }

  &__entry {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: $df-space-md 4px;
    border-right: 1px solid var(--df-border-color);
    border-bottom: 1px solid var(--df-border-color);
    cursor: pointer;
    transition: background 0.2s ease;
    -webkit-tap-highlight-color: transparent;

    // SVG 图标 / 文本不拦截点击，确保 @click 冒泡到 .home__entry
    :deep(svg),
    > * {
      pointer-events: none;
    }

    &:nth-child(4n) {
      border-right: none;
    }

    &:active {
      background: var(--df-color-primary-light);
    }
  }

  &__entry-icon-wrap {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: $df-radius-round;
    font-size: 22px;
    color: var(--df-color-primary);
    transition: background 0.2s ease, color 0.2s ease;
    // SVG 图标不拦截点击，确保 @click 冒泡到 .home__entry
    pointer-events: none;

    .home__entry:active & {
      background: var(--df-color-primary);
      color: #fff;
    }
  }

  &__entry-text {
    font-size: $df-font-size-xs;
    color: var(--df-text-regular);
    pointer-events: none;
  }

  &__prices {
    margin-bottom: $df-space-md;

    .paper-card__head h4 {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
  }

  // 报价元信息（城市 + 更新时间，与 PC 端首页一致）
  &__prices-meta {
    margin: 0 $df-space-lg $df-space-sm;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  // 「全部 ›」可点击：保留 paper-card__more 视觉，补 cursor
  .paper-card__more {
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }

  &__price-list {
    display: flex;
    flex-direction: column;
  }

  &__price-item {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    padding: $df-space-sm $df-space-lg;
    border-bottom: 1px dotted var(--df-border-color);
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    // SVG 图标 / 文本不拦截点击，确保 @click 冒泡到 .home__price-item
    :deep(svg),
    > * {
      pointer-events: none;
    }

    &:last-child {
      border-bottom: none;
    }

    &:active {
      background: var(--df-color-primary-light);
    }
  }

  &__price-left {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    pointer-events: none;
  }

  &__price-icon {
    font-size: 20px;
    color: var(--df-text-secondary);
    pointer-events: none;
  }

  &__price-name {
    font-size: $df-font-size-base;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  // 内置参考价标记（与 PC 端 price-card__tag 对齐）
  &__price-tag {
    display: inline-block;
    padding: 1px 8px;
    font-size: $df-font-size-xs;
    font-weight: 400;
    color: var(--df-text-secondary);
    background: var(--df-border-color);
    border-radius: 999px;
  }

  // 行内行情地区 + 更新时间（与 PC 端 price-card__meta 对齐，整行换行展示）
  &__price-meta {
    flex-basis: 100%;
    margin: 6px 0 0 28px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    pointer-events: none;
  }

  &__price-right {
    text-align: right;
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    pointer-events: none;
  }

  &__price-value {
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-color-primary-dark);
  }

  &__price-unit {
    font-size: $df-font-size-xs;
    font-weight: 400;
    color: var(--df-text-secondary);
    margin-left: 2px;
  }

  // 涨跌：报刊红/绿印戳
  &__price-trend {
    display: inline-block;
    font-size: $df-font-size-xs;
    font-weight: 600;
    padding: 1px 6px;
    border-radius: $df-radius-sm;
    min-width: 56px;
    text-align: center;

    &.up {
      color: var(--df-color-stamp);
      background: rgba(201, 79, 61, 0.1);
    }

    &.down {
      color: var(--df-color-primary);
      background: var(--df-color-primary-light);
    }

    &.flat {
      color: var(--df-text-secondary);
      background: var(--df-border-color);
    }

    &.builtin {
      color: var(--df-text-placeholder);
      background: transparent;
      font-weight: 400;
    }
  }

  // 铅笔批注：适老化提示
  &__elder-tip {
    margin-top: $df-space-md;
    padding: $df-space-sm $df-space-md;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    border: 1px dashed var(--df-color-secondary);
    border-radius: $df-radius-sm;
    background: rgba(242, 157, 40, 0.08);
    color: var(--df-color-secondary-dark);
    font-size: $df-font-size-sm;

    :deep(svg),
    > * {
      pointer-events: none;
      color: inherit;
    }
  }
}

.van-notice-bar {
  margin-bottom: $df-space-md;
  border-radius: $df-radius-lg;
}
</style>
