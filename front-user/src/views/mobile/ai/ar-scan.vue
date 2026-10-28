<script setup lang="ts">
// F6 AR 分类指导：端侧实时识别（零上传）；不支持环境回退云端拍照（F1）
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { useEdgeDetect, type EdgeDetection } from '@/composables/useEdgeDetect'
import { styleOf } from '@/constants/categoryStyle'
import { aiVerifyImage, type ImageVerifyResult } from '@/api/business'
import ClassifyCard from '@/components/ClassifyCard.vue'
import UploadRecognize from '@/components/UploadRecognize.vue'
import { useAutoClassify, MANUAL_RESULT_KEY } from '@/composables/useAutoClassify'
import ThreeViewer from '@/components/ThreeViewer.vue'
import { resolveModelKey } from '@/three/registry'
import type { Build3dMode } from '@/api/business'
import { useBuild3dStore } from '@/store/build3d'
import { saveAiModelCache } from '@/utils/aiModelCache'

const router = useRouter()

// ---- 三档重建等级（窄屏短文案；选中档位后点「开始生成」才提交构建） ----
const rebuildTiers: { mode: Build3dMode; label: string }[] = [
  { mode: 'standard', label: '标准' },
  { mode: 'advanced', label: '高质量' },
  { mode: 'extreme', label: '极致' },
]
const selectedTier = ref<Build3dMode>('standard')

// ---- 3D 构建任务（AI 生成：照片 → 视觉 LLM 重建 spec → three 渲染；失败回退品类预设模型） ----
const build3d = useBuild3dStore()

const { backend, supported, engine, modelVersion, running, loading, detections, renderDetections, videoRef, aspect, start, stop } =
  useEdgeDetect()

// ---- 模式页签（实时/图片/视频/拍照）：图片·视频识别收进 AR 页 ----
const mode = ref<'live' | 'image' | 'video' | 'photo'>('live')
// 切离实时页签 → 释放摄像头（避免后台占用）
watch(mode, (m) => {
  if (m !== 'live') stop()
})
// CAM 精分类（契约 C6-2~C6-5）：稳定目标自动裁剪上传 + 缓存/队列/降级；挂载点 C6-8 接入
const { results, queueing, forceClassify } = useAutoClassify(detections, videoRef)

// ---- 端侧标记交互（cam02：持久标记 + 手动拖框/自绘框兜底） ----
// fitEl = aspect-fit 容器（与摄像头画面同宽高比，框以源坐标系按百分比精确叠加）
const fitEl = ref<HTMLDivElement | null>(null)
/** 锁定目标 trackId（再点解除；锁定的是"目标"，位置随跟踪持续更新） */
const selectedId = ref<number | null>(null)
/** 手动/自绘框（归一化 [x, y, w, h]，虚线渲染，用于精分类兜底） */
const manualBox = ref<[number, number, number, number] | null>(null)
const drawing = ref(false)
let drawStart: [number, number] | null = null
let movingId: number | null = null
let moveOffset: [number, number] | null = null
let suppressClick = false // 拖拽后抑制 click，避免误切换锁定
let downClient: [number, number] | null = null

function onBoxClick(trackId: number) {
  if (suppressClick) return
  suppressClick = false
  selectedId.value = selectedId.value === trackId ? null : trackId // 再点解除
}

/** 目标消失（track 被清理）时自动解除锁定 */
watch(renderDetections, (list) => {
  if (selectedId.value !== null && !list.some((d) => d.trackId === selectedId.value)) {
    selectedId.value = null
  }
})

function normalizedPoint(e: PointerEvent): [number, number] {
  const rect = fitEl.value?.getBoundingClientRect()
  if (!rect) return [0, 0]
  return [(e.clientX - rect.left) / rect.width, (e.clientY - rect.top) / rect.height]
}

/** 命中检测：点在某个识别框内 */
function hitBox(x: number, y: number): EdgeDetection | undefined {
  return renderDetections.value.find((d) => {
    const [bx, by, bw, bh] = d.box
    return x >= bx && x <= bx + bw && y >= by && y <= by + bh
  })
}

function onStagePointerDown(e: PointerEvent) {
  const [x, y] = normalizedPoint(e)
  downClient = [e.clientX, e.clientY]
  suppressClick = false
  const hit = hitBox(x, y)
  if (hit) {
    // 点在已有框内 → 拖框调整模式（简化版：整框平移，起点复制识别框）
    movingId = hit.trackId
    moveOffset = [x - hit.box[0], y - hit.box[1]]
    manualBox.value = [...hit.box] as [number, number, number, number]
    return
  }
  // 空白区 → 开始自绘
  drawing.value = true
  drawStart = [x, y]
  manualBox.value = null
}

