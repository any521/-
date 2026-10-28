<script setup lang="ts">
// PC 科普专栏：文章详情（PDF 在线预览 / 积分下载 / 点赞 / 评论）
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.js?url'
import { request } from '@/api/request'
import { tokenStore } from '@/api/token'
import { getCarbonAccount } from '@/api/business'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

defineOptions({ name: 'pc-science-article' })

interface Author {
  id: number
  nickname: string
  avatar: string
  points: number
}

interface Comment {
  id: number
  content: string
  createTime: string
  user: {
    nickname: string
    avatar: string
  }
}

interface ArticleDetail {
  id: number
  title: string
  description: string
  content: string
  coverUrl: string
  pdfUrl: string
  pdfStoragePath: string
  pdfFilename: string
  author: Author
  viewCount: number
  downloadCount: number
  likeCount: number
  isSystem: boolean
  allowDownload: boolean
  status: number
  downloadPoints: number
  categories: string
  tags: string
  publishTime: string
}

const route = useRoute()
const router = useRouter()
// 雪花 ID 保持字符串，Number 转换丢精度致文章详情查无此篇
const articleId = String(route.params.id)

const article = ref<ArticleDetail | null>(null)
const comments = ref<Comment[]>([])
const commentPage = ref(1)
const commentTotal = ref(0)
const loading = ref(true)
const pdfVisible = ref(false)
const pdfSrc = ref('')
const pdfContainerRef = ref<HTMLElement | null>(null)
const pdfLoading = ref(false)
const pdfError = ref(false)
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
const downloading = ref(false)
const liked = ref(false)
const commentText = ref('')
const submittingComment = ref(false)
const userBalance = ref(0)

// PDF 预览 URL：优先后端直链，其次本地代理；science 为私有目录需带 query token（JwtAuthFilter C11）
const previewUrl = computed(() => {
  if (!article.value) return ''
  const token = tokenStore.getAccessToken()
  const q = token ? `?access_token=${encodeURIComponent(token)}` : ''
  if (article.value.pdfStoragePath) {
    return `${import.meta.env.VITE_API_BASE}/file/preview/${article.value.pdfStoragePath}${q}`
  }
  return article.value.pdfUrl || `/api/file/preview/${article.value.pdfFilename}${q}`
})

