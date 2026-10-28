<template>
  <div class="my-science">
    <van-nav-bar title="我的科普专栏" left-arrow @click-left="router.back()" fixed placeholder />

    <!-- 顶部摘要：发布按钮 + 收益 -->
    <div class="hero">
      <div class="hero__bg">
        <div class="hero__pattern" />
      </div>
      <div class="hero__content">
        <div class="hero__title">我的创作空间</div>
        <div class="hero__desc">分享环保知识，让每一次科普都被看见</div>
        <van-button
          round
          block
          type="primary"
          icon="plus"
          class="hero__btn"
          @click="router.push('/mobile/science/create')"
        >
          发布新文章
        </van-button>
      </div>
    </div>

    <!-- 统计面板：2x2 网格 -->
    <div class="stats">
      <div class="stats__item">
        <div class="stats__icon stats__icon--blue">
          <SvgIcon name="nav-memo" :size="20" />
        </div>
        <div class="stats__info">
          <div class="stats__value">{{ stats.totalArticles }}</div>
          <div class="stats__label">总文章数</div>
        </div>
      </div>
      <div class="stats__item">
        <div class="stats__icon stats__icon--green">
          <SvgIcon name="nav-eye" :size="20" />
        </div>
        <div class="stats__info">
          <div class="stats__value">{{ stats.totalViews }}</div>
          <div class="stats__label">总阅读量</div>
        </div>
      </div>
      <div class="stats__item">
        <div class="stats__icon stats__icon--orange">
          <SvgIcon name="nav-download" :size="20" />
        </div>
        <div class="stats__info">
          <div class="stats__value">{{ stats.totalDownloads }}</div>
          <div class="stats__label">总下载次数</div>
        </div>
      </div>
      <div class="stats__item stats__item--gold">
        <div class="stats__icon stats__icon--gold">
          <SvgIcon name="nav-money-bag" :size="20" />
        </div>
        <div class="stats__info">
          <div class="stats__value">¥{{ stats.estimatedIncome.toFixed(2) }}</div>
          <div class="stats__label">预计收益</div>
        </div>
      </div>
    </div>

    <!-- 筛选标签：横向滚动 -->
    <div class="filter">
      <div class="filter__scroller">
        <button
          :class="['filter__chip', { 'filter__chip--active': statusFilter === 'all' }]"
          @click="onFilter('all')"
        >
          全部 <span class="filter__count">{{ filteredCount }}</span>
        </button>
        <button
          :class="['filter__chip', { 'filter__chip--active': statusFilter === '0' }]"
          @click="onFilter('0')"
        >
          <SvgIcon name="nav-hourglass" :size="12" /> 待审核
        </button>
        <button
          :class="['filter__chip', { 'filter__chip--active': statusFilter === '1' }]"
          @click="onFilter('1')"
        >
          <SvgIcon name="nav-check-mark" :size="12" /> 已发布
        </button>
        <button
          :class="['filter__chip', { 'filter__chip--active': statusFilter === '2' }]"
          @click="onFilter('2')"
        >
          <SvgIcon name="nav-package" :size="12" /> 已下架
        </button>
        <button
          :class="['filter__chip', { 'filter__chip--active': statusFilter === '3' }]"
          @click="onFilter('3')"
        >
          <SvgIcon name="nav-cross-mark" :size="12" /> 被驳回
        </button>
      </div>
    </div>

    <!-- 文章列表：卡片式 -->
    <div class="list">
      <article
        v-for="article in articles"
        :key="article.id"
        class="card"
        @click="router.push(`/mobile/science/article/${article.id}`)"
      >
        <div class="card__head">
          <StatusBadge :status="article.status" />
          <span v-if="article.isSystem" class="card__system">系统文章</span>
          <span class="card__category">{{ getCategoryName(article.categories) }}</span>
        </div>

        <h3 class="card__title">{{ article.title }}</h3>
        <p v-if="article.description" class="card__desc">{{ article.description }}</p>

        <div class="card__meta">
          <div class="card__points">
            <SvgIcon name="nav-money-bag" :size="14" />
            <span>{{ article.downloadPoints }} 积分</span>
          </div>
          <div class="card__stats">
            <span><SvgIcon name="nav-eye" :size="14" /> {{ article.viewCount }}</span>
            <span><SvgIcon name="nav-download" :size="14" /> {{ article.downloadCount }}</span>
            <span><SvgIcon name="nav-heart" :size="14" /> {{ article.likeCount }}</span>
          </div>
        </div>

        <div class="card__actions" @click.stop>
          <button class="act act--view" @click="router.push(`/mobile/science/article/${article.id}`)">
            <SvgIcon name="nav-eye" :size="14" /> 查看
          </button>
          <button class="act act--edit" @click="editArticle(article)">
            <SvgIcon name="common-cube" :size="14" /> 编辑
          </button>
          <button
            v-if="article.status !== 1"
            class="act act--success"
            @click="publishArticle(article)"
          >
            <SvgIcon name="nav-check-mark" :size="14" /> 发布
          </button>
          <button
            v-if="article.status === 1"
            class="act act--warning"
            @click="withdrawArticle(article)"
          >
            <SvgIcon name="nav-package" :size="14" /> 下架
          </button>
          <button class="act act--danger" @click="deleteArticle(article)">
            <SvgIcon name="nav-cross-mark" :size="14" /> 删除
          </button>
        </div>
      </article>

      <!-- 空状态 -->
      <van-empty
        v-if="articles.length === 0"
        description="还没有文章，快来发布第一篇吧"
        class="list__empty"
      >
        <template #image>
          <div class="empty-illust">
            <svg viewBox="0 0 160 160" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <defs>
                <linearGradient id="emptyGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="currentColor" stop-opacity="0.18" />
                  <stop offset="100%" stop-color="currentColor" stop-opacity="0.05" />
                </linearGradient>
              </defs>
              <!-- 稿纸 -->
              <rect x="32" y="30" width="80" height="100" rx="6"
                fill="url(#emptyGrad)" stroke="currentColor" stroke-opacity="0.35" stroke-width="1.5" />
              <line v-for="(y, i) in [46, 58, 70, 82, 94, 106, 118]" :key="i"
                :x1="42" :x2="102" :y1="y" :y2="y"
                stroke="currentColor" stroke-opacity="0.25" stroke-width="1" stroke-dasharray="3 3" />
              <!-- 笔 -->
              <g transform="rotate(35 110 70)">
                <rect x="106" y="40" width="10" height="56" rx="2"
                  fill="currentColor" fill-opacity="0.75" />
                <polygon points="106,96 116,96 111,108" fill="currentColor" />
                <rect x="106" y="40" width="10" height="10" fill="currentColor" fill-opacity="0.4" />
              </g>
              <!-- 装饰小星 -->
              <circle cx="128" cy="40" r="2.5" fill="currentColor" fill-opacity="0.5" />
              <circle cx="138" cy="60" r="1.8" fill="currentColor" fill-opacity="0.4" />
              <circle cx="22" cy="120" r="2" fill="currentColor" fill-opacity="0.4" />
            </svg>
          </div>
        </template>
        <van-button round type="primary" @click="router.push('/mobile/science/create')">
          立即发布第一篇
        </van-button>
      </van-empty>
    </div>

    <!-- 分页 -->
    <div v-if="articles.length > 0" class="pager">
      <van-button
        size="small"
        plain
        round
        :disabled="pageNum === 1"
        @click="loadArticles(pageNum - 1)"
      >
        上一页
      </van-button>
      <span class="pager__info">{{ pageNum }} / {{ totalPages || 1 }}</span>
      <van-button
        size="small"
        plain
        round
        :disabled="pageNum >= totalPages"
        @click="loadArticles(pageNum + 1)"
      >
        下一页
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import StatusBadge from '@/components/StatusBadge.vue'
import { request } from '@/api/request'

