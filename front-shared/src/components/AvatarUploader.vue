<script setup lang="ts">
// 头像上传器（Upgrade U1，三端通用；弹窗裁剪改版）：
// 点击头像打开「修改头像」弹窗 → 本地上传 / 拍照上传 → 拖拽裁剪框选择区域（手柄调整大小）
// → 右侧实时预览 156/88/20 三种尺寸 → 确定后裁剪为 512x512 上传
// 上传走各端统一约定 POST /file/upload?biz=avatar（返回 {url}）；失败抛 error 事件由调用方 Toast
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { getSharedRequest } from '../http/sharedHttp'
import { BizError } from '../http/createRequest'

const props = withDefaults(
  defineProps<{
    /** 当前头像 URL（空串 = 未设置，显示默认人像 + 上传按钮） */
    modelValue: string
    /** 显示尺寸（正方形），默认 80 */
    size?: number
    /** 上传业务目录，默认 avatar（图片类校验） */
    biz?: string
  }>(),
  { size: 80, biz: 'avatar' },
)
const emit = defineEmits<{
  (e: 'update:modelValue', url: string): void
  (e: 'uploaded', url: string): void
  (e: 'error', message: string): void
}>()

// ---- 常量 ----
const OK_TYPES = ['image/jpeg', 'image/png', 'image/gif']
const MIN_CROP = 40 // 裁剪框最小边长（px）
const OUT_SIZE = 512 // 上传输出尺寸（正方形）
const MAX_UPLOAD = 2 * 1024 * 1024 // 与后端 AVATAR_MAX_SIZE（2MB）对齐

// ---- 触发器状态 ----
const inputLocalRef = ref<HTMLInputElement | null>(null)
const inputCameraRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

// ---- 弹窗状态 ----
const dialogOpen = ref(false)
const stageRef = ref<HTMLDivElement | null>(null)
const pv156 = ref<HTMLCanvasElement | null>(null)
const pv88 = ref<HTMLCanvasElement | null>(null)
const pv20 = ref<HTMLCanvasElement | null>(null)

const imgEl = ref<HTMLImageElement | null>(null)
const objectUrl = ref('')
const fileName = ref('avatar.jpg')
/** 图片在裁剪舞台中的 aspect-fit 显示矩形 */
const view = ref({ ox: 0, oy: 0, dw: 0, dh: 0, scale: 1 })
/** 正方形裁剪框（舞台坐标） */
const crop = ref({ x: 0, y: 0, s: 0 })
const stageSide = ref(0)

const imgLoaded = computed(() => !!imgEl.value)

const imgStyle = computed(() => ({
  left: `${view.value.ox}px`,
  top: `${view.value.oy}px`,
  width: `${view.value.dw}px`,
  height: `${view.value.dh}px`,
}))
const cropStyle = computed(() => ({
  left: `${crop.value.x}px`,
  top: `${crop.value.y}px`,
  width: `${crop.value.s}px`,
  height: `${crop.value.s}px`,
}))

// ---- 拖拽状态 ----
type Handle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w'
const drag = ref<{
  mode: 'move' | 'resize'
  handle?: Handle
  px: number
  py: number
  c0: { x: number; y: number; s: number }
  rectLeft: number
  rectTop: number
} | null>(null)

let raf = 0
let prevOverflow = ''

function clamp(v: number, lo: number, hi: number) {
  return Math.min(Math.max(v, lo), Math.max(lo, hi))
}

// ---- 弹窗开关 ----
function openDialog() {
  if (dialogOpen.value) return
  dialogOpen.value = true
  prevOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  nextTick(() => {
    measureStage()
    // 已有头像时预加载进裁剪器，便于在当前头像基础上调整
    if (props.modelValue) loadRemote(props.modelValue)
  })
  window.addEventListener('resize', onWindowResize)
  window.addEventListener('keydown', onEsc)
}

function closeDialog() {
  dialogOpen.value = false
  document.body.style.overflow = prevOverflow
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('keydown', onEsc)
  resetImage()
}

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape' && !uploading.value) closeDialog()
}

function resetImage() {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = ''
  imgEl.value = null
  crop.value = { x: 0, y: 0, s: 0 }
}

// ---- 几何计算 ----
function measureStage() {
  const rect = stageRef.value?.getBoundingClientRect()
  stageSide.value = rect ? Math.round(rect.width) : 0
  if (imgEl.value) fitView(false)
}

function onWindowResize() {
  measureStage()
}

