<script setup lang="ts">
// PC 端我的：员工档案 + 快捷入口（打卡/绩效/异常记录）+ 退出登录
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { AvatarUploader } from '@shared'
import { STAFF_ROLES } from '@shared/data/baseData'
import { applyThemeWithReveal, themeState, THEME_LABELS, THEME_MODES, type ThemeMode } from '@shared/theme'
import { updateAvatar } from '@/api/auth'
import { useStaffStore } from '@/store/staff'

defineOptions({ name: 'pc-mine' })

const router = useRouter()
const staffStore = useStaffStore()

// 主题外观（两套：亮色/深色，共享响应式状态，与顶栏切换按钮实时同步）
function onSelectTheme(mode: ThemeMode, e: MouseEvent) {
  const rect = (e.currentTarget as HTMLElement | null)?.getBoundingClientRect()
  applyThemeWithReveal(
    mode,
    rect ? rect.left + rect.width / 2 : window.innerWidth / 2,
    rect ? rect.top + rect.height / 2 : window.innerHeight / 2,
  )
}

/** 角色徽标色：三类员工差异化标识 */
const roleTagType = (role?: string) =>
  role === 'TECHNICIAN' ? 'primary' : role === 'SANITATION' ? 'warning' : 'success'

// ---- 自助换片区（PUT /api/staff/me/area；列表来自 store 缓存） ----
const showAreaSheet = ref(false)
const areaActions = computed(() =>
  staffStore.areas.map((a) => ({ name: a.areaName, areaId: a.id })),
)
async function onSelectArea(action: { areaId: string }) {
  showAreaSheet.value = false
  if (!action.areaId || action.areaId === staffStore.myAreaId) return
  try {
    await staffStore.changeArea(action.areaId)
    showSuccessToast(`已切换到 ${staffStore.myAreaName}`)
  } catch {
    // 失败时请求层已 Toast（如片区已停用）
  }
}

async function onLogout() {
  await showConfirmDialog({ title: '退出登录', message: '确认退出当前账号？' })
  await staffStore.logout()
  router.replace('/pc/login')
}

/** Upgrade U1：头像上传成功 → 提交后端 → 刷新员工画像 */
async function onAvatarUploaded(url: string) {
  try {
    await updateAvatar(url)
    await staffStore.fetchProfile()
    showSuccessToast('头像已更新')
  } catch {
    // 失败时请求层已 Toast，此处不再重复提示
  }
}

function onAvatarError(msg: string) {
  showToast(msg)
}

onMounted(async () => {
  if (!staffStore.profile) await staffStore.fetchProfile()
  staffStore.loadAreas()
})
</script>

