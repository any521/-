<script setup lang="ts">
// 我的排班（B3，对接 StaffOpsController /staff/ops/schedules）：本周快捷范围 + 班次列表
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showLoadingToast } from 'vant'
import { mySchedules, type ScheduleItem } from '@/api/business'

defineOptions({ name: 'schedule' })

const router = useRouter()

const SHIFT_TEXT: Record<number, string> = { 1: '白班', 2: '晚班' }
const BIZ_TEXT: Record<number, string> = { 0: '正常', 1: '节假日', 2: '跨片区支援' }

/** 本周一（周日视为本周最后一天） */
function mondayOf(date: Date): string {
  const d = new Date(date)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d.toISOString().slice(0, 10)
}

const dateFrom = ref(mondayOf(new Date()))
const dateTo = ref(mondayOf(new Date(new Date().getTime() + 6 * 86400000)))

const list = ref<ScheduleItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await mySchedules(dateFrom.value, dateTo.value)
  } finally {
    loading.value = false
  }
}

function onThisWeek() {
  const monday = mondayOf(new Date())
  dateFrom.value = monday
  dateTo.value = mondayOf(new Date(new Date().getTime() + 6 * 86400000))
  load()
}

onMounted(load)
</script>

<template>
  <div class="page schedule">
    <van-nav-bar title="我的排班" left-arrow @click-left="router.back()" />

    <div class="card schedule__range">
      <div class="schedule__range-text">
        {{ dateFrom }} ~ {{ dateTo }}
      </div>
      <van-button size="small" round plain type="primary" @click="onThisWeek">本周</van-button>
    </div>

    <van-loading v-if="loading" class="schedule__loading" vertical>加载中…</van-loading>

    <template v-else>
      <div v-for="item in list" :key="item.id" class="card schedule__item">
        <div class="schedule__item-date">
          {{ item.workDate }}
          <van-tag :type="item.shiftType === 1 ? 'primary' : 'warning'" round>
            {{ SHIFT_TEXT[item.shiftType] ?? `班次${item.shiftType}` }}
          </van-tag>
        </div>
        <div class="schedule__item-meta">
          <van-icon name="location-o" />
          {{ item.bizType === 2 && item.supportAreaId ? `支援片区 ${item.supportAreaId}` : `片区 ${item.areaId}` }}
          <van-tag v-if="item.bizType !== 0" plain type="default">
            {{ BIZ_TEXT[item.bizType] ?? item.bizType }}
          </van-tag>
        </div>
        <div v-if="item.remark" class="schedule__item-remark">{{ item.remark }}</div>
      </div>
      <van-empty v-if="list.length === 0" description="本周暂无排班" />
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.schedule {
  &__range {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $df-space-md;
  }

  &__range-text {
    color: var(--df-text-primary);
    font-size: $df-font-size-md;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  &__loading {
    margin-top: $df-space-xl;
  }

  &__item {
    margin-bottom: $df-space-md;
  }

  &__item-date {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: $df-font-size-md;
    font-weight: 600;
  }

  &__item-meta {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: $df-space-sm;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__item-remark {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }
}
</style>
