// ============================================================
// 上门服务费积分档位（onsite02，契约 C-O2）
// 复用 system ConfigController 公开端点 GET /api/system/config/value/{configKey}
// （登录即读、走缓存热更新，管理端参数配置页可调），并发取回收/维修两档拼装。
// ============================================================
import { request } from './request'

export interface ServiceFeeOptions {
  /** 回收上门服务费档位（积分）；[0] 或空 = 不提供积分支付 */
  recycle: number[]
  /** 维修上门服务费档位（积分）；[0] 或空 = 不提供积分支付 */
  repair: number[]
}

/** sys_config 逗号分隔多档 → number[]（空/缺省 → []） */
const toTiers = (v: string | number | null | undefined): number[] =>
  String(v ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s !== '')
    .map((s) => Number(s))
    .filter((n) => Number.isFinite(n))

/** 读服务费档位（复用 ConfigController 公开端点，无新后端端点） */
export async function getServiceFeeOptions(): Promise<ServiceFeeOptions> {
  const [recycle, repair] = await Promise.all([
    request.get<string | number | null>('/system/config/value/service.recycle.points'),
    request.get<string | number | null>('/system/config/value/service.repair.points'),
  ])
  return { recycle: toTiers(recycle), repair: toTiers(repair) }
}
