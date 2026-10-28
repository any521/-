<script setup lang="ts">
// 投放审核（B4）：审核用户垃圾投放上报（50m 内拍照），通过即发积分/驳回填原因
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import { listPendingKitchenReports, auditKitchenReport, type KitchenAuditItem } from '@/api/business'

const router = useRouter()

const list = ref<KitchenAuditItem[]>([])
const loading = ref(false)
/** 正在操作的单号（按钮 loading，防重复提交） */
const actingId = ref<string | null>(null)

// 照片预览
const showPreview = ref(false)
const previewUrl = ref('')

// 驳回弹窗
const showReject = ref(false)
const rejectTarget = ref<KitchenAuditItem | null>(null)
const rejectReason = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await listPendingKitchenReports()
  } finally {
    loading.value = false
  }
}

async function onPass(item: KitchenAuditItem) {
  actingId.value = item.id
  try {
    await auditKitchenReport(item.id, { pass: true })
    showSuccessToast(`已通过，发放 ${item.points} 积分`)
    await load()
  } finally {
    actingId.value = null
  }
}

function openReject(item: KitchenAuditItem) {
  rejectTarget.value = item
  rejectReason.value = ''
  showReject.value = true
}

async function onReject() {
  const target = rejectTarget.value
  if (!target) return
  if (!rejectReason.value.trim()) {
    showFailToast('请填写驳回原因')
    return
  }
  actingId.value = target.id
  try {
    await auditKitchenReport(target.id, { pass: false, remark: rejectReason.value.trim() })
    showSuccessToast('已驳回')
    showReject.value = false
    await load()
  } catch {
    // request 层已 toast，弹窗保持打开供重试
    showReject.value = true
  } finally {
    actingId.value = null
  }
}

function preview(item: KitchenAuditItem) {
  if (!item.photoUrl) return
  previewUrl.value = item.photoUrl
  showPreview.value = true
}

onMounted(load)
</script>

<template>
  <div class="page audit">
    <van-nav-bar title="投放审核" left-arrow @click-left="router.back()" />

    <!-- 加载中骨架 -->
    <div v-if="loading" class="card">
      <van-skeleton title :row="3" />
    </div>

    <van-empty v-else-if="!list.length" description="暂无待审核的投放上报" />

    <template v-else>
      <div class="card">
        <div class="card__title">待审核上报（{{ list.length }}）</div>

        <div v-for="item in list" :key="item.id" class="audit__item">
          <div class="audit__head">
            <div class="audit__user">
              <van-icon name="user-o" />
              {{ item.userName }}
            </div>
            <van-tag round type="warning">{{ item.aiAuditResult || '待审核' }}</van-tag>
          </div>

          <div class="audit__meta">
            <span>{{ item.areaName }}</span>
            <span>{{ (item.createTime || '').replace('T', ' ') }}</span>
          </div>

          <div class="audit__body">
            <div class="audit__info">
              <div class="audit__bags">{{ item.bags }} 袋</div>
              <div class="audit__points">通过发放 {{ item.points }} 积分</div>
            </div>
            <!-- 照片缩略图（无图时占位） -->
            <div v-if="item.photoUrl" class="audit__thumb" @click="preview(item)">
              <img :src="item.photoUrl" alt="投放照片" />
            </div>
          </div>

          <div class="audit__actions">
            <van-button
              size="small"
              round
              plain
              type="danger"
              :loading="actingId === item.id"
              @click="openReject(item)"
            >
              驳回
            </van-button>
            <van-button
              size="small"
              round
              type="primary"
              :loading="actingId === item.id"
              @click="onPass(item)"
            >
              通过并发放积分
            </van-button>
          </div>
        </div>
      </div>
    </template>

    <!-- 照片大图预览 -->
    <van-image-preview v-model:show="showPreview" :images="[previewUrl]" />

    <!-- 驳回原因弹窗 -->
    <van-dialog v-model:show="showReject" title="驳回投放" show-cancel-button @confirm="onReject">
      <div class="audit__reject-tip">
        驳回后该上报记录为「已驳回」，用户不会获得积分。
      </div>
      <van-field
        v-model="rejectReason"
        rows="2"
        type="textarea"
        maxlength="100"
        placeholder="请输入驳回原因（必填，将通知用户）"
      />
    </van-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.audit {
  &__item {
    padding: $df-space-md 0;
    border-bottom: 1px solid var(--df-border-color);

    &:last-child {
      border-bottom: none;
    }
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__user {
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__meta {
    margin-top: 4px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
    display: flex;
    gap: $df-space-md;
  }

  &__body {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $df-space-md;
    margin-top: $df-space-sm;
  }

  &__bags {
    font-size: $df-font-size-lg;
    font-weight: 700;
    color: var(--df-color-warning);
  }

  &__points {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__thumb {
    width: 64px;
    height: 64px;
    border-radius: $df-radius-lg;
    overflow: hidden;
    flex-shrink: 0;
    border: 1px solid var(--df-border-color);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: $df-space-sm;
    margin-top: $df-space-sm;
  }

  &__reject-tip {
    padding: 0 $df-space-lg;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }
}
</style>
