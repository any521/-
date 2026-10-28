<script setup lang="ts">
// PC 端工作台：三类员工角色差异化视图
// 回收员/环卫员 → 路线导航+核验登记链路；技术员 → 维修报告/配件登记链路
// 功能入口按 role 过滤，今日概览统计来自接单台同源数据
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { OrderStatus } from '@shared/http/types'
import type { StaffRole } from '@shared/data/baseData'
import { getTodayOrders, type StaffOrder } from '@/api/business'
import { useStaffStore } from '@/store/staff'

defineOptions({ name: 'pc-work' })

const router = useRouter()
const staffStore = useStaffStore()

const orders = ref<StaffOrder[]>([])

/** 今日概览：待接 / 进行中 / 已完成 */
const stat = computed(() => ({
  pending: orders.value.filter((o) => o.status === OrderStatus.PENDING).length,
  doing: orders.value.filter(
    (o) => o.status === OrderStatus.ACCEPTED || o.status === OrderStatus.PROCESSING,
  ).length,
  finished: orders.value.filter((o) => o.status === OrderStatus.FINISHED).length,
}))

/** 进行中的第一单（技术员快捷进维修作业 / 回收环卫快捷进核验登记） */
const workingOrder = computed(() =>
  orders.value.find((o) => o.status === OrderStatus.ACCEPTED || o.status === OrderStatus.PROCESSING),
)

interface WorkEntry {
  icon: string
  label: string
  desc: string
  /** 仅这些角色可见；缺省全角色 */
  roles?: StaffRole[]
  onClick: () => void
}

/** 需要进行中工单的入口：无单时 toast 引导去接单 */
function requireWorking(go: (order: StaffOrder) => void) {
  return () => {
    if (!workingOrder.value) {
      showToast('暂无进行中的工单，请先去接单台接单')
      return
    }
    go(workingOrder.value)
  }
}

const entries = computed<WorkEntry[]>(() => [
  {
    icon: 'guide-o',
    label: '路线导航',
    desc: '今日上门顺序',
    roles: ['RECYCLER', 'SANITATION'],
    onClick: () => router.push('/pc/route'),
  },
  {
    icon: 'balance-list-o',
    label: '物资登记',
    desc: '核验重量录入',
    roles: ['RECYCLER', 'SANITATION'],
    onClick: requireWorking((o) => router.push(`/pc/orders/${o.id}/register`)),
  },
  {
    icon: 'records-o',
    label: '维修报告',
    desc: '支持语音口述',
    roles: ['TECHNICIAN'],
    onClick: requireWorking((o) => router.push(`/pc/repair/${o.id}/report`)),
  },
  {
    icon: 'setting-o',
    label: '配件登记',
    desc: '维修用件记录',
    roles: ['TECHNICIAN'],
    onClick: requireWorking((o) => router.push(`/pc/repair/${o.id}/parts`)),
  },
  {
    icon: 'photo-o',
    label: '投放审核',
    desc: '垃圾投放待审',
    roles: ['RECYCLER', 'SANITATION'],
    onClick: () => router.push('/pc/kitchen/audit'),
  },
  {
    icon: 'warning-o',
    label: '异常上报',
    desc: '联系不上/货不符',
    onClick: () => router.push('/pc/abnormal'),
  },
  {
    icon: 'chart-trending-o',
    label: '我的绩效',
    desc: '单量/重量/排名',
    onClick: () => router.push('/pc/performance'),
  },
  {
    icon: 'chat-o',
    label: '在线聊天',
    desc: '与用户沟通',
    onClick: () => router.push('/pc/im'),
  },
])

const visibleEntries = computed(() =>
  entries.value.filter((e) => !e.roles || e.roles.includes(staffStore.role)),
)

onMounted(async () => {
  if (!staffStore.profile) await staffStore.fetchProfile()
  staffStore.loadAreas()
  orders.value = await getTodayOrders()
})

