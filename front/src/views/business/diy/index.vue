<template>
  <div class="admin-diy-container">
    <!-- 报头 -->
    <header class="diy-masthead">
      <div class="diy-masthead__title">
        <h1>变废为宝 · 作品管理</h1>
        <p>社区居民改造作品内容治理（n3）</p>
      </div>
    </header>

    <!-- 统计卡片 -->
    <div class="stats-panel">
      <div class="stat-card">
        <h3><el-icon><Picture /></el-icon> 作品总数</h3>
        <div class="value">{{ total }}</div>
      </div>
      <div class="stat-card stat-card--hint">
        <h3><el-icon><InfoFilled /></el-icon> 说明</h3>
        <div class="sub">删除为软删除，作品将从用户端社区列表/详情同步消失，点赞记录一并失效</div>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="filter-section">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索标题 / 内容 / 作者昵称 / 用户名..."
        class="search-input"
        @keyup.enter="loadPosts(1)"
      >
      <button class="btn-search" @click="loadPosts(1)">搜索</button>
      <button v-if="keyword.trim()" class="btn-reset" @click="resetSearch">清空</button>
    </div>

    <!-- 作品列表 -->
    <div class="table-wrapper">
      <table class="diy-table">
        <thead>
          <tr>
            <th>图片/标题</th>
            <th>作者</th>
            <th>内容</th>
            <th>点赞</th>
            <th>发布时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="post in posts" :key="post.id">
            <td>
              <div class="title-cell">
                <img v-if="post.imageUrl" :src="post.imageUrl" class="cover-thumb" alt="">
                <div v-else class="cover-thumb cover-thumb--empty"><el-icon><Picture /></el-icon></div>
                <span class="title-text" :title="post.title">{{ post.title }}</span>
              </div>
            </td>
            <td>
              <div class="author-cell">
                <span class="author-name">{{ post.nickname }}</span>
                <span class="author-id">ID:{{ post.userId }}</span>
              </div>
            </td>
            <td>
              <div class="content-cell" :title="post.content">{{ post.content }}</div>
            </td>
            <td>
              <span class="like-num"><el-icon><Star /></el-icon> {{ post.likes }}</span>
            </td>
            <td class="time-cell">{{ formatTime(post.createTime) }}</td>
            <td>
              <button class="btn-text btn-danger" @click="handleDelete(post)">删除</button>
            </td>
          </tr>

          <tr v-if="posts.length === 0">
            <td colspan="6" class="empty-cell">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <button :disabled="pageNum === 1" @click="loadPosts(pageNum - 1)" class="page-btn">上一页</button>
      <span class="page-info">{{ pageNum }} / {{ totalPages }}</span>
      <button :disabled="pageNum >= totalPages" @click="loadPosts(pageNum + 1)" class="page-btn">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/request'

interface DiyPostItem {
  id: number
  userId: number
  nickname: string
  title: string
  content: string
  imageUrl?: string
  likes: number
  createTime: string
}

const keyword = ref('')
const pageNum = ref(1)
const pageSize = 10
const posts = ref<DiyPostItem[]>([])
const total = ref(0)
const totalPages = ref(1)

const loadPosts = async (page: number = 1) => {
  try {
    pageNum.value = page
    const params: Record<string, string | number> = { pageNum: page, pageSize }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const response = (await request.get('/system/diy/posts', params)) as unknown as {
      list: DiyPostItem[]
      total: number
      pages: number
    }
    posts.value = response.list || []
    total.value = response.total || 0
    totalPages.value = Math.max(1, Math.ceil(total.value / pageSize))
  } catch (error) {
    console.error('加载作品失败:', error)
  }
}

const resetSearch = () => {
  keyword.value = ''
  loadPosts(1)
}

