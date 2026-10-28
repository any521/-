<template>
  <div class="science-container">
    <div class="page-header">
      <h1>科普专栏</h1>
      <p class="subtitle">环保知识分享平台</p>
      <div class="header-actions">
        <button @click="router.push('/mobile/science/create')" class="publish-btn">+ 发布文章</button>
        <button @click="router.push('/mobile/science/my')" class="my-btn"><SvgIcon name="nav-chart" :size="14" /> 我的文章</button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <select v-model="category" class="category-select">
        <option value="">全部分类</option>
        <option v-for="c in categories" :key="c.code" :value="c.code">{{ c.name }}</option>
      </select>

      <input 
        v-model="keyword" 
        type="text" 
        placeholder="搜索文章..." 
        class="search-input"
        @keyup.enter="handleSearch"
      >

      <button @click="handleSearch" class="search-btn">搜索</button>
    </div>

    <!-- 文章列表 -->
    <div class="article-grid">
      <div 
        v-for="article in articles" 
        :key="article.id"
        class="article-card"
        @click="goToDetail(article.id)"
      >
        <div class="cover-wrapper">
          <img 
            :src="coverWithToken(article.coverUrl) || '/default-cover.jpg'" 
            :alt="article.title"
            class="cover-image"
          >
          <span v-if="article.isSystem" class="system-badge">系统文章</span>
        </div>
        
        <div class="card-content">
          <h3 class="title">{{ article.title }}</h3>
          <p class="description">{{ article.description }}</p>
          
          <div class="meta-info">
            <span class="author"><SvgIcon name="nav-user" :size="14" /> {{ article.authorName }}</span>
            <span class="views"><SvgIcon name="nav-eye" :size="14" /> {{ article.viewCount }}</span>
            <span class="downloads"><SvgIcon name="nav-download" :size="14" /> {{ article.downloadCount }}</span>
            <span class="likes"><SvgIcon name="nav-heart" :size="14" /> {{ article.likeCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <button 
        :disabled="pageNum === 1" 
        @click="loadPreviousPage"
        class="page-btn"
      >
        上一页
      </button>
      
      <span class="page-info">第 {{ pageNum }} 页 / 共 {{ totalPages }} 页</span>
      
      <button 
        :disabled="pageNum >= totalPages" 
        @click="loadNextPage"
        class="page-btn"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '@/api/request'
import { tokenStore } from '@/api/token'

// science 为私有目录，<img> 无请求头需带 query token 直链（JwtAuthFilter C11）
const coverWithToken = (url?: string) => {
  if (!url) return ''
  const storagePath = url.replace(/^\/api\/file\/preview\//, '')
  return storagePath ? `${import.meta.env.VITE_API_BASE}/file/preview/${storagePath}${tokenStore.getAccessToken() ? `?access_token=${encodeURIComponent(tokenStore.getAccessToken()!)}` : ''}` : url
}

interface Article {
  /** 雪花 ID 后端序列化为 string，保持字符串避免精度丢失 */
  id: string
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
}

const router = useRouter()

// 状态管理
const articles = ref<Article[]>([])
const categories = ref<{ code: string; name: string }[]>([])
const category = ref('')
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)

// 加载动态分类
const loadCategories = async () => {
  try {
    const data = (await request.get('/science/categories')) as unknown as { code: string; name: string }[]
    categories.value = data || []
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

// 加载文章列表
const loadArticles = async () => {
  try {
    const params = new URLSearchParams({
      pageNum: pageNum.value.toString(),
      pageSize: pageSize.toString(),
      sortBy: 'view_count',
      status: '1'
    })

    if (category.value) params.append('categoryId', category.value)
    if (keyword.value.trim()) params.append('keyword', keyword.value.trim())

    // 拦截器已拆包 Result.data，返回即 PageVO { list, total }
    const response = (await request.get(`/science/articles?${params.toString()}`)) as unknown as { list: Article[]; total: number }
    articles.value = response.list || []
    total.value = response.total || 0
    totalPages.value = Math.ceil(total.value / pageSize)
  } catch (error) {
    console.error('加载文章失败:', error)
  }
}

// 搜索
const handleSearch = () => {
  pageNum.value = 1
  loadArticles()
}

// 分页控制
const loadPreviousPage = () => {
  if (pageNum.value > 1) {
    pageNum.value--
    loadArticles()
  }
}

const loadNextPage = () => {
  if (pageNum.value < totalPages.value) {
    pageNum.value++
    loadArticles()
  }
}

// 跳转到详情页（雪花 ID 保持字符串，Number 会丢精度）
const goToDetail = (id: string) => {
  router.push(`/mobile/science/article/${id}`)
}

onMounted(() => {
  loadArticles()
  loadCategories()
})
</script>

<style scoped>
.science-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  text-align: center;
  margin-bottom: 30px;
}

.page-header h1 {
  font-size: 32px;
  color: var(--df-text-primary);
  margin-bottom: 8px;
}

.subtitle {
  font-size: 16px;
  color: var(--df-text-secondary);
}

.header-actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.publish-btn,
.my-btn {
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  border: none;
  transition: opacity 0.2s;
}

.publish-btn {
  background: var(--df-gradient-brand);
  color: white;
}

.my-btn {
  background: white;
  color: var(--df-color-primary);
  border: 1px solid var(--df-color-primary);
}

.publish-btn:hover,
.my-btn:hover {
  opacity: 0.9;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.category-select,
.search-input {
  padding: 8px 12px;
  border: 1px solid var(--df-border-color);
  border-radius: 6px;
  font-size: 14px;
}

.category-select {
  min-width: 150px;
}

.search-input {
  flex: 1;
  min-width: 200px;
}

.search-btn {
  padding: 8px 24px;
  background: var(--df-gradient-brand);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.search-btn:hover {
  opacity: 0.9;
}

.article-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.article-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
}

.article-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.12);
}

.cover-wrapper {
  position: relative;
  width: 100%;
  height: 180px;
  background: var(--df-bg-nested);
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.system-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: var(--df-gradient-brand);
  color: white;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.card-content {
  padding: 16px;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 8px;
  line-height: 1.4;
}

.description {
  font-size: 14px;
  color: var(--df-text-secondary);
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-info {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--df-text-placeholder);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
}

.page-btn {
  padding: 8px 24px;
  background: white;
  border: 1px solid var(--df-border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--df-color-primary);
  color: var(--df-color-primary);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: var(--df-text-secondary);
}
</style>