const router = useRouter()

interface ArticleItem {
  id: number
  title: string
  description: string
  coverUrl?: string
  authorName: string
  viewCount: number
  downloadCount: number
  likeCount: number
  isSystem: boolean
  categories: { code: string, name: string }[]
  publishTime: string
  status: number
  downloadPoints: number
}

const statusFilter = ref('all')
const pageNum = ref(1)
const pageSize = 10
const articles = ref<ArticleItem[]>([])
const stats = ref({
  totalArticles: 0,
  totalViews: 0,
  totalDownloads: 0,
  estimatedIncome: 0
})
const totalPages = ref(1)
const total = ref(0)

// 当前筛选条件下的文章总数（来自后端分页结果）
const filteredCount = computed(() => total.value)

// 加载我的统计（/science/my-stats）
const loadStats = async () => {
  try {
    const data = (await request.get('/science/my-stats')) as unknown as typeof stats.value
    stats.value = { ...stats.value, ...(data || {}) }
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

// 加载文章列表
const loadArticles = async (page: number = 1) => {
  try {
    const params = new URLSearchParams({
      pageNum: page.toString(),
      pageSize: pageSize.toString(),
      statusFilter: statusFilter.value
    })
    
    // 拦截器已拆包 Result.data，返回即 PageVO { list, total }
    const response = (await request.get(`/science/my-articles?${params.toString()}`)) as unknown as { list: ArticleItem[]; total: number }
    articles.value = response.list || []
    total.value = response.total || 0
    totalPages.value = Math.ceil(total.value / pageSize)
  } catch (error) {
    console.error('加载文章列表失败:', error)
  }
}

// 切换筛选
const onFilter = (status: string) => {
  if (statusFilter.value === status) return
  statusFilter.value = status
  pageNum.value = 1
  loadArticles(1)
}

// 获取分类名称（后端可能返回 JSON 字符串或已解析数组）
const getCategoryName = (categories: { code: string, name: string }[] | string | undefined): string => {
  if (!categories) return ''
  try {
    const arr = typeof categories === 'string' ? JSON.parse(categories) : categories
    return arr[0]?.name || ''
  } catch {
    return typeof categories === 'string' ? categories : ''
  }
}

// 编辑文章
const editArticle = (article: ArticleItem) => {
  router.push(`/mobile/science/create?id=${article.id}`)
}

/** 确认弹窗：确认返回 true，取消返回 false */
const confirmAction = async (message: string, title: string): Promise<boolean> => {
  try {
    await showConfirmDialog({ title, message, confirmButtonText: '确认' })
    return true
  } catch {
    return false
  }
}

// 发布文章
const publishArticle = async (article: ArticleItem) => {
  if (article.status === 1) {
    showToast('该文章已发布')
    return
  }

  const ok = await confirmAction(
    `确认发布《${article.title}》吗？\n发布后需要管理员再次审核通过才能对外展示。`,
    '确认发布',
  )
  if (!ok) return

  try {
    await request.post(`/science/articles/${article.id}/publish`)
    showToast('发布成功，等待管理员审核')
    await loadArticles(pageNum.value)
  } catch (error: any) {
    showToast(error?.message || '发布失败')
  }
}

// 下架文章
const withdrawArticle = async (article: ArticleItem) => {
  if (article.status !== 1) {
    showToast('该文章当前不可下架')
    return
  }

  const ok = await confirmAction(
    `确认下架《${article.title}》？\n下架后将不再被其他用户看到。`,
    '确认下架',
  )
  if (!ok) return

  try {
    await request.post(`/science/articles/${article.id}/withdraw`)
    showToast('文章已成功下架')
    await loadArticles(pageNum.value)
  } catch (error: any) {
    showToast(error?.message || '下架失败')
  }
}

// 删除文章
const deleteArticle = async (article: ArticleItem) => {
  const ok = await confirmAction(
    `确定要删除《${article.title}》吗？\n此操作不可撤销！`,
    '确认删除',
  )
  if (!ok) return

  try {
    await request.delete(`/science/articles/${article.id}`)
    showToast('文章已删除')
    await loadArticles(pageNum.value)
  } catch (error: any) {
    showToast(error?.message || '删除失败')
  }
}

onMounted(() => {
  loadArticles()
  loadStats()
})
</script>

<style scoped lang="scss">
.my-science {
  min-height: 100vh;
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
  background: var(--df-bg-page);
}

/* ===== Hero 区 ===== */
.hero {
  position: relative;
  margin: 12px;
  border-radius: 16px;
  overflow: hidden;
  background: var(--df-gradient-brand);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);

  &__bg {
    position: absolute;
    inset: 0;
    overflow: hidden;
  }

  &__pattern {
    position: absolute;
    inset: -50%;
    background-image: radial-gradient(
      circle at 20% 30%,
      rgba(255, 255, 255, 0.18) 0,
      transparent 30%
    ),
    radial-gradient(
      circle at 80% 70%,
      rgba(255, 255, 255, 0.12) 0,
      transparent 25%
    );
  }

  &__content {
    position: relative;
    padding: 18px 18px 20px;
    z-index: 1;
  }

  &__title {
    font-size: 20px;
    font-weight: 700;
    color: #fff;
    margin-bottom: 4px;
    letter-spacing: 0.5px;
  }

  &__desc {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.85);
    margin-bottom: 14px;
  }

  &__btn {
    height: 40px;
    font-weight: 600;
    background: rgba(255, 255, 255, 0.95) !important;
    color: #1f3d2b !important;
    border: none !important;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);

    :global([data-theme='dark']) & {
      background: rgba(232, 227, 212, 0.95) !important;
      color: #161e16 !important;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
    }

    &:active {
      transform: scale(0.98);
    }
  }
}

