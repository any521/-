<script setup lang="ts">
// Markdown 渲染（语音碳管家等 AI 回复）：marked 解析后用 DOMPurify 净化，防止 XSS
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ text: string }>()

const html = computed(() =>
  DOMPurify.sanitize(marked.parse(props.text ?? '', { breaks: true, gfm: true }) as string, {
    // 允许常见 Markdown 标签，禁止 script/iframe/on* 等
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'a', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class'],
  }),
)
</script>

<template>
  <div class="md" v-html="html" />
</template>

<style scoped lang="scss">
.md {
  word-break: break-word;
  font-size: inherit;
  line-height: inherit;

  :deep(p) { margin: 0 0 6px; &:last-child { margin-bottom: 0; } }

  :deep(ul), :deep(ol) { margin: 4px 0; padding-left: 18px; }
  :deep(li) { margin: 2px 0; }

  :deep(strong) { font-weight: 600; }

  :deep(blockquote) {
    margin: 6px 0; padding: 4px 10px;
    border-left: 3px solid var(--df-color-carbon, #4caf50);
    color: var(--df-text-secondary, #666);
    background: rgba(0, 0, 0, 0.03);
    border-radius: 0 6px 6px 0;
  }

  :deep(code) {
    padding: 1px 5px; border-radius: 4px;
    background: rgba(0, 0, 0, 0.06);
    font-size: 0.92em;
  }

  :deep(table) { border-collapse: collapse; margin: 6px 0; width: 100%; }
  :deep(th), :deep(td) { border: 1px solid var(--df-border-color, #ddd); padding: 4px 8px; font-size: 0.95em; }
  :deep(th) { background: rgba(0, 0, 0, 0.03); font-weight: 600; }

  :deep(h1), :deep(h2), :deep(h3), :deep(h4) { margin: 8px 0 4px; font-size: 1.05em; font-weight: 700; }

  :deep(a) { color: var(--df-color-primary, #2f6b5e); }
}
</style>