/** aspect-fit 计算图片显示矩形；resetCrop=true 时重置裁剪框为居中 80% */
function fitView(resetCrop: boolean) {
  const img = imgEl.value
  const side = stageSide.value
  if (!img || !side) return
  const nw = img.naturalWidth || 1
  const nh = img.naturalHeight || 1
  const scale = Math.min(side / nw, side / nh)
  const dw = nw * scale
  const dh = nh * scale
  view.value = { ox: (side - dw) / 2, oy: (side - dh) / 2, dw, dh, scale }
  if (resetCrop || !crop.value.s) {
    const s = Math.max(MIN_CROP, Math.min(dw, dh) * 0.8)
    crop.value = { x: view.value.ox + (dw - s) / 2, y: view.value.oy + (dh - s) / 2, s }
  } else {
    // 舞台尺寸变化时把裁剪框钳回图片范围
    crop.value = {
      x: clamp(crop.value.x, view.value.ox, view.value.ox + dw - crop.value.s),
      y: clamp(crop.value.y, view.value.oy, view.value.oy + dh - crop.value.s),
      s: Math.min(crop.value.s, Math.min(dw, dh)),
    }
  }
  requestPreview()
}

// ---- 图片加载 ----
function pickLocal() {
  inputLocalRef.value?.click()
}
function pickCamera() {
  inputCameraRef.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!OK_TYPES.includes(file.type)) {
    emit('error', '仅支持 JPG、GIF、PNG 格式图片')
    return
  }
  fileName.value = file.name || 'avatar.jpg'
  loadBlob(file)
}

function loadBlob(blob: Blob) {
  resetImage()
  objectUrl.value = URL.createObjectURL(blob)
  const img = new Image()
  img.onload = () => {
    imgEl.value = img
    fitView(true)
  }
  img.onerror = () => emit('error', '图片解码失败，请更换图片')
  img.src = objectUrl.value
}

async function loadRemote(url: string) {
  // 预览接口为公开资源（IM 会话头像等场景直接 <img> 引用），无需鉴权头
  try {
    const resp = await fetch(url)
    if (!resp.ok) return
    loadBlob(await resp.blob())
  } catch {
    // 预加载失败时保持空裁剪器，用户可重新上传
  }
}

// ---- 裁剪框拖拽 ----
function onStagePointerDown(e: PointerEvent) {
  if (!imgEl.value || !stageRef.value) return
  const target = e.target as HTMLElement
  const handle = target.dataset.handle as Handle | undefined
  const isBox = !!target.closest('.df-cropbox')
  if (!handle && !isBox) return
  const rect = stageRef.value.getBoundingClientRect()
  drag.value = {
    mode: handle ? 'resize' : 'move',
    handle,
    px: e.clientX,
    py: e.clientY,
    c0: { ...crop.value },
    rectLeft: rect.left,
    rectTop: rect.top,
  }
  e.preventDefault()
  window.addEventListener('pointermove', onWinMove)
  window.addEventListener('pointerup', onWinUp)
}

function onWinMove(e: PointerEvent) {
  const d = drag.value
  const v = view.value
  if (!d) return
  const dx = e.clientX - d.px
  const dy = e.clientY - d.py
  const c0 = d.c0
  if (d.mode === 'move') {
    crop.value = {
      x: clamp(c0.x + dx, v.ox, v.ox + v.dw - c0.s),
      y: clamp(c0.y + dy, v.oy, v.oy + v.dh - c0.s),
      s: c0.s,
    }
  } else {
    // 手柄缩放：保持正方形，锚定对边/对角，再钳回图片范围
    let s2 = c0.s
    switch (d.handle) {
      case 'se': s2 = c0.s + Math.max(dx, dy); break
      case 'nw': s2 = c0.s - Math.max(dx, dy); break
      case 'ne': s2 = c0.s + Math.max(dx, -dy); break
      case 'sw': s2 = c0.s + Math.max(-dx, dy); break
      case 'e': s2 = c0.s + dx; break
      case 'w': s2 = c0.s - dx; break
      case 's': s2 = c0.s + dy; break
      case 'n': s2 = c0.s - dy; break
    }
    s2 = clamp(s2, MIN_CROP, Math.min(v.dw, v.dh))
    let nx = c0.x
    let ny = c0.y
    switch (d.handle) {
      case 'nw': nx = c0.x + c0.s - s2; ny = c0.y + c0.s - s2; break
      case 'ne': ny = c0.y + c0.s - s2; break
      case 'sw': nx = c0.x + c0.s - s2; break
      case 'w': nx = c0.x + c0.s - s2; ny = c0.y + (c0.s - s2) / 2; break
      case 'e': ny = c0.y + (c0.s - s2) / 2; break
      case 'n': ny = c0.y + c0.s - s2; nx = c0.x + (c0.s - s2) / 2; break
      case 's': nx = c0.x + (c0.s - s2) / 2; break
    }
    crop.value = {
      x: clamp(nx, v.ox, v.ox + v.dw - s2),
      y: clamp(ny, v.oy, v.oy + v.dh - s2),
      s: s2,
    }
  }
  requestPreview()
}