/* ===== 统计面板 ===== */
.stats {
  margin: 0 12px 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;

  &__item {
    background: var(--df-bg-card);
    border-radius: 12px;
    padding: 12px;
    display: flex;
    align-items: center;
    gap: 10px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }

  &__item--gold {
    background: linear-gradient(135deg, #fff8e1 0%, #ffecb3 100%);

    :global([data-theme='dark']) & {
      background: linear-gradient(135deg, #3a2e1a 0%, #2a2114 100%);
    }
  }

  &__icon {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    &--blue { background: rgba(33, 150, 243, 0.12); color: #1976d2; }
    &--green { background: rgba(76, 175, 80, 0.12); color: #388e3c; }
    &--orange { background: rgba(255, 152, 0, 0.12); color: #f57c00; }
    &--gold {
      background: rgba(255, 160, 0, 0.18);
      color: #ef6c00;

      :global([data-theme='dark']) & {
        background: rgba(212, 169, 107, 0.18);
        color: var(--df-color-gold);
      }
    }
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__value {
    font-size: 18px;
    font-weight: 700;
    color: var(--df-text-primary);
    line-height: 1.2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__label {
    font-size: 11px;
    color: var(--df-text-secondary);
    margin-top: 2px;
  }
}

/* ===== 筛选 ===== */
.filter {
  margin: 0 0 12px;
  background: var(--df-bg-card);

  &__scroller {
    display: flex;
    gap: 8px;
    padding: 10px 12px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;

    &::-webkit-scrollbar { display: none; }
  }

  &__chip {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 6px 14px;
    background: var(--df-bg-nested);
    border: 1px solid transparent;
    border-radius: 999px;
    font-size: 13px;
    color: var(--df-text-secondary);
    cursor: pointer;
    transition: all 0.2s;
  }

  &__chip--active {
    background: var(--df-color-primary);
    color: #fff;
  }

  &__count {
    font-size: 11px;
    padding: 1px 6px;
    background: rgba(0, 0, 0, 0.08);
    border-radius: 999px;

    .filter__chip--active & {
      background: rgba(255, 255, 255, 0.25);
    }
  }
}

/* ===== 文章列表 ===== */
.list {
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__empty {
    padding: 40px 0;
  }
}

/* 空状态插图：稿纸 + 笔，主题色 */
.empty-illust {
  width: 140px;
  height: 140px;
  color: var(--df-color-primary);
  opacity: 0.85;

  svg {
    width: 100%;
    height: 100%;
  }
}

.card {
  background: var(--df-bg-card);
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: transform 0.15s;

  &:active {
    transform: scale(0.99);
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
  }

  &__system {
    display: inline-block;
    padding: 2px 6px;
    background: var(--df-color-primary);
    color: #fff;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 500;
  }

  &__category {
    margin-left: auto;
    font-size: 11px;
    color: var(--df-text-secondary);
    background: var(--df-bg-nested);
    padding: 2px 8px;
    border-radius: 4px;
    max-width: 50%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: var(--df-text-primary);
    line-height: 1.4;
    margin: 0 0 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    word-break: break-all;
  }

  &__desc {
    font-size: 12px;
    color: var(--df-text-secondary);
    margin: 0 0 10px;
    line-height: 1.5;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 8px 0;
    margin-bottom: 8px;
    border-top: 1px dashed var(--df-border-color);
    border-bottom: 1px dashed var(--df-border-color);
  }

  &__points {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-weight: 600;
    color: var(--df-color-gold);
  }

  &__stats {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    font-size: 12px;
    color: var(--df-text-secondary);

    span {
      display: inline-flex;
      align-items: center;
      gap: 3px;
    }
  }

  &__actions {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
}

/* 操作按钮：图标 + 文字，更紧凑 */
.act {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 10px;
  background: var(--df-bg-nested);
  border: none;
  border-radius: 6px;
  font-size: 12px;
  color: var(--df-text-primary);
  cursor: pointer;
  transition: all 0.15s;

  &:active {
    transform: scale(0.95);
  }

  &--view { color: var(--df-color-primary); }
  &--edit { color: var(--df-text-secondary); }
  &--success {
    background: rgba(76, 175, 80, 0.12);
    color: #2e7d32;

    :global([data-theme='dark']) & {
      background: rgba(124, 196, 122, 0.16);
      color: var(--df-color-success);
    }
  }
  &--warning {
    background: rgba(255, 152, 0, 0.12);
    color: #ef6c00;

    :global([data-theme='dark']) & {
      background: rgba(196, 168, 107, 0.16);
      color: var(--df-color-warning);
    }
  }
  &--danger {
    background: rgba(244, 67, 54, 0.12);
    color: #c62828;

    :global([data-theme='dark']) & {
      background: rgba(224, 122, 106, 0.16);
      color: var(--df-color-danger);
    }
  }
}

/* ===== 分页 ===== */
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 20px;
  padding: 0 12px;

  &__info {
    font-size: 13px;
    color: var(--df-text-secondary);
    min-width: 60px;
    text-align: center;
  }
}
</style>
