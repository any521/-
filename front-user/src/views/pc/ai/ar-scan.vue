<script setup lang="ts">
// PC AR 分类指导（cam05）：四模式 = 实时识别（端侧 useEdgeDetect + 色标 + 锁定/自绘框）/
// 图片识别·视频识别（端侧上传零上传，UploadRecognize）/ 拍照识别（云端降级 F1：aiVerifyImage → 3D 预览）。
// 精分类挂载点（契约 C6-8）当前为占位：cam03 交付 ClassifyCard/useAutoClassify 后一行接入。
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useEdgeDetect, type EdgeDetection } from '@/composables/useEdgeDetect'
import { styleOf } from '@/constants/categoryStyle'
import { aiVerifyImage, type ImageVerifyResult, type Build3dMode } from '@/api/business'
import ThreeViewer from '@/components/ThreeViewer.vue'
import UploadRecognize from '@/components/UploadRecognize.vue'
import { resolveModelKey } from '@/three/registry'
import { useBuild3dStore } from '@/store/build3d'
import { saveAiModelCache } from '@/utils/aiModelCache'

defineOptions({ name: 'pc-ar-scan' })

type PcScanMode = 'live' | 'image' | 'video' | 'photo'
// 契约 C6-1 扩展字段（cam02 交付后为必填）：先行联调期按可选读取，trackId 缺失时以 index 兜底
type PcDetection = EdgeDetection & { trackId?: number; stable?: boolean }

const router = useRouter()
const mode = ref<PcScanMode>('live')
const { backend, supported, engine, modelVersion, running, loading, detections, renderDetections, videoRef, aspect, start, stop } =
  useEdgeDetect()

// 不支持端侧环境 → 自动切拍照 tab（对齐移动端降级语义）
const notSupported = computed(() => !supported.value)
watch(notSupported, (v) => { if (v) mode.value = 'photo' }, { immediate: true })
// 切离实时 tab → 释放摄像头（避免后台占用）
watch(mode, (m) => { if (m !== 'live') stop() })

// ---- 实时模式：点按锁定（PC 鼠标语义，契约 C6-1）----
const selectedId = ref<number | null>(null)

/** 目标 id：cam02 交付 trackId 后为稳定跨帧 id；当前以 index 兜底 */
function idOf(d: EdgeDetection, i: number): number {
  return (d as PcDetection).trackId ?? i + 1
}

function onBoxClick(d: EdgeDetection, i: number) {
  const id = idOf(d, i)
  selectedId.value = selectedId.value === id ? null : id // 再点解除
}

// 目标离开画面 → 解除锁定（避免 stale id 错配新目标）
watch(renderDetections, (list) => {
  if (selectedId.value === null) return
  if (!list.some((d, i) => idOf(d, i) === selectedId.value)) selectedId.value = null
})

// ---- 实时模式：手动框（空白自绘 + 整框平移，契约 C6-8 手动兜底）----
const manualBox = ref<[number, number, number, number] | null>(null)
const drawing = ref(false)
let dragStart: [number, number] | null = null
let movingManual = false
let moveStart: { box: [number, number, number, number]; x: number; y: number } | null = null

function clamp01(v: number, span: number): number {
  return Math.max(0, Math.min(1 - span, v))
}