function onStagePointerMove(e: PointerEvent) {
  if (!drawing.value && movingId === null) return
  // 位移超过阈值视为拖拽，抑制本次 click 的锁定切换
  if (downClient && Math.abs(e.clientX - downClient[0]) + Math.abs(e.clientY - downClient[1]) > 8) {
    suppressClick = true
  }
  const [x, y] = normalizedPoint(e)
  if (drawing.value && drawStart) {
    const [x0, y0] = drawStart
    const nx = Math.min(Math.max(Math.min(x0, x), 0), 1)
    const ny = Math.min(Math.max(Math.min(y0, y), 0), 1)
    manualBox.value = [nx, ny, Math.abs(x - x0), Math.abs(y - y0)]
  } else if (movingId !== null && manualBox.value && moveOffset) {
    // 整框平移（夹紧画面内）
    const mb = manualBox.value
    manualBox.value = [
      Math.min(Math.max(x - moveOffset[0], 0), 1 - mb[2]),
      Math.min(Math.max(y - moveOffset[1], 0), 1 - mb[3]),
      mb[2],
      mb[3],
    ]
  }
}

function onStagePointerUp(e?: PointerEvent) {
  const wasDrawing = drawing.value
  drawing.value = false
  // 框内起点但几乎无位移 → 视为轻点（锁定切换由 click 处理），撤销手动框拷贝
  let dragged = false
  if (downClient && e) {
    dragged = Math.abs(e.clientX - downClient[0]) + Math.abs(e.clientY - downClient[1]) > 8
  }
  if (movingId !== null && !dragged) {
    manualBox.value = null
  }
  movingId = null
  drawStart = null
  moveOffset = null
  if (manualBox.value && (manualBox.value[2] < 0.05 || manualBox.value[3] < 0.05)) {
    manualBox.value = null // 过小视为误触，丢弃
  }
  if (manualBox.value && (wasDrawing || dragged)) {
    onClassifyRequest(null, manualBox.value) // 自绘/拖框调整完成 → 精分类请求（占位）
  }
}

// ===== 精分类挂载点（契约 C6-8）：cam03 useAutoClassify 一行接入 =====
/** 锁定目标的结果（缓存读取，C6-4） */
const selectedResult = computed(() =>
  selectedId.value !== null ? (results.value.get(selectedId.value) ?? null) : null,
)
/** 手动框结果（未锁定且手动框存在时展示，键 MANUAL_RESULT_KEY） */
const manualResult = computed(() => results.value.get(MANUAL_RESULT_KEY) ?? null)

function onClassifyRequest(trackId: number | null, box?: [number, number, number, number]) {
  forceClassify(trackId, box)
}

/** 卡片重试：锁定目标 → 强制重识别该目标；手动框 → 用手动框区域重识别 */
function onRetryClassify() {
  if (selectedId.value !== null) forceClassify(selectedId.value)
  else forceClassify(null, manualBox.value ?? undefined)
}

// ---- 拍照上传（简单多张照片；声明置于 watch 之前，避免注册时 TDZ 访问） ----
const aiPhotos = ref<UploaderFileListItem[]>([])

// ---- 云端 AI 识别（F1：上传照片后自动识别品类，作为 3D 构建失败回退预设模型的依据） ----
const verifying = ref(false)
const cloudResult = ref<ImageVerifyResult | null>(null)
const cloudModelKey = computed(() => resolveModelKey(cloudResult.value?.category))

async function onCloudVerify() {
  const photo = aiPhotos.value[0]
  if (!photo?.content) return
  verifying.value = true
  try {
    cloudResult.value = await aiVerifyImage(String(photo.content), 'recycle')
    // 识别结果落本地缓存：预约页重进可恢复展示（图片 + 品类 + spec）
    saveAiModelCache({
      image: String(photo.content),
      result: cloudResult.value,
      category: cloudResult.value?.category ?? '',
    })
  } catch {
    // 自动识别失败静默：3D 构建主流程不受影响，回退默认预设模型
  } finally {
    verifying.value = false
  }
}

// ---- 3D 构建（点「开始生成」按所选档位提交；完成/失败后可点「重新渲染」换档位重试） ----
function onBuild3d() {
  const photo = aiPhotos.value[0]
  if (!photo?.content) {
    showToast('请先拍照或上传照片')
    return
  }
  build3d.start(String(photo.content), selectedTier.value)
}

