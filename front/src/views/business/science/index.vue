<template>
  <div class="admin-science-container">
    <!-- 报头：刊名 + 发行日期 + 版本号（双线报头） -->
    <header class="sci-masthead">
      <div class="sci-masthead__top">
        <div class="sci-masthead__title">
          <h1>点废成金 · 科普专栏</h1>
          <p class="sci-masthead__slogan">让每一次分类都被看见</p>
        </div>
        <button class="btn-upload" @click="openCreate">＋ 上传文章</button>
      </div>
      <div class="sci-masthead__meta">
        <span class="sci-masthead__date">{{ today }}</span>
        <span class="sci-masthead__ver">科普编辑部 · v2.0</span>
      </div>
    </header>

    <!-- 顶部统计看板 -->
    <div class="dashboard-panel">
      <div class="dashboard-head">
        <h2><el-icon><DataLine /></el-icon> 数据概览</h2>
      </div>

      <div class="stats-grid">
        <div class="stat-card">
          <h3><el-icon><Document /></el-icon> 总文章数</h3>
          <div class="value">{{ overview.totalArticles || 0 }}</div>
          <div class="sub">官方 {{ overview.systemArticles || 0 }} · 用户 {{ overview.userArticles || 0 }}</div>
        </div>
        <div class="stat-card">
          <h3><el-icon><CircleCheck /></el-icon> 已发布</h3>
          <div class="value">{{ overview.publishedArticles || 0 }}</div>
        </div>
        <div class="stat-card stat-card--warn">
          <h3><el-icon><Clock /></el-icon> 待审核</h3>
          <div class="value">{{ overview.pendingAudit || 0 }}</div>
        </div>
        <div class="stat-card">
          <h3><el-icon><View /></el-icon> 总阅读量</h3>
          <div class="value">{{ overview.totalViews || 0 }}</div>
        </div>
        <div class="stat-card">
          <h3><el-icon><Download /></el-icon> 总下载次数</h3>
          <div class="value">{{ overview.totalDownloads || 0 }}</div>
        </div>
        <div class="stat-card">
          <h3><el-icon><Star /></el-icon> 创作者数</h3>
          <div class="value">{{ overview.totalAuthors || 0 }}</div>
          <div class="sub">累计收益 {{ totalIncomeText }}</div>
        </div>
      </div>

      <!-- 热门榜单 -->
      <div v-if="topArticles.length || topAuthors.length" class="rank-row">
        <div v-if="topArticles.length" class="rank-col">
          <h4><el-icon><Flag /></el-icon> 热门文章 Top5</h4>
          <div v-for="(a, i) in topArticles" :key="a.id" class="rank-item">
            <span class="rank-no">{{ i + 1 }}</span>
            <span class="rank-title">{{ a.title }}</span>
            <span class="rank-num"><el-icon><View /></el-icon> {{ a.viewCount }} · <el-icon><Download /></el-icon> {{ a.downloadCount }}</span>
          </div>
        </div>
        <div v-if="topAuthors.length" class="rank-col">
          <h4><el-icon><Trophy /></el-icon> 热门作者 Top5</h4>
          <div v-for="(a, i) in topAuthors" :key="a.userId" class="rank-item">
            <span class="rank-no">{{ i + 1 }}</span>
            <span class="rank-title">{{ a.nickname }}</span>
            <span class="rank-num">{{ a.articleCount }} 篇 · <el-icon><Coin /></el-icon> {{ a.totalIncome || 0 }} 积分</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 筛选与操作区 -->
    <div class="filter-section">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索文章标题..."
        class="search-input"
        @keyup.enter="loadArticles(1)"
      >
      <select v-model="statusFilter" class="status-filter" @change="loadArticles(1)">
        <option value="">全部状态</option>
        <option value="0">待审核</option>
        <option value="1">已发布</option>
        <option value="2">已下架</option>
        <option value="3">被驳回</option>
      </select>
      <button class="btn-search" @click="loadArticles(1)">搜索</button>
    </div>

    <!-- 文章列表表格 -->
    <div class="table-wrapper">
      <table class="science-table">
        <thead>
          <tr>
            <th>封面/标题</th>
            <th>作者</th>
            <th>分类</th>
            <th>下载设置</th>
            <th>数据统计</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="article in articles" :key="article.id">
            <td>
              <div class="title-cell">
                <img v-if="article.coverUrl" :src="previewWithToken(article.coverUrl)" class="cover-thumb" alt="">
                <div class="title-info">
                  <span class="title-text">{{ article.title }}</span>
                  <span v-if="article.isSystem" class="system-tag">官方</span>
                  <span v-if="article.isTop > 0" class="top-tag">置顶</span>
                </div>
              </div>
            </td>
            <td>{{ article.authorName }}</td>
            <td>{{ article.categories || '-' }}</td>
            <td>
              <span v-if="!article.allowDownload" class="no-download"><el-icon><Lock /></el-icon> 禁止下载</span>
              <template v-else>
                <span :class="['points-text', { 'points-free': article.downloadPoints === 0 }]">
                  {{ article.downloadPoints === 0 ? '' : `${article.downloadPoints} 积分` }}<el-icon v-if="article.downloadPoints === 0"><Present /></el-icon><template v-if="article.downloadPoints === 0"> 免费</template>
                </span>
              </template>
            </td>
            <td>
              <div class="data-stats">
                <span title="阅读"><el-icon><View /></el-icon> {{ article.viewCount }}</span>
                <span title="下载"><el-icon><Download /></el-icon> {{ article.downloadCount }}</span>
                <span title="点赞"><el-icon><Star /></el-icon> {{ article.likeCount }}</span>
              </div>
            </td>
            <td>
              <span :class="['status-badge', getStatusClass(article.status)]">
                {{ getStatusText(article.status) }}
              </span>
            </td>
            <td>
              <div class="action-cells">
                <button v-if="article.status === 0" @click="openAudit(article)" class="btn-text btn-audit">审核</button>
                <button v-if="article.status === 1" @click="toggleStatus(article, 2)" class="btn-text btn-warn">下架</button>
                <button v-else-if="article.status === 2" @click="toggleStatus(article, 1)" class="btn-text btn-success">上架</button>
                <button v-if="article.isSystem" @click="openEdit(article)" class="btn-text">编辑</button>
                <button @click="openPoints(article)" class="btn-text">积分配置</button>
                <button @click="handleDelete(article)" class="btn-text btn-danger">删除</button>
              </div>
            </td>
          </tr>

          <tr v-if="articles.length === 0">
            <td colspan="7" class="empty-cell">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <button :disabled="pageNum === 1" @click="loadArticles(pageNum - 1)" class="page-btn">上一页</button>
      <span class="page-info">{{ pageNum }} / {{ totalPages }}</span>
      <button :disabled="pageNum >= totalPages" @click="loadArticles(pageNum + 1)" class="page-btn">下一页</button>
    </div>

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditVisible" width="760px" top="5vh">
      <template #header>
        <div class="dialog-title"><el-icon><Search /></el-icon> 文章审核</div>
      </template>
      <div v-if="auditDetail" class="audit-body">
        <div class="audit-info">
          <div class="info-row"><span class="label">标题：</span><span class="value">{{ auditDetail.title }}</span></div>
          <div class="info-row"><span class="label">作者：</span><span class="value">{{ auditDetail.author?.nickname || '-' }}</span></div>
          <div class="info-row"><span class="label">简介：</span><span class="value">{{ auditDetail.description || '-' }}</span></div>
          <div class="info-row">
            <span class="label">下载设置：</span>
            <span class="value">
              {{ auditDetail.allowDownload ? (auditDetail.downloadPoints === 0 ? '免费下载' : `${auditDetail.downloadPoints} 积分`) : '禁止下载' }}
            </span>
          </div>
        </div>
        <div class="pdf-frame-wrap">
          <iframe v-if="auditPdfUrl" :src="auditPdfUrl" class="pdf-frame"></iframe>
          <div v-else class="pdf-empty">该文章未关联 PDF 文件</div>
        </div>
        <div class="audit-remark">
          <label class="label">审核意见：</label>
          <el-input
            v-model="auditRemark"
            type="textarea"
            :rows="2"
            placeholder="通过时可留空；驳回时建议填写原因"
          ></el-input>
        </div>
      </div>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="danger" @click="handleAudit(false)"><el-icon><Close /></el-icon> 驳回</el-button>
        <el-button type="success" @click="handleAudit(true)"><el-icon><Check /></el-icon> 通过</el-button>
      </template>
    </el-dialog>

    <!-- 积分配置弹窗 -->
    <el-dialog v-model="pointsVisible" width="480px">
      <template #header>
        <div class="dialog-title"><el-icon><Coin /></el-icon> 积分配置</div>
      </template>
      <div class="points-form">
        <div class="form-row">
          <label class="label">单次下载积分：</label>
          <el-input-number v-model="pointsForm.downloadPoints" :min="0" :max="100"></el-input-number>
          <div class="form-hint">0 表示免费下载</div>
        </div>
        <div class="form-row">
          <label class="label">基础单价 (元)：</label>
          <el-input-number v-model="pointsForm.basePoints" :min="0.1" :max="10" :step="0.1" :precision="2"></el-input-number>
        </div>
        <div class="form-row">
          <label class="label">热门系数：</label>
          <el-input-number v-model="pointsForm.multiplier" :min="1" :max="5" :step="0.1" :precision="1"></el-input-number>
        </div>
        <div class="form-hint">作者收益 = 下载次数 × 基础单价 × 热门系数</div>
      </div>
      <template #footer>
        <el-button @click="pointsVisible = false">取消</el-button>
        <el-button type="primary" @click="savePoints">保存</el-button>
      </template>
    </el-dialog>

    <!-- 上传/编辑文章弹窗 -->
    <el-dialog
      v-model="createVisible"
      :title="createForm.id ? '编辑系统文章' : '上传科普文章'"
      width="720px"
      top="4vh"
    >
      <div class="create-form">
        <div class="form-row">
          <label class="label">文章标题 *</label>
          <el-input v-model="createForm.title" maxlength="200" placeholder="请输入文章标题"></el-input>
        </div>
        <div class="form-row">
          <label class="label">文章简介 *</label>
          <el-input v-model="createForm.description" type="textarea" :rows="2" maxlength="1000" placeholder="简要介绍文章内容"></el-input>
        </div>
        <div class="form-row form-row--half">
          <div>
            <label class="label">作者名称 *</label>
            <el-input v-model="createForm.authorName" maxlength="64" placeholder="对外展示的作者名"></el-input>
          </div>
          <div>
            <label class="label">所属分类 *</label>
            <el-select v-model="createForm.categoryCode" placeholder="请选择分类" style="width: 100%">
              <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code"></el-option>
            </el-select>
          </div>
        </div>
        <div class="form-row form-row--half">
          <div>
            <label class="label">封面图片 *</label>
            <div class="upload-box" @click="coverInputRef?.click()">
              <img v-if="createForm.coverUrl" :src="previewWithToken(createForm.coverUrl)" class="cover-preview">
              <span v-else>{{ coverUploading ? '上传中...' : '点击上传封面（JPG/PNG，≤5MB）' }}</span>
            </div>
            <input ref="coverInputRef" type="file" accept=".jpg,.jpeg,.png" style="display:none" @change="onCoverPick">
          </div>
          <div>
            <label class="label">PDF 文件 *</label>
            <div class="upload-box" @click="pdfInputRef?.click()">
              <span v-if="createForm.pdfFilename"><el-icon><Document /></el-icon> {{ createForm.pdfFilename }}</span>
              <span v-else>{{ pdfUploading ? '上传中...' : '点击上传 PDF（≤50MB）' }}</span>
            </div>
            <input ref="pdfInputRef" type="file" accept=".pdf" style="display:none" @change="onPdfPick">
          </div>
        </div>
        <div class="form-row">
          <label class="label">下载设置 *</label>
          <div class="download-config">
            <el-radio-group v-model="createForm.allowDownload">
              <el-radio :value="1">允许下载</el-radio>
              <el-radio :value="0">禁止下载</el-radio>
            </el-radio-group>
            <template v-if="createForm.allowDownload === 1">
              <el-radio-group v-model="createForm.downloadPoints" class="points-group">
                <el-radio :value="0">免费</el-radio>
                <el-radio :value="10">收费</el-radio>
              </el-radio-group>
              <el-input-number
                v-if="createForm.downloadPoints > 0"
                v-model="createForm.downloadPoints"
                :min="1"
                :max="100"
                placeholder="积分 1-100"
              ></el-input-number>
            </template>
          </div>
        </div>
        <div class="form-row">
          <label class="label">置顶排序（可选，越大越靠前）</label>
          <el-input-number v-model="createForm.isTop" :min="0" :max="999"></el-input-number>
        </div>
        <div class="form-row">
          <label class="label">标签（逗号分隔）</label>
          <el-input v-model="createForm.tags" placeholder="例如：垃圾分类,低碳生活"></el-input>
        </div>
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitArticle">
          {{ createForm.id ? '保存修改' : '上传并发布' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/request'
import { tokenStore } from '@/api/token'

interface ArticleItem {
  id: number
  title: string
  description: string
  coverUrl?: string
  authorName: string
  categories: string
  isSystem: boolean
  allowDownload: boolean
  downloadPoints: number
  isTop: number
  viewCount: number
  downloadCount: number
  likeCount: number
  status: number
}

interface DashboardData {
  overview: {
    totalArticles: number
    systemArticles: number
    userArticles: number
    pendingAudit: number
    publishedArticles: number
    totalViews: number
    totalDownloads: number
    totalAuthors: number
  }
  topArticles: ArticleItem[]
  topAuthors: { userId: number; nickname: string; articleCount: number; totalIncome: number }[]
}

const categories = ref<{ code: string; name: string }[]>([])

// 报头发行日期（报刊发行样式）
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})

