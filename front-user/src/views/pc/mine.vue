<script setup lang="ts">
// PC 个人中心：个人信息 / 适老化 / 常用入口
// GapFill B1：常用功能追加 消息中心（未读角标）/ 反馈投诉 / 实名认证（状态文案）
// onsite01：工作人员模式三态卡片（G1 C-O1）+ 在线聊天挂载点（C-O1-4，onsite03 消费）
// LoopFill B5 创意 C1：环保树入口（今日未签到红点角标）
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
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

defineOptions({ name: 'pc-mine' })

const router = useRouter()
const userStore = useUserStore()
const unreadStore = useUnreadStore()

// 入口角标：未读消息数（与侧栏共享 store）+ 实名状态文案 + 环保树未签到红点（B5）
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
    // 未读走共享 store，避免与侧栏角标两套数据
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
  await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
  await userStore.logout()
  showSuccessToast('已退出')
  router.replace('/pc/home')
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
</script>

<template>
  <div class="pc-mine">
    <div class="container">
      <h1 class="page-title">个人中心</h1>

      <div class="profile-card" @click="sessionReady && !hasToken() && router.push('/pc/login')">
        <template v-if="sessionReady">
          <AvatarUploader
            v-if="hasToken()"
            :model-value="userStore.profile?.avatar ?? ''"
            :size="72"
            @uploaded="onAvatarUploaded"
            @error="onAvatarError"
          />
          <div v-else class="profile-avatar">
            <SvgIcon name="nav-user" :size="48" />
          </div>
          <div v-if="hasToken()" class="profile-info">
            <div class="profile-name">
              {{ userStore.profile?.nickname || '环保达人' }}
              <van-icon name="edit" class="profile-name__edit" @click.stop="openNicknameEditor" />
            </div>
            <div class="profile-phone">{{ userStore.profile?.email || '点废成金用户' }}</div>
          </div>
          <div v-else class="profile-info">
            <div class="profile-name">点击登录</div>
            <div class="profile-phone">登录后体验完整功能</div>
          </div>
        </template>
        <template v-else>
          <div class="profile-avatar">
            <SvgIcon name="nav-user" :size="48" />
          </div>
          <div class="profile-info">
            <div class="profile-name">会话恢复中…</div>
            <div class="profile-phone">请稍候</div>
          </div>
        </template>
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

      <!-- 通用设置（G6：移除虚假「家庭成员 0 人」展示，真实家庭账户规划中） -->
      <div class="section-card">
        <h3 class="section-title">通用设置</h3>
        <div class="section-row">
          <span>适老化大字体</span>
          <label class="switch">
            <input type="checkbox" :checked="userStore.elderMode" @change="userStore.toggleElderMode()" />
            <span class="switch__slider"></span>
          </label>
        </div>
      </div>

      <!-- 工作人员模式（G1，onsite01，C-O1 三态：未申请→申请页 / 审核中→只读 / 已上岗→切换+工作台 / 已驳回→重新申请） -->
      <div v-if="hasToken()" class="section-card">
        <h3 class="section-title">工作人员模式</h3>
        <div v-if="staffAuditStatus === 1" class="staff-panel">
          <div class="section-row">
            <span>当前身份</span>
            <span>{{ staffTypeText }}</span>
          </div>
          <div class="staff-actions">
            <button class="staff-btn staff-btn--primary" @click="switchToStaff">切换工作人员模式</button>
            <button class="staff-btn" @click="openStaffWorkbench">打开工作台</button>
          </div>
        </div>
        <div v-else-if="staffAuditStatus === 0" class="section-row staff-row" @click="router.push('/pc/staff/apply')">
          <span>申请审核中</span>
          <span class="staff-value staff-value--pending">待审核 ›</span>
        </div>
        <div v-else-if="staffAuditStatus === 2" class="section-row staff-row" @click="router.push('/pc/staff/apply')">
          <span>申请被驳回</span>
          <span class="staff-value staff-value--rejected">{{ staffMode?.reason ? `已驳回 · ${staffMode.reason}` : '已驳回' }} ›</span>
        </div>
        <div v-else class="section-row staff-row" @click="router.push('/pc/staff/apply')">
          <span>申请成为工作人员</span>
          <span class="staff-value">回收员 / 清运工 / 维修技术员 ›</span>
        </div>
      </div>

      <div class="section-card">
        <h3 class="section-title">常用功能</h3>
        <div class="nav-grid">
          <div class="nav-item" @click="router.push('/pc/orders')">
            <SvgIcon name="recycle-recycle-symbol" :size="32" />
            <span>我的订单</span>
          </div>
          <!-- 积分市场（积分商城 × 跳蚤市场合并）：徽章兑换 + 闲置好物 C2C 积分交易 -->
          <div class="nav-item" @click="router.push('/pc/mall')">
            <SvgIcon name="nav-shopping" :size="32" />
            <span>积分市场</span>
          </div>
          <!-- LoopFill B5 创意 C1：环保树入口（今日未签到红点角标） -->
          <div class="nav-item" @click="router.push('/pc/eco/tree')">
            <div class="nav-badge-wrap">
              <SvgIcon name="carbon-tree" :size="32" />
              <span v-if="notSignedToday" class="nav-badge nav-badge--dot" />
            </div>
            <span>环保树</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/carbon/bill')">
            <SvgIcon name="carbon-sprout" :size="32" />
            <span>低碳账单</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/kitchen/records')">
            <SvgIcon name="recycle-kitchen" :size="32" />
            <span>垃圾投放记录</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/carbon/certificate')">
            <SvgIcon name="nav-link" :size="32" />
            <span>碳证书</span>
          </div>
          <!-- LoopFill B6 创意 C2：变废为宝社区 + 片区环保排行榜 -->
          <div class="nav-item" @click="router.push('/pc/community')">
            <SvgIcon name="common-lightbulb" :size="32" />
            <span>变废为宝</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/community/rank')">
            <SvgIcon name="nav-star" :size="32" />
            <span>环保排行</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/ai/voice-steward')">
            <SvgIcon name="ai-microphone" :size="32" />
            <span>语音碳管家</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/science')">
            <SvgIcon name="carbon-tree" :size="32" />
            <span>科普专栏</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/user/message')">
            <div class="nav-badge-wrap">
              <SvgIcon name="carbon-email" :size="32" />
              <span v-if="unreadCount + imUnread > 0" class="nav-badge">{{ unreadCount + imUnread > 99 ? '99+' : unreadCount + imUnread }}</span>
            </div>
            <span>消息中心</span>
          </div>
          <!-- onsite03 合并：聊天已并入消息中心（统一入口 + 未读红点），不再单列在线聊天 -->
          <div class="nav-item" @click="router.push('/pc/user/complaint')">
            <SvgIcon name="carbon-help" :size="32" />
            <span>反馈投诉</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/user/realname')">
            <SvgIcon name="carbon-user-cert" :size="32" />
            <span>实名认证</span>
          </div>
          <div class="nav-item" @click="router.push('/pc/user/password')">
            <SvgIcon name="common-lock" :size="32" />
            <span>设置密码</span>
          </div>
          <!-- address-book：地址簿（省市区 + 定位，预约/收货统一取用） -->
          <div class="nav-item" @click="router.push('/pc/address-book')">
            <SvgIcon name="nav-location-pin" :size="32" />
            <span>地址簿</span>
          </div>
        </div>
      </div>

      <div v-if="hasToken()" class="logout-section">
        <button class="logout-btn" @click="onLogout">退出登录</button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-mine {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 700px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 48px;
  text-align: center;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 24px;
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover { box-shadow: var(--df-shadow-float); }
}

