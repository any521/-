<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHomeSummary, getPrices, getPriceMeta, getHomeStats, type HomeSummary, type HomeStats, type CategoryPrice, type PriceMeta } from '@/api/business'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import { formatCarbon } from '@shared/utils/format'
import { useUserStore } from '@/store/user'
import HeroMascot from '@/components/HeroMascot.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const summary = ref<HomeSummary | null>(null)
const stats = ref<HomeStats | null>(null)
const prices = ref<CategoryPrice[]>([])
const priceMeta = ref<PriceMeta>({})

/** 报价展示列表（与实时报价页一致）：全品类内置兜底，接口命中用真实价，未命中标注「内置」 */
const displayList = computed(() =>
  RECYCLE_CATEGORIES.map((cat) => {
    const hit = prices.value.find((p) => p.code === cat.code || p.name === cat.name)
    return {
      code: cat.code,
      name: cat.name,
      icon: cat.icon,
      price: hit?.price ?? cat.price,
      builtin: !hit,
      trend: hit?.trend ?? 0,
      updateTime: hit?.updateTime ?? priceMeta.value.lastSyncTime,
      city: priceMeta.value.city || '北京',
    }
  }),
)

const features = [
  {
    icon: 'recycle-recycle-symbol',
    title: '可回收预约',
    desc: '一键下单，上门回收，智能称重，实时结算',
    to: '/pc/recycle/book',
    color: 'var(--df-color-primary)'
  },
  {
    icon: 'kitchen-leafy-green',
    title: '垃圾投放',
    desc: 'AI 视觉审核，智能分类，减少环境污染',
    to: '/pc/kitchen/drop',
    color: 'var(--df-color-primary-dark)'
  },
  {
    icon: 'repair-wrench',
    title: '维修服务',
    desc: '延长产品寿命，减少资源浪费，支持循环经济',
    to: '/pc/repair/book',
    color: 'var(--df-color-carbon)'
  },
  {
    icon: 'carbon-sprout',
    title: '低碳账单',
    desc: '记录每一次环保行为，积累碳减排成就',
    to: '/pc/carbon/bill',
    color: 'var(--df-color-carbon)'
  },
  {
    icon: 'ai-camera',
    title: 'AR 分类指导',
    desc: '端侧实时识别，零上传，精准分类指导',
    to: '/pc/ai/ar-scan',
    color: 'var(--df-color-gold)'
  },
  {
    icon: 'ai-microphone',
    title: '语音碳管家',
    desc: '语音交互，智能答疑，低碳生活小助手',
    to: '/pc/ai/voice-steward',
    color: 'var(--df-color-danger)'
  },
  {
    icon: 'nav-map',
    title: '回收站点',
    desc: '回收站 / 垃圾站 / 集散点，地图一键导航',
    to: '/pc/map/stations',
    color: 'var(--df-color-primary)'
  },
  {
    icon: 'nav-books',
    title: '科普专栏',
    desc: '环保知识科普，支持 UGC 投稿与下载',
    to: '/pc/science',
    color: 'var(--df-color-gold)'
  },
]

async function load() {
  try {
    const [s, st, p, m] = await Promise.all([getHomeSummary(), getHomeStats(), getPrices(), getPriceMeta()])
    summary.value = s
    stats.value = st
    prices.value = p
    priceMeta.value = m ?? {}
  } catch {
    // API 失败时保持初始值，不阻塞页面渲染
  } finally {
    loading.value = false
  }
}

/** 千分位格式化（中文数字） */
function formatNum(n: number | undefined | null): string {
  return n == null ? '—' : n.toLocaleString('zh-CN')
}

/** 碳减排中文格式化：≥1000kg 转吨 */
function formatCarbonZh(kg: number | undefined | null): string {
  if (kg == null) return '—'
  return kg >= 1000 ? `${(kg / 1000).toFixed(2)} 吨` : `${kg.toFixed(2)} 千克`
}

onMounted(load)
</script>

