<script setup lang="ts">
// PC 片区环保排行榜（LoopFill B6 创意 C2）：三指标聚合榜 + 我的片区高亮 + 刷新
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDiyRank, type DiyRankData, type DiyRankItem } from '@/api/business'

defineOptions({ name: 'pc-community-rank' })

const router = useRouter()

const data = ref<DiyRankData | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await getDiyRank()
  } finally {
    loading.value = false
  }
}

/** 排名 1-3 金银铜徽章样式，其余灰 */
function medalClass(index: number) {
  if (index === 0) return 'pc-rank__medal--gold'
  if (index === 1) return 'pc-rank__medal--silver'
  if (index === 2) return 'pc-rank__medal--bronze'
  return ''
}

function isMyArea(item: DiyRankItem) {
  return data.value?.myAreaId != null && item.areaId === data.value.myAreaId
}

onMounted(load)
</script>

<template>
  <div class="pc-rank">
    <div class="container">
      <h1 class="page-title">片区环保排行榜</h1>

      <div class="toolbar">
        <button class="btn-plain" @click="router.push('/pc/community')">
          <SvgIcon name="common-lightbulb" :size="18" /> 返回社区
        </button>
        <button class="btn-primary" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新榜单' }}
        </button>
      </div>

      <!-- 我的排名卡 -->
      <div class="card hero">
        <div class="hero__num">{{ data?.myAreaRank ? `第 ${data.myAreaRank} 名` : '暂未上榜' }}</div>
        <div class="hero__label">
          {{ data?.myAreaRank ? '我的片区排名' : '完成回收/垃圾投放后即可上榜' }}
        </div>
      </div>

      <div class="card">
        <div v-if="loading && !data" class="loading">加载中…</div>

        <div v-else-if="data && data.list.length === 0" class="empty">暂无片区环保数据</div>

        <div v-else class="list">
          <div
            v-for="(item, i) in data?.list ?? []"
            :key="item.areaId"
            class="row"
            :class="{ 'row--mine': isMyArea(item) }"
          >
            <div class="row__left">
              <span class="pc-rank__medal" :class="medalClass(i)">{{ i + 1 }}</span>
              <div class="row__info">
                <div class="row__area">
                  {{ item.areaName }}
                  <span v-if="isMyArea(item)" class="row__mine-tag">我的片区</span>
                </div>
                <div class="row__metrics">
                  回收 {{ item.recycleKg }}kg · {{ item.kitchenBags ?? 0 }}袋 · 碳减排 {{ item.carbonKg }}kg
                </div>
              </div>
            </div>
            <div class="row__score">
              <div class="row__score-num">{{ item.totalScore }}</div>
              <div class="row__score-label">总分</div>
            </div>
          </div>
        </div>

        <p class="note">榜单随回收/垃圾/碳减排数据实时聚合，无独立刷新任务</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-rank {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 900px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 48px;
  text-align: center;
}

.toolbar {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 24px;
}

.btn-primary {
  padding: 10px 28px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: var(--df-color-primary);
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) { opacity: 0.9; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.btn-plain {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 28px;
  font-size: 15px;
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

.card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.hero {
  text-align: center;
  background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
  color: #fff;

  &__num {
    font-size: 32px;
    font-weight: 700;
  }

  &__label {
    margin-top: 8px;
    font-size: 14px;
    opacity: 0.85;
  }
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--df-border-color);
  transition: all 0.3s ease;

  &:last-child { border-bottom: none; }

  &--mine {
    background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent);
    border-radius: 10px;
    padding-left: 12px;
    padding-right: 12px;
  }

  &__left {
    display: flex;
    align-items: center;
    gap: 16px;
    min-width: 0;
  }

  &__info { min-width: 0; }

  &__area {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 500;
    color: var(--df-text-primary);
  }

  &__mine-tag {
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 12px;
    color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
    border: 1px solid var(--df-color-primary);
  }

  &__metrics {
    margin-top: 4px;
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &__score {
    text-align: center;
    flex-shrink: 0;
    margin-left: 16px;
  }

  &__score-num {
    font-size: 24px;
    font-weight: 700;
    color: var(--df-color-gold);
  }

  &__score-label {
    font-size: 12px;
    color: var(--df-text-secondary);
  }
}

.pc-rank__medal {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: transparent;
  color: var(--df-text-secondary);
  font-size: 15px;
  font-weight: 600;
  flex-shrink: 0;

  &--gold { background: #ffe9a8; color: #b8860b; }
  &--silver { background: #e8ecf0; color: #7f8c9b; }
  &--bronze { background: #f3d9c2; color: #b0713a; }
}

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.note {
  margin: 8px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--df-text-secondary);
}
</style>
