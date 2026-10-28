<script setup lang="ts">
// PC 科普专栏：发布文章（封面/PDF 上传 + 下载设置）
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { request } from '@/api/request'

defineOptions({ name: 'pc-science-create' })

const route = useRoute()
const router = useRouter()

// 编辑模式：?id= 加载已有文章（仅自己未发布/被驳回的文章）
const editId = route.query.id ? String(route.query.id) : ''
const isEdit = !!editId

interface FormData {
  title: string
  description: string
  authorName: string
  coverUrl: string
  /** 雪花 ID 保持字符串，Number 丢精度 */
  pdfFileId: string
  pdfFilename: string
  pdfStoragePath: string
  categoryCode: string
  tags: string[]
  newTag?: string
  downloadPoints: number
  basePoints: number
  multiplier: number
  allowDownload: boolean
  isSystem: number
  content?: string
}

const formData = ref<FormData>({
  title: '',
  description: '',
  authorName: '',
  coverUrl: '',
  pdfFileId: '',
  pdfFilename: '',
  pdfStoragePath: '',
  categoryCode: '',
  tags: [],
  downloadPoints: 0,
  basePoints: 0.5,
  multiplier: 1.0,
  allowDownload: true,
  isSystem: 0,
  content: ''
})

const submitting = ref(false)
const coverUploading = ref(false)
const pdfUploading = ref(false)
const coverInputRef = ref<HTMLInputElement | null>(null)
const pdfInputRef = ref<HTMLInputElement | null>(null)

// 文件上传：POST /api/file/upload，返回 { id, url, name }（拦截器已拆包）
const uploadFile = async (file: File, biz: string) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('biz', biz)
  return (await request.post('/file/upload', fd)) as unknown as { id: string; url: string; name: string }
}

// 封面选择后立即上传
const onCoverPick = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    coverUploading.value = true
    const res = await uploadFile(file, 'science-cover')
    formData.value.coverUrl = res.url
  } catch (error) {
    console.error('封面上传失败:', error)
    showToast('封面上传失败')
  } finally {
    coverUploading.value = false
  }
}

// PDF 选择后立即上传
const onPdfPick = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    pdfUploading.value = true
    const res = await uploadFile(file, 'science-article')
    formData.value.pdfFileId = String(res.id)
    formData.value.pdfFilename = res.name
    // 存储路径：url 去掉 /api/file/preview/ 前缀，与后端登记一致
    formData.value.pdfStoragePath = res.url.replace(/^\/api\/file\/preview\//, '')
  } catch (error) {
    console.error('PDF 上传失败:', error)
    showToast('PDF 上传失败')
  } finally {
    pdfUploading.value = false
  }
}

// 添加标签
const addTag = () => {
  const trimmed = formData.value.newTag?.trim()
  if (!trimmed || formData.value.tags.includes(trimmed)) return

  formData.value.tags.push(trimmed)
  formData.value.newTag = ''
}

// 移除标签
const removeTag = (index: number) => {
  formData.value.tags.splice(index, 1)
}

// 提交发布
const handleSubmit = async () => {
  // 验证表单
  if (!formData.value.title.trim()) {
    showToast('请填写文章标题')
    return
  }
  if (!formData.value.description.trim()) {
    showToast('请填写文章简介')
    return
  }
  if (!formData.value.authorName.trim()) {
    showToast('请填写作者笔名')
    return
  }
  if (!formData.value.categoryCode) {
    showToast('请选择分类')
    return
  }
  if (!formData.value.coverUrl) {
    showToast('请上传封面图片')
    return
  }
  if (!formData.value.pdfStoragePath) {
    showToast('请上传 PDF 文件')
    return
  }

  try {
    submitting.value = true

    // 构建请求数据（文件已在选择时上传完成）
    const createData = {
      title: formData.value.title.trim(),
      description: formData.value.description.trim(),
      authorName: formData.value.authorName.trim(),
      coverUrl: formData.value.coverUrl,
      pdfFileId: formData.value.pdfFileId,
      pdfFilename: formData.value.pdfFilename,
      pdfStoragePath: formData.value.pdfStoragePath,
      categories: [{ code: formData.value.categoryCode, name: getCategoryName(formData.value.categoryCode) }],
      tags: formData.value.tags.join(','),
      downloadPoints: formData.value.allowDownload ? formData.value.downloadPoints : 0,
      basePoints: formData.value.basePoints,
      multiplier: formData.value.multiplier,
      allowDownload: formData.value.allowDownload ? 1 : 0
    }

    // 调用 API（拦截器拆包后返回 ScienceArticleVO）
    if (isEdit) {
      // 编辑：仅草稿/被驳回文章可编辑
      await request.put(`/science/articles/${editId}`, createData)
      showToast('保存成功')
      router.push(`/pc/science`)
    } else {
      const response = (await request.post('/science/articles', createData)) as unknown as { id: string }
      showToast('发布成功！当前状态为“待审核”')
      router.push(`/pc/science/article/${response.id}`)
    }
  } catch (error: any) {
    console.error('发布失败:', error)
    showToast(error?.message || '发布失败')
  } finally {
    submitting.value = false
  }
}