function onWinUp() {
  drag.value = null
  window.removeEventListener('pointermove', onWinMove)
  window.removeEventListener('pointerup', onWinUp)
}

// ---- 预览 ----
/** 裁剪框映射回原图自然坐标 */
function sourceRect() {
  const scale = view.value.scale || 1
  return {
    x: (crop.value.x - view.value.ox) / scale,
    y: (crop.value.y - view.value.oy) / scale,
    s: crop.value.s / scale,
  }
}

function requestPreview() {
  if (raf) return
  raf = requestAnimationFrame(() => {
    raf = 0
    drawPreviews()
  })
}

function drawPreviews() {
  const img = imgEl.value
  if (!img || !crop.value.s) return
  const src = sourceRect()
  const targets: Array<[HTMLCanvasElement | null, number]> = [
    [pv156.value, 156],
    [pv88.value, 88],
    [pv20.value, 20],
  ]
  for (const [cv, size] of targets) {
    const ctx = cv?.getContext('2d')
    if (!ctx) continue
    ctx.clearRect(0, 0, size, size)
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, size, size)
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(img, src.x, src.y, src.s, src.s, 0, 0, size, size)
  }
}

// ---- 确定：裁剪 + 上传 ----
async function onConfirm() {
  const img = imgEl.value
  if (!img || uploading.value || !crop.value.s) return
  uploading.value = true
  try {
    const src = sourceRect()
    const canvas = document.createElement('canvas')
    canvas.width = OUT_SIZE
    canvas.height = OUT_SIZE
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('canvas unavailable')
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, OUT_SIZE, OUT_SIZE)
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(img, src.x, src.y, src.s, src.s, 0, 0, OUT_SIZE, OUT_SIZE)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9))
    if (!blob) throw new Error('encode failed')
    if (blob.size > MAX_UPLOAD) {
      emit('error', '图片大小超过限制（2MB），请缩小裁剪范围')
      return
    }
    const form = new FormData()
    form.append('file', blob, fileName.value.replace(/\.\w+$/, '') + '.jpg')
    const req = getSharedRequest()
    if (!req) {
      emit('error', '请求层未就绪，请刷新页面重试')
      return
    }
    // 走统一请求层：自动注入 Token + 401 续期重放；skipErrorToast 避免与调用方 @error 重复提示
    const data = await req.post<{ url?: string }>(`/file/upload?biz=${props.biz}`, form, {
      skipErrorToast: true,
      allowDuplicate: true,
    })
    const url = data?.url ?? ''
    if (!url) {
      emit('error', '上传失败，请重试')
      return
    }
    emit('update:modelValue', url)
    emit('uploaded', url)
    closeDialog()
  } catch (err) {
    emit('error', err instanceof BizError ? err.message : '上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf)
  if (dialogOpen.value) document.body.style.overflow = prevOverflow
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
})
</script>

