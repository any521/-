<script setup lang="ts">
// F7 语音碳管家：对话式交互（ASR + TTS），支持图片上传→云端精分类识别
import { ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useVoiceSteward } from '@/composables/useVoiceSteward'
import { useUserStore } from '@/store/user'
import MarkdownText from '@/components/MarkdownText.vue'

const router = useRouter()
const userStore = useUserStore()
const { asrSupported, listening, parsing, messages, send, uploadImage, imageOf, startListen, stopListen } = useVoiceSteward()

const input = ref('')
const listRef = ref<HTMLDivElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

/** 意图 → 快捷跳转（语音成单闭环） */
const INTENT_ACTION: Record<string, { label: string; to: string }> = {
  RECYCLE_BOOK: { label: '去确认预约单 ›', to: '/recycle/book' },
  KITCHEN_DROP: { label: '去投放上报 ›', to: '/kitchen/drop' },
  QUERY_BILL: { label: '查看低碳账单 ›', to: '/carbon/bill' },
}

/** 选择图片后交由管家识别（选择框每次清空，可连续上传） */
function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (file) uploadImage(file)
}

/** 触发文件选择 */
function triggerFileSelect() {
  fileInputRef.value?.click()
}

function onSend() {
  send(input.value)
  input.value = ''
}

// 新消息自动滚底
watch(
  () => messages.value.length,
  async () => {
    await nextTick()
    listRef.value?.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' })
  },
)
</script>

<template>
  <div class="steward" :class="{ 'is-elder': userStore.elderMode }">
    <van-nav-bar title="语音碳管家" left-arrow @click-left="router.back()">
      <template #right>
        <span class="steward__elder-toggle" @click="userStore.toggleElderMode()">
          {{ userStore.elderMode ? '标准字体' : '大字体' }}
        </span>
      </template>
    </van-nav-bar>

    <!-- 对话流 -->
    <div ref="listRef" class="steward__list">
      <div v-for="(m, i) in messages" :key="i" class="steward__msg" :class="`steward__msg--${m.role}`">
        <div v-if="m.role === 'steward'" class="steward__avatar steward__avatar--steward">
          <SvgIcon name="carbon-sprout" :size="22" />
        </div>
        <div class="steward__bubble">
          <img v-if="imageOf(m)" :src="imageOf(m)" class="steward__img" alt="上传图片" />
          <MarkdownText v-if="m.role === 'steward'" :text="m.text" />
          <template v-else>{{ m.text }}</template>
          <div v-if="m.intent && INTENT_ACTION[m.intent]" class="steward__action"
            @click="router.push(INTENT_ACTION[m.intent]!.to)">
            {{ INTENT_ACTION[m.intent]!.label }}
          </div>
        </div>
        <template v-if="m.role === 'user'">
          <img v-if="userStore.profile?.avatar" :src="userStore.profile.avatar" class="steward__avatar" alt="我的头像" />
          <span v-else class="steward__avatar steward__avatar--fallback"><SvgIcon name="nav-user" /></span>
        </template>
      </div>
      <div v-if="parsing" class="steward__msg steward__msg--steward">
        <div class="steward__avatar steward__avatar--steward">
          <SvgIcon name="carbon-sprout" :size="22" />
        </div>
        <div class="steward__bubble steward__bubble--typing">正在思考…</div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="steward__footer">
      <!-- 图片上传按钮 -->
      <div class="steward__upload-row">
        <van-button
          plain
          round
          size="small"
          icon="photograph"
          @click="triggerFileSelect"
        >
          上传图片
        </van-button>

        <!-- 隐藏的 file input -->
        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/jpg,image/png,image/webp"
          style="display: none;"
          @change="onFileChange"
        />
      </div>
      <template v-if="asrSupported">
        <van-button
          class="steward__mic"
          round
          block
          :type="listening ? 'danger' : 'primary'"
          @touchstart.passive="startListen"
          @touchend.passive="stopListen"
          @mousedown="startListen"
          @mouseup="stopListen"
        >
          <SvgIcon name="ai-microphone" /> {{ listening ? '松开结束' : '按住说话' }}
        </van-button>
      </template>
      <div class="steward__text-row">
        <van-field v-model="input" placeholder="也可以打字，如：帮我约个废纸回收" @keyup.enter="onSend" />
        <van-button type="primary" size="small" round :disabled="!input.trim() || parsing" @click="onSend">
          发送
        </van-button>
      </div>
      <p v-if="!asrSupported" class="steward__tip">当前浏览器不支持语音识别，已切换为文字对话</p>
      <p v-else class="steward__tip">上传图片识别垃圾类型</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@shared/styles/tokens.scss' as *;

.steward {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--df-bg-page);

  &__elder-toggle {
    color: var(--df-color-primary);
    font-size: $df-font-size-sm;
  }

  &__list {
    flex: 1;
    overflow-y: auto;
    padding: $df-space-md;
  }

  &__msg {
    display: flex;
    align-items: flex-end;
    gap: $df-space-sm;
    margin-bottom: $df-space-md;

    &--user {
      justify-content: flex-end;

      .steward__bubble {
        background: var(--df-color-primary);
        color: #fff;
        border-radius: $df-radius-lg $df-radius-lg 2px $df-radius-lg;
      }
    }

    &--steward .steward__bubble {
      background: var(--df-bg-card);
      border-radius: $df-radius-lg $df-radius-lg $df-radius-lg 2px;
      box-shadow: var(--df-shadow-card);
    }
  }

  &__avatar {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    overflow: hidden;
    object-fit: cover;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: var(--df-bg-card);
    box-shadow: var(--df-shadow-card);

    &--steward {
      background: linear-gradient(135deg, var(--df-color-primary), var(--df-color-carbon));
    }

    &--fallback {
      color: var(--df-text-secondary);
    }
  }

  &__bubble {
    max-width: 78%;
    padding: $df-space-md;
    line-height: 1.6;
    font-size: $df-font-size-base;
    word-break: break-word;

    &--typing {
      color: var(--df-text-secondary);
    }
  }

  &__img {
    display: block;
    max-width: 200px;
    max-height: 180px;
    border-radius: $df-radius-sm;
    margin-bottom: $df-space-xs;
    object-fit: cover;
    box-shadow: var(--df-shadow-card);
  }

  &__action {
    margin-top: $df-space-sm;
    color: var(--df-color-carbon);
    font-size: $df-font-size-sm;
  }

  &__footer {
    padding: $df-space-md;
    background: var(--df-bg-card);
    box-shadow: var(--df-shadow-float);
  }

  &__mic {
    margin-bottom: $df-space-sm;
    user-select: none;
  }

  &__upload-row {
    display: flex;
    justify-content: center;
    gap: $df-space-sm;
    margin-bottom: $df-space-sm;
  }

  &__text-row {
    display: flex;
    align-items: center;
    gap: $df-space-sm;

    .van-field {
      flex: 1;
      background: var(--df-bg-page);
      border-radius: $df-radius-round;
    }
  }

  &__tip {
    margin: $df-space-sm 0 0;
    text-align: center;
    color: var(--df-text-secondary);
    font-size: $df-font-size-xs;
  }

  // 适老化：加大气泡字体与按钮
  &.is-elder {
    .steward__bubble {
      font-size: $df-font-size-elder;
    }

    .steward__mic {
      height: 56px;
      font-size: $df-font-size-elder;
    }
  }
}
</style>