// 获取分类中文名
const getCategoryName = (code: string): string => {
  const names: Record<string, string> = {
    '垃圾分类': '垃圾分类',
    '资源回收': '资源回收',
    '电子垃圾': '电子垃圾',
    '垃圾': '垃圾',
    '有害垃圾': '有害垃圾',
    '生活方式': '生活方式',
    '循环经济': '循环经济'
  }
  return names[code] || code
}

// 编辑模式：加载文章详情回填表单
const loadArticleForEdit = async () => {
  try {
    const data = (await request.get(`/science/articles/${editId}`)) as unknown as any
    formData.value.title = data.title || ''
    formData.value.description = data.description || ''
    formData.value.coverUrl = data.coverUrl || ''
    formData.value.pdfFileId = String(data.pdfFileId || '')
    formData.value.pdfFilename = data.pdfFilename || ''
    formData.value.pdfStoragePath = data.pdfStoragePath || ''
    formData.value.authorName = data.author?.nickname || data.authorName || ''
    formData.value.downloadPoints = data.downloadPoints || 0
    formData.value.basePoints = data.basePoints || 0.5
    formData.value.multiplier = data.multiplier || 1.0
    formData.value.allowDownload = !!data.allowDownload
    formData.value.tags = (data.tags || '').split(',').filter(Boolean)
    // categories 为 JSON 字符串，解析取第一个 code
    try {
      const cats = typeof data.categories === 'string' ? JSON.parse(data.categories || '[]') : (data.categories || [])
      formData.value.categoryCode = cats[0]?.code || ''
    } catch {
      formData.value.categoryCode = ''
    }
  } catch (error) {
    console.error('加载文章失败:', error)
  }
}

onMounted(() => {
  if (isEdit) {
    loadArticleForEdit()
  }
})
</script>

