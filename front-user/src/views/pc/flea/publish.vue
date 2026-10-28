<script setup lang="ts">
// PC 跳蚤市场发布（Upgrade U4）：分类/标题/描述/积分定价/图片上传（biz=flea，canvas 压缩 ≤300KB）
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { request } from '@/api/request'
import { publishFleaItem, FLEA_CATEGORIES } from '@/api/business'

defineOptions({ name: 'pc-flea-publish' })

const router = useRouter()

const category = ref('')
const title = ref('')
const description = ref('')
const price = ref('')
const imageUrl = ref('')
const submitting = ref(false)

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
    router.replace('/pc/flea/mine?tab=published')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pc-flea-publish">
    <div class="container">
      <h1 class="page-title">发布闲置</h1>

      <div class="card form">
        <div class="form__row">
          <label class="form__label">分类 <em>*</em></label>
          <select v-model="category" class="form__select">
            <option value="" disabled>请选择闲置分类</option>
            <option v-for="c in FLEA_CATEGORIES" :key="c.code" :value="c.code">{{ c.name }}</option>
          </select>
        </div>
        <div class="form__row">
          <label class="form__label">标题 <em>*</em></label>
          <input v-model="title" maxlength="64" class="form__input" placeholder="如：九成新儿童绘本 5 本（≤64 字）" />
        </div>
        <div class="form__row">
          <label class="form__label">描述 <em>*</em></label>
          <textarea
            v-model="description"
            rows="4"
            maxlength="500"
            class="form__textarea"
            placeholder="新旧程度、入手渠道、转让原因等（≤500 字）"
          ></textarea>
        </div>
        <div class="form__row">
          <label class="form__label">积分定价 <em>*</em></label>
          <input v-model="price" type="number" min="1" step="1" class="form__input form__input--short" placeholder="≥1 的整数" />
        </div>
        <div class="form__row">
          <label class="form__label">封面图</label>
          <div class="form__upload">
            <van-uploader :after-read="onImageRead" :max-count="1" accept="image/*">
              <div v-if="imageUrl" class="form__upload-box">
                <img :src="imageUrl" class="form__preview" alt="" />
                <button type="button" class="form__upload-remove" @click.stop="imageUrl = ''">删除</button>
              </div>
              <div v-else class="form__upload-box form__upload-box--empty">
                <van-icon name="plus" size="28" />
                <span>上传封面</span>
              </div>
            </van-uploader>
            <span class="form__tip">选填，自动压缩 ≤300KB</span>
          </div>
        </div>
        <div class="form__actions">
          <button class="btn-cancel" @click="router.back()">取消</button>
          <button class="btn-submit" :disabled="submitting" @click="onSubmit">
            {{ submitting ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-flea-publish {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container {
  max-width: 720px;
  margin: 0 auto;
}

.page-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--df-text-primary);
  margin-bottom: 48px;
  text-align: center;
}

.card {
  background: var(--df-bg-card);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--df-shadow-card);
}

.form {
  &__row {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    padding: 14px 0;
    border-bottom: 1px solid var(--df-border-color);

    &:last-of-type { border-bottom: none; }
  }

  &__label {
    width: 88px;
    flex-shrink: 0;
    padding-top: 10px;
    font-size: 15px;
    font-weight: 600;
    color: var(--df-text-regular);

    em {
      color: var(--df-color-danger);
      font-style: normal;
    }
  }

  &__input {
    flex: 1;
    padding: 10px 14px;
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

    &--short {
      flex: 0 0 220px;
    }
  }

  &__select {
    flex: 0 0 260px;
    padding: 10px 14px;
    font-size: 15px;
    color: var(--df-text-primary);
    background: var(--df-bg-card);
    border: 2px solid var(--df-border-color);
    border-radius: 10px;
    outline: none;
    cursor: pointer;
    transition: all 0.3s ease;

    &:focus {
      border-color: var(--df-color-primary);
    }
  }

  &__textarea {
    flex: 1;
    padding: 10px 14px;
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

  &__upload {
    flex: 1;
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
    border-radius: 10px;
    overflow: hidden;
  }

  &__upload-box--empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    font-size: 13px;
    color: var(--df-text-secondary);
    background: var(--df-bg-card);
    border: 2px dashed var(--df-border-color);
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      border-color: var(--df-color-primary);
      color: var(--df-color-primary);
    }
  }

  &__upload-remove {
    position: absolute;
    right: 4px;
    bottom: 4px;
    padding: 2px 10px;
    font-size: 12px;
    color: #fff;
    background: rgba(0, 0, 0, 0.55);
    border: none;
    border-radius: 999px;
    cursor: pointer;
  }

  &__tip {
    display: block;
    margin-top: 8px;
    font-size: 13px;
    color: var(--df-text-secondary);
  }

  &__actions {
    display: flex;
    gap: 12px;
    margin-top: 24px;
  }
}

.btn-cancel {
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

.btn-submit {
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
</style>