<template>
  <div class="pc-home">
    <!-- Hero 区域：森林小人插画(右侧背景) + 左侧标题/副标题/数据概览内联小列
         对齐模板 .hero-banner：数据不再用白卡片遮挡右侧插画
         注：不能用 class="hero"（与 workbench.scss 全局模块概览条 .hero 重名冲突，会被包上边框/缩成窄卡片） -->
    <section class="hero-section">
      <HeroMascot
        title="城市再生资源智能运营平台"
        subtitle="让废弃物变资源，让低碳生活触手可及"
        :height="208"
      >
        <!-- 左侧内联数据概览（模板 .hstats） -->
        <div class="hstats" v-if="!loading && summary">
          <div class="hstat">
            <div class="hstat__n">{{ summary.points }}</div>
            <div class="hstat__t">当前积分</div>
          </div>
          <div class="hstat">
            <div class="hstat__n">{{ formatCarbon(summary.totalCarbon) }}</div>
            <div class="hstat__t">累计减排</div>
          </div>
          <div class="hstat">
            <div class="hstat__n">{{ formatCarbon(summary.monthCarbon) }}</div>
            <div class="hstat__t">本月减排</div>
          </div>
        </div>
      </HeroMascot>
    </section>

    <!-- 功能模块 -->
    <section class="features">
      <h2 class="section-title">核心功能</h2>
      <p class="section-subtitle">全方位环保服务，让低碳生活更简单</p>
      
      <div class="features__grid">
        <div
          v-for="feature in features"
          :key="feature.to"
          class="feature-card"
          @click="router.push(feature.to)"
        >
          <div class="feature-card__icon" :style="{ color: feature.color }">
            <SvgIcon :name="feature.icon" :size="48" />
          </div>
          <h3 class="feature-card__title">{{ feature.title }}</h3>
          <p class="feature-card__desc">{{ feature.desc }}</p>
          <div class="feature-card__arrow">
            <SvgIcon name="common-arrow-right" :size="20" />
          </div>
        </div>
      </div>
    </section>

    <!-- 今日报价 -->
    <section class="pricing">
      <h2 class="section-title">今日报价</h2>
      <p class="section-subtitle">每日联网查询当地行情，自动更新</p>
      <p v-if="!loading && prices.length" class="pricing__meta">
        {{ priceMeta.city || '当地' }} · 更新于 {{ priceMeta.lastSyncTime || prices[0]?.updateTime || '—' }}（每天 08:05 自动定位并同步）
      </p>
      
      <div v-if="loading" class="pricing__loading">加载中...</div>
      <div v-else class="pricing__grid">
        <div v-for="price in displayList" :key="price.code" class="price-card">
          <div class="price-card__icon"><SvgIcon :name="price.icon" :size="48" /></div>
          <div class="price-card__name">
            {{ price.name }}
            <span v-if="price.builtin" class="price-card__tag">内置</span>
          </div>
          <div class="price-card__price">¥{{ price.price.toFixed(2)}}<span>/kg</span></div>
          <div
            v-if="!price.builtin"
            class="price-card__trend"
            :class="price.trend > 0 ? 'price-card__trend--up' : (price.trend < 0 ? 'price-card__trend--down' : '')"
          >
            <template v-if="price.trend !== 0">
              {{ price.trend > 0 ? '↑' : '↓' }} {{ Math.abs(price.trend) }}%
            </template>
            <template v-else>—</template>
          </div>
          <div v-else class="price-card__trend price-card__trend--builtin">内置参考价</div>
          <div v-if="!price.builtin" class="price-card__meta">
            {{ price.city }} · {{ price.updateTime || '—' }} 更新
          </div>
        </div>
      </div>
      
      <div class="pricing__more">
        <button class="pricing__more-btn" @click="router.push('/pc/prices')">
          查看全部报价 <SvgIcon name="common-arrow-right" :size="16" />
        </button>
      </div>
    </section>

    <!-- 环保数据 -->
    <section class="impact">
      <h2 class="section-title">平台环保数据</h2>
      <p class="section-subtitle">每一次参与，都是对地球的贡献</p>
      
      <div class="impact__grid">
        <div class="impact__item">
          <div class="impact__icon">
            <SvgIcon name="recycle-recycle-symbol" :size="48" color="var(--df-color-primary)" />
          </div>
          <div class="impact__value">{{ formatNum(stats?.recycleOrders) }}</div>
          <div class="impact__label">累计回收订单</div>
        </div>
        <div class="impact__item">
          <div class="impact__icon">
            <SvgIcon name="carbon-tree" :size="48" color="var(--df-color-primary-dark)" />
          </div>
          <div class="impact__value">{{ formatCarbonZh(stats?.carbonReduced) }}</div>
          <div class="impact__label">累计减少碳排放</div>
        </div>
        <div class="impact__item">
          <div class="impact__icon">
            <SvgIcon name="nav-user" :size="48" color="var(--df-color-carbon)" />
          </div>
          <div class="impact__value">{{ formatNum(stats?.totalUsers) }}</div>
          <div class="impact__label">注册用户数</div>
        </div>
        <div class="impact__item">
          <div class="impact__icon">
            <SvgIcon name="repair-wrench" :size="48" color="var(--df-color-gold)" />
          </div>
          <div class="impact__value">{{ formatNum(stats?.repairOrders) }}</div>
          <div class="impact__label">维修服务次数</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.pc-home {
  width: 100%;
  min-height: 100vh;
}

