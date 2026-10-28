<script setup lang="ts">
// PC 变废为宝社区（LoopFill B6 创意 C2）：瀑布流作品列表 + 发布弹层（图片压缩 base64）+ 点赞动效
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import {
  pageDiyPosts, publishDiyPost, likeDiyPost,
  type DiyPost,
} from '@/api/business'

defineOptions({ name: 'pc-community' })

const router = useRouter()

const list = ref<DiyPost[]>([])
const loading = ref(false)
const finished = ref(false)
let pageNum = 1

// ---------------- 发布弹层 ----------------
const showPublish = ref(false)
const publishSubmitting = ref(false)
const title = ref('')
const content = ref('')
const imageBase64 = ref('')

/** 图片压缩至 ≤300KB（canvas 阶梯质量，对齐 C6-3 隐私铁律/契约 ≤300KB） */
async function compressToBase64(file: File): Promise<string> {
  const url = URL.createObjectURL(file)
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image()
      el.onload = () => resolve(el)
      el.onerror = reject
      el.src = url
    })
    const scale = Math.min(1, 1080 / Math.max(img.width, img.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(img.width * scale))
    canvas.height = Math.max(1, Math.round(img.height * scale))
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('canvas 不可用')
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
    for (const q of [0.85, 0.7, 0.55, 0.4]) {
      const dataUrl = canvas.toDataURL('image/jpeg', q)
      const b64Len = dataUrl.length - dataUrl.indexOf(',') - 1
      if (b64Len <= 300 * 1024) return dataUrl
    }
    return canvas.toDataURL('image/jpeg', 0.4)
  } finally {
    URL.revokeObjectURL(url)
  }
}

function onImageRead(items: UploaderFileListItem | UploaderFileListItem[]) {
  const file = Array.isArray(items) ? items[0]?.file : items.file
  if (!file) return
  compressToBase64(file).then((b64) => {
    imageBase64.value = b64
  }).catch(() => {
    showFailToast('图片处理失败，请重试')
  })
}

async function onSubmit() {
  if (!title.value.trim()) {
    showFailToast('请填写作品标题')
    return
  }
  if (!content.value.trim()) {
    showFailToast('请填写改造心得')
    return
  }
  publishSubmitting.value = true
  try {
    await publishDiyPost({
      title: title.value.trim(),
      content: content.value.trim(),
      imageUrl: imageBase64.value || undefined,
    })
    showSuccessToast('发布成功')
    showPublish.value = false
    title.value = ''
    content.value = ''
    imageBase64.value = ''
    reload()
  } finally {
    publishSubmitting.value = false
  }
}

// ---------------- 列表（瀑布流 + 加载更多） ----------------
async function loadMore() {
  loading.value = true
  try {
    const page = await pageDiyPosts({ pageNum, pageSize: 10 })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum = 1
  finished.value = false
  loadMore()
}

// ---------------- 点赞（乐观更新 + 失败回滚） ----------------
const liking = ref<Set<string>>(new Set())

async function onLike(post: DiyPost) {
  if (liking.value.has(post.id)) return
  liking.value.add(post.id)
  const prevLiked = post.likedByMe
  const prevLikes = post.likes
  // 乐观更新：先翻转，失败回滚
  post.likedByMe = !prevLiked
  post.likes = Math.max(0, prevLikes + (prevLiked ? -1 : 1))
  try {
    const r = await likeDiyPost(post.id)
    post.likedByMe = r.liked
    post.likes = r.likes
  } catch {
    post.likedByMe = prevLiked
    post.likes = prevLikes
    showFailToast('操作失败，请重试')
  } finally {
    liking.value.delete(post.id)
  }
}

onMounted(() => {
  reload()
})
</script>

<template>
  <div class="pc-community">
    <div class="container">
      <h1 class="page-title">变废为宝社区</h1>

      <div class="toolbar">
        <button class="btn-primary" @click="showPublish = true">发布作品</button>
        <button class="btn-plain" @click="router.push('/pc/community/rank')">
          <SvgIcon name="carbon-tree" :size="18" /> 片区环保排行榜
        </button>
      </div>

      <div class="card">
        <div v-if="loading && list.length === 0" class="loading">加载中…</div>

        <div v-else-if="finished && list.length === 0" class="empty">
          还没有作品，来发第一个变废为宝的创意吧
        </div>

        <div v-else class="waterfall">
          <div v-for="post in list" :key="post.id" class="post">
            <img v-if="post.imageUrl" v-lazy="post.imageUrl" class="post__img" alt="" />
            <div class="post__body">
              <div class="post__title">{{ post.title }}</div>
              <div class="post__content">{{ post.content }}</div>
              <div class="post__foot">
                <div class="post__author">
                  <SvgIcon name="nav-user" :size="14" />
                  {{ post.nickname }}
                </div>
                <span class="post__time">{{ post.createTime }}</span>
                <button
                  class="post__like"
                  :class="{ 'post__like--on': post.likedByMe }"
                  @click="onLike(post)"
                >
                  <van-icon :name="post.likedByMe ? 'like' : 'like-o'" :class="{ 'post__like-boom': post.likedByMe }" />
                  {{ post.likes }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <button v-if="!finished && list.length > 0" class="load-more" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
        <p v-else-if="finished && list.length > 0" class="no-more">没有更多了</p>
      </div>
    </div>

    <!-- 发布弹层 -->
    <div v-if="showPublish" class="pc-community__mask" @click.self="showPublish = false">
      <div class="pc-community__dialog">
        <h3 class="pc-community__dialog-title">发布变废为宝作品</h3>
        <input v-model="title" maxlength="64" class="pc-community__input" placeholder="作品标题（如：牛奶盒变身小花盆）" />
        <textarea
          v-model="content"
          rows="4"
          maxlength="500"
          class="pc-community__textarea"
          placeholder="改造心得/小教程，分享你的变废为宝创意…"
        ></textarea>
        <div class="pc-community__upload">
          <van-uploader :after-read="onImageRead" :max-count="1">
            <template v-if="imageBase64">
              <img :src="imageBase64" class="pc-community__preview" alt="" />
            </template>
          </van-uploader>
          <span class="pc-community__tip">作品图（选填，自动压缩 ≤300KB）</span>
        </div>
        <div class="pc-community__actions">
          <button class="pc-community__cancel" @click="showPublish = false">取消</button>
          <button class="pc-community__submit" :disabled="publishSubmitting" @click="onSubmit">
            {{ publishSubmitting ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-community {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 980px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 48px;
  text-align: center;
}

.toolbar {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 24px;
}

.btn-primary {
  padding: 10px 28px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: var(--df-color-primary);
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover { opacity: 0.9; }
}

.btn-plain {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 28px;
  font-size: 15px;
  font-weight: 600;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-color-primary);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
  }
}

.card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-card);
}

