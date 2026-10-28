<script setup lang="ts">
// 环保树（LoopFill B5 创意 C1）：顶部签到卡 + SVG 成长树（随 totalCarbon 分档）+ 成就区
// 树档位：0-9kg 种子 / 10-49kg 幼苗 / 50-99kg 小树 / 100-199kg 大树 / ≥200kg 参天大树
// 签到日历 + 成就领取独立页 /eco/sign（本页互跳）
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import {
  getCarbonAccount, getEcoSignStatus, pageEcoAchievements, signIn,
  claimEcoAchievement,
  type CarbonAccount, type EcoSignStatus, type EcoAchievement,
} from '@/api/business'
import { formatCarbon } from '@shared/utils/format'

const router = useRouter()

const account = ref<CarbonAccount | null>(null)
const sign = ref<EcoSignStatus | null>(null)
const achievements = ref<EcoAchievement[]>([])
const loading = ref(true)
const signing = ref(false)

/** 树档位（0 种子 → 4 参天大树），随累计碳减排分档 */
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

/** 距下一档还差多少 kg；已满级显示 ∞ */
const nextFloor = computed(() => {
  const carbon = account.value?.totalCarbon ?? 0
  const next = TREE_LEVELS[treeLevel.value + 1]
  if (!next) return null
  return Math.max(0, next.floor - carbon)
})

/** 升级进度条百分比（当前档内） */
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
    await Promise.all([loadAccount(), loadSign(), loadAchievements()])
  } catch {
    // 签到失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    signing.value = false
  }
}

async function onClaim(a: EcoAchievement) {
  try {
    const r = await claimEcoAchievement(a.code)
    showSuccessToast(`已领取「${a.name}」+${r.points} 积分`)
    achievements.value = await pageEcoAchievements()
  } catch {
    // 领取失败：拦截器已 Toast，此处避免未捕获异常
  }
}

async function loadAccount() {
  try {
    account.value = await getCarbonAccount()
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  }
}

async function loadSign() {
  try {
    sign.value = await getEcoSignStatus()
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  }
}

async function loadAchievements() {
  try {
    achievements.value = await pageEcoAchievements()
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  }
}

