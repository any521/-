<script setup lang="ts">
// 片区环保排行榜（LoopFill B6 创意 C2）：三指标聚合榜 + 我的片区高亮 + 刷新
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDiyRank, type DiyRankData, type DiyRankItem } from '@/api/business'

defineOptions({ name: 'community-rank' })

const router = useRouter()

const data = ref<DiyRankData | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await getDiyRank()
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
}

/** 排名 1-3 金银铜徽章样式，其余灰 */
function medalClass(index: number) {
  if (index === 0) return 'rank__medal--gold'
  if (index === 1) return 'rank__medal--silver'
  if (index === 2) return 'rank__medal--bronze'
  return ''
}

function medalText(index: number) {
  return String(index + 1)
}

function isMyArea(item: DiyRankItem) {
  return data.value?.myAreaId != null && item.areaId === data.value.myAreaId
}

onMounted(load)
</script>

<template>
  <div class="page rank">
    <van-nav-bar title="片区环保榜" left-arrow @click-left="router.back()" />

    <!-- 我的排名卡 -->
    <div class="card rank__hero">
      <div class="rank__hero-num">
        {{ data?.myAreaRank ? `第 ${data.myAreaRank} 名` : '暂未上榜' }}
      </div>
      <div class="rank__hero-label">
        {{ data?.myAreaRank ? '我的片区排名' : '完成回收/垃圾投放后即可上榜' }}
      </div>
    </div>

    <div class="card">
      <div class="rank__head">
        <div class="card__title">片区环保榜</div>
        <van-button size="small" round plain type="primary" :loading="loading" @click="load">刷新</van-button>
      </div>

      <van-empty v-if="!loading && data && data.list.length === 0" description="暂无片区环保数据" />

      <div v-else class="rank__list">
        <div
          v-for="(item, i) in data?.list ?? []"
          :key="item.areaId"
          class="rank__row"
          :class="{ 'rank__row--mine': isMyArea(item) }"
        >
          <div class="rank__row-left">
            <span class="rank__medal" :class="medalClass(i)">{{ medalText(i) }}</span>
            <div>
              <div class="rank__area">
                {{ item.areaName }}
                <van-tag v-if="isMyArea(item)" type="primary" plain>我的片区</van-tag>
              </div>
              <div class="rank__metrics">
                回收 {{ item.recycleKg }}kg · {{ item.kitchenBags ?? 0 }}袋 · 碳减排 {{ item.carbonKg }}kg
              </div>
            </div>
          </div>
          <div class="rank__score">
            <div class="rank__score-num">{{ item.totalScore }}</div>
            <div class="rank__score-label">总分</div>
          </div>
        </div>
      </div>

      <div class="rank__note">榜单随回收/垃圾/碳减排数据实时聚合，无独立刷新任务</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.rank {
  &__hero {
    text-align: center;
    background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
    color: #fff;
  }

  &__hero-num {
    font-size: $df-font-size-xl;
    font-weight: 700;
  }

  &__hero-label {
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    opacity: 0.85;
  }

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $df-space-sm;
  }

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $df-space-md 0;
    border-bottom: 1px solid var(--df-border-color);

    &--mine {
      background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent);
      border-radius: $df-radius-sm;
      padding-left: $df-space-xs;
      padding-right: $df-space-xs;
    }
  }

  &__row-left {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    min-width: 0;
  }

  &__medal {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: var(--df-bg-page);
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    font-weight: 600;
    flex-shrink: 0;

    &--gold { background: #ffe9a8; color: #b8860b; }
    &--silver { background: #e8ecf0; color: #7f8c9b; }
    &--bronze { background: #f3d9c2; color: #b0713a; }
  }

  &__area {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
    font-size: $df-font-size-base;
    font-weight: 500;
  }

  &__metrics {
    margin-top: 2px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__score {
    text-align: center;
    flex-shrink: 0;
    margin-left: $df-space-sm;
  }

  &__score-num {
    font-size: $df-font-size-lg;
    font-weight: 700;
    color: var(--df-color-gold);
  }

  &__score-label {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__note {
    margin-top: $df-space-md;
    text-align: center;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }
}
</style>