<template>
  <div class="pc-create">
    <div class="container">
      <div class="page-header">
        <h1><SvgIcon :name="isEdit ? 'nav-pencil' : 'nav-memo'" :size="22" /> {{ isEdit ? '编辑科普文章' : '发布科普文章' }}</h1>
        <p class="subtitle">上传您的 PDF 文件，分享环保知识</p>
      </div>

      <form class="article-form" @submit.prevent="handleSubmit">
        <!-- 标题 -->
        <div class="form-group">
          <label class="label">文章标题 *</label>
          <input
            v-model="formData.title"
            type="text"
            placeholder="请输入吸引人的标题（最多 200 字）"
            class="form-input"
            maxlength="200"
          >
          <span class="char-count">{{ formData.title.length }}/200</span>
        </div>

        <!-- 简介 -->
        <div class="form-group">
          <label class="label">文章简介 *</label>
          <textarea
            v-model="formData.description"
            placeholder="请用 100-500 字简要介绍文章内容"
            class="form-textarea"
            rows="3"
            :maxlength="500"
          ></textarea>
          <span class="char-count">{{ formData.description.length }}/500</span>
        </div>

        <!-- 封面图片 -->
        <div class="form-group">
          <label class="label">封面图片 *</label>
          <div class="upload-box" @click="coverInputRef?.click()">
            <img v-if="formData.coverUrl" :src="formData.coverUrl" class="cover-preview">
            <span v-else>{{ coverUploading ? '上传中...' : '点击上传封面（JPG/PNG，≤5MB）' }}</span>
          </div>
          <input ref="coverInputRef" type="file" accept=".jpg,.jpeg,.png" style="display:none" @change="onCoverPick">
        </div>

        <!-- PDF 文件 -->
        <div class="form-group">
          <label class="label">PDF 文件 *</label>
          <div class="upload-box" @click="pdfInputRef?.click()">
            <span v-if="formData.pdfFilename"><SvgIcon name="nav-file" :size="14" /> {{ formData.pdfFilename }}</span>
            <span v-else>{{ pdfUploading ? '上传中...' : '点击上传 PDF 文档（最大 50MB）' }}</span>
          </div>
          <input ref="pdfInputRef" type="file" accept=".pdf" style="display:none" @change="onPdfPick">
        </div>

        <!-- 作者笔名 -->
        <div class="form-group">
          <label class="label">作者笔名 *</label>
          <input
            v-model="formData.authorName"
            type="text"
            placeholder="对外展示的作者名（最多 30 字）"
            class="form-input"
            maxlength="30"
          >
        </div>

        <!-- 分类选择 -->
        <div class="form-group">
          <label class="label">所属分类 *</label>
          <select v-model="formData.categoryCode" class="form-select">
            <option value="">请选择分类</option>
            <option value="垃圾分类">垃圾分类</option>
            <option value="资源回收">资源回收</option>
            <option value="电子垃圾">电子垃圾</option>
            <option value="垃圾">垃圾</option>
            <option value="其他垃圾">其他垃圾</option>
            <option value="有害垃圾">有害垃圾</option>
            <option value="生活方式">生活方式</option>
            <option value="循环经济">循环经济</option>
          </select>
        </div>

        <!-- 标签 -->
        <div class="form-group">
          <label class="label">文章标签</label>
          <div class="tags-input-wrapper">
            <div
              v-for="(tag, index) in formData.tags"
              :key="index"
              class="tag-item"
            >
              {{ tag }}
              <button
                type="button"
                @click="removeTag(index)"
                class="remove-tag">×</button>
            </div>

            <input
              v-model="formData.newTag"
              @keyup.enter="addTag"
              placeholder="输入标签后按回车"
              class="tag-input"
              :disabled="formData.tags.length >= 8"
            >
          </div>
          <span class="hint-text">共 {{ formData.tags.length }}/8 个标签</span>
        </div>

        <!-- 下载设置 -->
        <div class="form-group form-section">
          <h3 class="section-title"><SvgIcon name="nav-money-bag" :size="18" /> 下载设置</h3>

          <div class="config-row">
            <div class="config-item">
              <label class="label">是否允许下载 *</label>
              <div class="radio-group">
                <label class="radio-item" :class="{ active: formData.allowDownload }">
                  <input type="radio" v-model="formData.allowDownload" :value="true"> 允许下载
                </label>
                <label class="radio-item" :class="{ active: !formData.allowDownload }">
                  <input type="radio" v-model="formData.allowDownload" :value="false"> 禁止下载
                </label>
              </div>
            </div>

            <div v-if="formData.allowDownload" class="config-item">
              <label class="label">下载积分 *</label>
              <div class="radio-group">
                <label class="radio-item" :class="{ active: formData.downloadPoints === 0 }">
                  <input type="radio" v-model="formData.downloadPoints" :value="0"> 免费
                </label>
                <label class="radio-item" :class="{ active: formData.downloadPoints > 0 }">
                  <input type="radio" v-model="formData.downloadPoints" :value="10"> 收费
                </label>
              </div>
              <input
                v-if="formData.downloadPoints > 0"
                v-model.number="formData.downloadPoints"
                type="number"
                min="1"
                max="100"
                placeholder="输入积分（1-100）"
                class="form-input"
              >
            </div>

            <div v-if="formData.allowDownload" class="config-item">
              <label class="label">基础单价 (元)</label>
              <input
                v-model.number="formData.basePoints"
                type="number"
                min="0.10"
                max="10.00"
                step="0.10"
                class="form-input"
              >
            </div>

            <div v-if="formData.allowDownload" class="config-item">
              <label class="label">热门系数</label>
              <input
                v-model.number="formData.multiplier"
                type="number"
                min="1.0"
                max="5.0"
                step="0.1"
                class="form-input"
              >
            </div>
          </div>

          <div class="price-calculation">
            <p v-if="!formData.allowDownload">禁止下载时，其他用户仅可在线预览</p>
            <p v-else-if="formData.downloadPoints === 0">该文章将免费提供给所有用户下载</p>
            <p v-else>预计作者收益 = 下载次数 × 基础单价 × 热门系数</p>
          </div>
        </div>

        <!-- 内容摘要（可选）-->
        <div class="form-group">
          <label class="label">文章摘要（可选）</label>
          <textarea
            v-model="formData.content"
            placeholder="请填写文章内容摘要（可选）"
            class="form-textarea"
            rows="8"
          ></textarea>
        </div>

        <!-- 提交按钮 -->
        <div class="form-actions">
          <button type="button" @click="router.back()" class="btn btn-cancel">取消</button>
          <button type="submit" :disabled="submitting" class="btn btn-submit">
            {{ submitting ? '发布中...' : (isEdit ? '保存修改' : '发布文章') }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-create {
  min-height: calc(100vh - 72px);
  padding: 48px 24px 80px;
}

.container {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-header h1 {
  font-size: 36px;
  color: var(--df-text-primary);
  margin-bottom: 8px;
}

.subtitle {
  font-size: 16px;
  color: var(--df-text-secondary);
}

.article-form {
  background: var(--df-bg-card);
  border-radius: 12px;
  padding: 40px;
  box-shadow: var(--df-shadow-card);
}

.form-group {
  margin-bottom: 24px;
}

.label {
  display: block;
  font-weight: 600;
  color: var(--df-text-primary);
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 6px;
  font-size: 14px;
  background: var(--df-bg-page, var(--df-bg-nested));
  color: var(--df-text-primary);
  transition: border-color 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: var(--df-color-primary);
}

.char-count {
  float: right;
  font-size: 12px;
  color: var(--df-text-secondary);
}

.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 6px;
  font-size: 14px;
  background: var(--df-bg-page, var(--df-bg-nested));
  color: var(--df-text-primary);
  resize: vertical;
}

.form-select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 6px;
  font-size: 14px;
  background: var(--df-bg-page, var(--df-bg-nested));
  color: var(--df-text-primary);
}