<template>
  <div
    class="df-avatar-uploader"
    :style="{ width: `${size}px`, height: `${size}px` }"
    :class="{ 'is-uploading': uploading }"
    role="button"
    tabindex="0"
    :aria-label="modelValue ? '更换头像' : '上传头像'"
    @click="openDialog"
    @keydown.enter="openDialog"
  >
    <img v-if="modelValue" :src="modelValue" class="df-avatar-uploader__img" alt="头像" />
    <!-- 默认人像兜底（内联 SVG，三端通用） -->
    <svg v-else class="df-avatar-uploader__placeholder" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
    </svg>

    <!-- hover / 未设置时显示操作遮罩 -->
    <div class="df-avatar-uploader__mask">
      <template v-if="uploading">上传中…</template>
      <template v-else>{{ modelValue ? '更换' : '上传' }}</template>
    </div>
  </div>

  <Teleport to="body">
    <div v-if="dialogOpen" class="df-avatar-overlay" @click.self="!uploading && closeDialog()">
      <div class="df-avatar-dialog" role="dialog" aria-modal="true" aria-label="修改头像">
        <header class="df-avatar-dialog__header">
          <span class="df-avatar-dialog__title">修改头像</span>
          <button class="df-avatar-dialog__close" aria-label="关闭" :disabled="uploading" @click="closeDialog">×</button>
        </header>

        <div class="df-avatar-dialog__body">
          <div class="df-avatar-dialog__actions">
            <button type="button" class="df-btn" :disabled="uploading" @click="pickLocal">本地上传</button>
            <button type="button" class="df-btn" :disabled="uploading" @click="pickCamera">拍照上传</button>
          </div>
          <p class="df-avatar-dialog__tip">请上传小于2M的JPG、GIF或PNG文件；该头像会对外显示，请您留意不要上传私密头像</p>

          <div class="df-avatar-dialog__main">
            <div class="df-avatar-crop">
              <div ref="stageRef" class="df-avatar-stage" @pointerdown="onStagePointerDown">
                <img
                  v-if="imgLoaded"
                  :src="objectUrl"
                  class="df-avatar-stage__img"
                  :style="imgStyle"
                  alt=""
                  draggable="false"
                />
                <div v-if="imgLoaded" class="df-cropbox" :style="cropStyle">
                  <span class="df-cropbox__handle df-cropbox__handle--nw" data-handle="nw" />
                  <span class="df-cropbox__handle df-cropbox__handle--n" data-handle="n" />
                  <span class="df-cropbox__handle df-cropbox__handle--ne" data-handle="ne" />
                  <span class="df-cropbox__handle df-cropbox__handle--e" data-handle="e" />
                  <span class="df-cropbox__handle df-cropbox__handle--se" data-handle="se" />
                  <span class="df-cropbox__handle df-cropbox__handle--s" data-handle="s" />
                  <span class="df-cropbox__handle df-cropbox__handle--sw" data-handle="sw" />
                  <span class="df-cropbox__handle df-cropbox__handle--w" data-handle="w" />
                </div>
                <div v-else class="df-avatar-stage__empty">暂未选择图片<br />点击「本地上传」或「拍照上传」</div>
              </div>
              <p class="df-avatar-dialog__tip">拖动框可以选择裁剪区域，用手柄可以调整大小</p>
            </div>

            <div class="df-avatar-preview">
              <p class="df-avatar-preview__title">头像会生成三种尺寸，请注意头像是否清晰</p>
              <div class="df-avatar-preview__item">
                <canvas ref="pv156" width="156" height="156" />
                <span>156 x 156像素</span>
              </div>
              <div class="df-avatar-preview__item">
                <canvas ref="pv88" width="88" height="88" />
                <span>88 x 88像素</span>
              </div>
              <div class="df-avatar-preview__item">
                <canvas ref="pv20" width="20" height="20" />
                <span>20 x 20像素</span>
              </div>
            </div>
          </div>
        </div>

        <footer class="df-avatar-dialog__footer">
          <button type="button" class="df-btn df-btn--primary" :disabled="!imgLoaded || uploading" @click="onConfirm">
            {{ uploading ? '上传中…' : '确定' }}
          </button>
          <button type="button" class="df-btn" :disabled="uploading" @click="closeDialog">取消</button>
        </footer>
      </div>
    </div>
  </Teleport>

  <input ref="inputLocalRef" type="file" accept="image/png,image/jpeg,image/gif" hidden @change="onFileChange" />
  <input ref="inputCameraRef" type="file" accept="image/*" capture="environment" hidden @change="onFileChange" />
</template>

<style scoped lang="scss">
@use '../styles/tokens.scss' as *;

.df-avatar-uploader {
  position: relative;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  background: $df-bg-page;
  border: 1px solid $df-border-color;
  cursor: pointer;
  outline: none;

  &:focus-visible {
    box-shadow: 0 0 0 2px $df-color-primary-light;
  }

  &__img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &__placeholder {
    position: absolute;
    inset: 22%;
    width: 56%;
    height: 56%;
    color: $df-text-placeholder;
  }

  &__mask {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: $df-font-size-sm;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover &__mask,
  &.is-uploading &__mask {
    opacity: 1;
  }
}

// ---- 修改头像弹窗 ----
.df-avatar-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000; // 低于 Vant/Element Plus 的 Toast/Message（2000+），错误提示可盖在弹窗上
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $df-space-lg;
}

