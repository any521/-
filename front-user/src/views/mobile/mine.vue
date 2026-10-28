<script setup lang="ts">
// 我的：个人信息 / 适老化开关 / 常用入口 / 退出登录
// G6 焕新：渐变头部卡 + 玻璃 cell 组 + 积分金色点缀
// GapFill B1：常用功能追加 消息中心（未读角标）/ 反馈投诉 / 实名认证（状态文案）
// onsite01：工作人员模式三态卡片（G1 C-O1）+ 在线聊天挂载点（C-O1-4，onsite03 消费）
// LoopFill B5 创意 C1：环保树入口（今日未签到红点角标）
import { onMounted, onActivated, onBeforeUnmount, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { AvatarUploader } from '@shared'
import { useUserStore } from '@/store/user'
import { useUnreadStore } from '@/store/unread'
import { hasToken } from '@/api/token'
import { updateAvatar, updateNickname } from '@/api/auth'
import { getRealNameStatus, getEcoSignStatus } from '@/api/business'
import { getStaffApplyStatus, probeStaffApp, handoffToStaff, type StaffApplyStatus } from '@/api/staff'
import { getImUnreadTotal } from '@/api/im'
import { connect, onMessage as onWsMessage } from '@/composables/useImSocket'
import { applyThemeWithReveal, themeState, THEME_LABELS, THEME_MODES } from '@shared/theme'

defineOptions({ name: 'mine' })

const router = useRouter()
const userStore = useUserStore()
const unreadStore = useUnreadStore()

// 主题外观（两套：亮色/深色，共享响应式状态，与 PC 顶栏切换按钮实时同步）
const showThemeSheet = ref(false)
const themeActions = THEME_MODES.map((mode) => ({ name: THEME_LABELS[mode], mode }))
function onSelectTheme(action: { name: string }) {
  const mode = themeActions.find((a) => a.name === action.name)?.mode ?? 'light'
  applyThemeWithReveal(mode, window.innerWidth / 2, window.innerHeight / 2)
}

// 入口角标：未读消息数（与底栏共享 store）+ 实名状态文案 + 环保树未签到红点（B5）
const unreadCount = computed(() => unreadStore.count)
const realNameStatus = ref(0)
const REAL_NAME_TEXT: Record<number, string> = { 0: '未认证', 1: '待审核', 2: '已认证', 3: '未通过' }
/** 今日未签到（true 显示环保树入口红点） */
const notSignedToday = ref(false)
/** 会话恢复完成前为 false：渲染“恢复中”占位，避免返回/刷新时先闪“未登录”再变“已登录” */
const sessionReady = ref(false)

// 工作人员模式（G1，C-O1）：状态接口优先（含 reason），fallback profile.staffInfo
const staffMode = ref<StaffApplyStatus | null>(null)
const imUnread = ref(0)
const staffAuditStatus = computed(
  () => staffMode.value?.auditStatus ?? userStore.profile?.staffInfo?.auditStatus ?? -1,
)
const staffTypeText = computed(() => {
  const t = staffMode.value?.staffType ?? userStore.profile?.staffInfo?.staffType
  return t === 3 ? '维修技术员' : t === 2 ? '清运工' : t === 1 ? '回收员' : ''
})

async function loadEntries() {
  if (!hasToken()) return
  try {
    const [r, s, e] = await Promise.all([
      getRealNameStatus(), getStaffApplyStatus(), getEcoSignStatus(),
    ])
    realNameStatus.value = r.status
    staffMode.value = s
    notSignedToday.value = !e.todaySigned
    // 未读走共享 store，避免与底栏角标两套数据
    void unreadStore.refresh()
  } catch {
    // 入口数据失败静默降级，不影响页面主体
  }
}

/** onsite03：IM 未读角标（C-O1-4 挂载点接入，收到 MSG 帧即刷新） */
let offImWs: (() => void) | null = null
async function loadImUnread() {
  if (!hasToken()) return
  try {
    imUnread.value = await getImUnreadTotal()
  } catch {
    // 未读数失败静默降级
  }
}

/** C-O1 工作台切换：后端换取 STAFF 会话（双身份互通），sessionStorage 中转后免登录进入。 */
async function switchToStaff() {
  const ok = await probeStaffApp()
  if (!ok) {
    showToast('员工端服务未启动，请先启动员工端后重试')
    return
  }
  const handed = await handoffToStaff()
  if (!handed) {
    showToast('切换失败，请确认账号已通过工作人员审核')
    return
  }
  window.location.href = import.meta.env.BASE_URL + 'staff/'
}

async function openStaffWorkbench() {
  const ok = await probeStaffApp()
  if (!ok) {
    showToast('员工端服务未启动，请先启动员工端后重试')
    return
  }
  const handed = await handoffToStaff()
  if (!handed) {
    showToast('切换失败，请确认账号已通过工作人员审核')
    return
  }
  window.open(import.meta.env.BASE_URL + 'staff/', '_blank')
}

async function onLogout() {
  // 用户点「取消」会 reject，必须捕获避免 Unhandled Rejection 报错
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
  } catch {
    return
  }
  await userStore.logout()
  showSuccessToast('已退出')
  router.replace('/home')
}