.upload-box {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 88px;
  padding: 12px;
  border: 1px dashed var(--df-border-color, var(--df-border-input));
  border-radius: 8px;
  background: var(--df-bg-page, var(--df-bg-nested));
  color: var(--df-text-secondary);
  font-size: 14px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s;
}

.upload-box:hover {
  border-color: var(--df-color-primary);
}

.cover-preview {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.tags-input-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 6px;
  align-items: center;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--df-fill, var(--df-bg-nested));
  border-radius: 12px;
  font-size: 14px;
  color: var(--df-text-primary);
}

.remove-tag {
  border: none;
  background: none;
  color: var(--df-text-secondary);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
}

.tag-input {
  flex: 1;
  min-width: 100px;
  border: none;
  outline: none;
  font-size: 14px;
  background: transparent;
  color: var(--df-text-primary);
}

.hint-text {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--df-text-secondary);
}

.form-section {
  padding-top: 24px;
  border-top: 1px solid var(--df-border-color, var(--df-border-color));
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--df-text-primary);
}

.config-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.config-item {
  display: flex;
  flex-direction: column;
}

.radio-group {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.radio-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: 1px solid var(--df-border-color, var(--df-border-color));
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.radio-item input {
  display: none;
}

.radio-item.active {
  border-color: var(--df-color-primary);
  background: var(--df-primary-bg, var(--df-color-primary-light));
  color: var(--df-color-primary);
  font-weight: 500;
}

.price-calculation {
  margin-top: 16px;
  padding: 12px;
  background: var(--df-bg-page, var(--df-bg-nested));
  border-radius: 6px;
  font-size: 14px;
  color: var(--df-color-primary);
  font-weight: 500;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid var(--df-border-color, var(--df-border-color));
}

.btn {
  padding: 12px 32px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-cancel {
  background: var(--df-fill, var(--df-bg-nested));
  color: var(--df-text-secondary);
}

.btn-submit {
  background: var(--df-gradient-brand);
  color: white;
  flex: 1;
  max-width: 200px;
  margin-left: auto;
}

.btn-submit:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