function stagePoint(e: PointerEvent): [number, number] {
  const el = e.currentTarget as HTMLElement
  const r = el.getBoundingClientRect()
  return [(e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height]
}

function onStagePointerDown(e: PointerEvent) {
  if (!running.value) return
  const [px, py] = stagePoint(e)
  // 命中手动框 → 整框平移（简化版，与 cam02 约定一致）
  const mb = manualBox.value
  if (mb && px >= mb[0] && px <= mb[0] + mb[2] && py >= mb[1] && py <= mb[1] + mb[3]) {
    movingManual = true
    moveStart = { box: [...mb], x: px, y: py }
    return
  }
  // 命中自动检测框 → 不开始自绘（点按锁定由 click 处理）
  const hitAuto = renderDetections.value.some((d) => {
    const [bx, by, bw, bh] = d.box
    return px >= bx && px <= bx + bw && py >= by && py <= by + bh
  })
  if (hitAuto) return
  // 空白 → 自绘虚线框
  drawing.value = true
  dragStart = [px, py]
  manualBox.value = [px, py, 0, 0]
}

function onStagePointerMove(e: PointerEvent) {
  if (!running.value) return
  const [px, py] = stagePoint(e)
  if (drawing.value && dragStart) {
    const [sx, sy] = dragStart
    manualBox.value = [Math.min(sx, px), Math.min(sy, py), Math.abs(px - sx), Math.abs(py - sy)]
  } else if (movingManual && moveStart) {
    const dx = px - moveStart.x
    const dy = py - moveStart.y
    const [bx, by, bw, bh] = moveStart.box
    manualBox.value = [clamp01(bx + dx, bw), clamp01(by + dy, bh), bw, bh]
  }
}

function onStagePointerUp() {
  if (!running.value) return
  if (drawing.value) {
    const mb = manualBox.value
    // 过小视为误触清除；有效框触发精分类挂载点（cam03 接入后为 forceClassify(null, manualBox)）
    if (!mb || mb[2] < 0.02 || mb[3] < 0.02) manualBox.value = null
    else onClassifyRequest(null, mb)
  }
  drawing.value = false
  movingManual = false
  dragStart = null
  moveStart = null
}

// ---- 展示辅助 ----
function boxStyleOf(d: EdgeDetection) {
  const [x, y, w, h] = d.box
  return {
    left: `${x * 100}%`,
    top: `${y * 100}%`,
    width: `${w * 100}%`,
    height: `${h * 100}%`,
    borderColor: styleOf(d.category).color,
  }
}

const manualBoxStyle = computed(() => {
  const mb = manualBox.value
  if (!mb) return null
  const [x, y, w, h] = mb
  return { left: `${x * 100}%`, top: `${y * 100}%`, width: `${w * 100}%`, height: `${h * 100}%` }
})

/** 框标签：精分类结果存在时「物品名+置信」，否则「短名+置信」（契约 C6-1/C6-7） */
function labelOf(d: EdgeDetection, i: number): string {
  const conf = `${Math.round(d.confidence * 100)}%`
  const detail = classifyResults.value.get(idOf(d, i)) as { itemName?: string } | undefined
  return detail?.itemName ? `${detail.itemName} ${conf}` : `${styleOf(d.category).label} ${conf}`
}

// ===== 精分类挂载点（契约 C6-8）：cam03 交付 useAutoClassify/ClassifyCard 后替换 =====
// cam03: const { results, queueing, forceClassify } = useAutoClassify(detections, videoRef)
//        classifyResults → results（Map<trackId, ClassifyDetailResult>），labelOf 自动升级「物品名+置信」
//        onClassifyRequest → 转发 forceClassify(trackId, box)
const classifyResults = ref<Map<number, unknown>>(new Map())
async function onClassifyRequest(trackId: number | null, box?: [number, number, number, number]) {
  // cam03: 替换为 forceClassify(trackId, box)；当前占位仅 DEV 日志，不发起任何请求（契约 C6-3）
  if (import.meta.env.DEV) console.log('[cam03 挂载点] classify request', trackId, box)
}

// ---- 拍照模式（云端降级 F1：上传照片即自动生成 3D 模型）----
const photo = ref<string>('')
const verifying = ref(false)
const result = ref<ImageVerifyResult | null>(null)
const modelKey = computed(() => resolveModelKey(result.value?.category))

// ---- 三档重建等级（选中档位后点「重新渲染」提交构建）----
const rebuildTiers: { mode: Build3dMode; label: string }[] = [
  { mode: 'standard', label: '标准' },
  { mode: 'advanced', label: '高质量' },
  { mode: 'extreme', label: '极致' },
]
const selectedTier = ref<Build3dMode>('standard')

// ---- 3D 构建任务（AI 生成：照片 → 视觉 LLM 重建 spec → three 渲染；失败回退品类预设模型）----
const build3d = useBuild3dStore()

function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    photo.value = reader.result as string
    // 上传照片即自动构建 3D 模型 + 自动 AI 识别（识别结果作为构建失败回退预设模型的依据）
    build3d.start(photo.value, selectedTier.value)
    verify()
  }
  reader.readAsDataURL(file)
}

