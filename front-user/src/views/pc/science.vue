<script setup lang="ts">
// PC 科普专栏：API 列表（人人可发布，含封面/PDF 预览）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '@/api/request'
import { tokenStore } from '@/api/token'

defineOptions({ name: 'pc-science' })

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
  categories: string
  allowDownload: boolean
  downloadPoints: number
  publishTime: string
}

const router = useRouter()

// science 为私有目录，<img> 无请求头需带 query token 直链（JwtAuthFilter C11）
const coverWithToken = (url: string) => {
  if (!url) return ''
  const storagePath = url.replace(/^\/api\/file\/preview\//, '')
  return storagePath ? `${import.meta.env.VITE_API_BASE}/file/preview/${storagePath}${tokenStore.getAccessToken() ? `?access_token=${encodeURIComponent(tokenStore.getAccessToken()!)}` : ''}` : url
}

const articles = ref<ArticleItem[]>([])
const loading = ref(false)
const finished = ref(false)
let pageNum = 1
const pageSize = 12
const keyword = ref('')

const loadArticles = async (reset = false) => {
  if (reset) {
    pageNum = 1
    articles.value = []
    finished.value = false
  }
  loading.value = true
  try {
    const params = new URLSearchParams({
      pageNum: pageNum.toString(),
      pageSize: pageSize.toString(),
      sortBy: 'view_count',
      status: '1',
    })
    if (keyword.value.trim()) params.append('keyword', keyword.value.trim())

    // 拦截器已拆包 Result.data，返回即 PageVO { list, total }
    const response = (await request.get(`/science/articles?${params.toString()}`)) as unknown as {
      list: ArticleItem[]
      total: number
    }
    articles.value.push(...(response.list || []))
    finished.value = articles.value.length >= (response.total || 0)
    pageNum += 1
  } catch (error) {
    console.error('加载文章失败:', error)
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  loadArticles(true)
}

const onDetail = (id: number) => {
  router.push(`/pc/science/article/${id}`)
}

onMounted(() => {
  loadArticles()
})
</script>

<template>
  <div class="pc-science">
    <div class="container">
      <h1 class="page-title">科普专栏</h1>
      <p class="page-desc">学习环保知识，共建绿色家园</p>

      <div class="toolbar">
        <div class="search">
          <input
            v-model="keyword"
            class="search__input"
            placeholder="搜索科普文章"
            @keyup.enter="onSearch"
          />
          <button class="btn-primary" @click="onSearch">搜索</button>
        </div>
        <button class="btn-primary btn-primary--solid" @click="router.push('/pc/science/create')">发布文章</button>
      </div>

      <div class="articles-grid">
        <div v-for="article in articles" :key="article.id" class="article-card" @click="onDetail(article.id)">
          <div class="article-card__image">
            <img v-if="article.coverUrl" :src="coverWithToken(article.coverUrl)" :alt="article.title" class="article-card__cover" />
            <div v-else class="article-card__cover-fallback">
              <SvgIcon name="recycle-recycle-symbol" :size="64" color="var(--df-color-primary)" />
            </div>
            <span v-if="article.isSystem" class="article-card__badge">官方</span>
            <span v-if="article.allowDownload" class="article-card__badge article-card__badge--points">
              {{ article.downloadPoints === 0 ? '免费' : `${article.downloadPoints} 积分` }}
            </span>
          </div>
          <div class="article-card__content">
            <h3 class="article-card__title">{{ article.title }}</h3>
            <p class="article-card__desc">{{ article.description }}</p>
            <div class="article-card__footer">
              <span class="article-card__meta">
                <SvgIcon name="nav-user" :size="14" /> {{ article.authorName }} · <SvgIcon name="nav-eye" :size="14" /> {{ article.viewCount }} · <SvgIcon name="nav-download" :size="14" /> {{ article.downloadCount }}
              </span>
              <button class="article-card__btn">阅读全文 →</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading && articles.length === 0" class="loading">加载中…</div>
      <div v-else-if="finished && articles.length === 0" class="empty">
        暂无科普文章，快来发布第一篇吧
      </div>

      <div class="load-more-wrap">
        <button v-if="!finished && articles.length > 0" class="load-more" :disabled="loading" @click="loadArticles()">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-science {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 16px;
  text-align: center;
}

.page-desc {
  font-size: 18px;
  color: var(--df-text-regular);
  margin-bottom: 40px;
  text-align: center;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  margin-bottom: 40px;
  flex-wrap: wrap;
}

.search {
  display: flex;
  gap: 12px;
  flex: 1;
  min-width: 280px;
  max-width: 480px;
}

.search__input {
  flex: 1;
  padding: 12px 20px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 24px;
  font-size: 14px;
  background: var(--df-bg-card);
  color: var(--df-text-primary);
  outline: none;
  transition: border-color 0.3s;

  &:focus {
    border-color: var(--df-color-primary);
  }
}

.btn-primary {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-color-primary);
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: var(--df-color-primary);
    color: white;
  }
}

.btn-primary--solid {
  background: var(--df-color-primary);
  color: white;

  &:hover {
    opacity: 0.9;
  }
}

.articles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 32px;
}

.article-card {
  background: var(--df-bg-card);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--df-shadow-card);
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  cursor: pointer;

  &:hover {
    transform: translateY(-8px);
    box-shadow: var(--df-shadow-float);

    .article-card__btn {
      background: var(--df-color-primary);
      color: white;
    }
  }
}

.article-card__image {
  position: relative;
  height: 200px;
  background: var(--df-color-primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
}

.article-card__cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.article-card__cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
}

.article-card__badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 600;
  color: white;
  background: var(--df-color-primary);
  border-radius: 12px;
}

.article-card__badge--points {
  left: auto;
  right: 12px;
  background: var(--df-color-warning);
}

.article-card__content {
  padding: 32px;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.article-card__title {
  font-size: 22px;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 16px;
}

.article-card__desc {
  font-size: 16px;
  color: var(--df-text-regular);
  line-height: 1.6;
  margin-bottom: 24px;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.article-card__meta {
  font-size: 13px;
  color: var(--df-text-secondary);
}

.article-card__btn {
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-color-primary);
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.loading {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
}

.empty {
  text-align: center;
  padding: 80px 0;
  font-size: 16px;
  color: var(--df-text-secondary);
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}

.load-more {
  padding: 12px 48px;
  font-size: 14px;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-color-primary);
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    background: var(--df-color-primary);
    color: white;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}
</style>