// KeepAlive 缓存页（App.vue include）：完成登记/接单后返回重新拉取，概览统计与快捷卡即时更新
let firstActivate = true
onActivated(async () => {
  if (firstActivate) {
    firstActivate = false
    return
  }
  orders.value = await getTodayOrders()
})
</script>

<template>
  <div class="pc-page work">
    <div class="pc-page__header">
      <h1 class="pc-page__title">工作台</h1>
      <p class="pc-page__sub">{{ staffStore.profile?.roleName ?? '员工' }} · {{ staffStore.myAreaName }}</p>
    </div>

    <!-- 今日概览 -->
    <div class="pc-card work__stat">
      <div class="work__stat-item" @click="router.push('/pc/tasks')">
        <div class="work__num">{{ stat.pending }}</div>
        <div class="work__label">待接单</div>
      </div>
      <div class="work__stat-item" @click="router.push('/pc/tasks')">
        <div class="work__num work__num--doing">{{ stat.doing }}</div>
        <div class="work__label">进行中</div>
      </div>
      <div class="work__stat-item" @click="router.push('/pc/tasks')">
        <div class="work__num work__num--done">{{ stat.finished }}</div>
        <div class="work__label">已完成</div>
      </div>
    </div>

    <!-- 进行中工单快捷卡 -->
    <div v-if="workingOrder" class="pc-card work__doing" @click="router.push(`/pc/orders/${workingOrder.id}`)">
      <van-icon name="logistics" size="22" color="#2f6b5e" />
      <div class="work__doing-info">
        <div class="work__doing-title">{{ workingOrder.title }}</div>
        <div class="work__doing-meta">{{ workingOrder.address }} · {{ workingOrder.appointTime }}</div>
      </div>
      <van-icon name="arrow" color="#c8c9cc" />
    </div>

    <!-- 角色差异化功能入口 -->
    <div class="pc-card">
      <div class="work__entries-title">{{ staffStore.profile?.roleName ?? '员工' }}工作台</div>
      <div class="work__grid">
        <div
          v-for="e in visibleEntries"
          :key="e.label"
          class="work__entry"
          @click="e.onClick"
        >
          <van-icon :name="e.icon" size="28" color="#2f6b5e" />
          <div class="work__entry-label">{{ e.label }}</div>
          <div class="work__entry-desc">{{ e.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.work {
  &__stat {
    display: flex;
    justify-content: space-around;
    text-align: center;
  }

  &__stat-item {
    cursor: pointer;
    padding: $df-space-sm $df-space-xl;
    border-radius: $df-radius-md;
    transition: background 0.25s ease;

    &:hover {
      background: var(--df-fill-color-light);
    }
  }

  &__num {
    font-size: 32px;
    font-weight: 700;
    color: var(--df-color-warning);

    &--doing { color: var(--df-color-primary); }
    &--done { color: var(--df-text-secondary); }
  }

  &__label {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__doing {
    display: flex;
    align-items: center;
    gap: $df-space-md;
    cursor: pointer;
    transition: all 0.25s ease;

    &:hover {
      box-shadow: var(--df-shadow-float);
    }
  }

  &__doing-info {
    flex: 1;
    min-width: 0;
  }

  &__doing-title {
    font-weight: 600;
    font-size: $df-font-size-md;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__doing-meta {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__entries-title {
    font-size: $df-font-size-md;
    font-weight: 600;
    margin-bottom: $df-space-lg;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: $df-space-md;
  }

  &__entry {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: $df-space-xl $df-space-md;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-lg;
    cursor: pointer;
    transition: all 0.25s ease;

    &:hover {
      border-color: var(--df-color-primary);
      box-shadow: var(--df-shadow-card);
      transform: translateY(-2px);
    }
  }

  &__entry-label {
    font-size: $df-font-size-base;
    font-weight: 600;
  }

  &__entry-desc {
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }
}
</style>
