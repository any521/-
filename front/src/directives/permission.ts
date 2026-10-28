// ============================================================
// v-permission 按钮级权限指令（09_frontend §9.3）
// 用法：<el-button v-permission="'system:user:add'">新增</el-button>
// 权限码与后端接口注解一一对应，无权限时移除元素
// ============================================================
import type { App, Directive } from 'vue'
import { useUserStore } from '@/store/user'

const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    if (!binding.value) return
    const userStore = useUserStore()
    if (!userStore.hasPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', permission)
}
