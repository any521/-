<template>
  <div class="article-detail-container">
    <!-- 顶部导航 -->
    <div class="top-nav">
      <button @click="goBack" class="back-btn">← 返回</button>
      <h1 class="title">{{ article?.title || '加载中...' }}</h1>
    </div>

    <!-- 作者信息 -->
    <div v-if="article" class="author-section">
      <div class="author-info">
        <img :src="article.author.avatar || '/default-avatar.png'" class="author-avatar">
        <div class="author-meta">
          <span class="author-name">{{ article.author.nickname }}</span>
          <span class="author-stats">{{ article.author.followerCount }} 粉丝 · {{ article.author.articleCount }} 文章</span>
        </div>
      </div>
      
      <div class="publish-time">发布于 {{ formatDate(article.publishTime) }}</div>
    </div>

    <!-- 统计信息 -->
    <div v-if="article" class="stats-bar">
      <span><SvgIcon name="nav-eye" :size="14" /> 阅读 {{ article.viewCount }}</span>
      <span><SvgIcon name="nav-download" :size="14" /> 下载 {{ article.downloadCount }}</span>
      <span><SvgIcon name="nav-heart" :size="14" /> 点赞 {{ article.likeCount }}</span>
      <span v-if="article.rating"><SvgIcon name="nav-star" :size="14" /> {{ article.rating.average }} ({{ article.rating.count }})</span>
    </div>

    <!-- 分类标签 -->
    <div v-if="article && article.categories.length > 0" class="categories">
      <span 
        v-for="(cat, index) in article.categories" 
        :key="index"
        class="category-tag"
      >
        {{ cat.name }}
      </span>
    </div>

    <!-- PDF 预览区域 -->
    <div class="pdf-container">
      <div v-if="loadingPdf" class="pdf-loading">
        <div class="spinner"></div>
        <p>正在加载 PDF...</p>
      </div>

      <div v-else-if="pdfError" class="pdf-error">
        <p><SvgIcon name="nav-cross-mark" :size="16" /> PDF 加载失败</p>
        <button @click="retryPdfLoad" class="retry-btn">重试</button>
      </div>

      <div v-else ref="pdfContainerRef" class="pdf-viewer">
        <!-- PDF 将渲染到这里 -->
      </div>
    </div>

    <!-- 下载按钮 -->
    <div v-if="article" class="download-section">
      <div v-if="article.allowDownload" class="download-info">
        <span class="points-cost" :class="{ free: article.downloadPoints === 0 || article.isSystem }">
          {{ article.downloadPoints === 0 || article.isSystem ? '免费下载' : `需要 ${article.downloadPoints} 积分` }}
        </span>
        <span class="balance">当前余额：{{ userBalance }}</span>
      </div>
      
      <button 
        @click="handleDownload"
        :disabled="downloading || !article.allowDownload"
        class="download-btn"
      >
        {{ downloading ? '下载中...' : (article.allowDownload ? (article.downloadPoints === 0 || article.isSystem ? '免费下载 PDF' : '下载 PDF') : '不可下载') }}
      </button>

      <p v-if="!article.allowDownload" class="download-disabled">作者已禁止下载此文章</p>
    </div>

    <!-- 评论区 -->
    <div class="comments-section">
      <h2 class="section-title">评论与评分</h2>
      
      <!-- 评分表单 -->
      <div v-if="!hasRated" class="rating-form">
        <div class="star-rating">
          <span 
            v-for="star in 5" 
            :key="star"
            @click="setRating(star)"
            class="star"
            :class="{ active: ratingScore >= star }"
          >
            <SvgIcon name="nav-star" :size="20" />
          </span>
          <span class="rating-text">{{ ratingScore }} 星</span>
        </div>

        <textarea 
          v-model="commentText"
          placeholder="写下你的评价..."
          class="comment-input"
          rows="3"
        ></textarea>

        <button 
          @click="submitRating"
          :disabled="submitting || ratingScore === 0"
          class="submit-btn"
        >
          提交评价
        </button>
      </div>

      <p v-else class="rated-message"><SvgIcon name="nav-check-mark" :size="16" /> 您已评价过此文，感谢参与！</p>

      <!-- 评论列表 -->
      <div class="comments-list">
        <div 
          v-for="comment in comments" 
          :key="comment.id"
          class="comment-item"
        >
          <div class="comment-header">
            <span class="comment-author">{{ comment.user?.nickname || '匿名用户' }}</span>
            <span class="comment-time">{{ comment.createTime }}</span>
          </div>
          <p class="comment-content">{{ comment.content }}</p>
        </div>
        <p v-if="comments.length === 0" class="comment-empty">暂无评论，快来抢沙发～</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.js?url'
