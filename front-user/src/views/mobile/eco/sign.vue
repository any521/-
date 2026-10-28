<script setup lang="ts">
// 签到日历 + 成就领取（LoopFill B5 创意 C1）：本月日历（7 列）+ 连续天数 + 成就领取列表
// 与环保树页 /eco/tree 互跳；tree 页承载树+进度，本页承载日历+成就
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import {
  getEcoSignStatus, pageEcoAchievements, signIn, claimEcoAchievement,
  type EcoSignStatus, type EcoAchievement,
} from '@/api/business'

const router = useRouter()

const sign = ref<EcoSignStatus | null>(null)
const achievements = ref<EcoAchievement[]>([])
const loading = ref(true)
const signing = ref(false)

const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']

/** 本月日历格子（前导空位 + 每日签到态），7 列对齐 */
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
    sign.value = await getEcoSignStatus()
    achievements.value = await pageEcoAchievements()
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

onMounted(async () => {
  try {
    const [s, ach] = await Promise.all([getEcoSignStatus(), pageEcoAchievements()])
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
  <div class="page eco-sign">
    <van-nav-bar title="签到 · 成就" left-arrow @click-left="router.back()">
      <template #right>
        <span class="eco-sign__tree-link" @click="router.push('/eco/tree')">环保树</span>
      </template>
    </van-nav-bar>

    <van-skeleton :loading="loading" title :row="8">
      <!-- 签到卡 -->
      <div class="card eco-sign__hero">
        <div class="eco-sign__hero-stats">
          <div>
            <div class="eco-sign__num">{{ sign?.streakDays ?? 0 }}</div>
            <div class="eco-sign__label">连续天数</div>
          </div>
          <div>
            <div class="eco-sign__num">{{ sign?.totalSigns ?? 0 }}</div>
            <div class="eco-sign__label">累计签到</div>
          </div>
          <div>
            <div class="eco-sign__num">{{ sign?.signPoints ?? 5 }}</div>
            <div class="eco-sign__label">单次积分</div>
          </div>
        </div>
        <van-button
          round
          block
          type="primary"
          :loading="signing"
          :disabled="todaySigned"
          class="eco-sign__sign-btn"
          @click="onSign"
        >
          {{ todaySigned ? `今日已签 +${sign?.signPoints ?? 5} 分` : `立即签到 +${sign?.signPoints ?? 5} 分` }}
        </van-button>
        <div class="eco-sign__hint">连续签到 {{ sign?.signPoints ?? 5 }} 天解锁「七日坚持」成就（以配置为准）</div>
      </div>

      <!-- 本月日历 -->
      <div class="card">
        <div class="card__title">本月签到日历</div>
        <div class="eco-sign__week">
          <span v-for="w in WEEK_LABELS" :key="w" class="eco-sign__week-label">{{ w }}</span>
        </div>
        <div class="eco-sign__grid">
          <template v-for="(cell, i) in calendarCells" :key="i">
            <div v-if="!cell" class="eco-sign__cell eco-sign__cell--blank" />
            <div
              v-else
              class="eco-sign__cell"
              :class="{
                'eco-sign__cell--signed': cell.signed,
                'eco-sign__cell--today': cell.isToday,
              }"
            >
              {{ cell.date }}
            </div>
          </template>
        </div>
      </div>

      <!-- 成就列表 -->
      <div class="card">
        <div class="card__title">环保成就</div>
        <div
          v-for="a in achievements"
          :key="a.code"
          class="eco-sign__achv"
          :class="{ 'eco-sign__achv--locked': !a.unlocked, 'eco-sign__achv--claimed': a.claimed }"
        >
          <SvgIcon
            :name="a.code === 'RECYCLE_FIRST' ? 'recycle-recycle-symbol' : a.code === 'KITCHEN_FIRST' ? 'recycle-kitchen' : a.code === 'RECYCLE_TEN' ? 'nav-star' : 'nav-calendar'"
            :size="22"
          />
          <div class="eco-sign__achv-main">
            <div class="eco-sign__achv-name">{{ a.name }}<span class="eco-sign__achv-points">+{{ a.points }} 分</span></div>
            <div class="eco-sign__achv-desc">{{ a.description }}</div>
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

.eco-sign {
  padding-top: 0;

  &__tree-link {
    color: var(--df-color-primary);
    font-size: $df-font-size-sm;
  }

  &__hero {
    background: var(--df-gradient-carbon);
    color: #fff;
  }

  &__hero-stats {
    display: flex;
    justify-content: space-around;
    margin-bottom: $df-space-lg;
    text-align: center;
  }

  &__num {
    font-size: $df-font-size-xl;
    font-weight: 700;
  }

  &__label {
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    opacity: 0.9;
  }

  &__sign-btn {
    --van-button-primary-background: var(--df-bg-card);
    --van-button-primary-border-color: #fff;
    --van-button-primary-color: var(--df-color-carbon);
    font-weight: 600;
  }

  &__hint {
    margin-top: $df-space-sm;
    text-align: center;
    font-size: $df-font-size-xs;
    opacity: 0.85;
  }

  &__week {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    margin: $df-space-md 0 $df-space-xs;
    text-align: center;
  }

  &__week-label {
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 4px;
  }

  &__cell {
    aspect-ratio: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    font-size: $df-font-size-sm;

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