// Hero 区域：仅作为 wrapper，背景/遮罩由 HeroMascot 处理
// 类名用 .hero-section 而非 .hero：避免与 workbench.scss 全局 .hero（模块概览条）冲突导致被注入 flex/边框/背景
.hero-section {
  width: 100%;
  padding: 0 24px;
}

// 数据概览（HeroMascot 左侧内联小列，对齐模板 .hstats：透明、竖线分隔，不遮挡右侧插画）
.hstats {
  display: flex;
  gap: 26px;
}

.hstat {
  display: flex;
  flex-direction: column;
  gap: 2px;

  & + & {
    padding-left: 26px;
    border-left: 1px solid var(--df-border-input);
  }
}

.hstat__n {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--df-color-primary);
}

.hstat__t {
  font-size: 12px;
  color: var(--df-text-secondary);
}

// 通用 Section 样式
section {
  padding: 64px 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.section-title {
  font-size: 42px;
  font-weight: 700;
  color: var(--df-text-primary);
  text-align: center;
  margin-bottom: 16px;
}

.section-subtitle {
  font-size: 18px;
  color: var(--df-text-regular);
  text-align: center;
  margin-bottom: 40px;
}

// 功能模块
.features__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: 32px;
}

.feature-card {
  padding: 40px;
  background: var(--df-bg-card);
  border-radius: 16px;
  box-shadow: var(--df-shadow-card);
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;

  &:hover {
    transform: translateY(-8px);
    box-shadow: var(--df-shadow-float);
  }

  &:hover .feature-card__arrow {
    transform: translateX(8px);
  }
}

.feature-card__icon {
  margin-bottom: 24px;
}

.feature-card__title {
  font-size: 24px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 12px;
}

.feature-card__desc {
  font-size: 16px;
  color: var(--df-text-regular);
  line-height: 1.6;
  margin-bottom: 24px;
}

.feature-card__arrow {
  color: var(--df-color-primary);
  transition: transform 0.3s ease;
}

// 报价区域
.pricing {
  background: linear-gradient(135deg, var(--df-bg-page) 0%, var(--df-border-color) 100%);
  border-radius: 24px;
}

.pricing__meta {
  font-size: 14px;
  color: var(--df-text-secondary);
  text-align: center;
  margin: -24px 0 40px;
}

.pricing__loading {
  text-align: center;
  padding: 64px;
  color: var(--df-text-secondary);
}

.pricing__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.price-card {
  padding: 32px 24px;
  background: var(--df-bg-card);
  border-radius: 12px;
  text-align: center;
  box-shadow: var(--df-shadow-card);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--df-shadow-float);
  }
}

.price-card__icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.price-card__name {
  font-size: 16px;
  color: var(--df-text-primary);
  font-weight: 600;
  margin-bottom: 8px;
}

.price-card__tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--df-text-secondary);
  background: var(--df-border-color);
  border-radius: 999px;
  vertical-align: 2px;
}

.price-card__price {
  font-size: 28px;
  font-weight: 700;
  color: var(--df-color-primary);
  margin-bottom: 8px;

  span {
    font-size: 14px;
    color: var(--df-text-secondary);
    font-weight: 400;
  }
}

.price-card__trend {
  font-size: 14px;
  font-weight: 600;

  &--up {
    color: var(--df-color-danger);
  }

  &--down {
    color: var(--df-color-primary);
  }

  &--builtin {
    font-weight: 400;
    color: var(--df-text-placeholder);
  }
}

.price-card__meta {
  font-size: 12px;
  color: var(--df-text-secondary);
  padding-top: 8px;
  border-top: 1px dashed var(--df-border-color);
}

.pricing__more {
  text-align: center;
}

.pricing__more-btn {
  padding: 16px 40px;
  font-size: 16px;
  font-weight: 600;
  background: var(--df-bg-card);
  color: var(--df-color-primary);
  border: 2px solid var(--df-color-primary);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 8px;

  &:hover {
    background: var(--df-color-primary);
    color: white;
    transform: translateY(-2px);
  }
}

// 环保数据
.impact__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 48px;
}

.impact__item {
  text-align: center;
}

.impact__icon {
  margin-bottom: 24px;
}

.impact__value {
  font-size: 36px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 12px;
}

.impact__label {
  font-size: 16px;
  color: var(--df-text-regular);
}
</style>
