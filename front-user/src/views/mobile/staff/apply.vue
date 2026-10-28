<script setup lang="ts">
// 工作人员申请（G1，onsite01）：工种单选 + 实名拦截 + 申请态只读/重申请
// 状态机：未实名 → 提示+去实名；审核中/已上岗 → 只读态；已驳回 → 原因+表单重新申请；未申请 → 表单
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { useUserStore } from '@/store/user'
import { hasToken } from '@/api/token'
import { getRealNameStatus } from '@/api/business'
import { applyStaff, getStaffApplyStatus, type StaffApplyStatus } from '@/api/staff'

defineOptions({ name: 'staff-apply' })

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const submitting = ref(false)
const realVerified = ref(false)
const applyInfo = ref<StaffApplyStatus>({ applied: false })
const form = ref<{ staffType: 1 | 2 | 3; staffName: string; phone: string; reason: string }>({
  staffType: 1,
  staffName: '',
  phone: '',
  reason: '',
})

async function load() {
  try {
    const [r, s] = await Promise.all([getRealNameStatus(), getStaffApplyStatus()])
    realVerified.value = r.status === 2
    applyInfo.value = s
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (!form.value.staffName.trim()) {
    showToast('请输入姓名')
    return
  }
  if (!/^1\d{10}$/.test(form.value.phone.trim())) {
    showToast('请输入正确的手机号')
    return
  }
  submitting.value = true
  try {
    await applyStaff({
      staffType: form.value.staffType,
      staffName: form.value.staffName.trim(),
      phone: form.value.phone.trim(),
      reason: form.value.reason.trim() || undefined,
    })
    showSuccessToast('已提交，请等待审核')
    router.replace('/mobile/mine')
  } catch {
    // 提交失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}

/** 进入工作台（同域跳转，front-staff 零改动复用同一 JWT） */
function goStaff() {
  router.replace('/mobile/mine')
}

onMounted(() => {
  if (hasToken() && !userStore.profile) userStore.fetchProfile()
  load()
})
</script>

<template>
  <div class="page staff-apply">
    <van-nav-bar title="工作人员申请" left-arrow @click-left="router.back()" />

    <div v-if="loading" class="staff-apply__loading">加载中…</div>

    <template v-else>
      <!-- 未实名：置顶提示 + 去实名 -->
      <van-notice-bar v-if="!realVerified" wrapable left-icon="info-o" color="#ed6a0c" background="#fffbe8">
        申请工作人员需先完成实名认证
        <template #right-icon>
          <van-button size="mini" type="primary" class="staff-apply__go-realname" @click="router.push('/user/realname')">
            去实名
          </van-button>
        </template>
      </van-notice-bar>

      <!-- 审核中：只读态 -->
      <div v-else-if="applyInfo.applied && applyInfo.auditStatus === 0" class="card staff-apply__pending">
        <div class="staff-apply__icon"><SvgIcon name="nav-clock" :size="40" /></div>
        <div class="staff-apply__title">申请审核中</div>
        <div class="staff-apply__tip">您的申请已提交，审核通过后即可切换工作人员模式，请耐心等待</div>
      </div>

      <!-- 已上岗：只读态 + 进工作台 -->
      <div v-else-if="applyInfo.applied && applyInfo.auditStatus === 1" class="card staff-apply__done">
        <div class="staff-apply__icon"><SvgIcon name="carbon-checked" :size="40" /></div>
        <div class="staff-apply__title">已上岗</div>
        <div class="staff-apply__tip">审核已通过，可前往「我的」切换工作人员模式</div>
        <van-button round block type="primary" class="staff-apply__action" @click="goStaff">返回我的</van-button>
      </div>

      <!-- 已驳回：原因 + 表单可重新申请 -->
      <template v-else>
        <div v-if="applyInfo.applied && applyInfo.auditStatus === 2" class="card staff-apply__rejected">
          <div class="staff-apply__reason">上次申请未通过：{{ applyInfo.reason || '未填写原因' }}</div>
          <div class="staff-apply__tip">请修正信息后重新申请</div>
        </div>

        <van-cell-group inset title="申请工种">
          <van-radio-group v-model="form.staffType">
            <van-cell title="回收员" label="上门回收、称重登记" clickable @click="form.staffType = 1">
              <template #right-icon><van-radio :name="1" @click.stop /></template>
            </van-cell>
            <van-cell title="清运工" label="垃圾清运、投放审核" clickable @click="form.staffType = 2">
              <template #right-icon><van-radio :name="2" @click.stop /></template>
            </van-cell>
            <van-cell title="维修技术员" label="上门维修、检修报告" clickable @click="form.staffType = 3">
              <template #right-icon><van-radio :name="3" @click.stop /></template>
            </van-cell>
          </van-radio-group>
        </van-cell-group>

        <van-cell-group inset class="staff-apply__form">
          <van-field v-model="form.staffName" label="姓名" placeholder="请输入姓名" maxlength="32" />
          <van-field v-model="form.phone" label="手机号" placeholder="请输入手机号" maxlength="11" />
          <van-cell title="所属片区" label="可不选，审核通过后由平台分配片区" />
          <van-field
            v-model="form.reason"
            label="备注"
            type="textarea"
            rows="2"
            autosize
            placeholder="选填：自我介绍 / 从业经验等"
            maxlength="255"
            show-word-limit
          />
        </van-cell-group>

        <div class="staff-apply__submit">
          <van-button round block type="primary" :loading="submitting" @click="onSubmit">
            {{ applyInfo.applied && applyInfo.auditStatus === 2 ? '重新申请' : '提交申请' }}
          </van-button>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.staff-apply {
  &__loading {
    padding: $df-space-xl;
    text-align: center;
    color: var(--df-text-secondary);
  }

  &__go-realname {
    margin-left: $df-space-xs;
  }

  &__pending,
  &__done,
  &__rejected {
    margin: $df-space-lg;
    text-align: center;
    padding: $df-space-xl $df-space-lg;
  }

  &__icon {
    color: var(--df-color-primary);
  }

  &__title {
    margin-top: $df-space-sm;
    font-size: $df-font-size-lg;
    font-weight: 600;
  }

  &__reason {
    color: var(--df-color-danger);
    font-size: $df-font-size-base;
    line-height: 1.6;
  }

  &__tip {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    line-height: 1.6;
  }

  &__action {
    margin-top: $df-space-lg;
  }

  &__form {
    margin-top: $df-space-md;
  }

  &__submit {
    margin: $df-space-lg;
  }
}
</style>