.df-avatar-dialog {
  display: flex;
  flex-direction: column;
  width: min(680px, 100%);
  max-height: min(760px, 100%);
  background: $df-bg-card;
  border-radius: $df-radius-lg;
  box-shadow: $df-shadow-float;
  color: $df-text-primary;

  &__header {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: $df-space-lg $df-space-xl;
    border-bottom: 1px solid $df-border-color;
  }

  &__title {
    font-size: $df-font-size-base;
    font-weight: 600;
  }

  &__close {
    position: absolute;
    right: $df-space-md;
    top: 50%;
    transform: translateY(-50%);
    border: none;
    background: none;
    font-size: $df-font-size-xl;
    line-height: 1;
    color: $df-text-secondary;
    cursor: pointer;

    &:hover {
      color: $df-text-primary;
    }
  }

  &__body {
    padding: $df-space-xl;
    overflow-y: auto;
  }

  &__actions {
    display: flex;
    gap: $df-space-md;
  }

  &__tip {
    margin: $df-space-md 0;
    font-size: $df-font-size-sm;
    color: $df-text-secondary;
  }

  &__main {
    display: flex;
    gap: $df-space-xl;
    flex-wrap: wrap;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: $df-space-md;
    padding: $df-space-lg $df-space-xl;
    border-top: 1px solid $df-border-color;
  }
}

.df-btn {
  padding: 7px 18px;
  border: 1px solid $df-border-color;
  border-radius: $df-radius-sm;
  background: $df-bg-card;
  color: $df-text-regular;
  font-size: $df-font-size-base;
  cursor: pointer;

  &:hover:not(:disabled) {
    border-color: $df-color-primary;
    color: $df-color-primary;
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  &--primary {
    border-color: $df-color-primary;
    background: $df-color-primary;
    color: #fff;

    &:hover:not(:disabled) {
      background: $df-color-primary-dark;
      color: #fff;
    }
  }
}

// ---- 裁剪舞台 ----
.df-avatar-crop {
  flex: 1 1 300px;
  min-width: 260px;
}

.df-avatar-stage {
  position: relative;
  width: 100%;
  max-width: 340px;
  aspect-ratio: 1;
  margin: 0 auto;
  overflow: hidden;
  touch-action: none;
  user-select: none;
  background: conic-gradient(#c9c9c9 25%, #a8a8a8 0 50%, #c9c9c9 0 75%, #a8a8a8 0);
  background-size: 16px 16px;

  &__img {
    position: absolute;
    pointer-events: none;
  }

  &__empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    font-size: $df-font-size-sm;
    color: #fff;
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
  }
}

.df-cropbox {
  position: absolute;
  border: 1px solid $df-color-carbon;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.45);
  cursor: move;

  &__handle {
    position: absolute;
    width: 9px;
    height: 9px;
    background: $df-color-carbon;

    &--nw { left: -5px; top: -5px; cursor: nwse-resize; }
    &--n { left: 50%; top: -5px; margin-left: -4.5px; cursor: ns-resize; }
    &--ne { right: -5px; top: -5px; cursor: nesw-resize; }
    &--e { right: -5px; top: 50%; margin-top: -4.5px; cursor: ew-resize; }
    &--se { right: -5px; bottom: -5px; cursor: nwse-resize; }
    &--s { left: 50%; bottom: -5px; margin-left: -4.5px; cursor: ns-resize; }
    &--sw { left: -5px; bottom: -5px; cursor: nesw-resize; }
    &--w { left: -5px; top: 50%; margin-top: -4.5px; cursor: ew-resize; }
  }
}

// ---- 三尺寸预览 ----
.df-avatar-preview {
  flex: 1 1 200px;
  min-width: 180px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: $df-space-md;

  &__title {
    margin: 0;
    font-size: $df-font-size-base;
    color: $df-text-regular;
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: $df-space-xs;

    canvas {
      display: block;
      background: var(--df-bg-card);
      border: 1px solid $df-border-color;
    }

    span {
      font-size: $df-font-size-sm;
      color: $df-text-secondary;
    }
  }
}

// ---- 窄屏：预览横排 ----
@media (max-width: 560px) {
  .df-avatar-dialog__body {
    padding: $df-space-lg;
  }

  .df-avatar-preview {
    flex-direction: row;
    align-items: flex-end;
    flex-wrap: wrap;
    gap: $df-space-lg;
  }
}
</style>
