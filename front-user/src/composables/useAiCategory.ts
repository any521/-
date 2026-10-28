// ============================================================
// AI 拍照识别 → 品类（F1 云端视觉，双端复用）
// 请求令牌守护：仅最新一次 verify() 的结果会落地，过期响应静默丢弃。
// ============================================================
import { ref } from 'vue'
import { showToast } from 'vant'
import { aiVerifyImage, type ImageVerifyResult } from '@/api/business'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'

export function useAiCategory(scene: 'recycle' | 'kitchen' = 'recycle') {
  const verifying = ref(false)
  const result = ref<ImageVerifyResult | null>(null)
  let seq = 0

  /** 上传照片走 AI 识别；命中品类时回调 code，调用方决定是否覆盖当前选择 */
  async function verify(photoBase64: string, onCategory?: (code: string) => void) {
    const id = ++seq
    verifying.value = true
    try {
      const r = await aiVerifyImage(photoBase64, scene)
      if (id !== seq) return
      result.value = r
      const cat = RECYCLE_CATEGORIES.find((c) => c.name === r.category || c.code === r.category)
      if (cat) {
        onCategory?.(cat.code)
      } else {
        // 识别不到对应品类：优先展示后端驳回/审核原因（如重复图片），否则通用提示
        showToast(r.advice || '未识别到品类，请手动选择')
      }
    } catch {
      // 识别接口失败（链路耗尽/超时等）：自定义提示，不抛未捕获异常
      if (id === seq) showToast('AI 识别失败，请稍后重试')
    } finally {
      if (id === seq) verifying.value = false
    }
  }

  function reset() {
    result.value = null
  }

  return { verifying, result, verify, reset }
}
