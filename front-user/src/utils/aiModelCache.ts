// ============================================================
// AI 3D 模型/识别结果浏览器缓存（localStorage 持久化）
// 保存最近一次：物品照片 dataUrl + 识别结果 + AI 构建的 3D spec + 品类，
// 刷新/重进页面后由预约页恢复展示（模型不丢、识别数据不丢）。
// 图片 dataUrl 可达 1-3MB，localStorage 配额超限/隐私模式写入失败时静默忽略，
// 不阻断任何主流程。
// ============================================================
import type { ImageVerifyResult, ModelSpec } from '@/api/business'

const KEY = 'df_ai_3d_latest'

export interface AiModelCache {
  /** 物品照片 dataUrl（base64） */
  image: string
  /** 云端识别结果（品类/置信度/建议） */
  result: ImageVerifyResult | null
  /** AI 构建的 3D spec（null=未构建/构建失败，回退预设模型库） */
  spec: ModelSpec | null
  /** 识别/选定的品类 code */
  category: string
  /** 保存时间戳（用于提示条） */
  updatedAt: number
}

/** 合并保存缓存（部分字段更新：识别成功后存 image+result，spec 到达后补 spec） */
export function saveAiModelCache(partial: Partial<AiModelCache>): void {
  try {
    const prev = loadAiModelCache()
    const next: AiModelCache = {
      image: partial.image ?? prev?.image ?? '',
      result: partial.result !== undefined ? partial.result : (prev?.result ?? null),
      spec: partial.spec !== undefined ? partial.spec : (prev?.spec ?? null),
      category: partial.category ?? prev?.category ?? '',
      updatedAt: Date.now(),
    }
    if (!next.image) return
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // 配额超限（图片过大）/隐私模式：放弃持久化，不影响本次展示
  }
}

/** 读取缓存（无缓存/解析失败/缺图片 → null） */
export function loadAiModelCache(): AiModelCache | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const d = JSON.parse(raw) as Partial<AiModelCache>
    if (!d?.image) return null
    return {
      image: d.image,
      result: d.result ?? null,
      spec: d.spec ?? null,
      category: d.category ?? '',
      updatedAt: d.updatedAt ?? 0,
    }
  } catch {
    return null
  }
}