/** 选择重建档位：仅记录选择，点「开始生成」时按所选档位构建 */
function selectTier(mode: Build3dMode) {
  selectedTier.value = mode
}

// AI 构建的 spec 到达后补写缓存（预约页恢复时直接展示 AI 模型）
watch(() => build3d.spec, (s) => {
  if (s) saveAiModelCache({ spec: s })
})

// 上传/换照片（第一张变化）→ 仅自动 AI 识别品类；3D 构建由用户点「开始生成」按所选档位触发
watch(() => aiPhotos.value[0]?.content, (content) => {
  build3d.stop()
  if (!content) return
  onCloudVerify()
})
</script>

<template>
  <div class="ar">
    <van-nav-bar title="AR 分类指导" left-arrow @click-left="stop(), router.back()" />

    <!-- 模式页签：实时识别 / 图片识别 / 视频识别 / 3D 模型（上传后点「开始生成」构建） -->
    <div class="ar__tabs">
      <button class="ar__tab" :class="{ 'is-active': mode === 'live' }" @click="mode = 'live'">实时识别</button>
      <button class="ar__tab" :class="{ 'is-active': mode === 'image' }" @click="mode = 'image'">图片识别</button>
      <button class="ar__tab" :class="{ 'is-active': mode === 'video' }" @click="mode = 'video'">视频识别</button>
      <button class="ar__tab" :class="{ 'is-active': mode === 'photo' }" @click="mode = 'photo'">3D 模型</button>
    </div>

    <!-- 实时模式 -->
    <template v-if="mode === 'live' && supported">
      <div class="ar__stage">
        <!-- aspect-fit 容器：与摄像头画面同宽高比，源坐标系框按百分比精确叠加（cover 裁切会压扁框形） -->
        <div
          ref="fitEl"
          class="ar__fit"
          :style="aspect ? { aspectRatio: aspect } : undefined"
          @pointerdown="onStagePointerDown"
          @pointermove="onStagePointerMove"
          @pointerup="onStagePointerUp($event)"
          @pointercancel="onStagePointerUp()"
        >
          <video ref="videoRef" class="ar__video" playsinline muted />
        <!-- 识别框选（归一化坐标 → 百分比定位；框色/标签色取自品类色标单一来源 C6-7） -->
        <div
          v-for="d in renderDetections"
          :key="d.trackId"
          class="ar__box"
          :class="{ 'ar__box--locked': selectedId === d.trackId }"
          :style="{
            left: `${d.box[0] * 100}%`,
            top: `${d.box[1] * 100}%`,
            width: `${d.box[2] * 100}%`,
            height: `${d.box[3] * 100}%`,
            borderColor: styleOf(d.category).color,
          }"
          @click.stop="onBoxClick(d.trackId)"
        >
          <span class="ar__box-label" :style="{ background: styleOf(d.category).color }">
            {{ results.get(d.trackId)?.itemName || styleOf(d.category).label }}
            {{ Math.round((results.get(d.trackId)?.confidence ?? d.confidence) * 100) }}%
            <span v-if="selectedId === d.trackId" class="ar__box-locked">已锁定</span>
          </span>
        </div>
        <!-- 手动/自绘框（虚线，归一化坐标；拖框调整/自绘框兜底） -->
        <div
          v-if="manualBox"
          class="ar__box ar__box--manual"
          :style="{
            left: `${manualBox[0] * 100}%`,
            top: `${manualBox[1] * 100}%`,
            width: `${manualBox[2] * 100}%`,
            height: `${manualBox[3] * 100}%`,
          }"
        ></div>
        </div>
        <!-- 锁定操作条（cam03 接入精分类触发、cam04 接入业务按钮） -->
        <div v-if="selectedId !== null" class="ar__action-bar" @pointerdown.stop>
          <span class="ar__action-hint">已锁定目标，可发起精分类</span>
          <van-button
            size="small"
            round
            type="primary"
            :loading="queueing"
            @click="onClassifyRequest(selectedId)"
          >
            精分类
          </van-button>
        </div>
        <!-- CAM 精分类结果卡片（契约 C6-8）：锁定目标优先，未锁定且有手动框结果时展示手动框卡片 -->
        <div v-if="selectedResult || manualResult || (queueing && (selectedId !== null || manualBox))" class="ar__classify" @pointerdown.stop>
          <ClassifyCard
            :result="selectedId !== null ? selectedResult : manualResult"
            :loading="queueing"
            @retry="onRetryClassify"
          />
        </div>
        <div v-if="!running" class="ar__mask">
          <p><SvgIcon name="ai-camera" /> 对准废弃物，本地实时识别</p>
          <p class="ar__mask-sub">推理后端：{{ backend === 'webgpu' ? 'WebGPU 加速' : 'WASM' }} · 画面不上传云端</p>
          <p v-if="engine === 'onnx'" class="ar__mask-sub">端侧模型 v{{ modelVersion }}</p>
          <van-button round type="primary" :loading="loading" @click="start">开启摄像头</van-button>
        </div>
      </div>

      <!-- 识别建议 -->
      <div class="page">
        <div v-if="running && detections.length" class="card">
          <div class="card__title">投放建议</div>
          <p v-for="(d, i) in detections" :key="i" class="ar__advice">{{ d.advice }}</p>
        </div>
        <div v-else-if="running" class="card ar__scanning">正在扫描，请将废弃物置于画面中央…</div>
        <!-- cam03 挂载点：精分类结果卡片已渲染在扫描舞台底部（见 .ar__classify） -->
        <van-button v-if="running" round block plain type="danger" @click="stop">关闭摄像头</van-button>
      </div>
    </template>

    <!-- 图片识别模式（端侧单图推理，零上传） -->
    <template v-else-if="mode === 'image'">
      <div class="page">
        <UploadRecognize mode="image" />
      </div>
    </template>

    <!-- 视频识别模式（播放中逐帧实时识别，零上传） -->
    <template v-else-if="mode === 'video'">
      <div class="page">
        <UploadRecognize mode="video" />
      </div>
    </template>

    <!-- 3D 模型模式（上传后点「开始生成」构建：照片 → 视觉 LLM 重建 spec → three 渲染；失败回退品类预设模型） -->
    <template v-else>
      <div class="page">
        <van-notice-bar v-if="!supported" left-icon="info-o" text="当前设备不支持端侧识别，已切换 3D 模型模式" />
            
        <!-- 照片上传（多张照片，选择档位后点「开始生成」构建） -->
        <div class="card">
          <div class="card__title"><SvgIcon name="ai-camera" /> 上传照片生成 3D 模型</div>
          <van-uploader
            v-model="aiPhotos"
            :max-count="9"
            multiple
            reupload
            :preview-full="true"
            upload-text="拍照 / 上传"
            class="ar__uploader"
          >
            <div class="uploader-trigger">
              <div class="uploader-trigger__icon">
                <SvgIcon name="ai-camera" :size="28" />
              </div>
              <div class="uploader-trigger__body">
                <div class="uploader-trigger__title">点击拍照或上传图片</div>
                <div class="uploader-trigger__sub">上传后选择档位，点「开始生成」构建 3D 模型 · 最多 9 张</div>
              </div>
              <div class="uploader-trigger__action">选择</div>
            </div>
          </van-uploader>
          <p class="ar__advice">上传后自动识别品类，选择档位后点「开始生成」构建 3D 模型，照片同时供工作人员审核</p>
        </div>

        <!-- AI 识别（F1 云端视觉：上传后自动执行，展示品类 + 置信度 + 投放建议；供 3D 失败回退品类） -->
        <div v-if="aiPhotos.length" class="card">
          <div class="card__title"><SvgIcon name="ai-camera" /> AI 识别</div>
          <p v-if="verifying" class="ar__advice">识别中…</p>
          <div v-else-if="cloudResult" class="ar__cloud-result">
            <p class="ar__cloud-cat">
              {{ cloudResult.category }}
              <span class="ar__cloud-conf">置信 {{ Math.round(cloudResult.confidence * 100) }}%</span>
            </p>
            <p class="ar__advice">{{ cloudResult.advice }}</p>
          </div>
          <p v-if="aiPhotos.length > 1" class="ar__advice ar__cloud-hint">将识别第 1 张照片，如需处理其他照片请调整顺序</p>
        </div>

        <!-- 3D 模型（上传后点「开始生成」构建：照片 → 视觉 LLM 重建 spec → three 渲染；失败回退品类预设模型） -->
        <div v-if="aiPhotos.length" class="card">
          <div class="card__title"><SvgIcon name="common-cube" /> 3D 模型</div>
          <div class="build3d__tiers">
            <button
              v-for="t in rebuildTiers"
              :key="t.mode"
              class="build3d__tier"
              :class="{ 'is-active': selectedTier === t.mode }"
              @click="selectTier(t.mode)"
            >
              {{ t.label }}
            </button>
          </div>
          <van-button round block type="primary" :loading="build3d.running" @click="onBuild3d">
            {{ build3d.running ? build3d.stageText : build3d.status === 'DONE' || build3d.status === 'FAILED' ? '重新渲染 3D 模型' : '开始生成' }}
          </van-button>
          <p v-if="aiPhotos.length" class="ar__advice ar__cloud-hint">选择档位后点「开始生成」构建 3D 模型，完成后可换档位重新渲染</p>
          <!-- 构建进度条（轮询平滑推进，上限 88%） -->
          <div v-if="build3d.running" class="build3d__progress">
            <div class="build3d__bar"><i :style="{ width: build3d.progress + '%' }" /></div>
            <span class="build3d__pct">{{ build3d.progress }}%</span>
          </div>
          <!-- 3D 预览：开始生成后展示（AI spec 优先渲染，无效/失败回退品类预设模型） -->
          <div v-if="build3d.status !== 'IDLE'" class="build3d__viewer">
            <ThreeViewer :model="cloudModelKey" :spec="build3d.spec" />
          </div>
          <p v-if="build3d.status === 'FAILED'" class="ar__advice ar__cloud-hint">
            3D 构建失败（{{ build3d.error }}），已展示品类预设模型
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