async function verify() {
  if (!photo.value) return
  verifying.value = true
  try {
    result.value = await aiVerifyImage(photo.value, 'recycle')
    // 识别结果落本地缓存：预约页重进可恢复展示（图片 + 品类 + spec）
    saveAiModelCache({ image: photo.value, result: result.value, category: result.value?.category ?? '' })
  } catch {
    // 自动识别失败静默：3D 构建主流程不受影响，回退默认预设模型
  } finally {
    verifying.value = false
  }
}

/** 选择重建档位：已有照片时立即用新档位重新构建 */
function selectTier(mode: Build3dMode) {
  selectedTier.value = mode
  if (photo.value) build3d.start(photo.value, mode)
}

/** 重新渲染 3D 模型（失败/换档位后手动重试） */
function onBuild3d() {
  if (!photo.value) {
    showToast('请先上传物品照片')
    return
  }
  build3d.start(photo.value, selectedTier.value)
}

// AI 构建的 spec 到达后补写缓存（预约页恢复时直接展示 AI 模型）
watch(() => build3d.spec, (s) => {
  if (s) saveAiModelCache({ spec: s })
})
</script>

<template>
  <div class="pc-ar">
    <div class="container">
      <div class="header">
        <button class="back-btn" @click="router.back()">
          <SvgIcon name="nav-arrow-left" :size="20" /> 返回
        </button>
        <h1 class="page-title">AR 分类指导</h1>
      </div>

      <p class="page-desc">摄像头实时识别、上传图片/视频识别或上传照片生成 3D 模型，AI 智能分类指导</p>

      <!-- 模式切换：实时识别 / 图片识别 / 视频识别 / 拍照识别 -->
      <div class="pc-ar__tabs">
        <button class="pc-ar__tab" :class="{ 'is-active': mode === 'live' }" @click="mode = 'live'">实时识别</button>
        <button class="pc-ar__tab" :class="{ 'is-active': mode === 'image' }" @click="mode = 'image'">图片识别</button>
        <button class="pc-ar__tab" :class="{ 'is-active': mode === 'video' }" @click="mode = 'video'">视频识别</button>
        <button class="pc-ar__tab" :class="{ 'is-active': mode === 'photo' }" @click="mode = 'photo'">3D 模型</button>
      </div>

      <!-- ===== 实时模式 ===== -->
      <template v-if="mode === 'live'">
        <!-- 不支持端侧环境（supported=false 时 watch 已自动切拍照 tab，此为手动切回时的引导） -->
        <div v-if="notSupported" class="pc-ar__unsupported">
          <p>当前设备不支持摄像头实时识别</p>
          <button class="pc-ar__primary-btn" @click="mode = 'photo'">前往拍照识别</button>
        </div>

        <template v-else>
          <div class="pc-ar__stage" :class="{ 'is-drawing': drawing }">
            <!-- aspect-fit 容器：与摄像头画面同宽高比，源坐标系框按百分比精确叠加 -->
            <div
              class="pc-ar__fit"
              :style="aspect ? { aspectRatio: aspect } : undefined"
              @pointerdown="onStagePointerDown"
              @pointermove="onStagePointerMove"
              @pointerup="onStagePointerUp"
              @pointercancel="onStagePointerUp"
            >
              <video ref="videoRef" class="pc-ar__video" playsinline muted />

            <!-- 自动检测框（色标单一来源 categoryStyle，契约 C6-7） -->
            <div
              v-for="(d, i) in renderDetections"
              :key="idOf(d, i)"
              class="pc-ar__box"
              :class="{ 'pc-ar__box--selected': idOf(d, i) === selectedId }"
              :style="boxStyleOf(d)"
              @click.stop="onBoxClick(d, i)"
            >
              <span class="pc-ar__box-label" :style="{ background: styleOf(d.category).color }">{{ labelOf(d, i) }}</span>
              <span v-if="idOf(d, i) === selectedId" class="pc-ar__box-badge">已锁定</span>
            </div>

            <!-- 手动框（空白自绘 / 整框平移，虚线区分） -->
            <div v-if="manualBoxStyle" class="pc-ar__box pc-ar__box--manual" :style="manualBoxStyle">
              <span class="pc-ar__box-label pc-ar__box-label--manual" @click.stop="manualBox = null">手动选区 ×</span>
            </div>
            </div>

            <!-- 未开启遮罩 -->
            <div v-if="!running" class="pc-ar__mask">
              <p><SvgIcon name="ai-camera" :size="32" /> 对准废弃物，本地实时识别</p>
              <p class="pc-ar__mask-sub">推理后端：{{ backend === 'webgpu' ? 'WebGPU 加速' : 'WASM' }} · 画面不上传云端</p>
              <p v-if="engine === 'onnx'" class="pc-ar__mask-sub">端侧模型 v{{ modelVersion }}</p>
              <button class="pc-ar__primary-btn" :disabled="loading" @click="start">
                {{ loading ? '启动中…' : '开启摄像头' }}
              </button>
            </div>
          </div>

          <!-- 识别建议 -->
          <div v-if="running && detections.length" class="card pc-ar__advice-card">
            <h3 class="card-title">识别建议</h3>
            <p v-for="(d, i) in detections" :key="idOf(d, i)" class="pc-ar__advice">
              <span class="pc-ar__advice-dot" :style="{ background: styleOf(d.category).color }" />
              {{ d.advice }}
            </p>
          </div>
          <div v-else-if="running" class="card pc-ar__scanning">正在扫描，请将废弃物置于画面中央…</div>

          <!-- 精分类挂载点（契约 C6-8）：cam03 交付 <ClassifyCard :result="..." :loading="..." @retry="..." /> 后替换 -->

          <button v-if="running" class="pc-ar__stop-btn" @click="stop">关闭摄像头</button>
        </template>
      </template>

      <!-- ===== 图片识别模式（端侧单图推理，零上传） ===== -->
      <template v-else-if="mode === 'image'">
        <UploadRecognize mode="image" />
      </template>

      <!-- ===== 视频识别模式（播放中逐帧实时识别，零上传） ===== -->
      <template v-else-if="mode === 'video'">
        <UploadRecognize mode="video" />
      </template>

      <!-- ===== 拍照模式（上传照片即自动生成 3D 模型；失败回退品类预设模型） ===== -->
      <template v-else>
        <van-notice-bar
          v-if="notSupported"
          class="pc-ar__notice"
          left-icon="info-o"
          text="当前设备不支持端侧识别，已切换 3D 模型模式"
        />
        <div class="content-grid">
          <div class="card">
            <h3 class="card-title">3D 模型</h3>
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
            <button class="pc-ar__primary-btn build3d__render" :disabled="build3d.running || !photo" @click="onBuild3d">
              {{ build3d.running ? build3d.stageText : '重新渲染 3D 模型' }}
            </button>
            <!-- 构建进度条（轮询平滑推进，上限 88%） -->
            <div v-if="build3d.running" class="build3d__progress">
              <div class="build3d__bar"><i :style="{ width: build3d.progress + '%' }" /></div>
              <span class="build3d__pct">{{ build3d.progress }}%</span>
            </div>
            <!-- 3D 预览：AI spec 优先渲染，无效/失败回退品类预设模型（识别后展示） -->
            <div v-if="build3d.spec || build3d.status === 'DONE' || build3d.status === 'FAILED' || result" class="build3d__viewer">
              <ThreeViewer :model="modelKey" :spec="build3d.spec" />
            </div>
            <p v-if="build3d.status === 'FAILED'" class="build3d__fail">
              3D 构建失败（{{ build3d.error }}），已展示品类预设模型
            </p>
          </div>

          <div class="card">
            <h3 class="card-title">上传照片生成 3D 模型</h3>
            <div class="upload-area" @click="($refs.fileInput as HTMLInputElement)?.click()">
              <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFileChange" />
              <SvgIcon name="ai-camera" :size="64" color="var(--df-text-secondary)" />
              <p>点击上传物品照片，自动生成 3D 模型</p>
            </div>

            <div v-if="verifying" class="status">AI 识别中…</div>

            <div v-else-if="result" class="result-card">
              <div class="result-cat">
                {{ result.category }}
                <span class="result-confidence">置信 {{ Math.round(result.confidence * 100) }}%</span>
              </div>
              <p class="result-advice">{{ result.advice }}</p>
            </div>

            <p v-if="photo" class="build3d__hint">上传后已自动构建；切换档位或点「重新渲染」可再次生成</p>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-ar {
  min-height: calc(100vh - 72px);
  padding: 64px 24px;
}

