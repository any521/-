<script setup lang="ts">
// 垃圾投放记录（LoopFill B4 G3）：个人累计卡 + 投放记录分页（下拉刷新/加载更多）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  pageKitchenReports, getKitchenMyStats,
  KITCHEN_STATUS_TEXT,
  type KitchenRecordItem, type KitchenMyStats,
} from '@/api/business'

defineOptions({ name: 'kitchen-records' })

const router = useRouter()

const stats = ref<KitchenMyStats | null>(null)
const list = ref<KitchenRecordItem[]>([])
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
let pageNum = 1

async function loadMore() {
  loading.value = true
  try {
    const page = await pageKitchenReports({ pageNum, pageSize: 10 })
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  try {
    pageNum = 1
    finished.value = false
    const [s, page] = await Promise.all([
      getKitchenMyStats(),
      pageKitchenReports({ pageNum: 1, pageSize: 10 }),
    ])
    stats.value = s
    list.value = page.list
    finished.value = list.value.length >= page.total
    pageNum = 2
  } catch {
    // 刷新失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    refreshing.value = false
  }
}

/** 状态 tag 类型：0 待审核 warning / 1 已通过 success / 2 已复核 primary / 3 已驳回 danger */
function statusTagType(s: number) {
  if (s === 0) return 'warning'
  if (s === 2) return 'primary'
  if (s === 3) return 'danger'
  return 'success'
}

onMounted(async () => {
  try {
    stats.value = await getKitchenMyStats()
  } catch {
    // 累计卡失败静默降级，列表仍可加载
  }
})
</script>

<template>
  <div class="page records">
    <van-nav-bar title="垃圾投放记录" left-arrow @click-left="router.back()" />

    <!-- 个人累计卡 -->
    <div class="card records__hero">
      <div class="records__hero-item">
        <div class="records__hero-num">{{ stats?.totalBags ?? 0 }}</div>
        <div class="records__hero-label">累计投放（袋）</div>
      </div>
      <div class="records__hero-item">
        <div class="records__hero-num">{{ stats?.todayBags ?? 0 }}</div>
        <div class="records__hero-label">今日（袋）</div>
      </div>
      <div class="records__hero-item">
        <div class="records__hero-num" :class="{ 'records__hero-num--warn': (stats?.frozenReports ?? 0) > 0 }">
          {{ stats?.frozenReports ?? 0 }}
        </div>
        <div class="records__hero-label">待审核</div>
      </div>
    </div>

    <!-- 冻结提示 -->
    <van-notice-bar
      v-if="(stats?.frozenReports ?? 0) > 0"
      color="#f29d28"
      background="#fdf6e9"
      left-icon="warning-o"
      text="存在待审核投放单，工作人员审核通过后发放积分"
    />

    <!-- 投放记录 -->
    <div class="card">
      <div class="card__title">投放记录</div>
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
          <div v-for="r in list" :key="r.id" class="records__row">
            <div class="records__row-main">
              <div class="records__row-title">
                {{ r.pointName }}
                <van-tag :type="statusTagType(r.status)" plain>{{ KITCHEN_STATUS_TEXT[r.status] ?? '未知' }}</van-tag>
              </div>
              <div class="records__row-desc">{{ r.bagCount }} 袋 · {{ r.reportTime }}</div>
            </div>
            <div class="records__row-right">
              <SvgIcon name="kitchen-leafy-green" size="24" />
            </div>
          </div>
          <div v-if="finished && list.length === 0" class="records__empty">
            <SvgIcon name="kitchen-leafy-green" size="48" />
            <p>还没有投放记录，去投第一袋吧</p>
            <van-button size="small" round type="primary" plain @click="router.push('/kitchen/drop')">去投放</van-button>
          </div>
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.records {
  padding-top: 0;

  &__hero {
    display: flex;
    background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
    color: #fff;
  }

  &__hero-item {
    flex: 1;
    text-align: center;
  }

  &__hero-num {
    font-size: $df-font-size-xl;
    font-weight: 700;

    &--warn {
      color: var(--df-color-gold);
    }
  }

  &__hero-label {
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    opacity: 0.85;
  }

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $df-space-md 0;
    border-bottom: 1px solid var(--df-border-color);

    &:last-child {
      border-bottom: none;
    }
  }

  &__row-title {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
    font-size: $df-font-size-base;
    font-weight: 500;
  }

  &__row-desc {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__empty {
    padding: $df-space-xl 0;
    text-align: center;
    color: var(--df-text-secondary);

    p {
      margin: $df-space-sm 0 $df-space-md;
      font-size: $df-font-size-sm;
    }
  }
}

.van-notice-bar {
  margin-bottom: $df-space-md;
  border-radius: $df-radius-lg;
}
</style>
