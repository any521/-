// ============================================================
// 品类色标单一来源（契约 C6-7）：框色/标签色必须从此取，禁止散定义
// 色值与主题 token 体系协调（与 --df-color-primary 同饱和度系，
// 不做暗色微调，避免两套色值漂移）。类别对齐冻结枚举 C6-6（8 类）。
// ============================================================

export interface CategoryStyle {
  /** 框/标签主色 */
  color: string
  /** 短名（框标签展示） */
  label: string
}

export const CATEGORY_STYLE: Record<string, CategoryStyle> = {
  paper: { color: '#2ecc71', label: '纸类' }, // 可回收-绿
  plastic: { color: '#3498db', label: '塑料' },
  metal: { color: '#9b59b6', label: '金属' },
  fabric: { color: '#f39c12', label: '织物' },
  appliance: { color: '#e67e22', label: '家电' },
  electronics: { color: '#e74c3c', label: '电子' },
  glass: { color: '#1abc9c', label: '玻璃' },
  kitchen: { color: '#8b5e3c', label: '厨余' }, // 厨余-棕
  other: { color: '#95a5a6', label: '其他' }, // v0.3.0 六类：其他垃圾-灰
}

export const DEFAULT_CATEGORY_STYLE: CategoryStyle = { color: '#95a5a6', label: '其他' }

export function styleOf(code: string): CategoryStyle {
  return CATEGORY_STYLE[code] ?? DEFAULT_CATEGORY_STYLE
}
