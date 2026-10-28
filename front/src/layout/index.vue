<script setup lang="ts">
// 布局骨架（D1）：侧边栏（动态菜单）+ 顶栏 + 标签页 + 主内容区
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import Navbar from './Navbar.vue'
import TagsView from './TagsView.vue'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const activeMenu = computed(() => route.path)

// 侧栏头像首字（无头像时占位）
const avatarChar = computed(() => (userStore.nickname || '管').slice(0, 1))
</script>

<template>
  <div class="layout">
    <!-- 侧边栏：深墨绿渐变 + brand 框 + 圆角导航项（匹配 workbench-desktop 模板） -->
    <aside
      class="layout__aside"
      :class="{ 'is-collapsed': appStore.sidebarCollapsed }"
    >
      <div class="aside-brand">
        <div class="aside-brand__ava">
          <span>{{ avatarChar }}</span>
        </div>
        <div v-show="!appStore.sidebarCollapsed" class="aside-brand__txt">
          <h1>点废成金</h1>
          <p>管理后台 · 森林编辑部</p>
        </div>
      </div>

      <nav class="aside-nav">
        <div v-show="!appStore.sidebarCollapsed" class="aside-nav-sep">工作台</div>
        <a
          class="aside-navi"
          :class="{ 'is-active': activeMenu === '/dashboard' }"
          @click="$router.push('/dashboard')"
        >
          <el-icon><HomeFilled /></el-icon>
          <span v-show="!appStore.sidebarCollapsed">工作台</span>
        </a>

        <template v-for="dir in permissionStore.menus" :key="dir.id">
          <div v-show="!appStore.sidebarCollapsed" class="aside-nav-sep">{{ dir.title }}</div>
          <a
            v-for="item in dir.children"
            :key="item.id"
            class="aside-navi"
            :class="{ 'is-active': activeMenu === `${dir.path}/${item.path}` }"
            @click="$router.push(`${dir.path}/${item.path}`)"
          >
            <el-icon><component :is="item.icon || 'Document'" /></el-icon>
            <span v-show="!appStore.sidebarCollapsed">{{ item.title }}</span>
          </a>
        </template>
      </nav>

      <div class="aside-foot">
        <span v-show="!appStore.sidebarCollapsed">© 点废成金 · 环保编辑部</span>
      </div>
    </aside>

    <div class="layout__body">
      <header class="layout__header">
        <Navbar />
      </header>
      <TagsView />
      <main class="layout__main">
        <!-- 页面缓存：标签页切换保留状态 -->
        <router-view v-slot="{ Component }">
          <keep-alive :max="12">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.layout {
  height: 100%;
  display: flex;
  background: transparent;
}

// ---------- 侧边栏（深墨绿渐变 + brand 框） ----------
.layout__aside {
  background: linear-gradient(180deg, var(--df-bg-dark-top), var(--df-bg-dark));
  color: var(--df-drawer-text);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  width: var(--df-sidebar-w);
  height: 100vh;
  z-index: 30;
  transition: width 0.22s ease;
  overflow: hidden;

  &.is-collapsed {
    width: var(--df-sidebar-w-collapsed);
  }
}

.aside-brand {
  padding: 24px 20px 18px;
  display: flex;
  align-items: center;
  gap: 13px;

  &__ava {
    position: relative;
    width: 46px;
    height: 46px;
    border-radius: 13px;
    overflow: hidden;
    flex: 0 0 auto;
    background: var(--df-drawer-hover);
    box-shadow: 0 0 0 1px var(--df-drawer-active);
    display: grid;
    place-items: center;
    color: var(--df-drawer-text);
    font-family: var(--df-font-serif);
    font-size: 18px;
    font-weight: 800;
  }

  &__txt {
    min-width: 0;

    h1 {
      margin: 0;
      font-size: 17px;
      font-weight: 800;
      letter-spacing: -0.01em;
      font-family: var(--df-font-serif);
      color: var(--df-drawer-text);
    }

    p {
      margin: 3px 0 0;
      font-size: 11px;
      opacity: 0.5;
      letter-spacing: 0.05em;
    }
  }
}

.aside-nav {
  flex: 1;
  overflow-y: auto;
  padding: 6px 12px 12px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--df-drawer-text-mute);
    border-radius: 6px;
  }
}

.aside-nav-sep {
  font-size: 10.5px;
  opacity: 0.42;
  padding: 15px 14px 6px;
  letter-spacing: 0.1em;
  color: var(--df-drawer-text);
}

.aside-navi {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: 11px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: color-mix(in srgb, var(--df-drawer-text) 82%, transparent);
  transition: background 0.15s ease;
  margin-bottom: 2px;
  user-select: none;

  &:hover {
    background: var(--df-drawer-hover);
  }

  &.is-active {
    background: var(--df-drawer-active);
    color: var(--df-drawer-text);
    font-weight: 700;
  }

  .el-icon {
    color: var(--df-drawer-text-mute);
    flex: 0 0 auto;
    font-size: 16px;
  }

  &.is-active .el-icon {
    color: var(--df-drawer-text);
  }
}

.aside-foot {
  padding: 15px 20px;
  font-size: 11px;
  opacity: 0.42;
  border-top: 1px solid var(--df-drawer-hover);
  color: var(--df-drawer-text);
}

// ---------- 主体区 ----------
.layout__body {
  flex: 1;
  min-width: 0;
  margin-left: var(--df-sidebar-w);
  transition: margin-left 0.22s ease;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: transparent;
}

.layout__aside.is-collapsed + .layout__body {
  margin-left: var(--df-sidebar-w-collapsed);
}

.layout__header {
  display: flex;
  align-items: center;
  background: var(--df-bg-card);
  border-bottom: 1px solid var(--df-border-color);
  padding: 0 var(--df-space-xl);
  height: var(--df-topbar-h);
  flex: 0 0 auto;
}

.layout__main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: transparent;
}
</style>