import { request } from '@/api/request'
import { tokenStore } from '@/api/token'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

interface Author {
  id: number
  nickname: string
  avatar?: string
  followerCount: number
  articleCount: number
}

interface Rating {
  average: number
  count: number
}

interface ArticleDetail {
  id: number
  title: string
  description: string
  content?: string
  coverUrl?: string
  pdfFileId: number
  pdfFilename: string
  pdfStoragePath: string
  categories: { code: string, name: string }[]
  tags: string
  author: Author
  isSystem: boolean
  allowDownload: boolean
  status: number
  downloadPoints: number
  viewCount: number
  downloadCount: number
  likeCount: number
  rating?: Rating
  publishTime: string
}

interface CommentItem {
  id: string
  content: string
  createTime: string
  user?: { nickname?: string; avatar?: string }
}

const route = useRoute()
const router = useRouter()

// 状态
const article = ref<ArticleDetail | null>(null)
const pdfContainerRef = ref<HTMLElement | null>(null)
const loadingPdf = ref(true)
const pdfError = ref(false)
const userBalance = ref(0)
const downloading = ref(false)
const ratingScore = ref(0)
const commentText = ref('')
const submitting = ref(false)
const hasRated = ref(false)
const comments = ref<CommentItem[]>([])
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null

// PDF 预览地址：私密目录需登录，preview 端点支持 query token 直链（JwtAuthFilter C11）
const buildPdfUrl = (storagePath: string) => {
  const token = tokenStore.getAccessToken()
  return `${import.meta.env.VITE_API_BASE}/file/preview/${storagePath}${token ? `?access_token=${encodeURIComponent(token)}` : ''}`
}

// 加载文章详情
const loadArticle = async () => {
  try {
    const articleId = route.params.id
    // 响应拦截器已拆包 Result.data，返回值即 ScienceArticleDetailVO
    const data = (await request.get(`/science/articles/${articleId}`)) as unknown as ArticleDetail
    // categories 后端以 JSON 字符串存储，前端解析为数组
    if (typeof (data.categories as unknown) === 'string') {
      try {
        data.categories = JSON.parse((data.categories as unknown as string) || '[]')
      } catch {
        data.categories = []
      }
    }
    article.value = data
    // 阅读计数（静默失败不阻塞）
    request.post(`/science/articles/${articleId}/read`).catch(() => {})
    loadComments()
    loadRatingStatus()
    await loadPdf(data)
  } catch (error) {
    console.error('加载文章失败:', error)
  }
}

// 加载评论列表（CommentVO：{ id, content, createTime, user: { nickname, avatar } }）
const loadComments = async () => {
  try {
    const articleId = route.params.id
    const response = (await request.get(`/science/comments?articleId=${articleId}`)) as unknown as { list: CommentItem[]; total: number }
    comments.value = response.list || []
  } catch (error) {
    console.error('加载评论失败:', error)
  }
}

// 加载当前用户评分状态
const loadRatingStatus = async () => {
  try {
    const articleId = route.params.id
    const data = (await request.get(`/science/articles/${articleId}/rating-status`)) as unknown as { rated: boolean; score: number }
    hasRated.value = !!data?.rated
    ratingScore.value = data?.score || 0
  } catch (error) {
    console.error('加载评分状态失败:', error)
  }
}

