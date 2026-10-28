<script setup lang="ts">
// 跳蚤市场发布（Upgrade U4）：分类/标题/描述/积分定价/图片上传（biz=flea，canvas 压缩 ≤300KB）
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { request } from '@/api/request'
import { publishFleaItem, FLEA_CATEGORIES } from '@/api/business'

defineOptions({ name: 'flea-publish' })

const router = useRouter()

const category = ref('')
const title = ref('')
const description = ref('')
const price = ref('')
const imageUrl = ref('')
const submitting = ref(false)

// ---------------- 分类 picker ----------------
const showCategory = ref(false)
const categoryColumns = FLEA_CATEGORIES.map((c) => ({ text: c.name, value: c.code }))
const categoryText = () => FLEA_CATEGORIES.find((c) => c.code === category.value)?.name ?? ''

function onCategoryConfirm({ selectedOptions }: { selectedOptions: { value?: unknown }[] }) {
  category.value = String(selectedOptions[0]?.value ?? '')
  showCategory.value = false
}

// ---------------- 图片压缩 + 上传（canvas ≤300KB → FormData → /file/upload?biz=flea） ----------------
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

async function onImageRead(items: UploaderFileListItem | UploaderFileListItem[]) {
  try {
    const file = Array.isArray(items) ? items[0]?.file : items.file
    if (!file) return
    const b64 = await compressToBase64(file)
    const blob = await (await fetch(b64)).blob()
    const form = new FormData()
    form.append('file', blob, `flea-${Date.now()}.jpg`)
    const r = await request.post<{ url?: string } | string>('/file/upload?biz=flea', form)
    imageUrl.value = typeof r === 'string' ? r : r.url ?? ''
  } catch {
    showFailToast('图片上传失败，请重试')
  }
}

// ---------------- 提交 ----------------
async function onSubmit() {
  if (!category.value) {
    showFailToast('请选择分类')
    return
  }
  if (!title.value.trim()) {
    showFailToast('请填写标题')
    return
  }
  if (!description.value.trim()) {
    showFailToast('请填写商品描述')
    return
  }
  const priceNum = Number(price.value)
  if (!priceNum || priceNum < 1 || !Number.isInteger(priceNum)) {
    showFailToast('积分定价需为 ≥1 的整数')
    return
  }
  submitting.value = true
  try {
    await publishFleaItem({
      title: title.value.trim(),
      description: description.value.trim(),
      category: category.value,
      price: priceNum,
      imageUrl: imageUrl.value || undefined,
    })
    showSuccessToast('发布成功')
    router.replace('/mobile/flea/mine?tab=published')
  } catch {
    // 发布失败：拦截器已 Toast，此处避免未捕获异常
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page flea-publish">
    <van-nav-bar title="发布闲置" left-arrow @click-left="router.back()" />

    <div class="card flea-publish__form">
      <van-field
        :model-value="categoryText()"
        is-link
        readonly
        label="分类"
        placeholder="请选择闲置分类"
        @click="showCategory = true"
      />
      <van-field v-model="title" maxlength="64" label="标题" placeholder="如：九成新儿童绘本 5 本" />
      <van-field
        v-model="description"
        type="textarea"
        rows="4"
        maxlength="500"
        show-word-limit
        label="描述"
        placeholder="新旧程度、入手渠道、转让原因等"
      />
      <van-field v-model="price" type="digit" label="积分定价" placeholder="≥1 的整数">
        <template #button>
          <span class="flea-publish__unit">积分</span>
        </template>
      </van-field>

      <div class="flea-publish__img">
        <van-uploader :after-read="onImageRead" :max-count="1" accept="image/*">
          <div v-if="imageUrl" class="flea-publish__upload-box">
            <img :src="imageUrl" class="flea-publish__preview" alt="" />
            <button type="button" class="flea-publish__remove" @click.stop="imageUrl = ''">删除</button>
          </div>
          <div v-else class="flea-publish__upload-box flea-publish__upload-box--empty">
            <van-icon name="plus" size="28" />
            <span>上传封面</span>
          </div>
        </van-uploader>
        <span class="flea-publish__tip">封面图（选填，自动压缩 ≤300KB）</span>
      </div>

      <van-button
        block
        round
        type="primary"
        :loading="submitting"
        class="flea-publish__btn"
        @click="onSubmit"
      >
        发布
      </van-button>
    </div>

    <van-popup v-model:show="showCategory" position="bottom" round>
      <van-picker :columns="categoryColumns" title="选择分类" @confirm="onCategoryConfirm" @cancel="showCategory = false" />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.flea-publish {
  &__form {
    margin-top: $df-space-md;
    padding: $df-space-sm 0 $df-space-lg;
  }

  &__unit {
    color: var(--df-text-secondary);
    font-size: $df-font-size-sm;
  }

  &__img {
    margin: $df-space-md;
  }

  &__preview {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &__upload-box {
    position: relative;
    width: 96px;
    height: 96px;
    border-radius: $df-radius-sm;
    overflow: hidden;
  }

  &__upload-box--empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    background: var(--df-bg-page);
    border: 2px dashed var(--df-border-color);
  }

  &__remove {
    position: absolute;
    right: 4px;
    bottom: 4px;
    padding: 2px 10px;
    font-size: 12px;
    color: #fff;
    background: rgba(0, 0, 0, 0.55);
    border: none;
    border-radius: 999px;
  }

  &__tip {
    display: block;
    margin-top: $df-space-xs;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__btn {
    margin: $df-space-md $df-space-md 0;
  }
}
</style>
