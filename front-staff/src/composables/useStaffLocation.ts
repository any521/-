// ============================================================
// 员工实时位置上报（GapFill B2，契约 C3）：
// 接单成功后启动 navigator.geolocation.watchPosition，成功回调每 30s 节流上报
// PUT /api/staff/location（后端 Redis staff:loc:{userId} TTL 2h）；
// 权限拒绝/定位失败/上报失败 → 静默降级（console.info + 停止），不阻塞业务。
// 模块级单例：多组件/重复调用共享同一 watch，防重复启动。
// ============================================================
import { onBeforeUnmount } from 'vue'
import { reportStaffLocation } from '@/api/business'
import { wgs84ToGcj02 } from '@/utils/coord'

/** 上报节流间隔 ms（30s 内仅发一次） */
const REPORT_INTERVAL = 30_000

let watchId: number | null = null
/** 上次成功上报时刻（节流基准） */
let lastReportAt = 0

function report(lng: number, lat: number) {
  const now = Date.now()
  if (now - lastReportAt < REPORT_INTERVAL) return
  lastReportAt = now
  reportStaffLocation({ lng, lat }).catch((err: unknown) => {
    // 上报失败不阻塞定位链路，下个节流窗口重试
    console.info('[staff-location] 上报失败（静默降级）:', err)
  })
}

export function useStaffLocation() {
  function start() {
    if (watchId !== null) return // 单例：已在监听
    if (!('geolocation' in navigator)) {
      console.info('[staff-location] 当前环境不支持 Geolocation，静默降级')
      return
    }
    watchId = navigator.geolocation.watchPosition(
      (pos) => {
        // 浏览器返回 WGS-84，消费方（用户端追踪页 coordinate=gaode 地图渲染/路线规划）需 GCJ-02：上报前转换
        const [lng, lat] = wgs84ToGcj02(pos.coords.longitude, pos.coords.latitude)
        report(lng, lat)
      },
      (err) => {
        // 权限拒绝/定位失败：静默降级并停止监听（不阻塞接单业务）
        console.info('[staff-location] 定位不可用（静默降级）:', err.code, err.message)
        stop()
      },
    )
  }

  function stop() {
    if (watchId === null) return
    navigator.geolocation.clearWatch(watchId)
    watchId = null
    lastReportAt = 0
  }

  onBeforeUnmount(stop)
  return { start, stop }
}