// 封面同属 science 私有目录，<img> 无请求头需带 query token 直链
const coverWithToken = (url: string) => {
  if (!url) return ''
  const storagePath = url.replace(/^\/api\/file\/preview\//, '')
  return storagePath ? `${import.meta.env.VITE_API_BASE}/file/preview/${storagePath}${tokenStore.getAccessToken() ? `?access_token=${encodeURIComponent(tokenStore.getAccessToken()!)}` : ''}` : url
}

// 加载文章详情
const loadArticle = async () => {
  try {
    const data = (await request.get(`/science/articles/${articleId}`)) as unknown as ArticleDetail
    article.value = data
    // 图片 URL 补全
    if (data.coverUrl && data.coverUrl.startsWith('/')) {
      data.coverUrl = data.coverUrl
    }
    // 分页数据已由 loadComments 处理
  } catch (error) {
    console.error('加载文章失败:', error)
    showToast('文章不存在或已删除')
  } finally {
    loading.value = false
  }
}

// 加载评论
const loadComments = async (page: number = 1) => {
  try {
    const params = new URLSearchParams({
      pageNum: page.toString(),
      pageSize: '20',
      articleId: articleId.toString(),
    })
    // 拦截器已拆包 Result.data，返回即 PageVO { list, total }
    const response = (await request.get(`/science/comments?${params.toString()}`)) as unknown as {
      list: Comment[]
      total: number
    }
    comments.value = response.list || []
    commentTotal.value = response.total || 0
    commentPage.value = page
  } catch (error) {
    console.error('加载评论失败:', error)
  }
}

// 在线预览 PDF：iframe 直链会被后端 X-Frame-Options: DENY 拦截（弹窗空白），
// 改用 pdf.js 拉取渲染；预览版叠加水印（防另存绕过下载积分）
const openPreview = async () => {
  pdfSrc.value = previewUrl.value
  pdfVisible.value = true
  // 锁定背景滚动，避免滚轮穿透到后面的页面
  document.body.style.overflow = 'hidden'
  await nextTick()
  await renderPdf(previewUrl.value)
}

// 关闭预览弹窗：恢复背景滚动并释放 PDF 资源
const closePreview = () => {
  pdfVisible.value = false
  document.body.style.overflow = ''
  pdfDoc?.destroy()
  pdfDoc = null
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

// 渲染后叠加半透明斜排水印
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

// pdf.js 逐页渲染到弹窗容器
const renderPdf = async (url: string) => {
  try {
    pdfLoading.value = true
    pdfError.value = false
    const loadingTask = pdfjsLib.getDocument({ url })
    const pdf = await loadingTask.promise
    await pdfDoc?.destroy()
    pdfDoc = pdf
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
      canvas.className = 'pdf-preview-page'
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
    console.error('PDF 预览失败:', error)
    pdfError.value = true
  } finally {
    pdfLoading.value = false
  }
}

onUnmounted(() => {
  document.body.style.overflow = ''
  pdfDoc?.destroy()
  pdfDoc = null
})

// 下载 PDF（免费/积分）
const handleDownload = async () => {
  if (!article.value) return
  if (!article.value.allowDownload) {
    showToast('作者已禁止下载此文章')
    return
  }

  if (article.value.downloadPoints > 0 && !article.value.isSystem) {
    // 扣积分确认（官方文章免费，不弹确认）
    try {
      await showConfirmDialog({
        title: '确认下载',
        message: `下载本文需扣除 ${article.value.downloadPoints} 积分，当前余额 ${userBalance.value} 积分`,
        confirmButtonText: '确认下载',
      })
    } catch {
      return
    }
  }

  try {
    downloading.value = true
    const response = (await request.post(`/science/articles/${articleId}/download`)) as unknown as {
      downloadUrl?: string
    }
    // 优先使用后端返回的直链，否则走预览地址
    const url = response?.downloadUrl || previewUrl.value
    // blob 方式直接保存文件：preview 端点返回内联 PDF，window.open 会新开预览页
    // （需手动 Ctrl+S 保存，且直连 8080 在代理不可用时报"拒绝连接"），blob URL 强制触发下载
    const resp = await fetch(url)
    if (!resp.ok) throw new Error(`下载失败(HTTP ${resp.status})`)
    const blob = await resp.blob()
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = article.value.pdfFilename || 'article.pdf'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(objectUrl)
    showToast(article.value.downloadPoints > 0 ? '下载成功，已扣除积分' : '下载成功')
    article.value.downloadCount += 1
  } catch (error: any) {
    showToast(error?.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

// 点赞
const toggleLike = async () => {
  if (!article.value) return
  try {
    if (liked.value) {
      await request.delete(`/science/articles/${articleId}/like`)
      liked.value = false
      article.value.likeCount = Math.max(0, article.value.likeCount - 1)
    } else {
      await request.post(`/science/articles/${articleId}/like`)
      liked.value = true
      article.value.likeCount += 1
    }
  } catch (error: any) {
    showToast(error?.message || '操作失败')
  }
}

// 提交评论
const submitComment = async () => {
  const content = commentText.value.trim()
  if (!content) {
    showToast('请输入评论内容')
    return
  }
  try {
    submittingComment.value = true
    await request.post(`/science/articles/${articleId}/comments`, { content })
    commentText.value = ''
    showToast('评论成功')
    await loadComments()
  } catch (error: any) {
    showToast(error?.message || '评论失败')
  } finally {
    submittingComment.value = false
  }
}

onMounted(() => {
  loadArticle()
  loadComments()
  // 加载用户积分余额（未登录时静默失败）
  try {
    const token = tokenStore.getAccessToken()
    if (token) {
      getCarbonAccount().then((acc) => {
        userBalance.value = acc.points
      }).catch(() => {})
    }
  } catch {
    // 忽略余额加载失败
  }
})
</script>

<template>
  <div class="pc-article">
    <div class="container">
      <!-- 加载中 -->
      <div v-if="loading" class="state">加载中…</div>

      <!-- 文章主体 -->
      <template v-else-if="article">
        <div class="article-header">
          <h1 class="article-title">{{ article.title }}</h1>
          <div class="article-meta">
            <span class="meta-item"><SvgIcon name="nav-user" :size="14" /> {{ article.author?.nickname }}</span>
            <span class="meta-item"><SvgIcon name="nav-calendar" :size="14" /> {{ article.publishTime }}</span>
            <span class="meta-item"><SvgIcon name="nav-eye" :size="14" /> {{ article.viewCount }}</span>
            <span class="meta-item"><SvgIcon name="nav-download" :size="14" /> {{ article.downloadCount }}</span>
            <span class="meta-item"><SvgIcon name="nav-heart" :size="14" /> {{ article.likeCount }}</span>
            <span v-if="article.isSystem" class="meta-badge">官方</span>
          </div>
        </div>

        <div class="article-body">
          <!-- 封面 -->
          <div v-if="article.coverUrl" class="article-cover">
            <img :src="coverWithToken(article.coverUrl)" :alt="article.title" />
          </div>

          <!-- 简介 -->
          <p class="article-desc">{{ article.description }}</p>

          <!-- PDF 操作区 -->
          <div class="pdf-panel">
            <div class="pdf-panel__info">
              <span class="pdf-icon"><SvgIcon name="nav-file" :size="20" /></span>
              <div>
                <p class="pdf-name">{{ article.pdfFilename }}</p>
                <p class="pdf-points">
                  {{ article.allowDownload ? (article.downloadPoints === 0 || article.isSystem ? '免费下载' : `下载需 ${article.downloadPoints} 积分`) : '作者已禁止下载' }}
                </p>
              </div>
            </div>
            <div class="pdf-panel__actions">
              <button class="btn btn-outline" @click="openPreview">在线预览</button>
              <button
                v-if="article.allowDownload"
                class="btn btn-primary"
                :disabled="downloading"
                @click="handleDownload"
              >
                {{ downloading ? '下载中…' : (article.downloadPoints === 0 || article.isSystem ? '免费下载' : '积分下载') }}
              </button>
            </div>
          </div>
        </div>

        <!-- 互动区 -->
        <div class="article-actions">
          <button class="btn btn-outline" :class="{ liked }" @click="toggleLike">
            {{ liked ? '' : '' }}<SvgIcon :name="liked ? 'nav-heart' : 'nav-heart-empty'" :size="14" /> {{ liked ? '已点赞' : '点赞' }} ({{ article.likeCount }})
          </button>
          <button class="btn btn-outline" @click="router.back()">← 返回列表</button>
        </div>

        <!-- 评论区 -->
        <div class="comment-section">
          <h3 class="comment-title">评论 ({{ commentTotal }})</h3>

          <div class="comment-editor">
            <textarea
              v-model="commentText"
              class="comment-input"
              rows="3"
              placeholder="发表你的看法…"
            ></textarea>
            <button class="btn btn-primary comment-submit" :disabled="submittingComment" @click="submitComment">
              {{ submittingComment ? '提交中…' : '发表评论' }}
            </button>
          </div>

          <div v-if="comments.length === 0" class="state">暂无评论，快来抢沙发～</div>

          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <div class="comment-avatar">
              <img v-if="comment.user?.avatar" :src="comment.user.avatar" alt="" />
              <span v-else>{{ (comment.user?.nickname || '用户')[0] }}</span>
            </div>
            <div class="comment-main">
              <p class="comment-nickname">{{ comment.user?.nickname || '用户' }}</p>
              <p class="comment-content">{{ comment.content }}</p>
              <p class="comment-time">{{ comment.createTime }}</p>
            </div>
          </div>
        </div>

        <!-- PDF 预览弹窗 -->
        <div v-if="pdfVisible" class="pdf-overlay" @click.self="closePreview" @wheel.self.prevent>
          <div class="pdf-modal">
            <div class="pdf-modal__header">
              <span>{{ article.pdfFilename }}</span>
              <button class="pdf-modal__close" @click="closePreview"><SvgIcon name="nav-close" :size="14" /></button>
            </div>
            <div class="pdf-modal__body" ref="pdfContainerRef">
              <div v-if="pdfLoading" class="pdf-modal__state">正在加载 PDF…</div>
              <div v-else-if="pdfError" class="pdf-modal__state">PDF 预览失败，请重试</div>
            </div>
          </div>
        </div>
      </template>

      <!-- 加载失败 -->
      <div v-else class="state">
        <p>文章不存在或已删除</p>
        <button class="btn btn-primary" @click="router.back()">返回列表</button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-article {
  min-height: calc(100vh - 72px);
  padding: 48px 24px 80px;
}

.container {
  max-width: 900px;
  margin: 0 auto;
}

.state {
  text-align: center;
  padding: 80px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.article-header {
  margin-bottom: 32px;
}

.article-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--df-text-primary);
  line-height: 1.4;
  margin-bottom: 16px;
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  font-size: 14px;
  color: var(--df-text-secondary);
}

.meta-badge {
  padding: 2px 10px;
  font-size: 12px;
  color: white;
  background: var(--df-color-primary);
  border-radius: 10px;
}

.article-body {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 40px;
  box-shadow: var(--df-shadow-card);
}

.article-cover {
  margin-bottom: 24px;

  img {
    width: 100%;
    max-height: 400px;
    object-fit: cover;
    border-radius: 12px;
  }
}

.article-desc {
  font-size: 16px;
  line-height: 1.8;
  color: var(--df-text-regular);
  margin-bottom: 32px;
}

.pdf-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  padding: 24px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 12px;
  background: var(--df-bg-page, var(--df-bg-nested));
}

.pdf-panel__info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.pdf-icon {
  font-size: 36px;
}

.pdf-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 4px;
}

.pdf-points {
  font-size: 14px;
  color: var(--df-color-primary);
}

.pdf-panel__actions {
  display: flex;
  gap: 12px;
}

.btn {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;
}

.btn-primary {
  background: var(--df-color-primary);
  color: white;

  &:hover:not(:disabled) {
    opacity: 0.9;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.btn-outline {
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-color-primary);

  &:hover {
    background: var(--df-color-primary);
    color: white;
  }
}

.liked {
  background: var(--df-color-danger);
  border-color: var(--df-color-danger);
  color: white;

  &:hover {
    background: var(--df-color-danger);
    color: white;
  }
}

.article-actions {
  display: flex;
  gap: 16px;
  margin: 32px 0;
}

.comment-section {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-card);
}

.comment-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 24px;
}

.comment-editor {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  margin-bottom: 32px;
}

.comment-input {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 8px;
  font-size: 14px;
  background: var(--df-bg-page, var(--df-bg-nested));
  color: var(--df-text-primary);
  resize: vertical;
  outline: none;

  &:focus {
    border-color: var(--df-color-primary);
  }
}

.comment-submit {
  flex-shrink: 0;
}

.comment-item {
  display: flex;
  gap: 16px;
  padding: 20px 0;
  border-bottom: 1px solid var(--df-border-color, var(--df-border-color));

  &:last-child {
    border-bottom: none;
  }
}

.comment-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--df-color-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.comment-main {
  flex: 1;
}

.comment-nickname {
  font-size: 14px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 4px;
}

.comment-content {
  font-size: 14px;
  color: var(--df-text-regular);
  line-height: 1.6;
  margin-bottom: 8px;
}

.comment-time {
  font-size: 12px;
  color: var(--df-text-secondary);
}

.pdf-overlay {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, var(--df-text-primary) 60%, transparent);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.pdf-modal {
  width: 90%;
  height: 90%;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.pdf-modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--df-bg-page, var(--df-bg-nested));
  font-size: 14px;
  font-weight: 600;
  color: var(--df-text-primary);
}

.pdf-modal__close {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  color: var(--df-text-secondary);

  &:hover {
    color: var(--df-text-primary);
  }
}

.pdf-modal__body {
  flex: 1;
  overflow-y: auto;
  background: var(--df-bg-nested);
  // 左侧内边距加大：PDF 页面相对向右移动一点（视觉居中偏右，避免贴左白边观感）
  padding: 16px 16px 16px 48px;
}

.pdf-modal__state {
  text-align: center;
  color: var(--df-text-secondary);
  padding: 60px 0;
  font-size: 14px;
}

.pdf-preview-page {
  display: block;
  margin: 0 auto 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  background: white;
}
</style>