.container { max-width: 1000px; margin: 0 auto; }

.header {
  display: flex; align-items: center; gap: 16px; margin-bottom: 16px;
}

.back-btn {
  display: flex; align-items: center; gap: 6px; padding: 8px 16px;
  font-size: 14px; color: var(--df-text-regular); background: var(--df-bg-card);
  border: 2px solid var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
}

.page-title { font-size: 32px; font-weight: 700; color: var(--df-text-primary); margin: 0; }
.page-desc { font-size: 16px; color: var(--df-text-regular); margin-bottom: 32px; }

/* ---- 模式切换 tab ---- */
.pc-ar__tabs {
  display: flex; gap: 12px; margin-bottom: 32px;
}

.pc-ar__tab {
  padding: 10px 32px; font-size: 15px; font-weight: 600;
  color: var(--df-text-regular); background: var(--df-bg-card);
  border: 2px solid var(--df-border-color); border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
  &.is-active {
    border-color: var(--df-color-primary); color: var(--df-color-primary);
    background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent);
  }
}

/* ---- 实时识别舞台（video 16:9 + 框 overlay 绝对定位，独立类名避免 scoped 串扰） ---- */
.pc-ar__stage {
  position: relative; aspect-ratio: 16 / 9;
  background: var(--df-bg-dark); border-radius: 16px; overflow: hidden;
  &.is-drawing { cursor: crosshair; }
}

