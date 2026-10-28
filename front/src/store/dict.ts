// ============================================================
// 字典状态：按 dictType 缓存，DictSelect 组件消费
// ============================================================
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getDictByType, type DictItem } from '@/api/system'

export const useDictStore = defineStore('dict', () => {
  const cache = ref<Record<string, DictItem[]>>({})
  const pending = new Map<string, Promise<DictItem[]>>()

  /** 取字典（带缓存与并发合并） */
  async function getDict(type: string): Promise<DictItem[]> {
    const hit = cache.value[type]
    if (hit) return hit
    let p = pending.get(type)
    if (!p) {
      p = getDictByType(type).then((items) => {
        cache.value[type] = items
        pending.delete(type)
        return items
      })
      pending.set(type, p)
    }
    return p
  }

  /** 字典值转标签 */
  function labelOf(type: string, value: string | number): string {
    return cache.value[type]?.find((i) => i.value === String(value))?.label ?? String(value)
  }

  return { cache, getDict, labelOf }
})
