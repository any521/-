<script setup lang="ts">
// ============================================================
// 上传识别共享面板（F6 扩展：上传图片/视频端侧识别与分类，零上传）
// mode='image'：useStaticDetect 单图一次性推理；
// mode='video'：useLiveVideoDetect 播放中逐帧实时识别（框随播放实时变化）。
// 检测框已逆变换回源媒体坐标系，按百分比直接叠加；
// 移动端/PC 端页面均为薄包装（nav/布局差异在页面层）。
// ============================================================
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { useStaticDetect } from '@/composables/useStaticDetect'
import { useLiveVideoDetect } from '@/composables/useLiveVideoDetect'
import type { RawDetection } from '@/composables/yoloDecode'
import { styleOf } from '@/constants/categoryStyle'

const props = withDefaults(defineProps<{ mode: 'image' | 'video'; /** 页签是否激活（非激活时停视频实时循环） */ active?: boolean }>(), {
  active: true,
})

const {
  supported: imgSupported,
  busy: imgBusy,
  modelVersion: imgVersion,
  error: imgError,
  detect,
} = useStaticDetect()
const {
  supported: vidSupported,
  modelVersion: vidVersion,
  running: videoRunning,
  detections: videoDetections,
  error: vidError,
  start: videoStart,
  stop: videoStop,
} = useLiveVideoDetect()

const supported = computed(() => (props.mode === 'image' ? imgSupported.value : vidSupported.value))
const modelVersion = computed(() => (props.mode === 'image' ? imgVersion.value : vidVersion.value))

const files = ref<UploaderFileListItem[]>([])
// ---- 图片模式状态 ----
const imagePreview = ref('')
const imageDetections = ref<RawDetection[]>([])
const imageScanned = ref(false)
let imageObjectUrl: string | null = null
// ---- 视频模式状态（播放中逐帧实时识别） ----
const videoPreview = ref('')
const videoEl = ref<HTMLVideoElement | null>(null)
const videoRatio = ref('')
let videoObjectUrl: string | null = null

/** 模型加载失败统一 Toast（非浏览器弹窗）；error 为具体失败原因（路径/MIME/COOP-COEP 等） */
watch(vidError, (e) => {
  if (e === 'infer') return
  if (e) showToast(`模型加载失败：${e}`)
})

/** 页签切走停循环、切回且已上传视频时恢复 */
watch(
  () => props.active,
  (on) => {
    if (props.mode !== 'video') return
    if (!on) videoStop()
    else if (videoEl.value && videoPreview.value) videoStart(videoEl.value)
  },
)

onBeforeUnmount(() => {
  if (videoObjectUrl) URL.revokeObjectURL(videoObjectUrl)
})

function pickFile(item: UploaderFileListItem | UploaderFileListItem[]): File | null {
  const file = Array.isArray(item) ? item[0]?.file : item.file
  return file ?? null
}

/** 图片上传：对象 URL → 解码 → 端侧一次性推理 */
async function onImageRead(item: UploaderFileListItem | UploaderFileListItem[]) {
  const file = pickFile(item)
  if (!file) return
  imageScanned.value = false
  imageDetections.value = []
  if (imageObjectUrl) URL.revokeObjectURL(imageObjectUrl)
  imageObjectUrl = URL.createObjectURL(file)
  imagePreview.value = imageObjectUrl
  const img = new Image()
  img.src = imageObjectUrl
  try {
    await img.decode()
  } catch {
    showToast('图片无法解析')
    return
  }
  imageDetections.value = await detect(img)
  imageScanned.value = true
  if (imgError.value === 'infer') showToast('识别失败，请重试')
  else if (imgError.value) showToast(`模型加载失败：${imgError.value}`)
}

/** 视频上传：换预览源，元数据就绪后自动开启实时识别 */
function onVideoRead(item: UploaderFileListItem | UploaderFileListItem[]) {
  const file = pickFile(item)
  if (!file) return
  videoStop()
  videoRatio.value = ''
  if (videoObjectUrl) URL.revokeObjectURL(videoObjectUrl)
  videoObjectUrl = URL.createObjectURL(file)
  videoPreview.value = videoObjectUrl
}

/** 视频元数据就绪：记录宽高比（框叠加对齐）+ 页签激活时开启实时循环 */
function onVideoMeta() {
  const v = videoEl.value
  if (!v || !v.videoWidth || !v.videoHeight) return
  videoRatio.value = `${v.videoWidth} / ${v.videoHeight}`
  if (props.active) videoStart(v)
}
</script>

