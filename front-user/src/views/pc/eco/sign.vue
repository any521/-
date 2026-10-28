<script setup lang="ts">
// PC 签到日历 + 成就领取（LoopFill B5 创意 C1）：与 mobile/eco/sign.vue 同构
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import {
  getEcoSignStatus, pageEcoAchievements, signIn, claimEcoAchievement,
  type EcoSignStatus, type EcoAchievement,
} from '@/api/business'

defineOptions({ name: 'pc-eco-sign' })

const router = useRouter()

const sign = ref<EcoSignStatus | null>(null)
const achievements = ref<EcoAchievement[]>([])
const loading = ref(true)
const signing = ref(false)

const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']

const calendarCells = computed(() => {
  const days = sign.value?.monthDays ?? []
  const first = days.length ? new Date(days[0]!.date).getDay() : 0
  const blanks = Array.from({ length: first }, () => null)
  return [
    ...blanks,
    ...days.map((d) => ({
      date: Number(d.date.slice(8, 10)),
      signed: d.signed,
      isToday: d.date === new Date().toISOString().slice(0, 10),
    })),
  ]
})

const todaySigned = computed(() => sign.value?.todaySigned ?? false)

async function onSign() {
  if (signing.value || todaySigned.value) return
  signing.value = true
  try {
    const r = await signIn()
    showSuccessToast(`签到成功 +${r.points} 积分`)
    const [s, ach] = await Promise.all([getEcoSignStatus(), pageEcoAchievements()])
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
    const [s, ach] = await Promise.all([getEcoSignStatus(), pageEcoAchievements()])
    sign.value = s
    achievements.value = ach
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="pc-eco-sign">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">签到 · 成就</h1>
        <button class="header-link" @click="router.push('/pc/eco/tree')">环保树 ›</button>
      </div>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else>
        <!-- 签到卡 -->
        <div class="hero-card">
          <div class="hero-stats">
            <div class="hero-stat">
              <div class="hero-num">{{ sign?.streakDays ?? 0 }}</div>
              <div class="hero-label">连续天数</div>
            </div>
            <div class="hero-stat">
              <div class="hero-num">{{ sign?.totalSigns ?? 0 }}</div>
              <div class="hero-label">累计签到</div>
            </div>
            <div class="hero-stat">
              <div class="hero-num">{{ sign?.signPoints ?? 5 }}</div>
              <div class="hero-label">单次积分</div>
            </div>
          </div>
          <button
            class="sign-btn"
            :disabled="todaySigned"
            @click="onSign"
          >
            {{ todaySigned ? `今日已签 +${sign?.signPoints ?? 5} 分` : `立即签到 +${sign?.signPoints ?? 5} 分` }}
          </button>
        </div>

        <!-- 本月日历 -->
        <div class="section-card">
          <h3 class="section-title">本月签到日历</h3>
          <div class="week-row">
            <span v-for="w in WEEK_LABELS" :key="w" class="week-label">{{ w }}</span>
          </div>
          <div class="calendar-grid">
            <template v-for="(cell, i) in calendarCells" :key="i">
              <div v-if="!cell" class="cell cell--blank" />
              <div
                v-else
                class="cell"
                :class="{ 'cell--signed': cell.signed, 'cell--today': cell.isToday }"
              >
                {{ cell.date }}
              </div>
            </template>
          </div>
        </div>

        <!-- 成就列表 -->
        <div class="section-card">
          <h3 class="section-title">环保成就</h3>
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
.pc-eco-sign {
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

.section-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.week-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 8px;
  text-align: center;
}

.week-label {
  color: var(--df-text-secondary);
  font-size: 13px;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
}

.cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 14px;

  &--blank {
    visibility: hidden;
  }

  &--signed {
    background: var(--df-color-carbon);
    color: #fff;
    font-weight: 600;
  }

  &--today {
    outline: 2px solid var(--df-color-primary);
    outline-offset: -2px;
  }
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
  background: #ff976a;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
</style>
