<script setup lang="ts">
// 标签页（D1）：路由访问自动加标签，可关闭，关当前页跳相邻页
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/store/app'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

watch(
  () => route.path,
  () => appStore.addTag({ path: route.path, title: (route.meta.title as string) ?? '' }),
  { immediate: true },
)

function close(path: string) {
  const next = appStore.removeTag(path, route.path)
  if (next) router.push(next)
}
</script>

<template>
  <div class="tags-view">
    <router-link to="/dashboard" class="tags-view__item" :class="{ 'is-active': route.path === '/dashboard' }">
      工作台
    </router-link>
    <template v-for="tag in appStore.tags" :key="tag.path">
      <router-link
        v-if="tag.path !== '/dashboard'"
        :to="tag.path"
        class="tags-view__item"
        :class="{ 'is-active': route.path === tag.path }"
      >
        {{ tag.title }}
        <el-icon class="tags-view__close" @click.prevent.stop="close(tag.path)"><Close /></el-icon>
      </router-link>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.tags-view {
  display: flex;
  align-items: center;
  gap: $df-space-sm;
  height: 36px;
  padding: 0 $df-space-lg;
  background: var(--df-bg-card);
  border-bottom: 1px solid var(--df-border-color);
  overflow-x: auto;
  white-space: nowrap;

  &__item {
    display: inline-flex;
    align-items: center;
    gap: $df-space-xs;
    height: 24px;
    padding: 0 $df-space-sm;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-sm;
    font-size: $df-font-size-xs;
    color: var(--df-text-regular);
    text-decoration: none;

    &.is-active {
      background: var(--df-color-primary);
      border-color: var(--df-color-primary);
      color: #fff;
    }
  }

  &__close {
    font-size: 12px;
    border-radius: 50%;

    &:hover {
      background: rgba(0, 0, 0, 0.15);
    }
  }
}
</style>