// 使用 pdf.js 逐页渲染 PDF 到容器
const loadPdf = async (data: ArticleDetail) => {
  try {
    loadingPdf.value = true
    pdfError.value = false
    if (!data.pdfStoragePath) throw new Error('文章未关联 PDF 文件')

    const loadingTask = pdfjsLib.getDocument({ url: buildPdfUrl(data.pdfStoragePath) })
    const pdf = await loadingTask.promise
    await pdfDoc?.destroy()
    pdfDoc = pdf

    // v-if 切换后需等 DOM 挂载
    loadingPdf.value = false
    await nextTick()
    const container = pdfContainerRef.value
    if (!container) return
    container.innerHTML = ''

    const dpr = window.devicePixelRatio || 1
    const watermark = watermarkText()
    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
      const page = await pdf.getPage(pageNum)
      const baseViewport = page.getViewport({ scale: 1 })
      const scale = container.clientWidth / baseViewport.width
      const viewport = page.getViewport({ scale })

      const canvas = document.createElement('canvas')
      canvas.className = 'pdf-page'
      canvas.style.width = '100%'
      canvas.width = Math.floor(viewport.width * dpr)
      canvas.height = Math.floor(viewport.height * dpr)
      container.appendChild(canvas)

      const ctx = canvas.getContext('2d')
      if (!ctx) continue
      await page.render({
        canvasContext: ctx,
        viewport,
        transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined,
      }).promise
      drawWatermark(ctx, viewport, watermark)
    }
  } catch (error) {
    console.error('PDF 加载失败:', error)
    pdfError.value = true
    loadingPdf.value = false
  }
}

// 预览水印文案：取 JWT payload 中的用户名，匿名则固定文案
const watermarkText = () => {
  try {
    const token = tokenStore.getAccessToken()
    if (!token) return '点废成金·预览版'
    const payload = JSON.parse(atob((token.split('.')[1] || '').replace(/-/g, '+').replace(/_/g, '/')))
    return `点废成金·${payload?.username || payload?.sub || '预览版'}`
  } catch {
    return '点废成金·预览版'
  }
}

// 渲染后叠加半透明斜排水印（防预览另存绕过下载积分）
const drawWatermark = (ctx: CanvasRenderingContext2D, viewport: { width: number; height: number }, text: string) => {
  ctx.save()
  ctx.globalAlpha = 0.12
  ctx.fillStyle = 'var(--df-text-secondary)'
  ctx.font = `600 ${Math.max(20, Math.round(viewport.width * 0.06))}px "Microsoft YaHei", sans-serif`
  ctx.translate(viewport.width / 2, viewport.height / 2)
  ctx.rotate(-Math.PI / 4)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 0, 0)
  ctx.restore()
}

// 重试加载 PDF
const retryPdfLoad = () => {
  if (article.value) {
    loadPdf(article.value)
  }
}

// 下载 PDF（后端扣积分成功后，直链附件下载：preview 端点 ?download=1 返回 Content-Disposition: attachment）
// 不用 blob+a[download]：微信内置浏览器/部分安卓浏览器会尝试打开 blob: URL 报"链接无效"，直链可全端统一触发保存
const handleDownload = async () => {
  if (!article.value) return

  try {
    downloading.value = true
    await request.post(`/science/articles/${article.value.id}/download`)
    const base = buildPdfUrl(article.value.pdfStoragePath)
    const url = `${base}${base.includes('?') ? '&' : '?'}download=1`
    const link = document.createElement('a')
    link.href = url
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    link.remove()
    showToast('下载已开始，请留意浏览器下载通知')
  } catch (error) {
    console.error('下载失败:', error)
    showToast('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 设置评分
const setRating = (score: number) => {
  ratingScore.value = score
}

// 提交评价
const submitRating = async () => {
  if (!article.value || ratingScore.value === 0) return
  
  try {
    submitting.value = true
    await request.post(`/science/articles/${article.value.id}/rating`, {
      score: ratingScore.value,
      comment: commentText.value.trim()
    })
    
    // 短评同步发布为评论，评论列表可见
    if (commentText.value.trim()) {
      await request.post(`/science/articles/${article.value.id}/comments`, { content: commentText.value.trim() }).catch(() => {})
    }
    
    hasRated.value = true
    showToast('评价成功！')
    loadComments()
    loadRatingStatus()
  } catch (error) {
    console.error('提交评价失败:', error)
  } finally {
    submitting.value = false
  }
}

// 格式化日期（兼容 "yyyy-MM-dd HH:mm:ss" 字符串）
const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr.includes(' ') ? dateStr.replace(' ', 'T') : dateStr)
  return isNaN(d.getTime()) ? dateStr : d.toLocaleDateString('zh-CN')
}

// 返回上一页
const goBack = () => {
  router.back()
}

onMounted(() => {
  loadArticle()
})

onUnmounted(() => {
  pdfDoc?.destroy()
  pdfDoc = null
})
</script>

<style scoped>
.article-detail-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
}

