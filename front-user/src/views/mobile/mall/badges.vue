<script setup lang="ts">
// 移动端我的徽章（积分市场合并升级）：已兑换徽章墙 + 分页加载
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyBadges, badgeIconName, type MyBadge } from '@/api/business'

defineOptions({ name: 'mobile-mall-badges' })

const router = useRouter()

const list = ref<MyBadge[]>([])
const loading = ref(false)
const finished = ref(false)
let pageNum = 1

async function loadMore() {
  loading.value = true
  try {
    const page = await pageMyBadges({ pageNum, pageSize: 10 })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
}

onMounted(loadMore)
</script>

<template>
  <div class="page badges">
    <van-nav-bar title="我的徽章" left-arrow @click-left="router.back()" />

    <div v-if="loading && list.length === 0" class="badges__loading">加载中…</div>

    <van-empty
      v-else-if="finished && list.length === 0"
      description="还没有徽章，去积分市场兑换第一枚吧"
    >
      <van-button size="small" round type="primary" plain @click="router.push('/mall')">
        去兑换
      </van-button>
    </van-empty>

    <van-list v-else v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
      <div class="badges__grid">
        <div v-for="b in list" :key="b.id" class="badges__item card card--glass">
          <div class="badges__icon">
            <img v-if="b.badgeIcon" :src="b.badgeIcon" class="badges__img" alt="" />
            <SvgIcon v-else :name="badgeIconName(b.badgeCode)" size="40" color="var(--df-color-gold)" />
          </div>
          <div class="badges__name">{{ b.badgeName }}</div>
          <div class="badges__time">{{ b.createTime }}</div>
        </div>
      </div>
    </van-list>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.badges {
  &__loading {
    text-align: center;
    padding: 60px 0;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: $df-space-sm;
    padding: $df-space-md;
  }

  &__item {
    margin-bottom: 0;
    text-align: center;
    padding: $df-space-md $df-space-xs;
  }

  &__icon {
    width: 56px;
    height: 56px;
    margin: 0 auto $df-space-xs;
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
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-text-primary);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__time {
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }
}
</style>