/** Upgrade U1：头像上传成功 → 提交后端 → 刷新画像；失败由拦截器/Toast 兜底 */
async function onAvatarUploaded(url: string) {
  try {
    await updateAvatar(url)
    await userStore.fetchProfile()
    showSuccessToast('头像已更新')
  } catch {
    // 失败时请求层已 Toast，此处不再重复提示
  }
}

function onAvatarError(msg: string) {
  showToast(msg)
}

// n7：修改显示昵称（对话框输入 → 提交后端 → 刷新画像）
const showNicknameDialog = ref(false)
const nicknameDraft = ref('')
function openNicknameEditor() {
  nicknameDraft.value = userStore.profile?.nickname ?? ''
  showNicknameDialog.value = true
}
/** before-close：confirm 时提交保存，成功才允许关闭；取消直接关 */
async function onNicknameBeforeClose(action: string) {
  if (action !== 'confirm') return true
  const name = nicknameDraft.value.trim()
  if (!name) {
    showToast('昵称不能为空')
    return false
  }
  try {
    await updateNickname(name)
    await userStore.fetchProfile()
    showSuccessToast('昵称已更新')
    return true
  } catch {
    // 失败时请求层已 Toast，保留输入不关闭
    return false
  }
}

onMounted(async () => {
  // 刷新/返回后内存 Token 丢失：用 Refresh Cookie 静默恢复会话（避免个人中心刷新即"退出登录"）
  // 恢复完成前保持 sessionReady=false，登录态区域渲染"恢复中"占位，不与导航栏产生时序差
  const restored = await userStore.restoreSession()
  sessionReady.value = true
  if (restored) {
    loadEntries()
    loadImUnread()
  }
  // onsite03：IM 连接 + 未读订阅（挂载点，C-O1-4）
  connect()
  offImWs = onWsMessage(() => loadImUnread())
})

onBeforeUnmount(() => {
  offImWs?.()
})

// KeepAlive 恢复自愈：切回时刷新入口角标/实名/签到状态（含上次失败重试）；首次挂载由 onMounted 处理
onActivated(() => {
  if (sessionReady.value) {
    loadEntries()
    loadImUnread()
  }
})
</script>