.profile-avatar {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: color-mix(in srgb, var(--df-color-secondary) 10%, transparent);
}

.profile-name {
  font-size: 22px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.profile-name__edit {
  margin-left: 8px;
  font-size: 16px;
  color: var(--df-text-secondary);
  cursor: pointer;
  vertical-align: 2px;
  transition: color 0.2s;
}

.profile-name__edit:hover {
  color: var(--df-color-primary);
}

.profile-phone {
  margin-top: 4px;
  color: var(--df-text-secondary);
  font-size: 14px;
}

.section-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 28px;
  margin-bottom: 24px;
  box-shadow: var(--df-shadow-card);
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin: 0 0 20px;
}

.section-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--df-border-color);
  font-size: 15px;
  color: var(--df-text-regular);

  &:last-child { border-bottom: none; }
}

.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;

  input { opacity: 0; width: 0; height: 0; }

  &__slider {
    position: absolute;
    inset: 0;
    background: var(--df-border-color);
    border-radius: 24px;
    transition: 0.3s;

    &::before {
      content: '';
      position: absolute;
      height: 18px;
      width: 18px;
      left: 3px;
      bottom: 3px;
      background: var(--df-bg-card);
      border-radius: 50%;
      transition: 0.3s;
    }
  }

  input:checked + .switch__slider { background: var(--df-color-primary); }
  input:checked + .switch__slider::before { transform: translateX(20px); }
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.nav-badge-wrap {
  position: relative;
}

.nav-badge {
  position: absolute;
  top: -6px;
  right: -14px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 9px;
  background: var(--df-color-danger);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;

  &--dot {
    min-width: 8px;
    width: 8px;
    height: 8px;
    padding: 0;
    border-radius: 50%;
  }
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: var(--df-text-regular);

  &:hover {
    background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent);
    color: var(--df-color-primary);
  }

  span { font-size: 14px; font-weight: 500; }
}

.staff-row {
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover { color: var(--df-color-primary); }
}

.staff-value {
  color: var(--df-text-secondary);
  font-size: 14px;

  &--pending { color: var(--df-color-warning); }
  &--rejected { color: var(--df-color-danger); }
}

.staff-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.staff-btn {
  flex: 1;
  padding: 12px 0;
  border: 2px solid var(--df-color-primary);
  border-radius: 10px;
  background: transparent;
  color: var(--df-color-primary);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  &--primary {
    background: var(--df-color-primary);
    color: #fff;
  }

  &:hover { opacity: 0.9; }
}

.logout-section {
  text-align: center;
  margin-top: 32px;
}

.logout-btn {
  padding: 12px 48px;
  font-size: 16px;
  font-weight: 600;
  color: var(--df-color-danger);
  background: var(--df-bg-card);
  border: 2px solid var(--df-color-danger);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(231, 76, 60, 0.05);
  }
}
</style>
