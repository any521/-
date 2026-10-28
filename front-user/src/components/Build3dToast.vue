<script setup lang="ts">
// 全局 3D 构建任务浮窗（右下角）：
// 路由切换后仍可见——进行中显示进度条；完成可点击跳回回收页查看模型；失败提示已回退预设模型
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useBuild3dStore } from '@/store/build3d'

const store = useBuild3dStore()
const router = useRouter()
const route = useRoute()

const visible = ref(false)
let hideTimer: number | undefined

watch(
  () => store.status,
  (s) => {
    if (store.running) {
      visible.value = true
      clearTimeout(hideTimer)
    } else if (s === 'DONE' || s === 'FAILED') {
      visible.value = true
      clearTimeout(hideTimer)
      // 完成停留 6s 供点击查看；失败停留 4s 自动收起
      hideTimer = window.setTimeout(() => {
        visible.value = false
      }, s === 'DONE' ? 6000 : 4000)
    } else {
      visible.value = false
    }
  },
)

/** 完成态点击：跳回回收预约页查看 AI 构建的模型 */
function goView() {
  const name = route.path.startsWith('/pc') ? 'pc-recycle-book' : 'mobile-recycle-book'
  router.push({ name })
  visible.value = false
}
</script>

<template>
  <div
    v-if="visible"
    class="build3d-toast"
    :class="{ 'is-done': store.status === 'DONE', 'is-failed': store.status === 'FAILED' }"
    @click="store.status === 'DONE' && goView()"
  >
    <template v-if="store.running">
      <div class="build3d-toast__head">
        <span class="build3d-toast__spinner" />
        <span>{{ store.stageText }}</span>
      </div>
      <div class="build3d-toast__bar">
        <i :style="{ width: store.progress + '%' }" />
      </div>
    </template>

    <template v-else-if="store.status === 'DONE'">
      <div class="build3d-toast__icon">
        <SvgIcon v-if="store.spec" name="nav-check-mark" :size="18" />
        <SvgIcon v-else name="nav-hourglass" :size="18" />
      </div>
      <div class="build3d-toast__text">
        {{ store.spec ? '3D 模型已生成，点击查看' : '3D 构建未完成，已用预设模型' }}
      </div>
    </template>

    <template v-else-if="store.status === 'FAILED'">
      <div class="build3d-toast__icon">
        <SvgIcon name="nav-cross-mark" :size="18" />
      </div>
      <div class="build3d-toast__text">3D 构建失败，已使用预设模型</div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.build3d-toast {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 240px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--df-card-bg);
  border: 1px solid var(--df-border-color);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.18);
  font-size: 13px;
  color: var(--df-text-primary);
  cursor: default;
  animation: build3d-slide-in 0.25s ease;

  &.is-done {
    cursor: pointer;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
  }

  &__spinner {
    width: 14px;
    height: 14px;
    border: 2px solid var(--df-primary);
    border-top-color: transparent;
    border-radius: 50%;
    animation: build3d-spin 0.8s linear infinite;
  }

  &__bar {
    margin-top: 8px;
    height: 4px;
    border-radius: 2px;
    background: var(--df-border-color);
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      border-radius: 2px;
      background: var(--df-primary);
      transition: width 0.4s ease;
    }
  }

  &__icon {
    color: var(--df-primary);

    .is-failed & {
      color: var(--df-danger, #ee4d38);
    }
  }
}

@keyframes build3d-slide-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes build3d-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
