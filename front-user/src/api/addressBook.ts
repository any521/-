// ============================================================
// 地址簿 API（address-book 正式版，/api/user/addresses）
// 预约回收/维修/商城/跳蚤统一地址来源；AddressValue 双向转换
// D0: 片区列表改为从后端 API /api/staff/areas 动态加载
// ============================================================
import type { AddressValue } from '@shared/components/address'
import { request } from './request'

/** 片区实体（与后端 BizArea 对齐；雪花 ID 为字符串） */
export interface Area {
  id: string
  code?: string
  name: string
  address?: string
}

/** 全局片区缓存（避免重复请求） */
let cachedAreas: Area[] | null = null

export interface AddressBookEntry extends AddressValue {
  /** 雪花 id（后端 Long 序列化为字符串） */
  id: string
  contactName: string
  contactPhone: string
  tag: string
  /** 0非默认 1默认 */
  isDefault: number
}

/** 后端 UserAddress 实体（Long 序列化为字符串） */
interface UserAddressVO {
  id: string
  contactName?: string
  contactPhone?: string
  province?: string
  city?: string
  district?: string
  detail?: string
  lng?: string | number | null
  lat?: string | number | null
  areaId?: string | number | null
  tag?: string
  isDefault?: number
}

/** 获取所有片区列表（懒加载缓存，首次按需调用后端 API） */
export async function getAllAreas(): Promise<Area[]> {
  if (cachedAreas) return cachedAreas
  try {
    const resp = await request.get<Area[]>('/staff/areas')
    cachedAreas = resp || []
    return cachedAreas
  } catch (e) {
    console.error('[地址簿] 片区列表加载失败', e)
    // 失败不缓存（保持 null 下次重试），本次降级为空列表
    return []
  }
}

/** 片区 id → code（A+雪花 ID 字符串；范围外回空 = 未选片区） */
export const areaCodeOf = (areaId?: number | string | null): string => {
  const s = String(areaId ?? '').trim()
  return s ? `A${s}` : ''
}

/** 片区 id → 片区名（字符串比较，避免雪花 ID 精度丢失；
 * 未命中/缓存未加载回退为“片区{id}”，不可用“未选择片区”——该文案会随常用地址混入订单地址文本 */
const areaNameOf = (areaId?: number | string | null): string => {
  const s = String(areaId ?? '').trim()
  if (!s || s === '0' || s === 'null') return ''
  if (!cachedAreas) return `片区${s}`
  const area = cachedAreas.find((a) => String(a.id) === s)
  return area?.name ?? `片区${s}`
}

/** 片区 code → id（雪花 ID 字符串透传，与 business.ts areaIdOf 同规则） */
const areaIdOf = (code?: string): string | null => {
  const c = String(code ?? '').trim()
  if (!c) return null
  return c.startsWith('A') ? c.slice(1) : c
}

const toEntry = (v: UserAddressVO): AddressBookEntry => {
  // 雪花 ID 字符串透传，避免 Number() 精度丢失
  const areaId = v.areaId != null && v.areaId !== '' ? String(v.areaId) : null
  return {
    id: v.id,
    areaCode: areaCodeOf(areaId),
    areaName: areaNameOf(areaId),
    province: v.province ?? '',
    city: v.city ?? '',
    district: v.district ?? '',
    detail: v.detail ?? '',
    lng: v.lng != null && v.lng !== '' ? Number(v.lng) : null,
    lat: v.lat != null && v.lat !== '' ? Number(v.lat) : null,
    contactName: v.contactName ?? '',
    contactPhone: v.contactPhone ?? '',
    tag: v.tag ?? '家',
    isDefault: Number(v.isDefault ?? 0),
  }
}

/** 提交载荷（AddressValue → 后端字段） */
const toPayload = (a: AddressValue, extra: { contactName?: string; contactPhone?: string; tag?: string; isDefault?: number }) => ({
  contactName: extra.contactName ?? '',
  contactPhone: extra.contactPhone ?? '',
  province: a.province ?? '',
  city: a.city ?? '',
  district: a.district ?? '',
  detail: a.detail ?? '',
  lng: a.lng ?? null,
  lat: a.lat ?? null,
  areaId: areaIdOf(a.areaCode),
  tag: extra.tag ?? '家',
  isDefault: extra.isDefault ?? 0,
})

/** 我的地址列表（默认排前；先预热片区缓存，保证自动创建片区名展示） */
export async function listAddresses(): Promise<AddressBookEntry[]> {
  // 预热片区名缓存（失败静默，名称降级为未选择片区）
  await getAllAreas()
  const rows = await request.get<UserAddressVO[]>('/user/addresses')
  return (rows ?? []).map(toEntry)
}

/** 新增地址 */
export const saveAddress = (a: AddressValue, extra: { contactName?: string; contactPhone?: string; tag?: string; isDefault?: number } = {}) =>
  request.post<UserAddressVO>('/user/addresses', toPayload(a, extra))

/** 更新地址 */
export const updateAddress = (id: string, a: AddressValue, extra: { contactName?: string; contactPhone?: string; tag?: string; isDefault?: number } = {}) =>
  request.put<UserAddressVO>(`/user/addresses/${id}`, toPayload(a, extra))

/** 删除地址 */
export const removeAddress = (id: string) => request.delete(`/user/addresses/${id}`)

/** 设为默认 */
export const setDefaultAddress = (id: string) => request.put(`/user/addresses/${id}/default`)