/* 物品照片上传按钮自定义样式 */
.ar__uploader {
  :deep(.van-uploader__wrapper) {
    display: block;
  }

  :deep(.van-uploader__input-wrapper) {
    width: 100%;
  }

  :deep(.van-uploader__preview) {
    margin: 0 $df-space-sm $df-space-sm 0;
  }
}

/* 大尺寸上传触发按钮 */
.uploader-trigger {
  display: flex;
  align-items: center;
  gap: $df-space-md;
  width: 100%;
  // 全局无 box-sizing reset：必须 border-box，否则 width 100% + padding + border 向右溢出屏幕
  box-sizing: border-box;
  padding: $df-space-md;
  background: color-mix(in srgb, var(--df-color-primary) 8%, var(--df-bg-card));
  border: 1.5px dashed var(--df-color-primary);
  border-radius: $df-radius-lg;
  cursor: pointer;
  transition: all 0.2s;

  &:active {
    transform: scale(0.99);
    background: color-mix(in srgb, var(--df-color-primary) 14%, var(--df-bg-card));
  }

  &__icon {
    flex-shrink: 0;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    background: var(--df-color-primary);
    color: var(--df-on-accent, #fff);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px color-mix(in srgb, var(--df-color-primary) 40%, transparent);
  }

  &__body {
    flex: 1;
    min-width: 0;
    text-align: left;
  }

  &__title {
    font-size: $df-font-size-md;
    font-weight: 600;
    color: var(--df-text-primary);
    margin-bottom: 2px;
  }

  &__sub {
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }

  &__action {
    flex-shrink: 0;
    padding: 6px 14px;
    background: var(--df-color-primary);
    color: var(--df-on-accent, #fff);
    border-radius: $df-radius-sm;
    font-size: $df-font-size-sm;
    font-weight: 600;
  }
}

/* 3D 预览卡片：进度条覆盖层定位基准 */
.ar {
  &__tabs {
    display: flex;
    gap: $df-space-sm;
    padding: $df-space-sm $df-space-md;
    overflow-x: auto;
  }

  &__tab {
    flex: none;
    padding: 6px 16px;
    font-size: $df-font-size-sm;
    font-weight: 600;
    color: var(--df-text-regular);
    background: var(--df-bg-card);
    border: 1.5px solid var(--df-border-color);
    border-radius: $df-radius-lg;
    cursor: pointer;

    &.is-active {
      color: var(--df-color-primary);
      border-color: var(--df-color-primary);
      background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
    }
  }

  &__stage {
    position: relative;
    height: 55vh;
    background: var(--df-bg-dark);
    overflow: hidden;
  }

  // aspect-fit 容器：宽高比由摄像头画面决定（aspect 内联样式），框/手势均以其为坐标系
  &__fit {
    position: relative;
    height: 100%;
    max-width: 100%;
    margin: 0 auto;
    touch-action: none; // 自绘/拖框手势交给 pointer 事件，避免浏览器滚动干扰
  }

  &__video {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: fill; // 容器已与画面同宽高比，fill 无畸变
  }

  &__box {
    position: absolute;
    border: 2px solid var(--df-color-primary);
    border-radius: $df-radius-sm;
    box-shadow: 0 0 0 2000px rgba(0, 0, 0, 0.05);
  }

  // 锁定态：框加粗 + 四角点角标（锁定的是目标，位置随跟踪持续更新）
  &__box--locked {
    border-width: 3px;

    &::after {
      content: '';
      position: absolute;
      inset: -5px;
      pointer-events: none;
      background:
        linear-gradient(#fff, #fff) top left / 16px 3px,
        linear-gradient(#fff, #fff) top left / 3px 16px,
        linear-gradient(#fff, #fff) top right / 16px 3px,
        linear-gradient(#fff, #fff) top right / 3px 16px,
        linear-gradient(#fff, #fff) bottom left / 16px 3px,
        linear-gradient(#fff, #fff) bottom left / 3px 16px,
        linear-gradient(#fff, #fff) bottom right / 16px 3px,
        linear-gradient(#fff, #fff) bottom right / 3px 16px;
      background-repeat: no-repeat;
    }
  }

  // 手动/自绘框：虚线 + 半透明填充
  &__box--manual {
    border: 2px dashed #fff;
    background: rgba(255, 255, 255, 0.12);
    box-shadow: none;
  }

  // 已锁定徽标
  &__box-locked {
    margin-left: 4px;
    padding: 0 3px;
    border-radius: 2px;
    background: rgba(255, 255, 255, 0.92);
    color: #333;
    font-size: 10px;
  }

  // 锁定操作条（底部浮层，占位：cam03 精分类 / cam04 业务按钮）
  &__action-bar {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $df-space-md;
    padding: $df-space-sm $df-space-md;
    background: rgba(11, 28, 44, 0.72);
    backdrop-filter: blur(2px);
    z-index: 2;
  }

  &__action-hint {
    color: #fff;
    font-size: $df-font-size-xs;
  }

  // CAM 精分类结果卡片浮层（操作条上方；点击不冒泡到画布手势）
  &__classify {
    position: absolute;
    left: $df-space-md;
    right: $df-space-md;
    bottom: 52px;
    z-index: 2;
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
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: $df-space-md;
    color: #fff;
    background: rgba(11, 28, 44, 0.75);
    text-align: center;

    p {
      margin: 0;
    }
  }

  &__mask-sub {
    font-size: $df-font-size-xs;
    opacity: 0.7;
  }

  &__advice {
    margin: $df-space-xs 0;
    line-height: 1.7;
    color: var(--df-text-regular);
  }

  &__scanning {
    color: var(--df-text-secondary);
    text-align: center;
  }

  &__cloud-cat {
    margin: 0 0 $df-space-sm;
    font-size: $df-font-size-md;
    font-weight: 600;
  }

  // AI 识别结果卡（照片模式：F1 云端视觉）
  &__cloud-result {
    margin-top: $df-space-md;
    padding: $df-space-md;
    border-radius: $df-radius-lg;
    background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
  }

  &__cloud-conf {
    font-size: $df-font-size-xs;
    color: var(--df-color-primary);
    margin-left: $df-space-xs;
  }

  &__cloud-hint {
    margin-top: $df-space-sm;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
  }
}

/* 3D 构建卡片：档位选择 + 进度条 + 预览 */
.build3d {
  &__tiers {
    display: flex;
    gap: $df-space-sm;
    margin-bottom: $df-space-md;
  }

  &__tier {
    flex: 1;
    padding: 6px 0;
    font-size: $df-font-size-xs;
    font-weight: 600;
    color: var(--df-text-regular);
    background: var(--df-bg-card);
    border: 1.5px solid var(--df-border-color);
    border-radius: $df-radius-sm;
    cursor: pointer;

    &.is-active {
      color: var(--df-color-primary);
      border-color: var(--df-color-primary);
      background: color-mix(in srgb, var(--df-color-secondary) 8%, transparent);
    }
  }

  &__progress {
    display: flex;
    align-items: center;
    gap: $df-space-sm;
    margin-top: $df-space-md;
  }

  &__bar {
    flex: 1;
    height: 6px;
    border-radius: 3px;
    background: var(--df-border-color);
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      background: var(--df-color-primary);
      transition: width 0.4s ease;
    }
  }

  &__pct {
    min-width: 34px;
    font-size: $df-font-size-xs;
    color: var(--df-text-secondary);
    text-align: right;
  }

  &__viewer {
    height: 320px;
    margin-top: $df-space-md;
  }
}

.van-notice-bar {
  margin-bottom: $df-space-md;
  border-radius: $df-radius-lg;
}
</style>
