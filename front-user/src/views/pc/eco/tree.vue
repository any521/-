<script setup lang="ts">
// PC 环保树（LoopFill B5 创意 C1）：与 mobile/eco/tree.vue 同构
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import {
  getCarbonAccount, getEcoSignStatus, pageEcoAchievements, signIn, claimEcoAchievement,
  type CarbonAccount, type EcoSignStatus, type EcoAchievement,
} from '@/api/business'
import { formatCarbon } from '@shared/utils/format'

defineOptions({ name: 'pc-eco-tree' })

const router = useRouter()

const account = ref<CarbonAccount | null>(null)
const sign = ref<EcoSignStatus | null>(null)
const achievements = ref<EcoAchievement[]>([])
const loading = ref(true)
const signing = ref(false)

const TREE_LEVELS = [
  { floor: 0, label: '种子' },
  { floor: 10, label: '幼苗' },
  { floor: 50, label: '小树' },
  { floor: 100, label: '大树' },
  { floor: 200, label: '参天大树' },
] as const

const treeLevel = computed(() => {
  const carbon = account.value?.totalCarbon ?? 0
  let idx = 0
  TREE_LEVELS.forEach((l, i) => {
    if (carbon >= l.floor) idx = i
  })
  return idx
})

const levelInfo = computed(() => TREE_LEVELS[treeLevel.value] ?? TREE_LEVELS[0]!)

const nextFloor = computed(() => {
  const carbon = account.value?.totalCarbon ?? 0
  const next = TREE_LEVELS[treeLevel.value + 1]
  if (!next) return null
  return Math.max(0, next.floor - carbon)
})

const progressPct = computed(() => {
  const carbon = account.value?.totalCarbon ?? 0
  const cur = levelInfo.value
  const next = TREE_LEVELS[treeLevel.value + 1]
  if (!next) return 100
  const span = next.floor - cur.floor
  return Math.min(100, Math.round(((carbon - cur.floor) / span) * 100))
})

const todaySigned = computed(() => sign.value?.todaySigned ?? false)

async function onSign() {
  if (signing.value || todaySigned.value) return
  signing.value = true
  try {
    const r = await signIn()
    showSuccessToast(`签到成功 +${r.points} 积分`)
    const [a, s, ach] = await Promise.all([getCarbonAccount(), getEcoSignStatus(), pageEcoAchievements()])
    account.value = a
    sign.value = s
    achievements.value = ach
  } finally {
    signing.value = false
  }
}

async function onClaim(a: EcoAchievement) {
  const r = await claimEcoAchievement(a.code)
  showSuccessToast(`已领取「${a.name}」+${r.points} 积分`)
  achievements.value = await pageEcoAchievements()
}

