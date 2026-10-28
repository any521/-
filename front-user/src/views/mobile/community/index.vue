<script setup lang="ts">
// 变废为宝社区（LoopFill B6 创意 C2）：瀑布流作品列表 + 发布弹层（图片压缩 base64）+ 点赞动效
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import {
  pageDiyPosts, publishDiyPost, likeDiyPost,
  type DiyPost,
} from '@/api/business'

defineOptions({ name: 'community' })

const router = useRouter()

const list = ref<DiyPost[]>([])
const loading = ref(false)
const refreshing = ref(false)
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
  } catch {
    // 发布失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    publishSubmitting.value = false
  }
}

// ---------------- 列表（瀑布流 + 下拉刷新/加载更多） ----------------
async function loadMore() {
  loading.value = true
  try {
    const page = await pageDiyPosts({ pageNum, pageSize: 10 })
    if (pageNum === 1) list.value = []
    list.value.push(...page.list)
    finished.value = list.value.length >= page.total
    pageNum += 1
  } catch {
    // 加载失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function reload() {
  pageNum = 1
  finished.value = false
  loadMore()
}

function onRefresh() {
  refreshing.value = true
  reload()
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
  <div class="page community">
    <van-nav-bar
      title="变废为宝社区"
      left-arrow
      right-text="发布"
      @click-left="router.back()"
      @click-right="showPublish = true"
    />

    <!-- 排行榜入口 -->
    <div class="card community__toolbar" @click="router.push('/community/rank')">
      <SvgIcon name="carbon-tree" size="22" />
      <span>片区环保排行榜，看看你家片区排第几</span>
      <van-icon name="arrow" />
    </div>

    <!-- 瀑布流列表 -->
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-if="list.length > 0" class="community__waterfall">
          <div v-for="post in list" :key="post.id" class="community__post">
            <img v-if="post.imageUrl" v-lazy="post.imageUrl" class="community__post-img" alt="" />
            <div class="community__post-body">
              <div class="community__post-title">{{ post.title }}</div>
              <div class="community__post-content">{{ post.content }}</div>
              <div class="community__post-foot">
                <div class="community__post-author">
                  <SvgIcon name="nav-user" size="14" />
                  {{ post.nickname }}
                </div>
                <span class="community__post-time">{{ post.createTime }}</span>
                <button
                  class="community__like"
                  :class="{ 'community__like--on': post.likedByMe }"
                  @click.stop="onLike(post)"
                >
                  <van-icon :name="post.likedByMe ? 'like' : 'like-o'" :class="{ 'community__like-boom': post.likedByMe }" />
                  {{ post.likes }}
                </button>
              </div>
            </div>
          </div>
        </div>
        <van-empty
          v-if="finished && list.length === 0"
          description="还没有作品，来发第一个变废为宝的创意吧"
        >
          <van-button size="small" round type="primary" plain @click="showPublish = true">发布作品</van-button>
        </van-empty>
      </van-list>
    </van-pull-refresh>

    <!-- 发布弹层 -->
    <van-popup v-model:show="showPublish" position="bottom" round class="community__publish">
      <div class="community__publish-title">发布变废为宝作品</div>
      <van-field v-model="title" maxlength="64" placeholder="作品标题（如：牛奶盒变身小花盆）" />
      <van-field
        v-model="content"
        type="textarea"
        rows="3"
        maxlength="500"
        show-word-limit
        placeholder="改造心得/小教程，分享你的变废为宝创意…"
      />
      <div class="community__publish-img">
        <van-uploader :after-read="onImageRead" :max-count="1" :preview-image="true">
          <template v-if="imageBase64">
            <img :src="imageBase64" class="community__publish-preview" alt="" />
          </template>
        </van-uploader>
        <span class="community__publish-tip">作品图（选填，自动压缩 ≤300KB）</span>
      </div>
      <van-button
        block
        round
        type="primary"
        :loading="publishSubmitting"
        class="community__publish-btn"
        @click="onSubmit"
      >
        发布
      </van-button>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.community {
  &__toolbar {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    margin-bottom: $df-space-md;
    color: var(--df-text-regular);
    font-size: $df-font-size-sm;
    cursor: pointer;

    span {
      flex: 1;
    }
  }

  &__waterfall {
    columns: 2;
    column-gap: $df-space-sm;
  }

  &__post {
    break-inside: avoid;
    margin-bottom: $df-space-sm;
    background: var(--df-bg-card);
    border-radius: $df-radius-lg;
    overflow: hidden;
    box-shadow: var(--df-shadow-card);
  }

  &__post-img {
    width: 100%;
    display: block;
    aspect-ratio: 4 / 3;
    object-fit: cover;
  }

  &__post-body {
    padding: $df-space-sm;
  }

  &__post-title {
    font-size: $df-font-size-base;
    font-weight: 600;
    color: var(--df-text-primary);
  }

  &__post-content {
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__post-foot {
    display: flex;
    align-items: center;
    gap: $df-space-xs;
    margin-top: $df-space-sm;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__post-author {
    display: flex;
    align-items: center;
    gap: 2px;
    max-width: 45%;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__post-time {
    flex: 1;
    text-align: right;
    overflow: hidden;
    white-space: nowrap;
  }

  &__like {
    display: flex;
    align-items: center;
    gap: 3px;
    padding: 2px 6px;
    border: none;
    border-radius: $df-radius-sm;
    background: transparent;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
    cursor: pointer;
    transition: color 0.2s;

    &--on {
      color: var(--df-color-danger);
      font-weight: 600;
    }
  }

  &__like-boom {
    animation: community-like-boom 0.35s ease;
  }

  &__publish {
    padding: $df-space-lg $df-space-md calc($df-space-lg + env(safe-area-inset-bottom));
  }

  &__publish-title {
    font-size: $df-font-size-lg;
    font-weight: 600;
    text-align: center;
    margin-bottom: $df-space-md;
  }

  &__publish-img {
    margin-top: $df-space-md;
  }

  &__publish-preview {
    width: 96px;
    height: 96px;
    object-fit: cover;
    border-radius: $df-radius-sm;
    display: block;
  }

  &__publish-tip {
    display: block;
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__publish-btn {
    margin-top: $df-space-lg;
  }
}

@keyframes community-like-boom {
  0% { transform: scale(1); }
  40% { transform: scale(1.5); }
  100% { transform: scale(1); }
}
</style>