<template>
  <div class="up">
    <van-notice-bar
      v-if="!supported"
      left-icon="info-o"
      text="当前设备不支持端侧识别（无 WebGPU/WASM），请使用 AR 实时页的拍照云端识别"
    />
    <template v-else>
      <div class="card">
        <div class="card__title">
          <SvgIcon :name="props.mode === 'image' ? 'ai-photo' : 'ai-film'" />
          {{ props.mode === 'image' ? '上传图片识别' : '上传视频识别' }}
        </div>
        <van-uploader
          v-model="files"
          :max-count="1"
          :accept="props.mode === 'image' ? 'image/*' : 'video/*'"
          :after-read="props.mode === 'image' ? onImageRead : onVideoRead"
          reupload
        />
        <p class="up__hint">
          端侧模型本地识别，文件不上传云端
          <template v-if="modelVersion"> · 模型 v{{ modelVersion }}</template>
        </p>
      </div>

      <!-- ============ 图片模式 ============ -->
      <template v-if="props.mode === 'image'">
        <div v-if="imagePreview" class="card">
          <div class="up__stage">
            <div class="up__frame">
              <img :src="imagePreview" class="up__media" />
              <div
                v-for="(d, i) in imageDetections"
                :key="i"
                class="up__box"
                :style="{
                  left: `${d.box[0] * 100}%`,
                  top: `${d.box[1] * 100}%`,
                  width: `${d.box[2] * 100}%`,
                  height: `${d.box[3] * 100}%`,
                  borderColor: styleOf(d.category).color,
                }"
              >
                <span class="up__box-label" :style="{ background: styleOf(d.category).color }">
                  {{ styleOf(d.category).label }} {{ Math.round(d.confidence * 100) }}%
                </span>
              </div>
              <div v-if="imgBusy" class="up__mask"><van-loading size="20px" /> 正在识别…</div>
            </div>
          </div>
        </div>
        <div v-if="imageScanned && !imgBusy" class="card">
          <div class="card__title">识别结果</div>
          <template v-if="imageDetections.length">
            <p v-for="(d, i) in imageDetections" :key="i" class="up__advice">{{ d.advice }}</p>
          </template>
          <p v-else class="up__empty">未识别到可回收物，请将物品靠近、光线充足后重试</p>
        </div>
      </template>

      <!-- ============ 视频模式（播放中逐帧实时识别） ============ -->
      <template v-else>
        <div v-if="videoPreview" class="card">
          <div class="up__stage">
            <div class="up__frame up__frame--video" :style="{ aspectRatio: videoRatio }">
              <video
                ref="videoEl"
                :src="videoPreview"
                class="up__media up__media--video"
                controls
                playsinline
                muted
                autoplay
                @loadedmetadata="onVideoMeta"
              />
              <div
                v-for="d in videoDetections"
                :key="d.trackId"
                class="up__box"
                :style="{
                  left: `${d.box[0] * 100}%`,
                  top: `${d.box[1] * 100}%`,
                  width: `${d.box[2] * 100}%`,
                  height: `${d.box[3] * 100}%`,
                  borderColor: styleOf(d.category).color,
                }"
              >
                <span class="up__box-label" :style="{ background: styleOf(d.category).color }">
                  {{ styleOf(d.category).label }} {{ Math.round(d.confidence * 100) }}%
                </span>
              </div>
              <div v-if="videoRunning" class="up__live">
                <van-loading size="14px" /> 实时识别中
              </div>
            </div>
          </div>
          <p class="up__hint">播放期间识别框逐帧实时更新，暂停时框冻结</p>
        </div>
        <div v-if="videoPreview && !vidError" class="card">
          <div class="card__title">识别结果</div>
          <template v-if="videoDetections.length">
            <p v-for="d in videoDetections" :key="d.trackId" class="up__advice">{{ d.advice }}</p>
          </template>
          <p v-else class="up__empty">未识别到可回收物，请将物品靠近、光线充足后重试</p>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.up {
  &__hint {
    margin: $df-space-sm 0 0;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__stage {
    display: flex;
    justify-content: center;
    background: var(--df-bg-dark);
    border-radius: $df-radius-md;
    overflow: hidden;
  }

  // 媒体包裹层：尺寸贴合媒体，框按百分比叠加才能对齐
  &__frame {
    position: relative;
    display: inline-block;
    max-width: 100%;

    &--video {
      width: auto;
      max-height: 55vh;
    }
  }

  &__media {
    display: block;
    max-width: 100%;
    max-height: 55vh;

    &--video {
      width: 100%;
      height: 100%;
      object-fit: fill; // 容器 aspect-ratio 已与视频同比例，fill 无畸变
    }
  }

  &__box {
    position: absolute;
    border: 2px solid var(--df-color-primary);
    border-radius: $df-radius-sm;
    pointer-events: none;
  }

  &__box-label {
    position: absolute;
    top: -22px;
    left: -2px;
    padding: 1px 6px;
    border-radius: $df-radius-sm;
    background: var(--df-color-primary);
    color: #fff;
    font-size: $df-font-size-xs;
    white-space: nowrap;
  }

  &__mask {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: $df-space-sm;
    color: #fff;
    background: rgba(11, 28, 44, 0.55);
  }

  &__live {
    position: absolute;
    top: $df-space-sm;
    left: $df-space-sm;
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    border-radius: $df-radius-sm;
    background: rgba(11, 28, 44, 0.6);
    color: #fff;
    font-size: $df-font-size-xs;
    pointer-events: none;
  }

  &__advice {
    margin: $df-space-xs 0;
    line-height: 1.7;
    color: var(--df-text-regular);
  }

  &__empty {
    margin: $df-space-xs 0;
    color: var(--df-text-secondary);
  }
}

.van-notice-bar {
  margin-bottom: $df-space-md;
  border-radius: $df-radius-md;
}
</style>
