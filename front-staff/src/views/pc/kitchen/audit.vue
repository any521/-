<script setup lang="ts">
// PC 端投放审核（B4）：审核用户垃圾投放上报（50m 内拍照），通过即发积分/驳回填原因
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
  <div class="pc-page">
    <div class="pc-page__header">
      <button class="pc-back" @click="router.back()">← 返回</button>
      <h1 class="pc-page__title">投放审核</h1>
      <p class="pc-page__sub">审核用户 50m 内拍照的垃圾投放，通过即发放积分</p>
    </div>

    <div class="pc-card">
      <div class="audit__head">
        <span class="audit__title">待审核上报（{{ list.length }}）</span>
        <van-button size="small" round plain icon="replay" @click="load">刷新</van-button>
      </div>

      <van-skeleton v-if="loading" title :row="3" :loading="loading" />

      <van-empty v-else-if="!list.length" description="暂无待审核的投放上报" />

      <div v-else class="audit__table">
        <div v-for="item in list" :key="item.id" class="audit__row">
          <!-- 照片缩略图 -->
          <div v-if="item.photoUrl" class="audit__thumb" @click="preview(item)">
            <img :src="item.photoUrl" alt="投放照片" />
          </div>
          <div v-else class="audit__thumb audit__thumb--empty">
            <van-icon name="photo-o" />
          </div>

          <!-- 信息 -->
          <div class="audit__info">
            <div class="audit__user">
              {{ item.userName }}
              <van-tag round type="warning">{{ item.aiAuditResult || '待审核' }}</van-tag>
            </div>
            <div class="audit__meta">
              {{ item.areaName }} · {{ (item.createTime || '').replace('T', ' ') }}
            </div>
          </div>

          <div class="audit__points">
            <div class="audit__bags">{{ item.bags }} 袋</div>
            <div class="audit__pts">通过 +{{ item.points }} 积分</div>
          </div>

          <!-- 操作 -->
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
    </div>

    <!-- 照片大图预览 -->
    <van-image-preview v-model:show="showPreview" :images="[previewUrl]" />

    <!-- 驳回原因弹窗 -->
    <van-dialog v-model:show="showReject" title="驳回投放" show-cancel-button @confirm="onReject">
      <div class="audit__reject-tip">驳回后该上报记录为「已驳回」，用户不会获得积分。</div>
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

.pc-back {
  border: none;
  background: transparent;
  color: var(--df-text-secondary);
  font-size: $df-font-size-base;
  cursor: pointer;
  padding: 4px 0;

  &:hover {
    color: var(--df-color-primary);
  }
}

.audit {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $df-space-md;
  }

  &__title {
    font-size: $df-font-size-md;
    font-weight: 600;
  }

  &__table {
    display: flex;
    flex-direction: column;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: $df-space-lg;
    padding: $df-space-md 0;
    border-bottom: 1px solid var(--df-border-color);

    &:last-child {
      border-bottom: none;
    }
  }

  &__thumb {
    width: 72px;
    height: 72px;
    border-radius: $df-radius-md;
    overflow: hidden;
    border: 1px solid var(--df-border-color);
    cursor: zoom-in;
    flex-shrink: 0;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }

    &--empty {
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--df-text-placeholder);
      background: var(--df-fill-color-light);
      cursor: default;
    }
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__user {
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: $df-space-sm;
  }

  &__meta {
    margin-top: 4px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__points {
    text-align: center;
    flex-shrink: 0;
  }

  &__bags {
    font-size: $df-font-size-lg;
    font-weight: 700;
    color: var(--df-color-warning);
  }

  &__pts {
    margin-top: 2px;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  &__actions {
    display: flex;
    gap: $df-space-sm;
    flex-shrink: 0;
  }

  &__reject-tip {
    padding: 0 $df-space-lg;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }
}
</style>
