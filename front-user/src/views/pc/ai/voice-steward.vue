<script setup lang="ts">
// PC 语音碳管家：对话式交互（ASR + TTS），支持图片上传→云端精分类识别
import { ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useVoiceSteward } from '@/composables/useVoiceSteward'
import { useUserStore } from '@/store/user'
import MarkdownText from '@/components/MarkdownText.vue'

defineOptions({ name: 'pc-voice-steward' })

const router = useRouter()
const userStore = useUserStore()
const { asrSupported, listening, parsing, messages, send, uploadImage, imageOf, startListen, stopListen } = useVoiceSteward()

const input = ref('')
const listRef = ref<HTMLDivElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const INTENT_ACTION: Record<string, { label: string; to: string }> = {
  RECYCLE_BOOK: { label: '去确认预约单 ›', to: '/pc/recycle/book' },
  KITCHEN_DROP: { label: '去垃圾投放上报 ›', to: '/pc/kitchen/drop' },
  QUERY_BILL: { label: '查看低碳账单 ›', to: '/pc/carbon/bill' },
}

/** 选择图片后交由管家识别（选择框每次清空，可连续上传） */
function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (file) uploadImage(file)
}

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function onSend() {
  send(input.value)
  input.value = ''
}

watch(
  () => messages.value.length,
  async () => {
    await nextTick()
    listRef.value?.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' })
  },
)
</script>

<template>
  <div class="pc-steward">
    <div class="container">
      <header class="banner">
        <div class="banner__left">
          <button class="back-btn" @click="router.back()">
            <SvgIcon name="nav-arrow-left" :size="18" /> 返回
          </button>
          <h1 class="page-title">语音碳管家</h1>
          <p class="banner__sub">说一句话，帮你完成回收预约 · 积分查询 · 图片识别</p>
        </div>
        <span class="elder-toggle" @click="userStore.toggleElderMode()">
          {{ userStore.elderMode ? '标准字体' : '大字体' }}
        </span>
      </header>

      <div ref="listRef" class="chat-list">
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="msg"
          :class="`msg--${m.role}`"
        >
          <div v-if="m.role === 'steward'" class="avatar">
            <SvgIcon name="carbon-sprout" :size="22" />
          </div>
          <div class="bubble">
            <img v-if="imageOf(m)" :src="imageOf(m)" class="bubble__img" alt="上传图片" />
            <MarkdownText v-if="m.role === 'steward'" :text="m.text" />
            <template v-else>{{ m.text }}</template>
            <div v-if="m.intent && INTENT_ACTION[m.intent]" class="bubble-action"
              @click="router.push(INTENT_ACTION[m.intent]!.to)">
              {{ INTENT_ACTION[m.intent]!.label }}
            </div>
          </div>
          <template v-if="m.role === 'user'">
            <img v-if="userStore.profile?.avatar" :src="userStore.profile.avatar" class="avatar avatar--user" alt="我的头像" />
            <span v-else class="avatar avatar--user"><SvgIcon name="nav-user" :size="18" /></span>
          </template>
        </div>
        <div v-if="parsing" class="msg msg--steward">
          <div class="avatar">
            <SvgIcon name="carbon-sprout" :size="22" />
          </div>
          <div class="bubble typing">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>

      <div class="input-area">
        <template v-if="asrSupported">
          <button
            class="mic-btn"
            :class="{ listening }"
            @mousedown="startListen"
            @mouseup="stopListen"
            @touchstart.passive="startListen"
            @touchend.passive="stopListen"
          >
            <SvgIcon name="ai-microphone" :size="20" />
            {{ listening ? '松开结束' : '按住说话' }}
          </button>
        </template>
        <div class="text-row">
          <button class="upload-btn" title="上传图片识别" @click="triggerFileSelect">
            <SvgIcon name="ai-photo" :size="20" />
          </button>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/jpeg,image/jpg,image/png,image/webp"
            style="display: none;"
            @change="onFileChange"
          />
          <input v-model="input" class="text-input" placeholder="也可以打字，如：帮我约个废纸回收" @keyup.enter="onSend" />
          <button class="send-btn" :disabled="!input.trim() || parsing" @click="onSend">发送</button>
        </div>
        <div class="input-area__tips">
          <span v-if="!asrSupported" class="tip">当前浏览器不支持语音识别，已切换为文字对话</span>
          <span v-else class="tip">上传图片识别垃圾类型</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pc-steward {
  min-height: calc(100vh - 72px);
  padding: 48px 24px 32px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, color-mix(in srgb, var(--df-color-primary) 6%, transparent) 0%, transparent 240px);
}