onMounted(async () => {
  try {
    const [a, s, ach] = await Promise.all([getCarbonAccount(), getEcoSignStatus(), pageEcoAchievements()])
    account.value = a
    sign.value = s
    achievements.value = ach
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page eco-tree">
    <van-nav-bar title="环保树" left-arrow @click-left="router.back()">
      <template #right>
        <span class="eco-tree__calendar-link" @click="router.push('/eco/sign')">签到日历</span>
      </template>
    </van-nav-bar>

    <van-skeleton :loading="loading" title :row="6">
      <!-- 顶部签到卡 -->
      <div class="card eco-tree__sign">
        <div class="eco-tree__sign-info">
          <div class="eco-tree__sign-streak">{{ sign?.streakDays ?? 0 }} 天</div>
          <div class="eco-tree__sign-label">连续签到 · 本月 {{ sign?.totalSigns ?? 0 }} 次</div>
        </div>
        <van-button
          round
          type="primary"
          size="small"
          :loading="signing"
          :disabled="todaySigned"
          @click="onSign"
        >
          {{ todaySigned ? `今日已签 +${sign?.signPoints ?? 5}` : `签到 +${sign?.signPoints ?? 5} 分` }}
        </van-button>
      </div>

      <!-- 环保树 -->
      <div class="card eco-tree__tree">
        <div class="eco-tree__tree-stage">
          <svg viewBox="0 0 200 240" class="eco-tree__svg" aria-label="环保成长树">
            <!-- 档位 0：种子 -->
            <g v-if="treeLevel === 0">
              <ellipse cx="100" cy="200" rx="46" ry="12" fill="var(--df-color-carbon)" opacity="0.25" />
              <ellipse cx="100" cy="196" rx="34" ry="9" fill="#8d6e63" />
              <path d="M100 196 C 94 178 96 168 100 158 C 104 168 106 178 100 196 Z" fill="#66bb6a" />
            </g>
            <!-- 档位 1：幼苗 -->
            <g v-else-if="treeLevel === 1">
              <ellipse cx="100" cy="200" rx="46" ry="12" fill="var(--df-color-carbon)" opacity="0.25" />
              <ellipse cx="100" cy="196" rx="34" ry="9" fill="#8d6e63" />
              <line x1="100" y1="196" x2="100" y2="132" stroke="#5d4037" stroke-width="5" stroke-linecap="round" />
              <path d="M100 138 C 78 128 62 138 54 156 C 76 152 92 146 100 138 Z" fill="#66bb6a" />
              <path d="M100 122 C 122 112 138 122 146 140 C 124 136 108 130 100 122 Z" fill="#81c784" />
            </g>
            <!-- 档位 2：小树 -->
            <g v-else-if="treeLevel === 2">
              <ellipse cx="100" cy="200" rx="50" ry="13" fill="var(--df-color-carbon)" opacity="0.25" />
              <path d="M88 198 L 92 110 L 108 110 L 112 198 Z" fill="#795548" />
              <circle cx="100" cy="88" r="44" fill="#66bb6a" />
              <circle cx="78" cy="100" r="26" fill="#81c784" />
              <circle cx="122" cy="100" r="26" fill="#4caf50" />
            </g>
            <!-- 档位 3：大树 -->
            <g v-else-if="treeLevel === 3">
              <ellipse cx="100" cy="200" rx="56" ry="14" fill="var(--df-color-carbon)" opacity="0.25" />
              <path d="M84 198 L 90 84 L 110 84 L 116 198 Z" fill="#5d4037" />
              <circle cx="100" cy="60" r="52" fill="#43a047" />
              <circle cx="70" cy="80" r="30" fill="#66bb6a" />
              <circle cx="130" cy="80" r="30" fill="#388e3c" />
              <circle cx="100" cy="104" r="22" fill="#81c784" />
            </g>
            <!-- 档位 4：参天大树 -->
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
            <!-- 树底小标识：每棵树代一棵减排树 -->
            <text x="100" y="228" text-anchor="middle" class="eco-tree__svg-caption">
              已减排 {{ formatCarbon(account?.totalCarbon ?? 0) }} kg
            </text>
          </svg>
          <div class="eco-tree__level-tag">{{ levelInfo.label }}</div>
        </div>

        <div class="eco-tree__progress">
          <div class="eco-tree__progress-bar">
            <div class="eco-tree__progress-fill" :style="{ width: `${progressPct}%` }" />
          </div>
          <div class="eco-tree__progress-text">
            <span v-if="nextFloor !== null">再减排 {{ formatCarbon(nextFloor) }} kg 升级为「{{ TREE_LEVELS[treeLevel + 1]?.label ?? '' }}」</span>
            <span v-else>已达最高等级 <SvgIcon name="carbon-tree" :size="16" /></span>
            <span>≈ 植树 {{ formatCarbon((account?.totalCarbon ?? 0) / 18) }} 棵</span>
          </div>
        </div>
      </div>

      <!-- 成就区 -->
      <div class="card">
        <div class="card__title">
          环保成就
          <span class="eco-tree__more" @click="router.push('/eco/sign')">签到日历 ›</span>
        </div>
        <div
          v-for="a in achievements"
          :key="a.code"
          class="eco-tree__achv"
          :class="{ 'eco-tree__achv--locked': !a.unlocked, 'eco-tree__achv--claimed': a.claimed }"
        >
          <SvgIcon
            :name="a.code === 'RECYCLE_FIRST' ? 'recycle-recycle-symbol' : a.code === 'KITCHEN_FIRST' ? 'recycle-kitchen' : a.code === 'RECYCLE_TEN' ? 'nav-star' : 'nav-calendar'"
            :size="22"
          />
          <div class="eco-tree__achv-main">
            <div class="eco-tree__achv-name">{{ a.name }}<span class="eco-tree__achv-points">+{{ a.points }} 分</span></div>
            <div class="eco-tree__achv-desc">{{ a.description }}</div>
          </div>
          <van-tag v-if="!a.unlocked" plain>未达成</van-tag>
          <van-tag v-else-if="a.claimed" type="success" plain>已领取</van-tag>
          <van-button v-else size="mini" type="warning" round @click="onClaim(a)">领取</van-button>
        </div>
      </div>
    </van-skeleton>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.eco-tree {
  padding-top: 0;

  &__calendar-link {
    color: var(--df-color-primary);
    font-size: $df-font-size-sm;
  }

  &__sign {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: var(--df-gradient-carbon);
    color: #fff;
  }

  &__sign-streak {
    font-size: $df-font-size-xl;
    font-weight: 700;
  }

  &__sign-label {
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    opacity: 0.9;
  }

  &__tree {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__tree-stage {
    position: relative;
    width: 100%;
    max-width: 280px;
  }

  &__svg {
    width: 100%;
    height: auto;
  }

  &__svg-caption {
    font-size: 11px;
    fill: var(--df-text-secondary);
  }

  &__level-tag {
    position: absolute;
    top: $df-space-sm;
    left: 50%;
    transform: translateX(-50%);
    padding: 2px 12px;
    border-radius: 999px;
    background: var(--df-color-primary);
    color: #fff;
    font-size: $df-font-size-xs;
  }

  &__progress {
    width: 100%;
    margin-top: $df-space-md;
  }

  &__progress-bar {
    height: 8px;
    border-radius: 999px;
    background: var(--df-border-color);
    overflow: hidden;
  }

  &__progress-fill {
    height: 100%;
    border-radius: 999px;
    background: var(--df-gradient-carbon);
    transition: width 0.4s ease;
  }

  &__progress-text {
    display: flex;
    justify-content: space-between;
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__more {
    float: right;
    color: var(--df-color-primary);
    font-size: $df-font-size-xs;
  }

  &__achv {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    padding: $df-space-md 0;
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

  &__achv-main {
    flex: 1;
    min-width: 0;
  }

  &__achv-name {
    font-size: $df-font-size-base;
    font-weight: 500;
  }

  &__achv-points {
    margin-left: $df-space-xs;
    color: var(--df-color-gold);
    font-size: $df-font-size-xs;
  }

  &__achv-desc {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }
}
</style>