<template>
  <div class="pc-page mine">
    <div class="pc-page__header">
      <h1 class="pc-page__title">我的</h1>
      <p class="pc-page__sub">员工档案与个人设置</p>
    </div>

    <div class="mine__cols">
      <!-- 员工档案卡（Upgrade U1：头像上传 + 展示，替换原固定 manager 图标） -->
      <div class="pc-card mine__profile">
        <AvatarUploader
          :model-value="staffStore.profile?.avatar ?? ''"
          :size="72"
          @uploaded="onAvatarUploaded"
          @error="onAvatarError"
        />
        <div class="mine__info">
          <div class="mine__name">
            {{ staffStore.profile?.name ?? '—' }}
            <van-tag :type="roleTagType(staffStore.profile?.role)" round>
              {{ staffStore.profile?.roleName ?? STAFF_ROLES[0].name }}
            </van-tag>
          </div>
          <div class="mine__meta">
            工号 {{ staffStore.profile?.staffNo ?? '—' }} ·
            {{ staffStore.profile?.email ?? '—' }}
          </div>
          <div class="mine__rows">
            <div class="mine__row">
              <span>负责片区</span>
              <b class="mine__area" @click="showAreaSheet = true">
                {{ staffStore.myAreaName }}
                <van-icon name="arrow" size="14" />
              </b>
            </div>
            <div class="mine__row">
              <span>入职日期</span>
              <b>{{ staffStore.profile?.hireDate ?? '—' }}</b>
            </div>
          </div>
        </div>
      </div>

      <!-- 常用功能 -->
      <div class="pc-card mine__functions">
        <div class="mine__section-title">常用功能</div>
        <div class="mine__grid">
          <div class="mine__entry" @click="router.push('/pc/performance')">
            <van-icon name="chart-trending-o" size="22" color="#2f6b5e" />
            <span>我的绩效</span>
          </div>
          <div class="mine__entry" @click="router.push('/pc/abnormal')">
            <van-icon name="warning-o" size="22" color="#2f6b5e" />
            <span>异常上报</span>
          </div>
          <div class="mine__entry" @click="router.push('/pc/route')">
            <van-icon name="guide-o" size="22" color="#2f6b5e" />
            <span>智能路线</span>
          </div>
          <div class="mine__entry" @click="router.push('/pc/leave')">
            <van-icon name="calendar-o" size="22" color="#2f6b5e" />
            <span>请假申请</span>
          </div>
          <div class="mine__entry" @click="router.push('/pc/schedule')">
            <van-icon name="clock-o" size="22" color="#2f6b5e" />
            <span>我的排班</span>
          </div>
          <div class="mine__entry" @click="router.push('/pc/im')">
            <van-icon name="chat-o" size="22" color="#2f6b5e" />
            <span>在线聊天</span>
          </div>
        </div>

        <div class="mine__section-title">外观设置</div>
        <div class="mine__themes">
          <button
            v-for="mode in THEME_MODES"
            :key="mode"
            class="mine__theme"
            :class="{ 'is-active': themeState === mode }"
            @click="onSelectTheme(mode, $event)"
          >
            {{ THEME_LABELS[mode] }}
          </button>
        </div>

        <van-button round block plain type="danger" class="mine__logout" @click="onLogout">
          退出登录
        </van-button>
      </div>
    </div>

    <van-action-sheet
      v-model:show="showAreaSheet"
      :actions="areaActions"
      cancel-text="取消"
      @select="onSelectArea"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.mine {
  &__cols {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: $df-space-lg;
    align-items: start;
  }

  &__profile {
    display: flex;
    align-items: flex-start;
    gap: $df-space-xl;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__name {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    font-size: $df-font-size-xl;
    font-weight: 700;
  }

  &__meta {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__rows {
    margin-top: $df-space-lg;
    border-top: 1px solid var(--df-border-color);
  }

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $df-space-md 0;
    border-bottom: 1px solid var(--df-border-color);
    font-size: $df-font-size-sm;

    span {
      color: var(--df-text-secondary);
    }

    b {
      font-weight: 600;
    }
  }

  &__area {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: var(--df-color-primary);
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }

  &__section-title {
    font-size: $df-font-size-md;
    font-weight: 600;
    margin-bottom: $df-space-md;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: $df-space-md;
    margin-bottom: $df-space-xl;
  }

  &__entry {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: $df-space-lg $df-space-sm;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-lg;
    font-size: $df-font-size-sm;
    cursor: pointer;
    transition: all 0.25s ease;

    &:hover {
      border-color: var(--df-color-primary);
      box-shadow: var(--df-shadow-card);
      transform: translateY(-2px);
    }
  }

  &__themes {
    display: flex;
    gap: $df-space-sm;
    margin-bottom: $df-space-xl;
  }

  &__theme {
    flex: 1;
    padding: 8px 0;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-round;
    background: var(--df-bg-card);
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    cursor: pointer;
    transition: all 0.25s ease;

    &.is-active {
      background: var(--df-color-primary);
      border-color: var(--df-color-primary);
      color: #fff;
      font-weight: 600;
    }
  }
}
</style>
