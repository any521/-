<script setup lang="ts">
// 实名认证：状态机 0未认证 1待审 2已通过 3已驳回（GapFill B1 V1）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getRealNameStatus, submitRealName, type RealNameStatus } from '@/api/business'

defineOptions({ name: 'user-realname' })

const router = useRouter()
const loading = ref(true)
const submitting = ref(false)
const info = ref<RealNameStatus>({ status: 0 })
const form = ref({ realName: '', idCard: '' })

// 身份证 18 位格式校验（同后端 RealNameSubmitDTO 正则）
const ID_CARD_RE = /^\d{17}[\dXx]$/

/** 姓名脱敏：首字 + **（展示用） */
const maskName = (name?: string) => (name ? `${name.slice(0, 1)}**` : '—')

async function loadStatus() {
  try {
    info.value = await getRealNameStatus()
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (form.value.realName.trim().length < 2 || form.value.realName.trim().length > 20) {
    showToast('真实姓名长度 2-20 字')
    return
  }
  if (!ID_CARD_RE.test(form.value.idCard.trim())) {
    showToast('身份证号格式不正确')
    return
  }
  submitting.value = true
  try {
    await submitRealName(form.value.realName.trim(), form.value.idCard.trim())
    showSuccessToast('已提交，请等待审核')
    await loadStatus()
  } catch {
    // 提交失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}

onMounted(loadStatus)
</script>

<template>
  <div class="page realname">
    <van-nav-bar title="实名认证" left-arrow @click-left="router.back()" />

    <div v-if="loading" class="realname__loading">加载中…</div>

    <template v-else>
      <!-- 已通过：完成态 -->
      <div v-if="info.status === 2" class="card realname__done">
        <div class="realname__icon"><SvgIcon name="carbon-checked" :size="40" /></div>
        <div class="realname__title">实名认证已完成</div>
        <div class="realname__name">姓名：{{ maskName(info.realName) }}</div>
        <div class="realname__tip">身份信息已通过审核，不可修改</div>
      </div>

      <!-- 待审：等待提示 -->
      <div v-else-if="info.status === 1" class="card realname__pending">
        <div class="realname__icon"><SvgIcon name="nav-clock" :size="40" /></div>
        <div class="realname__title">审核中</div>
        <div class="realname__tip">实名资料已提交，预计 1-3 个工作日完成审核，请耐心等待</div>
      </div>

      <!-- 已驳回：原因 + 可重新提交 -->
      <div v-else-if="info.status === 3" class="card realname__rejected">
        <div class="realname__icon"><SvgIcon name="nav-close" :size="40" /></div>
        <div class="realname__title">审核未通过</div>
        <div class="realname__reason">原因：{{ info.remark || '未填写' }}</div>
        <div class="realname__tip">请修正后重新提交</div>
      </div>

      <!-- 未认证 / 已驳回：表单 -->
      <van-cell-group v-if="info.status === 0 || info.status === 3" inset class="realname__form">
        <van-field
          v-model="form.realName"
          label="真实姓名"
          placeholder="请输入真实姓名"
          maxlength="20"
        />
        <van-field
          v-model="form.idCard"
          label="身份证号"
          placeholder="请输入 18 位身份证号"
          maxlength="18"
        />
      </van-cell-group>

      <div v-if="info.status === 0 || info.status === 3" class="realname__submit">
        <van-button round block type="primary" :loading="submitting" @click="onSubmit">提交认证</van-button>
      </div>

      <div class="realname__notice">实名信息仅用于服务核验，我们将严格保护您的隐私安全</div>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.realname {
  &__loading {
    padding: $df-space-xl;
    text-align: center;
    color: var(--df-text-secondary);
  }

  &__done,
  &__pending,
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

  &__name {
    margin-top: $df-space-sm;
    font-size: $df-font-size-base;
  }

  &__reason {
    margin-top: $df-space-sm;
    color: var(--df-color-danger);
    font-size: $df-font-size-base;
  }

  &__tip {
    margin-top: $df-space-xs;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    line-height: 1.6;
  }

  &__form {
    margin-top: $df-space-md;
  }

  &__submit {
    margin: $df-space-lg;
  }

  &__notice {
    margin: 0 $df-space-lg;
    color: var(--df-text-tertiary);
    font-size: $df-font-size-sm;
    text-align: center;
    line-height: 1.6;
  }
}
</style>