onMounted(async () => {
  try {
    const [a, s, ach] = await Promise.all([getCarbonAccount(), getEcoSignStatus(), pageEcoAchievements()])
    account.value = a
    sign.value = s
    achievements.value = ach
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="pc-eco-tree">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">环保树</h1>
        <button class="header-link" @click="router.push('/pc/eco/sign')">签到日历 ›</button>
      </div>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else>
        <!-- 签到卡 -->
        <div class="hero-card">
          <div class="hero-stats">
            <div class="hero-stat">
              <div class="hero-num">{{ sign?.streakDays ?? 0 }}</div>
              <div class="hero-label">连续签到（天）</div>
            </div>
            <div class="hero-stat">
              <div class="hero-num">{{ sign?.totalSigns ?? 0 }}</div>
              <div class="hero-label">本月累计签到</div>
            </div>
          </div>
          <button
            class="sign-btn"
            :disabled="todaySigned"
            @click="onSign"
          >
            {{ todaySigned ? `今日已签 +${sign?.signPoints ?? 5} 分` : `签到 +${sign?.signPoints ?? 5} 分` }}
          </button>
        </div>

        <!-- 环保树 -->
        <div class="section-card tree-card">
          <div class="tree-stage">
            <svg viewBox="0 0 200 240" class="tree-svg" aria-label="环保成长树">
              <g v-if="treeLevel === 0">
                <ellipse cx="100" cy="200" rx="46" ry="12" fill="var(--df-color-carbon)" opacity="0.25" />
                <ellipse cx="100" cy="196" rx="34" ry="9" fill="#8d6e63" />
                <path d="M100 196 C 94 178 96 168 100 158 C 104 168 106 178 100 196 Z" fill="#66bb6a" />
              </g>
              <g v-else-if="treeLevel === 1">
                <ellipse cx="100" cy="200" rx="46" ry="12" fill="var(--df-color-carbon)" opacity="0.25" />
                <ellipse cx="100" cy="196" rx="34" ry="9" fill="#8d6e63" />
                <line x1="100" y1="196" x2="100" y2="132" stroke="#5d4037" stroke-width="5" stroke-linecap="round" />
                <path d="M100 138 C 78 128 62 138 54 156 C 76 152 92 146 100 138 Z" fill="#66bb6a" />
                <path d="M100 122 C 122 112 138 122 146 140 C 124 136 108 130 100 122 Z" fill="#81c784" />
              </g>
              <g v-else-if="treeLevel === 2">
                <ellipse cx="100" cy="200" rx="50" ry="13" fill="var(--df-color-carbon)" opacity="0.25" />
                <path d="M88 198 L 92 110 L 108 110 L 112 198 Z" fill="#795548" />
                <circle cx="100" cy="88" r="44" fill="#66bb6a" />
                <circle cx="78" cy="100" r="26" fill="#81c784" />
                <circle cx="122" cy="100" r="26" fill="#4caf50" />
              </g>
              <g v-else-if="treeLevel === 3">
                <ellipse cx="100" cy="200" rx="56" ry="14" fill="var(--df-color-carbon)" opacity="0.25" />
                <path d="M84 198 L 90 84 L 110 84 L 116 198 Z" fill="#5d4037" />
                <circle cx="100" cy="60" r="52" fill="#43a047" />
                <circle cx="70" cy="80" r="30" fill="#66bb6a" />
                <circle cx="130" cy="80" r="30" fill="#388e3c" />
                <circle cx="100" cy="104" r="22" fill="#81c784" />
              </g>
              <g v-else>
                <ellipse cx="100" cy="200" rx="62" ry="15" fill="var(--df-color-carbon)" opacity="0.25" />
                <path d="M82 198 L 90 58 L 110 58 L 118 198 Z" fill="#4e342e" />
                <circle cx="100" cy="38" r="34" fill="#2e7d32" />
                <circle cx="68" cy="62" r="30" fill="#43a047" />
                <circle cx="132" cy="62" r="30" fill="#388e3c" />
                <circle cx="84" cy="92" r="26" fill="#66bb6a" />
                <circle cx="116" cy="92" r="26" fill="#4caf50" />
                <circle cx="100" cy="116" r="18" fill="#81c784" />
              </g>
              <text x="100" y="228" text-anchor="middle" class="tree-caption">
                已减排 {{ formatCarbon(account?.totalCarbon ?? 0) }} kg
              </text>
            </svg>
            <span class="level-tag">{{ levelInfo.label }}</span>
          </div>
          <div class="progress">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: `${progressPct}%` }" />
            </div>
            <div class="progress-text">
              <span v-if="nextFloor !== null">再减排 {{ formatCarbon(nextFloor) }} kg 升级为「{{ TREE_LEVELS[treeLevel + 1]?.label ?? '' }}」</span>
              <span v-else>已达最高等级 <SvgIcon name="carbon-tree" :size="16" /></span>
              <span>≈ 植树 {{ ((account?.totalCarbon ?? 0) / 18).toFixed(1) }} 棵</span>
            </div>
          </div>
        </div>

        <!-- 成就区 -->
        <div class="section-card">
          <div class="section-head">
            <h3 class="section-title">环保成就</h3>
            <span class="more-link" @click="router.push('/pc/eco/sign')">签到日历 ›</span>
          </div>
          <div
            v-for="a in achievements"
            :key="a.code"
            class="achv-row"
            :class="{ 'achv-row--locked': !a.unlocked, 'achv-row--claimed': a.claimed }"
          >
            <SvgIcon
              :name="a.code === 'RECYCLE_FIRST' ? 'recycle-recycle-symbol' : a.code === 'KITCHEN_FIRST' ? 'recycle-kitchen' : a.code === 'RECYCLE_TEN' ? 'nav-star' : 'nav-calendar'"
              :size="22"
            />
            <div class="achv-main">
              <div class="achv-name">{{ a.name }}<span class="achv-points">+{{ a.points }} 分</span></div>
              <div class="achv-desc">{{ a.description }}</div>
            </div>
            <span v-if="!a.unlocked" class="achv-state">未达成</span>
            <span v-else-if="a.claimed" class="achv-state achv-state--claimed">已领取</span>
            <button v-else class="claim-btn" @click="onClaim(a)">领取</button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-eco-tree {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 760px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 40px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: 1px solid var(--df-border-color);
  border-radius: 8px;
  background: var(--df-bg-card);
  color: var(--df-text-primary);
  cursor: pointer;
}

.page-title {
  font-size: 40px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin: 0;
}

.header-link {
  padding: 8px 12px;
  border: none;
  background: none;
  color: var(--df-color-primary);
  font-size: 14px;
  cursor: pointer;
}

.loading {
  padding: 80px 0;
  text-align: center;
  color: var(--df-text-secondary);
}

.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 32px;
  border-radius: 16px;
  background: var(--df-gradient-carbon);
  color: #fff;
}

.hero-stats {
  display: flex;
  gap: 48px;
}

.hero-num {
  font-size: 36px;
  font-weight: 700;
}

.hero-label {
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.9;
}

.sign-btn {
  padding: 12px 28px;
  border: none;
  border-radius: 999px;
  background: var(--df-bg-card);
  color: var(--df-color-carbon);
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

.section-card {
  margin-top: 24px;
  padding: 24px 32px;
  border-radius: 16px;
  background: var(--df-bg-card);
}

.tree-card {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tree-stage {
  position: relative;
  width: 100%;
  max-width: 320px;
}

.tree-svg {
  width: 100%;
  height: auto;
}

.tree-caption {
  font-size: 11px;
  fill: var(--df-text-secondary);
}

.level-tag {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 16px;
  border-radius: 999px;
  background: var(--df-color-primary);
  color: #fff;
  font-size: 12px;
}

.progress {
  width: 100%;
  margin-top: 16px;
}

.progress-bar {
  height: 10px;
  border-radius: 999px;
  background: var(--df-border-color);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--df-gradient-carbon);
  transition: width 0.4s ease;
}

.progress-text {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  color: var(--df-text-secondary);
  font-size: 13px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.more-link {
  color: var(--df-color-primary);
  font-size: 13px;
  cursor: pointer;
}

.achv-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--df-border-color);
  color: var(--df-text-primary);

  &:last-child {
    border-bottom: none;
  }

  &--locked {
    opacity: 0.45;
  }

  &--claimed {
    color: var(--df-text-regular);
  }
}

.achv-main {
  flex: 1;
  min-width: 0;
}

.achv-name {
  font-size: 15px;
  font-weight: 500;
}

.achv-points {
  margin-left: 8px;
  color: var(--df-color-gold);
  font-size: 12px;
}

.achv-desc {
  margin-top: 2px;
  color: var(--df-text-secondary);
  font-size: 12px;
}

.achv-state {
  padding: 4px 12px;
  border: 1px solid var(--df-border-color);
  border-radius: 999px;
  font-size: 12px;

  &--claimed {
    border-color: var(--df-color-carbon);
    color: var(--df-color-carbon);
  }
}

.claim-btn {
  padding: 6px 18px;
  border: none;
  border-radius: 999px;
  background: var(--df-gradient-gold);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
</style>