.container {
  max-width: 720px;
  margin: 0 auto;
  width: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.banner {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;

  &__left { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }
}

.back-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px;
  font-size: 13px; color: var(--df-text-regular); background: var(--df-bg-card);
  border: 2px solid var(--df-border-color); border-radius: 999px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); color: var(--df-color-primary); }
}

.page-title {
  margin: 0; font-size: 30px; font-weight: 700; color: var(--df-text-primary);
  background: linear-gradient(90deg, var(--df-color-primary), var(--df-color-carbon));
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}

.banner__sub { margin: 0; font-size: 13px; color: var(--df-text-secondary); }

.elder-toggle {
  flex-shrink: 0; font-size: 13px; color: var(--df-color-primary);
  background: var(--df-bg-card); border: 2px solid var(--df-border-color);
  padding: 8px 16px; border-radius: 999px; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { border-color: var(--df-color-primary); }
}

.chat-list {
  flex: 1; overflow-y: auto; padding: 20px 0;
  display: flex; flex-direction: column; gap: 18px;
  scrollbar-width: thin;
  scrollbar-color: var(--df-border-color) transparent;
}

.msg { display: flex; align-items: flex-end; gap: 10px; }
.msg--user { justify-content: flex-end; }
.msg--steward { justify-content: flex-start; }

.avatar {
  flex-shrink: 0; width: 38px; height: 38px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-carbon));
  box-shadow: var(--df-shadow-card);
}

.avatar--user {
  overflow: hidden;
  object-fit: cover;
  background: var(--df-bg-card);
  color: var(--df-text-secondary);
}

.bubble {
  max-width: 72%; padding: 14px 18px; line-height: 1.7; font-size: 15px;
  border-radius: 18px;
  word-break: break-word;
}

.msg--user .bubble {
  background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
  color: #fff;
  border-radius: 18px 18px 4px 18px;
}

.msg--steward .bubble {
  background: var(--df-bg-card); color: var(--df-text-primary);
  border-radius: 4px 18px 18px 18px;
  box-shadow: var(--df-shadow-card);
}

.bubble__img {
  display: block; max-width: 240px; max-height: 200px;
  border-radius: 12px; margin-bottom: 8px; object-fit: cover;
  box-shadow: var(--df-shadow-card);
}

.typing { display: flex; align-items: center; gap: 5px; color: var(--df-text-secondary); }

.dot {
  width: 7px; height: 7px; border-radius: 50%; background: var(--df-text-secondary);
  animation: blink 1.2s infinite;
  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}

@keyframes blink { 0%, 60%, 100% { opacity: 0.25; } 30% { opacity: 1; } }

.bubble-action {
  margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--df-border-color);
  color: var(--df-color-carbon); font-size: 14px; font-weight: 600; cursor: pointer;
  display: inline-block;
}

.input-area {
  background: var(--df-bg-card); border-radius: 20px; padding: 18px;
  box-shadow: var(--df-shadow-float);
  margin-top: 16px;
  border: 2px solid var(--df-border-color);
}

.mic-btn {
  width: 100%; padding: 14px; margin-bottom: 14px;
  font-size: 16px; font-weight: 600; color: white;
  background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
  border: none; border-radius: 14px; cursor: pointer;
  transition: all 0.3s ease;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  user-select: none;
  &:hover { filter: brightness(1.08); }
  &.listening { background: linear-gradient(135deg, var(--df-color-danger), #d6383a); animation: pulse 1.2s infinite; }
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 82, 82, 0.4); }
  50% { box-shadow: 0 0 0 10px rgba(255, 82, 82, 0); }
}

.text-row { display: flex; align-items: center; gap: 10px; }

.upload-btn {
  flex-shrink: 0; width: 44px; height: 44px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  color: var(--df-text-regular); background: var(--df-bg-card);
  border: 2px solid var(--df-border-color); cursor: pointer;
  transition: all 0.3s ease;
  &:hover { color: var(--df-color-primary); border-color: var(--df-color-primary); }
}

.text-input {
  flex: 1; min-width: 0; padding: 12px 16px; font-size: 15px;
  border: 2px solid var(--df-border-color); border-radius: 12px;
  background: transparent;
  transition: all 0.3s ease;
  &:focus { outline: none; border-color: var(--df-color-primary); }
}

.send-btn {
  flex-shrink: 0; padding: 12px 22px; font-size: 15px; font-weight: 600; color: white;
  background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-primary-dark));
  border: none; border-radius: 12px; cursor: pointer;
  transition: all 0.3s ease;
  display: inline-flex; align-items: center; gap: 6px;
  &:hover:not(:disabled) { filter: brightness(1.08); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

.input-area__tips { min-height: 20px; margin-top: 10px; }

.tip { display: block; text-align: center; color: var(--df-text-secondary); font-size: 12px; }
</style>