const handleDelete = async (post: DiyPostItem) => {
  try {
    await ElMessageBox.confirm(
      `确定删除作品「${post.title}」吗？删除后用户端将无法查看。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return // 用户取消
  }
  try {
    await request.delete(`/system/diy/posts/${post.id}`)
    ElMessage.success('删除成功')
    // 当前页删空则回退一页
    if (posts.value.length === 1 && pageNum.value > 1) loadPosts(pageNum.value - 1)
    else loadPosts(pageNum.value)
  } catch (error) {
    console.error('删除作品失败:', error)
  }
}

// LocalDateTime（yyyy-MM-dd HH:mm:ss）原样展示；兼容 ISO 格式截断 T 前的部分
const formatTime = (t?: string) => {
  if (!t) return '-'
  return t.includes('T') ? t.slice(0, 19).replace('T', ' ') : t
}

onMounted(() => loadPosts(1))
</script>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.admin-diy-container {
  padding: $df-space-xl $df-space-xl $df-space-xxl;
}

// ---------- 报头 ----------
.diy-masthead {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: $df-space-lg;
  padding-bottom: $df-space-md;
  border-bottom: 2px solid var(--df-color-primary);

  &__title {
    h1 {
      margin: 0;
      font-size: $df-font-size-xl;
      font-family: var(--df-font-serif);
      color: var(--df-text-primary);
      letter-spacing: 0.02em;
    }

    p {
      margin: $df-space-xs 0 0;
      font-size: $df-font-size-sm;
      color: var(--df-text-secondary);
    }
  }
}

// ---------- 统计 ----------
.stats-panel {
  display: flex;
  gap: $df-space-md;
  margin-bottom: $df-space-lg;

  .stat-card {
    flex: 1;
    max-width: 320px;
    background: var(--df-bg-card);
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    padding: $df-space-md $df-space-lg;

    h3 {
      margin: 0 0 $df-space-xs;
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: $df-font-size-sm;
      color: var(--df-text-regular);
      font-weight: 600;

      .el-icon {
        color: var(--df-color-primary);
      }
    }

    .value {
      font-size: $df-font-size-xl;
      font-weight: 700;
      font-family: var(--df-font-serif);
      color: var(--df-text-primary);
    }

    .sub {
      font-size: $df-font-size-sm;
      color: var(--df-text-secondary);
      line-height: 1.6;
    }
  }

  .stat-card--hint {
    background: var(--df-bg-nested);
  }
}

// ---------- 筛选 ----------
.filter-section {
  display: flex;
  align-items: center;
  gap: $df-space-sm;
  margin-bottom: $df-space-md;

  .search-input {
    flex: 1;
    max-width: 380px;
    padding: 8px 14px;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-card);
    color: var(--df-text-primary);
    font-size: $df-font-size-sm;
    font-family: var(--df-font-serif);
    outline: none;
    transition: border-color 0.15s ease;

    &:focus {
      border-color: var(--df-color-primary);
    }

    &::placeholder {
      color: var(--df-text-placeholder);
    }
  }

  .btn-search {
    padding: 8px 22px;
    border: none;
    border-radius: $df-radius-md;
    background: var(--df-color-primary);
    color: var(--df-on-accent);
    font-size: $df-font-size-sm;
    font-weight: 600;
    font-family: var(--df-font-serif);
    cursor: pointer;
    transition: opacity 0.12s ease;

    &:hover {
      opacity: 0.88;
    }
  }

  .btn-reset {
    padding: 8px 14px;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: none;
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
    font-family: var(--df-font-serif);
    cursor: pointer;

    &:hover {
      color: var(--df-text-primary);
      border-color: var(--df-text-regular);
    }
  }
}

// ---------- 表格 ----------
.table-wrapper {
  background: var(--df-bg-card);
  border: 1px solid var(--df-border-color);
  border-radius: $df-radius-md;
  overflow: hidden;
}

.diy-table {
  width: 100%;
  border-collapse: collapse;

  th,
  td {
    padding: 12px 16px;
    text-align: left;
    border-bottom: 1px solid var(--df-border-color);
    vertical-align: middle;
  }

  th {
    background: var(--df-bg-nested);
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-text-regular);
    font-family: var(--df-font-serif);
    white-space: nowrap;
  }

  td {
    font-size: $df-font-size-sm;
    color: var(--df-text-primary);
    font-family: var(--df-font-serif);
  }

  tbody tr:last-child td {
    border-bottom: none;
  }

  tbody tr:hover {
    background: color-mix(in srgb, var(--df-color-primary-light) 30%, transparent);
  }
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .cover-thumb {
    width: 56px;
    height: 56px;
    object-fit: cover;
    border-radius: $df-radius-sm;
    border: 1px solid var(--df-border-color);
    flex-shrink: 0;
  }

  .cover-thumb--empty {
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--df-bg-nested);
    color: var(--df-text-placeholder);
  }

  .title-text {
    max-width: 240px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 600;
    color: var(--df-text-primary);
  }
}

.author-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .author-name {
    font-weight: 600;
  }

  .author-id {
    font-size: $df-font-size-xs;
    color: var(--df-text-placeholder);
  }
}

.content-cell {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--df-text-regular);
}

.like-num {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--df-color-gold-dark);
  font-weight: 700;

  .el-icon {
    font-size: 14px;
  }
}

.time-cell {
  white-space: nowrap;
  color: var(--df-text-regular);
}

.btn-text {
  border: none;
  background: none;
  cursor: pointer;
  font-size: $df-font-size-sm;
  padding: 4px 8px;
  white-space: nowrap;
  font-family: var(--df-font-serif);
  border-radius: $df-radius-xs;
  transition: background 0.12s ease;

  &:hover {
    text-decoration: underline;
  }
}

.btn-danger {
  color: var(--df-color-danger);

  &:hover {
    background: var(--df-color-danger-muted);
  }
}

.empty-cell {
  text-align: center !important;
  padding: 40px 0 !important;
  color: var(--df-text-placeholder);
}

// ---------- 分页 ----------
.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: $df-space-sm;
  margin-top: $df-space-md;

  .page-btn {
    padding: 7px 16px;
    border: 1px solid var(--df-border-color);
    border-radius: $df-radius-md;
    background: var(--df-bg-card);
    color: var(--df-text-primary);
    font-size: $df-font-size-sm;
    font-family: var(--df-font-serif);
    cursor: pointer;
    transition: border-color 0.12s ease;

    &:hover:not(:disabled) {
      border-color: var(--df-color-primary);
      color: var(--df-color-primary);
    }

    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }

  .page-info {
    font-size: $df-font-size-sm;
    color: var(--df-text-secondary);
    font-family: var(--df-font-serif);
  }
}
</style>