.pc-ar__fit {
  position: relative;
  height: 100%;
  max-width: 100%;
  margin: 0 auto;
}

.pc-ar__video {
  position: absolute; inset: 0;
  width: 100%; height: 100%; object-fit: fill; /* 容器已与画面同宽高比，fill 无畸变 */
}

.pc-ar__box {
  position: absolute; border: 2px solid; border-radius: 8px;
  box-shadow: 0 0 0 2000px rgba(0, 0, 0, 0.06);

  &--selected { border-width: 3px; }

  &--manual {
    border-style: dashed; border-color: var(--df-color-primary); box-shadow: none;
  }
}

.pc-ar__box-label {
  position: absolute; top: -24px; left: -2px; padding: 2px 8px;
  border-radius: 6px; color: #fff; font-size: 12px; white-space: nowrap;

  &--manual { background: var(--df-color-primary); cursor: pointer; }
}

.pc-ar__box-badge {
  position: absolute; top: -24px; right: -2px; padding: 2px 8px;
  border-radius: 6px; background: rgba(0, 0, 0, 0.65); color: #fff; font-size: 12px;
}

.pc-ar__mask {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 12px;
  color: #fff; background: rgba(11, 28, 44, 0.75); text-align: center;

  p { margin: 0; display: flex; align-items: center; gap: 8px; }
}

