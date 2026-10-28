<script setup lang="ts">
// FormDialog（D1 通用组件）：表单弹窗壳，确认按钮带 loading
const visible = defineModel<boolean>({ required: true })

withDefaults(defineProps<{ title: string; width?: string; loading?: boolean }>(), {
  width: '560px',
  loading: false,
})

const emit = defineEmits<{ confirm: []; closed: [] }>()
</script>

<template>
  <el-dialog v-model="visible" :title="title" :width="width" destroy-on-close @closed="emit('closed')">
    <slot />
    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="loading" @click="emit('confirm')">确 定</el-button>
    </template>
  </el-dialog>
</template>