<template>
  <div class="page mine">
    <!-- 读者名片卡（报刊式渐变卡 + 邮票贴纸） -->
    <div class="card mine__profile" @click="sessionReady && !hasToken() && router.push({ path: '/login', query: { redirect: '/mine' } })">
      <template v-if="sessionReady">
        <AvatarUploader
          v-if="hasToken()"
          :model-value="userStore.profile?.avatar ?? ''"
          :size="56"
          @uploaded="onAvatarUploaded"
          @error="onAvatarError"
        />
        <div v-else class="mine__avatar"><SvgIcon name="nav-user" /></div>
        <div v-if="hasToken()" class="mine__info">
          <div class="mine__name font-serif">
            {{ userStore.profile?.nickname || '环保达人' }}
            <van-icon v-if="hasToken()" name="edit" class="mine__edit" @click.stop="openNicknameEditor" />
          </div>
          <div class="mine__phone">{{ userStore.profile?.email || '点废成金用户' }}</div>
        </div>
        <div v-else class="mine__info">
          <div class="mine__name font-serif">点击登录</div>
          <div class="mine__phone">登录后体验完整功能</div>
        </div>
      </template>
      <template v-else>
        <div class="mine__avatar"><SvgIcon name="nav-user" /></div>
        <div class="mine__info">
          <div class="mine__name font-serif">会话恢复中…</div>
          <div class="mine__phone">请稍候</div>
        </div>
      </template>
      <span class="paper-stamp mine__stamp">{{ hasToken() ? '环保\n达人' : '新\n读者' }}</span>
    </div>

    <!-- n7：修改昵称对话框 -->
    <van-dialog
      v-model:show="showNicknameDialog"
      title="修改昵称"
      show-cancel-button
      :before-close="onNicknameBeforeClose"
    >
      <van-field
        v-model="nicknameDraft"
        maxlength="30"
        clearable
        placeholder="请输入昵称（1-30 字符）"
      />
    </van-dialog>

    <!-- 通用设置（G6：移除虚假「家庭碳账户 · 0 人」展示，真实家庭账户规划中） -->
    <van-cell-group inset title="通用设置">
      <van-cell title="适老化大字体" icon="eye-o" center>
        <template #right-icon>
          <van-switch :model-value="userStore.elderMode" size="22" @update:model-value="userStore.toggleElderMode()" />
        </template>
      </van-cell>
      <van-cell
        title="主题外观"
        icon="setting-o"
        center
        :value="THEME_LABELS[themeState]"
        is-link
        @click="showThemeSheet = true"
      />
      <van-action-sheet v-model:show="showThemeSheet" :actions="themeActions" cancel-text="取消" @select="onSelectTheme" />
    </van-cell-group>

    <!-- 工作人员模式（G1，onsite01，C-O1 三态卡片：未申请→申请页 / 审核中→只读 / 已上岗→切换+工作台 / 已驳回→重新申请） -->
    <van-cell-group v-if="hasToken()" inset title="工作人员模式">
      <template v-if="staffAuditStatus === 1">
        <van-cell title="工作人员模式" icon="manager-o" :value="staffTypeText" />
        <van-cell title="工作台" icon="briefcase-o" is-link @click="openStaffWorkbench" />
        <van-button block round type="primary" class="mine__staff-switch" @click="switchToStaff">切换工作人员模式</van-button>
      </template>
      <van-cell
        v-else-if="staffAuditStatus === 0"
        title="申请审核中"
        icon="clock-o"
        value="待审核"
        is-link
        @click="router.push('/staff/apply')"
      />
      <van-cell
        v-else-if="staffAuditStatus === 2"
        title="申请被驳回"
        icon="warning-o"
        :value="staffMode?.reason ? `已驳回 · ${staffMode.reason}` : '已驳回'"
        is-link
        @click="router.push('/staff/apply')"
      />
      <van-cell
        v-else
        title="申请成为工作人员"
        icon="manager-o"
        value="回收员 / 清运工 / 维修技术员"
        is-link
        @click="router.push('/staff/apply')"
      />
    </van-cell-group>

    <!-- 常用功能 -->
    <van-cell-group inset title="常用功能">
      <van-cell title="我的订单" icon="orders-o" is-link @click="router.push('/orders')" />
      <!-- 积分市场（积分商城 × 跳蚤市场合并）：徽章兑换 + 闲置好物 C2C 积分交易 -->
      <van-cell title="积分市场" icon="nav-shopping" is-link @click="router.push('/mall')" />
      <!-- LoopFill B5 创意 C1：环保树入口（今日未签到红点角标） -->
      <van-cell
        title="环保树"
        icon="carbon-tree"
        is-link
        :badge="notSignedToday ? { color: 'danger', content: '' } : undefined"
        @click="router.push('/eco/tree')"
      />
      <van-cell title="垃圾投放记录" icon="recycle-kitchen" is-link @click="router.push('/kitchen/records')" />
      <van-cell title="碳证书" icon="nav-link" is-link @click="router.push('/carbon/certificate')" />
      <!-- LoopFill B6 创意 C2：变废为宝社区 + 片区环保排行榜 -->
      <van-cell title="变废为宝社区" icon="bulb-o" is-link @click="router.push('/community')" />
      <van-cell title="环保排行榜" icon="award-o" is-link @click="router.push('/community/rank')" />
      <van-cell title="低碳账单" icon="chart-trending-o" is-link @click="router.push('/carbon/bill')" />
      <van-cell title="语音碳管家" icon="volume-o" is-link @click="router.push('/voice-steward')" />
      <van-cell title="科普专栏" icon="bookmark-o" is-link @click="router.push('/science')" />
      <van-cell title="消息中心" icon="bell-o" is-link :badge="(unreadCount + imUnread) || undefined" @click="router.push('/user/message')" />
      <!-- onsite03 合并：聊天已并入消息中心（统一入口 + 未读红点），不再单列在线聊天 -->
      <van-cell title="反馈投诉" icon="service-o" is-link @click="router.push('/user/complaint')" />
      <van-cell
        title="实名认证"
        icon="user-circle-o"
        is-link
        :value="REAL_NAME_TEXT[realNameStatus] ?? '未认证'"
        @click="router.push('/user/realname')"
      />
      <van-cell title="设置密码" icon="lock" is-link @click="router.push('/user/password')" />
      <!-- address-book：地址簿（省市区 + 定位，预约/收货统一取用） -->
      <van-cell title="地址簿" icon="location-o" is-link @click="router.push('/address-book')" />
    </van-cell-group>

    <div v-if="hasToken()" class="mine__logout">
      <van-button round block plain type="danger" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.mine {
  &__profile {
    display: flex;
    align-items: center;
    gap: $df-space-lg;
    background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
    color: #fff;
    border: none;
    border-radius: $df-radius-lg;
    position: relative;

    .mine__phone {
      color: rgba(255, 255, 255, 0.8);
    }
  }

  &__stamp {
    margin-left: auto;
    border-color: rgba(255, 255, 255, 0.55);
    color: rgba(255, 255, 255, 0.92);
    background: rgba(255, 255, 255, 0.08);
  }

  &__avatar {
    width: 56px;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 32px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.2);
    flex-shrink: 0;
  }

  &__name {
    font-size: $df-font-size-md;
    font-weight: 700;
    letter-spacing: 0.03em;
  }

  &__edit {
    margin-left: $df-space-xs;
    font-size: 14px;
    vertical-align: 2px;
    opacity: 0.85;
  }

  &__phone {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__logout {
    margin: $df-space-xl $df-space-lg 0;
  }

  &__staff-switch {
    margin: $df-space-sm $df-space-md $df-space-md;
  }
}

.van-cell-group--inset {
  margin: $df-space-md 0 0;
}
</style>
