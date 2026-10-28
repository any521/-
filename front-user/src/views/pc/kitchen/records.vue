<script setup lang="ts">
// PC 垃圾投放记录（LoopFill B4 G3）：个人累计卡 + 投放记录分页
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  pageKitchenReports, getKitchenMyStats,
  KITCHEN_STATUS_TEXT,
  type KitchenRecordItem, type KitchenMyStats,
} from '@/api/business'

defineOptions({ name: 'pc-kitchen-records' })

const router = useRouter()

const stats = ref<KitchenMyStats | null>(null)
const list = ref<KitchenRecordItem[]>([])
const loading = ref(false)
const finished = ref(false)
let pageNum = 1

async function loadMore() {
  loading.value = true
  try {
    const page = await pageKitchenReports({ pageNum, pageSize: 10 })
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } finally {
    loading.value = false
  }
}

/** 状态 tag 类型：0 待审核 warn / 1 已通过 ok / 2 已复核 info / 3 已驳回 danger */
function statusClass(s: number) {
  if (s === 0) return 'status--warn'
  if (s === 2) return 'status--info'
  if (s === 3) return 'status--danger'
  return 'status--ok'
}

onMounted(async () => {
  try {
    stats.value = await getKitchenMyStats()
  } catch {
    // 累计卡失败静默降级，列表仍可加载
  }
  loadMore()
})
</script>

<template>
  <div class="pc-records">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">垃圾投放记录</h1>
      </div>

      <!-- 个人累计卡 -->
      <div class="hero-card">
        <div class="hero-item">
          <div class="hero-num">{{ stats?.totalBags ?? 0 }}</div>
          <div class="hero-label">累计投放（袋）</div>
        </div>
        <div class="hero-item">
          <div class="hero-num">{{ stats?.todayBags ?? 0 }}</div>
          <div class="hero-label">今日（袋）</div>
        </div>
        <div class="hero-item">
          <div class="hero-num" :class="{ 'hero-num--warn': (stats?.frozenReports ?? 0) > 0 }">
            {{ stats?.frozenReports ?? 0 }}
          </div>
          <div class="hero-label">待审核</div>
        </div>
        <div v-if="(stats?.frozenReports ?? 0) > 0" class="hero-warn">
          存在待审核投放单，工作人员审核通过后发放积分
        </div>
      </div>

      <!-- 投放记录 -->
      <div class="section-card">
        <h3 class="section-title">投放记录</h3>
        <div v-if="finished && list.length === 0" class="empty">暂无记录</div>
        <div v-for="r in list" :key="r.id" class="record-row">
          <div class="record-main">
            <div class="record-title">
              {{ r.pointName }}
              <span class="record-status" :class="statusClass(r.status)">{{ KITCHEN_STATUS_TEXT[r.status] ?? '未知' }}</span>
            </div>
            <div class="record-desc">{{ r.bagCount }} 袋 · {{ r.reportTime }}</div>
          </div>
          <SvgIcon name="kitchen-leafy-green" :size="24" />
        </div>
        <button v-if="!finished" class="load-more" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
        <p v-else-if="list.length > 0" class="no-more">没有更多了</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-records {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container { max-width: 800px; margin: 0 auto; }

.header {
  display: flex; align-items: center; gap: 16px; margin-bottom: 48px;
}

.back-btn {
  display: flex; align-items: center; gap: 6px; padding: 8px 16px;
  font-size: 14px; color: var(--df-text-regular); background: var(--df-bg-card);
  border: 2px solid var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
}

.page-title { font-size: 32px; font-weight: 700; color: var(--df-text-primary); margin: 0; }

.hero-card {
  position: relative;
  display: flex; align-items: center; gap: 32px;
  background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
  border-radius: 16px; padding: 36px; margin-bottom: 24px; color: #fff;
}

.hero-item { flex: 1; text-align: center; }
.hero-num { font-size: 36px; font-weight: 700; }
.hero-num--warn { color: var(--df-color-gold); }
.hero-label { margin-top: 4px; font-size: 14px; opacity: 0.85; }
.hero-warn {
  position: absolute; right: 36px; bottom: 14px;
  font-size: 12px; color: var(--df-color-gold);
}

.section-card {
  background: var(--df-bg-card); border-radius: 16px; padding: 28px; margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.section-title { font-size: 18px; font-weight: 600; color: var(--df-text-primary); margin: 0 0 8px; }

.record-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 0; border-bottom: 1px solid var(--df-border-color);
  &:last-child { border-bottom: none; }
}

.record-title { font-size: 15px; font-weight: 500; color: var(--df-text-primary); }
.record-status { margin-left: 8px; font-size: 12px; font-weight: 400; }
.status--ok { color: var(--df-color-primary); }
.status--warn { color: var(--df-color-warning); }
.status--info { color: var(--df-color-carbon); }
.status--danger { color: var(--df-color-danger); }
.record-desc { margin-top: 4px; color: var(--df-text-secondary); font-size: 13px; }

.empty { text-align: center; padding: 60px 0; color: var(--df-text-secondary); font-size: 16px; }

.load-more {
  width: 100%; padding: 12px; margin-top: 16px; font-size: 15px; font-weight: 500;
  color: var(--df-color-primary); background: transparent;
  border: 2px solid var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover:not(:disabled) { border-color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.no-more { text-align: center; color: var(--df-text-secondary); font-size: 14px; margin: 16px 0 0; }
</style>
