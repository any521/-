<script setup lang="ts">
// DictSelect（D1 通用组件）：字典下拉，数据走 useDictStore 缓存
import { onMounted, ref } from 'vue'
import { useDictStore } from '@/store/dict'
import type { DictItem } from '@/api/system'

const model = defineModel<string | number | undefined>()

const props = withDefaults(defineProps<{ dictType: string; placeholder?: string; width?: string }>(), {
  placeholder: '请选择',
  width: '180px',
})

const items = ref<DictItem[]>([])

onMounted(async () => {
  items.value = await useDictStore().getDict(props.dictType)
})
</script>

<template>
  <el-select v-model="model" :placeholder="placeholder" clearable :style="{ width }">
    <el-option v-for="item in items" :key="item.value" :label="item.label" :value="item.value" />
  </el-select>
</template>
