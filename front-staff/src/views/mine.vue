<script setup lang="ts">
// 我的（Tab 3）：员工档案 + 快捷入口（打卡/绩效/异常记录）+ 退出登录
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { AvatarUploader } from '@shared'
import { STAFF_ROLES } from '@shared/data/baseData'
import { applyThemeWithReveal, themeState, THEME_LABELS, THEME_MODES } from '@shared/theme'
import { updateAvatar, handoffToUser } from '@/api/auth'
import { useStaffStore } from '@/store/staff'

defineOptions({ name: 'mine' })

const router = useRouter()
const staffStore = useStaffStore()

// 主题外观（两套：亮色/深色，共享响应式状态，与顶栏切换按钮实时同步）
const showThemeSheet = ref(false)
const themeActions = THEME_MODES.map((mode) => ({ name: THEME_LABELS[mode], mode }))
function onSelectTheme(action: { name: string }) {
  const mode = themeActions.find((a) => a.name === action.name)?.mode ?? 'light'
  applyThemeWithReveal(mode, window.innerWidth / 2, window.innerHeight / 2)
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
  router.replace('/login')
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

/** 双身份互通（C-O1）：先换回 USER 会话中转令牌再跳用户端，免重复登录
 * （BASE_URL=.../staff/，去尾缀即用户端；dev/nginx 同域反代） */
async function switchToClient() {
  const ok = await handoffToUser().catch(() => false)
  if (!ok) showToast('切换会话失败，跳转后可能需要重新登录')
  window.location.href = import.meta.env.BASE_URL.replace(/staff\/$/, '')
}

onMounted(async () => {
  if (!staffStore.profile) await staffStore.fetchProfile()
  staffStore.loadAreas()
})
</script>

<template>
  <div class="page mine">
    <!-- 员工档案卡（Upgrade U1：头像上传 + 展示，替换原固定 manager 图标） -->
    <div class="card mine__profile">
      <AvatarUploader
        :model-value="staffStore.profile?.avatar ?? ''"
        :size="56"
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
      </div>
    </div>

    <div class="card">
      <van-cell-group :border="false">
        <van-cell
          title="负责片区"
          icon="location-o"
          :value="staffStore.myAreaName"
          is-link
          @click="showAreaSheet = true"
        />
        <van-cell title="入职日期" icon="calendar-o" :value="staffStore.profile?.hireDate ?? '—'" />
        <van-cell
          title="主题外观"
          icon="setting-o"
          center
          :value="THEME_LABELS[themeState]"
          is-link
          @click="showThemeSheet = true"
        />
        <van-action-sheet v-model:show="showThemeSheet" :actions="themeActions" cancel-text="取消" @select="onSelectTheme" />
        <van-action-sheet
          v-model:show="showAreaSheet"
          :actions="areaActions"
          cancel-text="取消"
          @select="onSelectArea"
        />
      </van-cell-group>
    </div>

    <div class="card">
      <van-cell-group :border="false">
        <van-cell title="我的绩效" icon="chart-trending-o" is-link @click="router.push('/performance')" />
        <van-cell title="异常上报" icon="warning-o" is-link @click="router.push('/abnormal')" />
        <van-cell title="智能路线" icon="guide-o" is-link @click="router.push('/route')" />
      </van-cell-group>
    </div>

    <div class="card">
      <van-cell-group :border="false">
        <van-cell title="请假申请" icon="calendar-o" is-link @click="router.push('/leave')" />
        <van-cell title="我的排班" icon="clock-o" is-link @click="router.push('/schedule')" />
      </van-cell-group>
    </div>

    <van-button round block plain type="primary" class="mine__client" icon="exchange" @click="switchToClient">
      切换客户端
    </van-button>

    <van-button round block plain type="danger" @click="onLogout">退出登录</van-button>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.mine {
  &__profile {
    display: flex;
    align-items: center;
    gap: $df-space-lg;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__name {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    font-size: $df-font-size-lg;
    font-weight: 600;
  }

  &__meta {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__client {
    margin-bottom: $df-space-md;
  }
}
</style>