.pc-ar__mask-sub { font-size: 12px; opacity: 0.7; }

.pc-ar__primary-btn {
  padding: 10px 32px; font-size: 15px; font-weight: 600; color: #fff;
  background: var(--df-color-primary); border: none; border-radius: 24px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { filter: brightness(1.1); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.pc-ar__stop-btn {
  display: block; margin: 24px auto 0; padding: 10px 32px; font-size: 15px;
  color: var(--df-color-danger); background: transparent; border: 2px solid var(--df-color-danger);
  border-radius: 24px; cursor: pointer; transition: all 0.3s ease;
  &:hover { background: var(--df-color-danger); color: #fff; }
}

.pc-ar__advice-card { margin-top: 24px; }

.pc-ar__advice {
  display: flex; align-items: center; gap: 8px;
  margin: 8px 0; line-height: 1.7; color: var(--df-text-regular);
}

.pc-ar__advice-dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }

.pc-ar__scanning { margin-top: 24px; color: var(--df-text-secondary); text-align: center; }

.pc-ar__unsupported {
  display: flex; flex-direction: column; align-items: center; gap: 16px;
  padding: 48px; background: var(--df-bg-card); border-radius: 16px;
  box-shadow: var(--df-shadow-card); color: var(--df-text-secondary); font-size: 15px;

  p { margin: 0; }
}

.pc-ar__notice { margin-bottom: 24px; border-radius: 12px; }

/* ---- 拍照模式（原有样式保留） ---- */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
}

.card {
  background: var(--df-bg-card); border-radius: 16px; padding: 28px;
  box-shadow: var(--df-shadow-card);
}

.card-title { font-size: 18px; font-weight: 600; color: var(--df-text-primary); margin: 0 0 16px; }

.upload-area {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 60px; border: 3px dashed var(--df-border-color); border-radius: 12px;
  cursor: pointer; transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); background: color-mix(in srgb, var(--df-color-secondary) 5%, transparent); }
  p { font-size: 16px; color: var(--df-text-regular); margin: 12px 0 0; }
}

.status { margin-top: 16px; color: var(--df-text-secondary); font-size: 14px; }

.result-card { margin-top: 16px; }
.result-cat { font-size: 18px; font-weight: 600; color: var(--df-text-primary); margin-bottom: 8px; }
.result-confidence { font-size: 14px; font-weight: 400; color: var(--df-color-primary); margin-left: 8px; }
.result-advice { font-size: 14px; color: var(--df-text-secondary); line-height: 1.7; margin: 0; }

/* ---- 3D 构建卡片：档位选择 + 进度条 + 预览 ---- */
.build3d {
  &__tiers {
    display: flex; gap: 8px; margin-bottom: 16px;
  }

  &__tier {
    flex: 1; padding: 8px 0; font-size: 14px; font-weight: 600;
    color: var(--df-text-regular); background: var(--df-bg-card);
    border: 2px solid var(--df-border-color); border-radius: 10px; cursor: pointer;
    transition: all 0.3s ease;
    &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
    &.is-active {
      color: var(--df-color-primary); border-color: var(--df-color-primary);
      background: color-mix(in srgb, var(--df-color-secondary) 6%, transparent);
    }
  }

  &__render { width: 100%; }

  &__progress {
    display: flex; align-items: center; gap: 12px; margin-top: 16px;
  }

  &__bar {
    flex: 1; height: 6px; border-radius: 3px; background: var(--df-border-color); overflow: hidden;
    i {
      display: block; height: 100%; background: var(--df-color-primary); transition: width 0.4s ease;
    }
  }

  &__pct {
    min-width: 40px; font-size: 13px; color: var(--df-text-secondary); text-align: right;
  }

  &__viewer {
    height: 320px; margin-top: 16px;
  }

  &__fail { margin-top: 12px; font-size: 14px; color: var(--df-text-secondary); line-height: 1.7; }
  &__hint { margin-top: 16px; font-size: 13px; color: var(--df-text-secondary); }
}
</style>
