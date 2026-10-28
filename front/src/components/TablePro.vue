<script setup lang="ts" generic="T extends Record<string, any>">
// ============================================================
// TablePro（D1 通用组件）：查询表单 + 分页表格一体化
// 用法：传 fetcher（分页接口），#search 放查询项，默认插槽放列
// ============================================================
import { onMounted, reactive, ref } from 'vue'
import type { PageQuery, PageResult } from '@shared/http/types'

const props = withDefaults(
  defineProps<{
    fetcher: (q: PageQuery) => Promise<PageResult<T>>
    /** 查询初始值（重置时恢复） */
    initQuery?: Record<string, unknown>
    immediate?: boolean
    /** 是否渲染内置分页条（大屏等自定义分页场景可关闭，配合 onLoaded 外部同步 total） */
    pagination?: boolean
    /** 是否渲染内置查询表单（外部自定义查询区可关闭，配合外部绑定 query/search/reset） */
    search?: boolean
    /** 每次加载完成回调（外部自定义分页同步 total 用） */
    onLoaded?: (total: number) => void
  }>(),
  { immediate: true, pagination: true, search: true },
)

defineOptions({ inheritAttrs: false })

// 插槽类型：query 放宽为 any 索引，便于页面 v-model 绑查询项
defineSlots<{
  default?: () => unknown
  search?: (props: { query: Record<string, any> }) => unknown
  toolbar?: (props: { refresh: () => void }) => unknown
}>()

const query = reactive<PageQuery>({ pageNum: 1, pageSize: 10, ...props.initQuery })
const rows = ref<T[]>([]) as import('vue').Ref<T[]>
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await props.fetcher({ ...query })
    rows.value = res.list
    // 后端全局 Jackson 规则 Long→String（防雪花 ID 精度丢失），total 需转数字（el-pagination 要求 Number）
    total.value = Number(res.total)
    props.onLoaded?.(total.value)
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  for (const key of Object.keys(query)) {
    if (key !== 'pageNum' && key !== 'pageSize') delete query[key]
  }
  Object.assign(query, props.initQuery)
  search()
}

onMounted(() => {
  if (props.immediate) load()
})

defineExpose({ refresh: load, search, reset, query })
</script>

<template>
  <div class="app-card table-pro">
    <el-form v-if="$slots.search && props.search" inline class="table-pro__search" @submit.prevent="search">
      <slot name="search" :query="query" />
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">查询</el-button>
        <el-button icon="Refresh" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div v-if="$slots.toolbar" class="table-pro__toolbar">
      <slot name="toolbar" :refresh="load" />
    </div>
    <!-- 弹性占满剩余高度：父级高度确定时满屏（无整页滚动），不确定时自动回落内容高度 -->
    <el-table v-loading="loading" :data="rows" stripe height="100%" class="table-pro__table" v-bind="$attrs">
      <slot />
    </el-table>
    <el-pagination
      v-if="props.pagination"
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      class="table-pro__pagination"
      layout="total, sizes, prev, pager, next, jumper"
      :page-sizes="[10, 20, 50]"
      :total="total"
      @change="load"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.table-pro {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  &__search,
  &__toolbar {
    flex-shrink: 0;
  }

  &__toolbar {
    margin-bottom: $df-space-md;
  }

  &__table {
    flex: 1;
    min-height: 0;
  }

  &__pagination {
    flex-shrink: 0;
    margin-top: $df-space-lg;
    justify-content: flex-end;
  }
}
</style>
