// ============================================================
// 地址模型与文本工具（D0 通用业务组件，address-book 正式版）
// 独立 ts 模块：AddressValue 与 addressTextOf 供各端 API/页面复用，
// 不放在 .vue 内（<script setup> 禁止 ES module 导出）
// ============================================================

export interface AddressValue {
  /** 片区 code（业务 service 区，派单用；格式 A{片区雪花ID}，如 A2083419158956417026） */
  areaCode: string
  /** 片区名 */
  areaName: string
  /** 省（定位/级联填充；为空时组件退化为「片区+详细地址」旧模式） */
  province?: string
  city?: string
  district?: string
  /** 详细地址（道路/小区/楼栋门牌） */
  detail: string
  lng?: number | null
  lat?: number | null
}

/** 地址 → 完整文本：有省市区时「省市区 片区 详情」，否则「片区 详情」（历史契约兼容） */
export function addressTextOf(a: AddressValue): string {
  const region = [a.province, a.city, a.district].filter(Boolean).join('')
  const head = region ? `${region} ${a.areaName ?? ''}`.trim() : (a.areaName ?? '')
  return `${head} ${a.detail ?? ''}`.trim()
}
