<script setup lang="ts">
// PC 端布局：侧栏（PcNav）+ 暗色森林绿纸纹背景 + 内容区
// 配色与背景匹配用户提供的森林日报暗色截图：暖灰褐纹理底 + 深墨绿卡片/侧栏
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import PcNav from '@/components/PcNav.vue'
import HeroMascot from '@/components/HeroMascot.vue'
import { initLenis, destroyLenis } from '@/utils/lenis'

const route = useRoute()

// 首页自带完整 hero（pc/home.vue），其余内页由布局统一注入紧凑 hero，实现“每页都显示 hero”
const showInnerHero = computed(() => route.name !== 'pc-home' && Boolean(route.meta?.title))
const heroTitle = computed(() => (route.meta?.title as string) || '点废成金')

onMounted(() => {
  initLenis()
})

onUnmounted(() => {
  destroyLenis()
})
</script>

<template>
  <div class="pc-layout">
    <PcNav />

    <div class="pc-layout__content">
      <HeroMascot
        v-if="showInnerHero"
        class="pc-layout__hero"
        compact
        :title="heroTitle"
        subtitle="点废成金 · 森林日报"
        :height="120"
      />
      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.pc-layout {
  min-height: 100vh;
  position: relative;
  background: transparent;
}

.pc-layout__content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  margin-left: var(--df-sidebar-w);
  padding: 26px 34px 48px;
  background: transparent;
}

.pc-layout__hero {
  margin-bottom: 22px;
}

@media (max-width: 1024px) {
  .pc-layout__content {
    padding: 18px 18px 32px;
  }
}
</style>
