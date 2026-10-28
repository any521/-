// ============================================================
// 应用状态：侧边栏折叠 / 标签页（TagsView）
// ============================================================
import { ref } from 'vue'
import { defineStore } from 'pinia'

export interface TagItem {
  path: string
  title: string
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const tags = ref<TagItem[]>([])

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function addTag(tag: TagItem) {
    if (!tag.title || tag.path === '/login') return
    if (!tags.value.some((t) => t.path === tag.path)) tags.value.push(tag)
  }

  /** 关闭标签，返回关闭后应跳转的路径（关当前页时跳相邻页） */
  function removeTag(path: string, currentPath: string): string | null {
    const idx = tags.value.findIndex((t) => t.path === path)
    if (idx === -1) return null
    tags.value.splice(idx, 1)
    if (path !== currentPath) return null
    const next = tags.value[idx] ?? tags.value[idx - 1]
    return next?.path ?? '/'
  }

  return { sidebarCollapsed, tags, toggleSidebar, addTag, removeTag }
})