// ---- 动态分类 ----
const loadCategories = async () => {
  try {
    const data = (await request.get('/science/categories')) as unknown as { code: string; name: string }[]
    categories.value = data || []
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

const keyword = ref('')
const statusFilter = ref('')
const pageNum = ref(1)
const pageSize = 10
const articles = ref<ArticleItem[]>([])
const totalPages = ref(1)
const overview = ref<DashboardData['overview']>({
  totalArticles: 0, systemArticles: 0, userArticles: 0, pendingAudit: 0,
  publishedArticles: 0, totalViews: 0, totalDownloads: 0, totalAuthors: 0,
})
const topArticles = ref<ArticleItem[]>([])
const topAuthors = ref<DashboardData['topAuthors']>([])

const totalIncomeText = computed(() => {
  const sum = topAuthors.value.reduce((acc, a) => acc + (a.totalIncome || 0), 0)
  return sum ? `${sum} 积分` : '-'
})

// ---- 列表 ----
const loadArticles = async (page: number = 1) => {
  try {
    pageNum.value = page
    const params: Record<string, string | number> = {
      pageNum: page,
      pageSize,
    }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (statusFilter.value) params.status = statusFilter.value

    const response = (await request.get('/system/science/articles', params)) as unknown as {
      list: ArticleItem[]
      total: number
    }
    articles.value = response.list || []
    totalPages.value = Math.ceil((response.total || 0) / pageSize)
  } catch (error) {
    console.error('加载文章失败:', error)
  }
}

// ---- 看板 ----
const loadDashboard = async () => {
  try {
    const data = (await request.get('/system/science/dashboard')) as unknown as DashboardData
    overview.value = data.overview || overview.value
    topArticles.value = data.topArticles || []
    topAuthors.value = data.topAuthors || []
  } catch (error) {
    console.error('加载看板失败:', error)
  }
}

// ---- 状态工具 ----
const getStatusClass = (status: number): string => {
  const map: Record<number, string> = { 0: 'status-pending', 1: 'status-published', 2: 'status-withdrawn', 3: 'status-rejected' }
  return map[status] || ''
}

const getStatusText = (status: number): string => {
  const map: Record<number, string> = { 0: '待审核', 1: '已发布', 2: '已下架', 3: '被驳回' }
  return map[status] || '未知'
}

// ---- 审核 ----
const auditVisible = ref(false)
const auditDetail = ref<any>(null)
const auditPdfUrl = ref('')
const auditRemark = ref('')

const buildPreviewUrl = (storagePath: string) => {
  const token = tokenStore.getAccessToken()
  return `${import.meta.env.VITE_API_BASE}/file/preview/${storagePath}${token ? `?access_token=${encodeURIComponent(token)}` : ''}`
}

// science 为私有目录，<img> 无请求头需带 query token 直链（JwtAuthFilter C11）；保存到后端的一律为纯路径
const previewWithToken = (url: string) => {
  if (!url) return ''
  const storagePath = url.replace(/^\/api\/file\/preview\//, '')
  return storagePath ? buildPreviewUrl(storagePath) : url
}

const openAudit = async (article: ArticleItem) => {
  try {
    const data = (await request.get(`/system/science/articles/${article.id}`)) as unknown as any
    auditDetail.value = data
    auditPdfUrl.value = data.pdfStoragePath ? buildPreviewUrl(data.pdfStoragePath) : ''
    auditRemark.value = ''
    auditVisible.value = true
  } catch (error) {
    console.error('加载文章详情失败:', error)
  }
}

const handleAudit = async (pass: boolean) => {
  if (!auditDetail.value) return
  if (!pass && !auditRemark.value.trim()) {
    ElMessage.warning('驳回时请填写审核意见')
    return
  }
  try {
    await request.put(`/system/science/articles/${auditDetail.value.id}/audit`, {
      pass,
      remark: auditRemark.value.trim(),
    })
    ElMessage.success(pass ? '已通过审核并发布' : '已驳回')
    auditVisible.value = false
    loadArticles(pageNum.value)
    loadDashboard()
  } catch (error) {
    console.error('审核失败:', error)
  }
}

// ---- 上下架 ----
const toggleStatus = async (article: ArticleItem, status: number) => {
  const actionText = status === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(
      `确认${actionText}《${article.title}》吗？`,
      `${actionText}确认`,
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await request.put(`/system/science/articles/${article.id}/status`, null, { params: { status } })
    ElMessage.success(`${actionText}成功`)
    loadArticles(pageNum.value)
    loadDashboard()
  } catch (error) {
    console.error(`${actionText}失败:`, error)
  }
}

// ---- 删除 ----
const handleDelete = async (article: ArticleItem) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除《${article.title}》吗？此操作不可恢复！`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' },
    )
  } catch {
    return
  }
  try {
    await request.delete(`/system/science/articles/${article.id}`)
    ElMessage.success('删除成功')
    loadArticles(pageNum.value)
    loadDashboard()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

// ---- 积分配置 ----
const pointsVisible = ref(false)
const pointsForm = ref({ id: 0, title: '', downloadPoints: 0, basePoints: 0.5, multiplier: 1 })

const openPoints = async (article: ArticleItem) => {
  try {
    const data = (await request.get(`/system/science/articles/${article.id}`)) as unknown as any
    pointsForm.value = {
      id: article.id,
      title: article.title,
      downloadPoints: data.downloadPoints ?? 0,
      basePoints: data.basePoints ?? 0.5,
      multiplier: data.multiplier ?? 1,
    }
    pointsVisible.value = true
  } catch (error) {
    console.error('加载文章详情失败:', error)
  }
}

const savePoints = async () => {
  try {
    await request.put(`/system/science/articles/${pointsForm.value.id}/points-config`, {
      downloadPoints: pointsForm.value.downloadPoints,
      basePoints: pointsForm.value.basePoints,
      multiplier: pointsForm.value.multiplier,
    })
    ElMessage.success(`《${pointsForm.value.title}》积分配置已保存`)
    pointsVisible.value = false
    loadArticles(pageNum.value)
  } catch (error) {
    console.error('保存积分配置失败:', error)
  }
}

// ---- 上传/编辑文章 ----
const createVisible = ref(false)
const createSubmitting = ref(false)
const coverUploading = ref(false)
const pdfUploading = ref(false)
const coverInputRef = ref<HTMLInputElement | null>(null)
const pdfInputRef = ref<HTMLInputElement | null>(null)

const createForm = ref({
  id: 0,
  title: '',
  description: '',
  authorName: '平台官方',
  categoryCode: '',
  coverUrl: '',
  pdfFileId: '',
  pdfFilename: '',
  pdfStoragePath: '',
  allowDownload: 1,
  downloadPoints: 0,
  isTop: 0,
  tags: '',
})

const resetCreateForm = () => {
  createForm.value = {
    id: 0, title: '', description: '', authorName: '平台官方', categoryCode: '',
    coverUrl: '', pdfFileId: '', pdfFilename: '', pdfStoragePath: '',
    allowDownload: 1, downloadPoints: 0, isTop: 0, tags: '',
  }
}

const openCreate = () => {
  resetCreateForm()
  createVisible.value = true
}

const openEdit = async (article: ArticleItem) => {
  try {
    const data = (await request.get(`/system/science/articles/${article.id}`)) as unknown as any
    let categoryCode = ''
    try {
      const cats = typeof data.categories === 'string' ? JSON.parse(data.categories || '[]') : (data.categories || [])
      categoryCode = cats[0]?.code || ''
    } catch { /* 忽略解析失败 */ }
    createForm.value = {
      id: article.id,
      title: data.title || '',
      description: data.description || '',
      authorName: data.author?.nickname || data.authorName || '',
      categoryCode,
      coverUrl: data.coverUrl || '',
      pdfFileId: String(data.pdfFileId || ''),
      pdfFilename: data.pdfFilename || '',
      pdfStoragePath: data.pdfStoragePath || '',
      allowDownload: data.allowDownload ? 1 : 0,
      downloadPoints: data.downloadPoints || 0,
      isTop: data.isTop || 0,
      tags: data.tags || '',
    }
    createVisible.value = true
  } catch (error) {
    console.error('加载文章详情失败:', error)
  }
}

const uploadFile = async (file: File, biz: string) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('biz', biz)
  return (await request.post('/file/upload', fd)) as unknown as { id: string; url: string; name: string }
}

const onCoverPick = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    coverUploading.value = true
    const res = await uploadFile(file, 'science-cover')
    createForm.value.coverUrl = res.url
  } catch (error) {
    console.error('封面上传失败:', error)
    ElMessage.error('封面上传失败')
  } finally {
    coverUploading.value = false
  }
}

const onPdfPick = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    pdfUploading.value = true
    const res = await uploadFile(file, 'science-article')
    createForm.value.pdfFileId = String(res.id)
    createForm.value.pdfFilename = res.name
    createForm.value.pdfStoragePath = res.url.replace(/^\/api\/file\/preview\//, '')
  } catch (error) {
    console.error('PDF 上传失败:', error)
    ElMessage.error('PDF 上传失败')
  } finally {
    pdfUploading.value = false
  }
}

const submitArticle = async () => {
  const f = createForm.value
  if (!f.title.trim()) return ElMessage.warning('请填写文章标题')
  if (!f.description.trim()) return ElMessage.warning('请填写文章简介')
  if (!f.authorName.trim()) return ElMessage.warning('请填写作者名称')
  if (!f.categoryCode) return ElMessage.warning('请选择分类')
  if (!f.coverUrl) return ElMessage.warning('请上传封面图片')
  if (!f.pdfStoragePath) return ElMessage.warning('请上传 PDF 文件')

  const categoryName = categories.value.find((c) => c.code === f.categoryCode)?.name || f.categoryCode
  const payload = {
    title: f.title.trim(),
    description: f.description.trim(),
    authorName: f.authorName.trim(),
    coverUrl: f.coverUrl,
    pdfFileId: f.pdfFileId,
    pdfFilename: f.pdfFilename,
    pdfStoragePath: f.pdfStoragePath,
    categories: [{ code: f.categoryCode, name: categoryName }],
    tags: f.tags.trim(),
    allowDownload: f.allowDownload,
    downloadPoints: f.downloadPoints,
    isTop: f.isTop,
    basePoints: 0.5,
    multiplier: 1,
  }

  try {
    createSubmitting.value = true
    if (f.id) {
      await request.put(`/system/science/articles/${f.id}`, payload)
      ElMessage.success('保存成功')
    } else {
      await request.post('/system/science/articles', payload)
      ElMessage.success('上传成功，已直接发布')
    }
    createVisible.value = false
    loadArticles(1)
    loadDashboard()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    createSubmitting.value = false
  }
}

onMounted(() => {
  loadArticles()
  loadDashboard()
  loadCategories()
})
</script>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.admin-science-container {
  padding: $df-space-xl $df-space-xl $df-space-xxl;
  background: transparent;
  min-height: 100%;
  position: relative;

  > * {
    position: relative;
    z-index: 1;
  }
}

// ---------- 报头（masthead） ----------
.sci-masthead {
  padding: $df-space-md 0;
  margin-bottom: $df-space-lg;
  border-top: 3px double var(--df-color-primary);
  border-bottom: 1px solid var(--df-border-color);

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $df-space-md;
  }

  &__title {
    h1 {
      margin: 0;
      font-family: var(--df-font-serif);
      font-size: $df-font-size-xl;
      font-weight: 800;
      color: var(--df-color-title);
      letter-spacing: -0.02em;
      line-height: 1.2;
    }
  }

  &__slogan {
    margin: 4px 0 0;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    font-family: var(--df-font-hand);
    letter-spacing: 0.05em;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: $df-space-xs;
    padding-top: $df-space-xs;
    border-top: 1px solid var(--df-border-color);
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__date {
    font-family: var(--df-font-serif);
    letter-spacing: 0.05em;
  }

  &__ver {
    font-family: var(--df-font-hand);
    letter-spacing: 0.04em;
  }
}

// ---------- 上传文章按钮 ----------
.btn-upload {
  padding: 8px 18px;
  background: var(--df-color-primary);
  color: var(--df-on-accent);
  border: 1px solid var(--df-color-primary);
  border-radius: $df-radius-round;
  font-size: $df-font-size-sm;
  font-weight: 700;
  font-family: var(--df-font-serif);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: var(--df-color-primary-dark);
    border-color: var(--df-color-primary-dark);
    transform: translateY(-1px);
    box-shadow: var(--df-shadow-overlay);
  }
}

// ---------- 数据概看板 ----------
.dashboard-panel {
  background: var(--df-bg-card);
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-lg;
  padding: $df-space-lg $df-space-lg $df-space-md;
  margin-bottom: $df-space-lg;
  box-shadow: var(--df-shadow-card);
}

.dashboard-head {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: $df-space-md;

  h2 {
    margin: 0;
    font-size: $df-font-size-base;
    font-weight: 800;
    color: var(--df-text-primary);
    letter-spacing: -0.01em;
    font-family: var(--df-font-serif);
    display: flex;
    align-items: center;

    &::before {
      content: '';
      width: 3px;
      height: 14px;
      border-radius: 2px;
      background: var(--df-color-primary);
      margin-right: 9px;
      flex-shrink: 0;
    }
  }
}

// ---------- 统计卡片网格 ----------
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: $df-space-md;
  margin-bottom: $df-space-md;
}

.stat-card {
  background: var(--df-bg-card);
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-md;
  padding: $df-space-md;
  box-shadow: var(--df-shadow-card);
  transition: transform 0.12s, box-shadow 0.18s, border-color 0.15s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--df-shadow-overlay);
    border-color: var(--df-border-input);
  }

  &--warn {
    background: var(--df-color-danger-muted);
    border-color: color-mix(in srgb, var(--df-color-warning) 35%, transparent);
  }

  h3 {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    margin: 0 0 6px;
    font-family: var(--df-font-serif);
    letter-spacing: 0.04em;
    font-weight: 600;
  }

  .value {
    font-size: $df-font-size-title;
    font-weight: 800;
    color: var(--df-color-primary);
    font-family: var(--df-font-serif);
    letter-spacing: -0.02em;
    line-height: 1.1;
  }

  .sub {
    margin-top: 4px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    font-family: var(--df-font-hand);
  }
}

// ---------- 排行榜 ----------
.rank-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: $df-space-md;
}

.rank-col {
  background: var(--df-bg-nested);
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-md;
  padding: $df-space-md;

  h4 {
    margin: 0 0 $df-space-sm;
    font-size: $df-font-size-sm;
    color: var(--df-text-primary);
    font-family: var(--df-font-serif);
    font-weight: 700;
    letter-spacing: 0.02em;
    padding-bottom: 6px;
    border-bottom: 1px dashed var(--df-border-color);
  }
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  font-size: $df-font-size-sm;
  color: var(--df-text-regular);
}

.rank-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--df-color-primary);
  color: var(--df-on-accent);
  font-size: $df-font-size-xs;
  font-weight: 700;
  font-family: var(--df-font-serif);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rank-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--df-text-primary);
  font-weight: 600;
}

.rank-num {
  color: var(--df-text-secondary);
  font-size: $df-font-size-xs;
  white-space: nowrap;
  font-family: var(--df-font-hand);
}

// ---------- 筛选区 ----------
.filter-section {
  display: flex;
  gap: $df-space-sm;
  margin-bottom: $df-space-lg;
  flex-wrap: wrap;
}

.search-input,
.status-filter {
  padding: 8px 14px;
  border: 1px solid var(--df-border-input);
  border-radius: $df-radius-sm;
  background: var(--df-bg-card);
  color: var(--df-text-primary);
  font-size: $df-font-size-sm;
  font-family: var(--df-font-serif);
  transition: border-color 0.15s ease;

  &::placeholder {
    color: var(--df-text-placeholder);
    font-family: var(--df-font);
  }

  &:focus {
    outline: none;
    border-color: var(--df-color-primary);
    box-shadow: 0 0 0 3px var(--df-color-primary-light);
  }
}

.search-input {
  min-width: 260px;
}

.btn-search {
  padding: 8px 22px;
  background: var(--df-color-primary);
  color: var(--df-on-accent);
  border: 1px solid var(--df-color-primary);
  border-radius: $df-radius-sm;
  font-size: $df-font-size-sm;
  font-weight: 700;
  font-family: var(--df-font-serif);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: var(--df-color-primary-dark);
    border-color: var(--df-color-primary-dark);
  }
}

// ---------- 表格 ----------
.table-wrapper {
  background: var(--df-bg-card);
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-lg;
  overflow-x: auto;
  box-shadow: var(--df-shadow-card);
}

.science-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 1100px;

  th,
  td {
    padding: 12px 16px;
    text-align: left;
    border-bottom: 1px solid var(--df-border-color);
    vertical-align: middle;
  }

  thead {
    background: var(--df-bg-nested);
  }

  thead th {
    font-weight: 700;
    color: var(--df-color-title);
    font-size: $df-font-size-sm;
    font-family: var(--df-font-serif);
    letter-spacing: 0.04em;
    border-bottom: 2px solid var(--df-color-primary);
  }

  tbody tr {
    transition: background 0.12s ease;

    &:hover {
      background: var(--df-color-primary-light);
    }
  }
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cover-thumb {
  width: 56px;
  height: 40px;
  border-radius: $df-radius-xs;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--df-bg-nested);
  border: 1px solid var(--df-border-color);
}

.title-info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.title-text {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--df-text-primary);
}

.system-tag {
  background: var(--df-color-primary);
  color: var(--df-on-accent);
  padding: 1px 8px;
  border-radius: $df-radius-xs;
  font-size: $df-font-size-xs;
  font-weight: 700;
  font-family: var(--df-font-serif);
  letter-spacing: 0.04em;
}

.top-tag {
  background: var(--df-color-gold);
  color: var(--df-on-accent);
  padding: 1px 8px;
  border-radius: $df-radius-xs;
  font-size: $df-font-size-xs;
  font-weight: 700;
  font-family: var(--df-font-serif);
  letter-spacing: 0.04em;
}

.points-text {
  color: var(--df-color-gold-dark);
  font-weight: 700;
  font-family: var(--df-font-serif);
}

.points-free {
  color: var(--df-color-secondary);
}

.no-download {
  color: var(--df-text-secondary);
  font-family: var(--df-font-hand);
}

.data-stats {
  display: flex;
  gap: 10px;
  font-size: $df-font-size-sm;
  color: var(--df-text-regular);
  white-space: nowrap;
  font-family: var(--df-font-serif);
}

.action-cells {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.btn-text {
  border: none;
  background: none;
  color: var(--df-color-primary);
  cursor: pointer;
  font-size: $df-font-size-sm;
  padding: 4px 8px;
  white-space: nowrap;
  font-family: var(--df-font-serif);
  border-radius: $df-radius-xs;
  transition: background 0.12s ease;

  &:hover {
    text-decoration: underline;
    background: var(--df-color-primary-light);
  }
}

.btn-audit {
  color: var(--df-color-primary);
  font-weight: 700;
}

.btn-success {
  color: var(--df-color-secondary);
}

.btn-warn {
  color: var(--df-color-warning);
}

.btn-danger {
  color: var(--df-color-danger);

  &:hover {
    background: var(--df-color-danger-muted);
  }
}

// ---------- 状态徽章 ----------
.status-badge {
  display: inline-block;
  padding: 3px 11px;
  border-radius: $df-radius-round;
  font-size: $df-font-size-xs;
  font-weight: 600;
  font-family: var(--df-font-serif);
  letter-spacing: 0.04em;
  border: 1px solid transparent;
}

.status-pending {
  background: color-mix(in srgb, var(--df-color-warning) 15%, transparent);
  color: var(--df-color-warning);
  border-color: color-mix(in srgb, var(--df-color-warning) 35%, transparent);
}

.status-published {
  background: color-mix(in srgb, var(--df-color-secondary) 15%, transparent);
  color: var(--df-color-secondary-dark);
  border-color: color-mix(in srgb, var(--df-color-secondary) 35%, transparent);
}

.status-withdrawn {
  background: var(--df-bg-nested);
  color: var(--df-text-secondary);
  border-color: var(--df-border-color);
}

.status-rejected {
  background: var(--df-color-danger-muted);
  color: var(--df-color-danger);
  border-color: color-mix(in srgb, var(--df-color-danger) 35%, transparent);
}

.empty-cell {
  text-align: center;
  color: var(--df-text-secondary);
  padding: 40px !important;
  font-family: var(--df-font-hand);
  font-size: $df-font-size-base;
}

// ---------- 分页 ----------
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: $df-space-md;
  margin-top: $df-space-lg;
}

.page-btn {
  padding: 8px 18px;
  background: var(--df-bg-card);
  color: var(--df-text-primary);
  border: 1px solid var(--df-border-input);
  border-radius: $df-radius-sm;
  cursor: pointer;
  font-family: var(--df-font-serif);
  font-size: $df-font-size-sm;
  font-weight: 600;
  transition: all 0.15s ease;

  &:hover:not(:disabled) {
    background: var(--df-color-primary);
    color: var(--df-on-accent);
    border-color: var(--df-color-primary);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.page-info {
  font-size: $df-font-size-sm;
  color: var(--df-text-secondary);
  font-family: var(--df-font-serif);
  letter-spacing: 0.05em;
}

// ---------- 弹窗内容 ----------
.audit-body {
  display: flex;
  flex-direction: column;
  gap: $df-space-md;
}

.audit-info {
  background: var(--df-bg-nested);
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-sm;
  padding: $df-space-md;
}

.info-row {
  font-size: $df-font-size-sm;
  padding: 3px 0;
  color: var(--df-text-primary);

  .label {
    color: var(--df-text-secondary);
    font-family: var(--df-font-serif);
    letter-spacing: 0.03em;
    margin-right: 6px;
  }

  .value {
    color: var(--df-text-primary);
    font-weight: 600;
  }
}

.pdf-frame-wrap {
  height: 480px;
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-sm;
  overflow: hidden;
  background: var(--df-bg-nested);
}

.pdf-frame {
  width: 100%;
  height: 100%;
  border: none;
}

.pdf-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--df-text-secondary);
  font-family: var(--df-font-hand);
  font-size: $df-font-size-base;
}

.audit-remark .label {
  display: block;
  margin-bottom: $df-space-sm;
  font-weight: 700;
  color: var(--df-text-primary);
  font-family: var(--df-font-serif);
  letter-spacing: 0.03em;
}

.points-form,
.create-form {
  display: flex;
  flex-direction: column;
  gap: $df-space-md;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: $df-space-sm;

  &--half {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: $df-space-md;
  }

  .label {
    font-weight: 700;
    color: var(--df-text-primary);
    font-family: var(--df-font-serif);
    letter-spacing: 0.03em;
    font-size: $df-font-size-sm;
  }
}

.form-hint {
  font-size: $df-font-size-xs;
  color: var(--df-text-secondary);
  font-family: var(--df-font-hand);
}

.upload-box {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  padding: 10px;
  border: 1px dashed var(--df-border-input);
  border-radius: $df-radius-sm;
  background: var(--df-bg-nested);
  color: var(--df-text-secondary);
  font-size: $df-font-size-sm;
  font-family: var(--df-font-serif);
  text-align: center;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
    background: var(--df-color-primary-light);
  }
}

.cover-preview {
  max-width: 140px;
  max-height: 140px;
  border-radius: $df-radius-sm;
  border: 1px solid var(--df-border-color);
}

.download-config {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.points-group {
  margin-left: 4px;
}
</style>