.back-btn {
  padding: 8px 16px;
  background: white;
  border: 1px solid var(--df-border-color);
  border-radius: 6px;
  cursor: pointer;
}

.title {
  font-size: 28px;
  color: var(--df-text-primary);
  flex: 1;
}

.author-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--df-border-color);
}

.author-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.author-meta {
  display: flex;
  flex-direction: column;
}

.author-name {
  font-weight: 600;
  color: var(--df-text-primary);
}

.author-stats {
  font-size: 13px;
  color: var(--df-text-secondary);
}

.publish-time {
  font-size: 14px;
  color: var(--df-text-placeholder);
}

.stats-bar {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  font-size: 14px;
  color: var(--df-text-secondary);
}

.categories {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.category-tag {
  padding: 4px 12px;
  background: var(--df-bg-nested);
  border-radius: 12px;
  font-size: 13px;
  color: var(--df-text-regular);
}

.pdf-container {
  min-height: 600px;
  background: var(--df-bg-nested);
  border-radius: 12px;
  margin-bottom: 24px;
  position: relative;
}

.pdf-loading,
.pdf-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--df-bg-nested);
  border-top: 4px solid var(--df-color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.retry-btn {
  padding: 8px 24px;
  background: var(--df-color-primary);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.pdf-viewer {
  padding: 8px 0;
}

.pdf-viewer :deep(.pdf-page) {
  display: block;
  width: 100%;
  margin: 0 auto 8px;
  background: white;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

.download-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  margin-bottom: 24px;
}

.download-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.points-cost {
  font-size: 18px;
  font-weight: 600;
  color: var(--df-color-danger);
}

.points-cost.free {
  color: var(--df-color-carbon);
}

.balance {
  font-size: 14px;
  color: var(--df-text-secondary);
}

.download-btn {
  width: 100%;
  padding: 12px;
  background: var(--df-gradient-brand);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.download-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.download-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.download-disabled {
  margin-top: 12px;
  text-align: center;
  color: var(--df-text-placeholder);
  font-size: 14px;
}

.comments-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--df-text-primary);
}

.rating-form {
  margin-bottom: 24px;
  padding: 20px;
  background: var(--df-bg-nested);
  border-radius: 8px;
}

.star-rating {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.star {
  font-size: 24px;
  color: var(--df-border-color);
  cursor: pointer;
  transition: color 0.2s;
}

.star:hover,
.star.active {
  color: var(--df-color-warning);
}

.rating-text {
  margin-left: 12px;
  font-size: 14px;
  color: var(--df-text-secondary);
}

.comment-input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid var(--df-border-color);
  border-radius: 8px;
  font-size: 14px;
  resize: vertical;
  margin-bottom: 16px;
}

.submit-btn {
  padding: 10px 24px;
  background: var(--df-color-carbon);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.rated-message {
  text-align: center;
  color: var(--df-color-carbon);
  font-size: 16px;
  margin-bottom: 24px;
}

.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--df-border-color);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 14px;
}

.comment-author {
  font-weight: 600;
  color: var(--df-text-primary);
}

.comment-time {
  color: var(--df-text-placeholder);
  font-size: 13px;
}

.comment-empty {
  text-align: center;
  color: var(--df-text-placeholder);
  font-size: 14px;
  padding: 24px 0;
}

.comment-content {
  font-size: 14px;
  color: var(--df-text-regular);
  line-height: 1.6;
}
</style>