.waterfall {
  columns: 3;
  column-gap: 20px;

  @media (max-width: 900px) {
    columns: 2;
  }
}

.post {
  break-inside: avoid;
  margin-bottom: 20px;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    box-shadow: var(--df-shadow-float);
  }

  &__img {
    width: 100%;
    display: block;
    aspect-ratio: 4 / 3;
    object-fit: cover;
  }

  &__body {
    padding: 16px;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__content {
    margin-top: 8px;
    font-size: 14px;
    color: var(--df-text-secondary);
    line-height: 1.7;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &__author {
    display: flex;
    align-items: center;
    gap: 4px;
    max-width: 40%;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__time {
    flex: 1;
    text-align: right;
  }

  &__like {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 4px 10px;
    border: none;
    border-radius: 999px;
    background: transparent;
    color: var(--df-text-secondary);
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover { background: var(--df-bg-card); }

    &--on {
      color: var(--df-color-danger);
      font-weight: 600;
    }
  }

  &__like-boom {
    animation: pc-community-boom 0.35s ease;
  }
}

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: var(--df-text-secondary);
  font-size: 16px;
}

.load-more {
  width: 100%;
  padding: 12px;
  font-size: 15px;
  font-weight: 500;
  color: var(--df-color-primary);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    border-color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent);
  }

  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.no-more {
  text-align: center;
  color: var(--df-text-secondary);
  font-size: 14px;
  margin: 16px 0 0;
}

.pc-community__mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.pc-community__dialog {
  width: 560px;
  max-width: calc(100vw - 48px);
  padding: 32px;
  background: var(--df-bg-card);
  border-radius: 16px;
  box-shadow: var(--df-shadow-float);

  &-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--df-text-primary);
    text-align: center;
    margin-bottom: 20px;
  }
}

.pc-community__input {
  width: 100%;
  padding: 12px 14px;
  font-size: 15px;
  color: var(--df-text-primary);
  background: var(--df-bg-card);
  border: 2px solid var(--df-border-color);
  border-radius: 10px;
  outline: none;
  transition: all 0.3s ease;

  &:focus {
    border-color: var(--df-color-primary);
  }
}

.pc-community__textarea {
  width: 100%;
  margin-top: 12px;
  padding: 12px 14px;
  font-size: 15px;
  font-family: inherit;
  line-height: 1.6;
  color: var(--df-text-primary);
  background: var(--df-bg-card);
  border: 2px solid var(--df-border-color);
  border-radius: 10px;
  outline: none;
  resize: vertical;
  transition: all 0.3s ease;

  &:focus {
    border-color: var(--df-color-primary);
  }
}

.pc-community__upload {
  margin-top: 12px;
}

.pc-community__preview {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border-radius: 10px;
  display: block;
}

.pc-community__tip {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: var(--df-text-secondary);
}

.pc-community__actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.pc-community__cancel {
  flex: 1;
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--df-text-regular);
  background: transparent;
  border: 2px solid var(--df-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--df-color-primary);
    color: var(--df-color-primary);
  }
}

.pc-community__submit {
  flex: 1;
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: var(--df-color-primary);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) { opacity: 0.9; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

@keyframes pc-community-boom {
  0% { transform: scale(1); }
  40% { transform: scale(1.5); }
  100% { transform: scale(1); }
}
</style>
