<script setup lang="ts">
// 维修报告（技术员，09：语音口述维修报告）
// useDictation 语音转写填故障描述/处理方案，不支持环境降级纯手输
// 修复失败 → 维修转回收强制联动（决策 D6：生成回收单，repair_ref 双向追溯）
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast, showToast } from 'vant'
import {
  getStaffOrderDetail, submitRepairReport, transferToRecycle,
  type StaffOrder,
} from '@/api/business'
import { useDictation } from '@/composables/useDictation'

const route = useRoute()
const router = useRouter()

const orderId = String(route.params.id)
const order = ref<StaffOrder | null>(null)
const submitting = ref(false)

const faultDesc = ref('')
const solution = ref('')
const fee = ref('')
/** 科普留痕（面向用户的用电安全/环保知识讲解摘要，V8） */
const scienceNote = ref('')
/** 修复结果：true 成功 / false 失败（失败强制走转回收） */
const success = ref(true)

/** 语音口述目标字段：话筒按下前选择 */
const dictTarget = ref<'fault' | 'solution' | 'science'>('fault')
const dictation = useDictation((text) => {
  if (dictTarget.value === 'fault') faultDesc.value += text
  else if (dictTarget.value === 'solution') solution.value += text
  else scienceNote.value += text
})

function toggleDictation(target: 'fault' | 'solution' | 'science') {
  if (dictation.listening.value && dictTarget.value === target) {
    dictation.stop()
    return
  }
  dictTarget.value = target
  dictation.start()
}

async function onSubmit() {
  if (!order.value) return
  if (!faultDesc.value.trim() || !solution.value.trim()) {
    showFailToast('请填写故障描述与处理方案')
    return
  }
  submitting.value = true
  try {
    await submitRepairReport(order.value, {
      faultDesc: faultDesc.value.trim(),
      solution: solution.value.trim(),
      fee: Number(fee.value) || 0,
      success: success.value,
      scienceNote: scienceNote.value.trim(),
    })
    if (success.value) {
      showSuccessToast('报告已提交，工单完结')
      router.replace('/tasks')
      return
    }
    // 决策 D6：不可修复件必须生成回收工单，禁止随意丢弃
    await showConfirmDialog({
      title: '维修转回收（强制联动）',
      message: '该件不可修复，按平台规定必须转回收处置，将生成回收工单并推送片区回收员。',
      confirmButtonText: '发起转回收',
    })
    // 后端契约：POST 无请求体（不可修复原因已随报告 reportContent 留痕）
    const { recycleOrderNo } = await transferToRecycle(orderId)
    showSuccessToast(`已生成回收单 ${recycleOrderNo}`)
    router.replace('/tasks')
  } catch (e) {
    showToast(e instanceof Error ? e.message : '报告提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  order.value = await getStaffOrderDetail(orderId)
})
</script>

<template>
  <div class="page">
    <van-nav-bar title="维修报告" left-arrow @click-left="router.back()" />

    <template v-if="order">
      <div class="card">
        <div class="card__title">{{ order.title }}</div>
        <div class="report__meta">{{ order.orderNo }} · {{ order.customerName }}</div>
        <!-- F2 预诊参考（低置信为空不展示） -->
        <div v-if="order.aiPrediagnosis" class="report__ai">
          <SvgIcon name="ai-robot" :size="16" />
          <span>{{ order.aiPrediagnosis }}</span>
        </div>
      </div>

      <div class="card">
        <div class="card__title">
          故障描述
          <van-button
            v-if="dictation.supported"
            size="mini"
            round
            :type="dictation.listening.value && dictTarget === 'fault' ? 'danger' : 'primary'"
            plain
            icon="volume-o"
            @click="toggleDictation('fault')"
          >
            {{ dictation.listening.value && dictTarget === 'fault' ? '停止口述' : '语音口述' }}
          </van-button>
        </div>
        <van-field
          v-model="faultDesc"
          rows="3"
          type="textarea"
          maxlength="200"
          show-word-limit
          placeholder="现场勘验的故障情况（支持语音口述）"
        />
      </div>

      <div class="card">
        <div class="card__title">
          处理方案
          <van-button
            v-if="dictation.supported"
            size="mini"
            round
            :type="dictation.listening.value && dictTarget === 'solution' ? 'danger' : 'primary'"
            plain
            icon="volume-o"
            @click="toggleDictation('solution')"
          >
            {{ dictation.listening.value && dictTarget === 'solution' ? '停止口述' : '语音口述' }}
          </van-button>
        </div>
        <van-field
          v-model="solution"
          rows="3"
          type="textarea"
          maxlength="200"
          show-word-limit
          placeholder="维修措施与更换配件说明"
        />
        <van-field v-model="fee" type="number" label="维修费" placeholder="0" suffix="元" />
      </div>

      <div class="card">
        <div class="card__title">
          科普留痕
          <van-button
            v-if="dictation.supported"
            size="mini"
            round
            :type="dictation.listening.value && dictTarget === 'science' ? 'danger' : 'primary'"
            plain
            icon="volume-o"
            @click="toggleDictation('science')"
          >
            {{ dictation.listening.value && dictTarget === 'science' ? '停止口述' : '语音口述' }}
          </van-button>
        </div>
        <p class="report__science-tip">面向用户的用电安全/环保知识讲解摘要，完成后用户与管理员可见</p>
        <van-field
          v-model="scienceNote"
          rows="2"
          type="textarea"
          maxlength="200"
          show-word-limit
          placeholder="向用户讲解的节能小知识，如「老旧冰箱密封条老化耗电增加 30%」"
        />
      </div>

      <div v-if="dictation.error.value" class="report__dict-error">{{ dictation.error.value }}</div>

      <div class="card">
        <div class="card__title">修复结果</div>
        <van-radio-group v-model="success" direction="horizontal">
          <van-radio :name="true">修复成功</van-radio>
          <van-radio :name="false">不可修复（转回收）</van-radio>
        </van-radio-group>
        <p v-if="!success" class="report__transfer-tip">
          不可修复件将强制生成回收工单推送片区回收员（repair_ref 双向追溯），禁止随意丢弃。
        </p>
      </div>

      <div class="report__actions">
        <van-button round plain type="primary" @click="router.push(`/repair/${orderId}/parts`)">
          配件登记
        </van-button>
        <van-button round type="primary" :loading="submitting" @click="onSubmit">
          {{ success ? '提交报告并完结' : '提交并转回收' }}
        </van-button>
      </div>
    </template>
    <van-skeleton v-else title :row="8" />
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.report__meta {
  color: var(--df-text-secondary);
  font-size: $df-font-size-sm;
}

.report__ai {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: $df-space-sm;
  padding: $df-space-sm;
  background: var(--df-color-primary-light);
  border-radius: $df-radius-lg;
  font-size: $df-font-size-sm;
  color: var(--df-color-primary-dark);
}

.card__title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report__dict-error {
  margin-bottom: $df-space-md;
  color: var(--df-color-warning);
  font-size: $df-font-size-sm;
}

.report__science-tip {
  margin: 0 0 $df-space-sm;
  color: var(--df-text-secondary);
  font-size: $df-font-size-xs;
  line-height: 1.6;
}

.report__transfer-tip {
  margin: $df-space-md 0 0;
  color: var(--df-color-danger);
  font-size: $df-font-size-sm;
  line-height: 1.6;
}

.report__actions {
  display: flex;
  gap: $df-space-md;

  .van-button {
    flex: 1;
  }
}
</style>
