// ============================================================
// 员工端地图 API（onsite04 独占新建，禁止追加到 business.ts）
// 契约：onsite_overview.md C-O4（地图契约，/api/map/* 登录即可访问）
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
}

export interface RouteResult {
  /** 总距离 米 */
  distance: number
  /** 预计耗时 分钟 */
  duration: number
  /** 路线折线（lng,lat）序列 */
  polyline: [number, number][]
}

/** 地图配置（未配置 web_key 时后端返 5001，前端降级提示） */
export const getMapConfig = () => request.get<MapConfig>('/map/config')

/** 地址 → 经纬度（RouteStop 无坐标，导航页经此补齐） */
export const geocode = (address: string) =>
  request.get<GeocodeResult>('/map/geocode', { address })

export const routePlan = (p: {
  originLng: number
  originLat: number
  destLng: number
  destLat: number
  mode?: 'driving' | 'cycling' | 'walking'
}) => request.get<RouteResult>('/map/route', p)
