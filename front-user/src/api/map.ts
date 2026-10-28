// ============================================================
// 用户端地图 API（onsite04 独占新建，禁止追加到 business.ts）
// 契约：onsite_overview.md C-O4（地图契约）
// 后端：back/dianfei-business/map/**（AmapProxyService / MapController / StationController）
// ============================================================
import { request } from './request'

export interface MapConfig {
  webKey: string
  /** 高德安全密钥（V37）：JS API 2.0 加载前注入 window._AMapSecurityConfig.securityJsCode，可为空 */
  securityCode?: string
}

export interface GeocodeResult {
  lng: number
  lat: number
  formattedAddress?: string
  /** 逆地理/地理编码的行政区划（定位后填充地址簿用；直辖市 city 可能为空，前端用 province 兜底） */
  province?: string
  city?: string
  district?: string
}

export interface RouteResult {
  /** 总距离 米 */
  distance: number
  /** 预计耗时 分钟 */
  duration: number
  /** 路线折线（lng,lat）序列 */
  polyline: [number, number][]
}

export interface StationItem {
  id: string
  name: string
  stationType: 'RECYCLE' | 'KITCHEN' | 'COLLECT'
  lng: number
  lat: number
  address: string
  openHours?: string
}

/** 地图配置：前端 env（VITE_AMAP_WEB_KEY）优先，未配置时后端下发（/map/config），未配置 → 前端降级提示 */
export const getMapConfig = (): Promise<MapConfig> => {
  const envKey = (import.meta.env.VITE_AMAP_WEB_KEY as string | undefined)?.trim()
  if (envKey) {
    return Promise.resolve({
      webKey: envKey,
      securityCode: (import.meta.env.VITE_AMAP_SECURITY_CODE as string | undefined)?.trim() || undefined,
    })
  }
  return request.get<MapConfig>('/map/config')
}

/** 地址 → 经纬度 */
export const geocode = (address: string) =>
  request.get<GeocodeResult>('/map/geocode', { address })

/** 经纬度 → 地址（逆地理） */
export const regeo = (lng: number, lat: number) =>
  request.get<GeocodeResult>('/map/regeo', { lng, lat })

export const routePlan = (p: {
  originLng: number
  originLat: number
  destLng: number
  destLat: number
  mode?: 'driving' | 'cycling' | 'walking'
}) => request.get<RouteResult>('/map/route', p)

/** 回收站点（type 可选；全国种子数据 5600+ 条，lng/lat/radiusKm 齐全时后端按半径 km 过滤） */
export const pageStations = (type?: string, lng?: number, lat?: number, radiusKm?: number) =>
  request.get<StationItem[]>('/stations', {
    type,
    ...(lng !== undefined && lat !== undefined && radiusKm !== undefined
      ? { lng, lat, radiusKm }
      : {}),
  })
