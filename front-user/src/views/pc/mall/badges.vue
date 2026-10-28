<script setup lang="ts">
// PC 我的徽章（积分市场合并升级）：徽章墙——已兑换徽章网格 + 分页加载
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyBadges, badgeIconName, type MyBadge } from '@/api/business'

defineOptions({ name: 'pc-mall-badges' })

const router = useRouter()

const list = ref<MyBadge[]>([])
const loading = ref(false)
const finished = ref(false)
let pageNum = 1

async function loadMore() {
  loading.value = true
  try {
    const page = await pageMyBadges({ pageNum, pageSize: 12 })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } finally {
    loading.value = false
  }
}

onMounted(loadMore)
</script>

<template>
  <div class="pc-badges">
    <div class="container">
      <h1 class="page-title">我的徽章</h1>
      <p class="page-desc">已兑换的成就徽章收藏，继续积累积分收集全部 10 枚</p>

      <div class="head">
        <span class="head__count">已拥有 <strong>{{ list.length }}</strong> 枚</span>
        <button class="head__link" @click="router.push('/pc/mall')">去兑换</button>
      </div>

      <div v-if="loading && list.length === 0" class="loading">加载中…</div>

      <div v-else-if="finished && list.length === 0" class="empty">
        还没有徽章，去积分市场兑换第一枚吧
      </div>

      <div v-else class="grid">
        <div v-for="b in list" :key="b.id" class="badge-card">
          <div class="badge-card__icon">
            <img v-if="b.badgeIcon" :src="b.badgeIcon" class="badge-card__img" alt="" />
            <SvgIcon v-else :name="badgeIconName(b.badgeCode)" :size="56" color="var(--df-color-gold)" />
          </div>
          <div class="badge-card__name">{{ b.badgeName }}</div>
          <div class="badge-card__time">兑换于 {{ b.createTime }}</div>
        </div>
      </div>

      <button v-if="!finished && list.length > 0" class="load-more" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
      <p v-else-if="finished && list.length > 0" class="no-more">没有更多了</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-badges {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 1100px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 16px;
  text-align: center;
}

.page-desc {
  font-size: 16px;
  color: var(--df-text-secondary);
  text-align: center;
  margin-bottom: 40px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-bottom: 32px;

  &__count {
    font-size: 15px;
    color: var(--df-text-regular);

    strong {
      color: var(--df-color-gold);
      font-size: 20px;
      font-weight: 700;
    }
  }

  &__link {
    padding: 8px 24px;
    font-size: 14px;
    font-weight: 600;
    color: var(--df-color-primary);
    background: transparent;
    border: 2px solid var(--df-color-primary);
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
    }
  }
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 24px;
}

.badge-card {
  padding: 28px 20px;
  background: var(--df-bg-card);
  border-radius: 16px;
  text-align: center;
  box-shadow: var(--df-shadow-card);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-6px);
    box-shadow: var(--df-shadow-float);
  }

  &__icon {
    width: 88px;
    height: 88px;
    margin: 0 auto 16px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, color-mix(in srgb, var(--df-color-gold) 10%, transparent) 0%, color-mix(in srgb, var(--df-color-secondary) 10%, transparent) 100%);
    overflow: hidden;
  }

  &__img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &__name {
    font-size: 18px;
    font-weight: 700;
    color: var(--df-text-primary);
    margin-bottom: 8px;
  }

  &__time {
    font-size: 12px;
    color: var(--df-text-secondary);
  }
}

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.load-more {
  display: block;
  width: 100%;
  max-width: 400px;
  margin: 32px auto 0;
  padding: 12px;
  font-size: 15px;
  font-weight: 500;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.no-more {
  text-align: center;
  color: var(--df-text-secondary);
  font-size: 14px;
  margin: 32px 0 0;
}
</style>
