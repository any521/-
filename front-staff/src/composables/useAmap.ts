// ============================================================
// 高德地图封装（onsite04 核心单例，员工端）：
// 与 front-user/src/composables/useAmap.ts 同实现副本（两工程独立，任务 2.6 指定复制）
// - ensureAmap()：动态加载 JS API 2.0（key 经 GET /api/map/config 下发，
//   缺失/加载失败 → mapReady=false，页面降级提示不崩）
// - createMap / addMarker / drawPolyline / fitBounds：常用渲染原语
// - 模块级单例：Map 实例与加载 Promise 全局缓存，多页面复用不重复加载
// ============================================================
import { ref } from 'vue'
import { getMapConfig } from '@/api/map'

/** 地图是否就绪（页面据此切换真实地图/降级 UI） */
export const mapReady = ref(false)

/** AMap 全局类型（JS API 无 npm 类型，运行时挂 window.AMap） */
type AMapNamespace = {
  Map: new (el: HTMLElement, opts: Record<string, unknown>) => unknown
  Marker: new (opts: Record<string, unknown>) => unknown
  Polyline: new (opts: Record<string, unknown>) => unknown
  LngLat: new (lng: number, lat: number) => unknown
  [key: string]: unknown
}
type AMapInstance = AMapNamespace | undefined

declare global {
  interface Window {
    AMap?: AMapNamespace
    _AMapSecurityConfig?: { securityJsCode?: string }
  }
}

/** 加载 Promise（单例；失败后允许重试） */
let loadPromise: Promise<boolean> | null = null
/** 已创建的 Map 实例缓存（key: 容器元素，页面 onUnmounted 主动销毁后清除） */
const mapCache = new WeakMap<HTMLElement, { map: unknown; amap: AMapInstance }>()

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('AMap 脚本加载失败'))
    document.head.appendChild(script)
  })
}

/** 确保高德 JS API 就绪：返回是否可用（未配置 key / 加载失败 → false） */
export function ensureAmap(): Promise<boolean> {
  if (window.AMap) {
    mapReady.value = true
    return Promise.resolve(true)
  }
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    try {
      const cfg = await getMapConfig()
      if (!cfg?.webKey) return false
      // V37：高德新版要求 key + securityJsCode（安全密钥）配对。
      // 必须在加载 JS API 之前注入 _AMapSecurityConfig，脚本加载时才携带安全密钥
      if (cfg.securityCode) {
        window._AMapSecurityConfig = { securityJsCode: cfg.securityCode }
      }
      await loadScript(
        `https://webapi.amap.com/maps?v=2.0&key=${cfg.webKey}&plugin=AMap.Driving,AMap.Riding,AMap.Walking`,
      )
      if (!window.AMap) return false
      mapReady.value = true
      return true
    } catch {
      mapReady.value = false
      return false
    } finally {
      // 失败后释放 promise，允许下次进入页面重试
      if (!mapReady.value) loadPromise = null
    }
  })()
  return loadPromise
}

/** 创建地图实例（同一容器重复调用返回既有实例） */
export function createMap(el: HTMLElement, center: [number, number], zoom = 14) {
  const cached = mapCache.get(el)
  if (cached) return cached
  const amap = window.AMap
  if (!amap) throw new Error('AMap 未就绪')
  const map = new amap.Map(el, {
    zoom,
    center: new amap.LngLat(center[0], center[1]),
    viewMode: '2D',
    resizeEnable: true,
  })
  mapCache.set(el, { map, amap })
  return { map, amap }
}

/** 销毁地图实例（页面卸载时调用，释放 WebGL/事件资源） */
export function destroyMap(el: HTMLElement) {
  const cached = mapCache.get(el)
  if (cached?.map && typeof (cached.map as { destroy?: () => void }).destroy === 'function') {
    ;(cached.map as { destroy: () => void }).destroy()
  }
  mapCache.delete(el)
}

/** 添加 Marker（opts: content/offset/icon/title 等透传 AMap.Marker） */
export function addMarker(
  map: { map: unknown; amap: AMapInstance },
  lnglat: [number, number],
  opts: Record<string, unknown> = {},
) {
  const { map: mapInst, amap } = map
  const marker = new (amap as AMapNamespace).Marker({
    position: new (amap as AMapNamespace).LngLat(lnglat[0], lnglat[1]),
    ...opts,
  })
  ;(mapInst as { add: (o: unknown) => void }).add(marker)
  return marker
}

/** 绘制路线折线（coords (lng,lat) 序列），返回 Polyline 实例 */
export function drawPolyline(
  map: { map: unknown; amap: AMapInstance },
  coords: [number, number][],
  opts: Record<string, unknown> = {},
) {
  const { map: mapInst, amap } = map
  const path = coords.map(([lng, lat]) => new (amap as AMapNamespace).LngLat(lng, lat))
  const polyline = new (amap as AMapNamespace).Polyline({
    path,
    strokeColor: '#07c160',
    strokeWeight: 6,
    strokeOpacity: 0.9,
    lineJoin: 'round',
    ...opts,
  })
  ;(mapInst as { add: (o: unknown) => void }).add(polyline)
  return polyline
}

/** 视野自适应到全部覆盖物（无覆盖物时按给定点集中心/缩放兜底） */
export function fitBounds(map: { map: unknown; amap: AMapInstance }, points: [number, number][], zoom = 14) {
  const { map: mapInst } = map
  if (!points.length) return
  const mid = points[Math.floor(points.length / 2)]
  ;(mapInst as { setZoomAndCenter: (z: number, c: unknown) => void }).setZoomAndCenter(zoom, mid)
  ;(mapInst as { setFitView: (o?: unknown) => void }).setFitView()
}
