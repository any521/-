// ============================================================
// 权限路由状态（09_frontend §9.3 动态路由方案）
// 登录后拉取菜单 → 映射 views 组件 → router.addRoute 注册
// ============================================================
import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { RouteRecordRaw } from 'vue-router'
import { getMenus, type MenuNode } from '@/api/auth'

// views 组件表：菜单 component 字段（如 system/user）→ 懒加载组件（D4 路由懒加载）
const viewModules = import.meta.glob('@/views/**/*.vue')

function resolveComponent(component: string) {
  // 菜单惯例：平级 xxx.vue；科普等子目录页面回退 xxx/index.vue
  return viewModules[`/src/views/${component}.vue`] ?? viewModules[`/src/views/${component}/index.vue`]
}

/** 菜单树 → 路由记录（挂到 Layout 之下） */
function toRoutes(menus: MenuNode[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  for (const dir of menus) {
    for (const item of dir.children ?? []) {
      const comp = item.component && resolveComponent(item.component)
      if (!comp) continue
      routes.push({
        path: `${dir.path}/${item.path}`,
        name: item.component,
        component: comp,
        meta: { title: item.title, icon: item.icon },
      })
    }
  }
  return routes
}

export const usePermissionStore = defineStore('permission', () => {
  /** 菜单树（侧边栏渲染用） */
  const menus = ref<MenuNode[]>([])
  /** 动态路由是否已注册（守卫据此判断） */
  const loaded = ref(false)

  async function generateRoutes(): Promise<RouteRecordRaw[]> {
    menus.value = await getMenus()
    loaded.value = true
    return toRoutes(menus.value)
  }

  function reset() {
    menus.value = []
    loaded.value = false
  }

  return { menus, loaded, generateRoutes, reset }
})
