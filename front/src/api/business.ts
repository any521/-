// ============================================================
// 业务管理域 API（回收/垃圾投放/维修/商城/售后/风控/排班运维/大屏/Agent 驾驶舱）
// 联调版：已对齐后端真实契约（/api/admin/** 前缀 + int 枚举，
// 见 back/dianfei-business 各 Admin*Controller、dianfei-ai AgentController）
// ============================================================
import type { PageQuery, PageResult } from '@shared/http/types'
import type { OrderStatus } from '@shared/http/types'
import { request } from './request'

// ---------------- 回收订单（B 线 AdminRecycleController，已对齐真实路径） ----------------

/** 管理端片区缓存：/admin/areas 分页接口 pageSize 拉满一次，供订单/员工列表映射片区名 */
interface AdminAreaRow { id: string; areaName: string }
let adminAreas: AdminAreaRow[] | null = null
let adminAreasLoading: Promise<AdminAreaRow[]> | null = null

export async function ensureAdminAreas(): Promise<AdminAreaRow[]> {
  if (adminAreas) return adminAreas
  if (!adminAreasLoading) {
    adminAreasLoading = (async () => {
      try {
        const page = await request.get<PageResult<AdminAreaRow>>('/admin/areas', { pageNum: 1, pageSize: 999 })
        adminAreas = page?.list ?? []
      } catch {
        adminAreas = []
      }
      return adminAreas
    })()
  }
  return adminAreasLoading
}

/** 片区 id → 片区名（雪花 ID 字符串比较；未命中回退“片区{id}”，不误导为无片区） */
export function adminAreaNameOf(areaId?: string | number | null): string {
  const s = String(areaId ?? '').trim()
  if (!s || s === '0' || s === 'null') return '未分配'
  return adminAreas?.find((a) => String(a.id) === s)?.areaName ?? `片区${s}`
}

export interface RecycleOrder {
  /** 雪花 ID 后端序列化为 string，前端不得转 Number（精度丢失致详情查无此单） */
  id: string
  orderNo: string
  userName: string
  phone: string
  areaName: string
  categories: string
  weight: number
  amount: number
  carbon: number
  status: OrderStatus
  staffName: string
  appointTime: string
  createTime: string
}

/** 后端 RecycleOrder 实体行（无 userName/areaName/carbon，联表字段缺失时降级展示 ID） */
type RecycleOrderRow = Partial<RecycleOrder> & {
  category?: string
  estWeight?: number
  userId?: string | number
  areaId?: string | number
  staffId?: string | number | null
}

/** 订单详情 VO（Long 序列化为 string，字段与后端 RecycleOrderVO 对齐；areaName 后端联表已返回） */
export interface RecycleOrderDetail {
  id: string; orderNo: string; userId: string; staffId: string | null; staffName: string | null
  areaId: string; areaName: string | null; category: string; estWeight: number; weight: number; quantity: number
  address: string; appointTime: string; status: number; statusDesc: string
  amount: number; transferTo: string | null; repairRef: string | null
  cancelReason: string | null; exceptionReason: string | null
  finishTime: string | null; createTime: string
  items: { category: string; weight: number; amount: number }[]
}

/** 报价行（RecyclePrice 实体；id 为 string，联网同步/手动改价都会刷新 updateTime） */
export interface RecyclePrice {
  id: string
  category: string
  pricePerKg: number
  status: 0 | 1
  /** 最近更新时间 yyyy-MM-dd HH:mm:ss（手动改价/联网同步落库时刷新） */
  updateTime?: string | null
  /** 行情地区（联网落库时记录，未同步过为 null） */
  city?: string | null
}

/** 指派下拉用员工行（BizStaff） */
export interface RecyclerOption { id: string; staffName: string; phone: string; areaId: string }

export async function pageRecycleOrders(q: PageQuery): Promise<PageResult<RecycleOrder>> {
  await ensureAdminAreas()
  const page = await request.get<PageResult<RecycleOrderRow>>('/admin/recycle/orders', q)
  return {
    ...page,
    list: (page.list ?? []).map((r) => ({
      id: String(r.id ?? ''),
      orderNo: r.orderNo ?? '',
      userName: r.userName ?? (r.userId != null ? `用户${r.userId}` : '—'),
      phone: r.phone ?? '—',
      areaName: r.areaName ?? adminAreaNameOf(r.areaId),
      categories: r.categories ?? r.category ?? '—',
      weight: r.weight ?? r.estWeight ?? 0,
      amount: r.amount ?? 0,
      carbon: r.carbon ?? 0,
      status: (r.status ?? 0) as OrderStatus,
      staffName: r.staffName ?? (r.staffId != null ? `员工${r.staffId}` : '—'),
      appointTime: r.appointTime ?? '',
      createTime: r.createTime ?? '',
    })),
  }
}

/** M-03 订单详情（RecycleOrderVO：含实收明细/回收员/联动维修单） */
export const getRecycleOrder = (id: string) => request.get<RecycleOrderDetail>(`/admin/recycle/orders/${id}`)
/** M-03 改派（0待接单/1已接单/5异常 → 重置 0 指派本片区在岗回收员）
 * staffId 传字符串（雪花 ID 19 位，Number() 会丢精度致后端查无此员工） */
export const reassignRecycleOrder = (id: string, staffId: string) =>
  request.put<void>(`/admin/recycle/orders/${id}/reassign?staffId=${staffId}`)
/** M-03 报价列表（含下架，供配置页） */
export const listRecyclePrice = () => request.get<RecyclePrice[]>('/admin/recycle/price')
/** M-03 配置报价（category 幂等 upsert，body={category,pricePerKg,status}） */
export const saveRecyclePrice = (data: { category: string; pricePerKg: number; status: 0 | 1 }) =>
  request.put<RecyclePrice>('/admin/recycle/price', data)

// ---------------- 垃圾投放记录（G5，AdminKitchenController，已对齐真实路径） ----------------

export interface KitchenRecord {
  id: number
  areaName: string
  userName: string
  bags: number
  photoUrl: string
  aiAuditResult: '自动通过' | '待人审' | '驳回'
  points: number
  createTime: string
}

export const pageKitchenRecords = (q: PageQuery) => request.get<PageResult<KitchenRecord>>('/admin/kitchen/records', q)

// ---------------- 清运任务（B4，AdminKitchenController：管理员发布/列表/改派） ----------------

/** 清运任务行（KitchenTask，雪花 ID 序列化为 string） */
export interface KitchenTaskRow {
  id: string
  areaId: string
  /** 片区名称（后端联表填充） */
  areaName?: string | null
  taskDate: string
  totalBags: number
  vehicleId: string | null
  staffId: string | null
  /** 0待执行 1已完成 */
  status: number
  totalWeight?: number | null
  vehicleNo?: string | null
  destination?: string | null
  finishTime?: string | null
  createTime: string
}

export const KITCHEN_TASK_STATUS_TEXT: Record<number, string> = { 0: '待执行', 1: '已完成' }

/** 清运任务分页（日期/片区/状态过滤） */
export const pageKitchenTasks = (q: PageQuery & { taskDate?: string; areaId?: string; status?: number }) =>
  request.get<PageResult<KitchenTaskRow>>('/admin/kitchen/tasks', q)

/** 管理员发布清运任务（片区/日期/袋数；同片区同日期重复发布后端拦截） */
export const publishKitchenTask = (data: { areaId: string; taskDate: string; totalBags: number }) =>
  request.post<KitchenTaskRow>('/admin/kitchen/tasks/publish', data)

/** 清运任务改派车辆/环卫员（仅待执行，限本片区环卫员） */
export const reassignKitchenTask = (id: string, vehicleId?: string, staffId?: string) =>
  request.put<KitchenTaskRow>(
    `/admin/kitchen/tasks/${id}/reassign?${[vehicleId && `vehicleId=${vehicleId}`, staffId && `staffId=${staffId}`]
      .filter(Boolean)
      .join('&')}`,
  )

/** 清运车辆列表（含停用，供配置页/改派下拉） */
export interface KitchenVehicle {
  id: string
  vehicleNo: string
  areaId: string | null
  /** 容量（袋） */
  capacity: number | null
  /** 0停用 1可用 */
  status: number
}

export const listKitchenVehicles = () => request.get<KitchenVehicle[]>('/admin/kitchen/vehicles')

// ---------------- 工作台摘要（G6，AdminDashboardController；大屏取 /admin/dashboard/overview） ----------------

export interface DashboardSummary {
  todayOrders: number
  todayWeight: number
  todayCarbon: number
  totalUsers: number
  weekTrend: { date: string; orders: number; weight: number; carbon: number }[]
  categoryRatio: { name: string; value: number }[]
  areaRank: { name: string; weight: number }[]
}

export const getDashboardSummary = () => request.get<DashboardSummary>('/admin/dashboard/summary')

// ---------------- 维修工单（C1/C2，AdminRepairController） ----------------

/** 维修状态机：0待接单 1已接单 2检修中 3已完成 4已取消 5已转回收 */
export const REPAIR_STATUS_TEXT: Record<number, string> = {
  0: '待接单', 1: '已接单', 2: '检修中', 3: '已完成', 4: '已取消', 5: '已转回收',
}
/** 可修复性：0未判定 1可修复 2不可修复 */
export const REPAIRABLE_TEXT: Record<number, string> = { 0: '未判定', 1: '可修复', 2: '不可修复' }

export interface RepairOrder {
  id: string
  orderNo: string
  userId: string
  staffId: string | null
  areaId: string
  faultType: string
  faultDesc: string
  address: string
  appointStart: string
  appointEnd: string
  oldForNew: 0 | 1
  gasDelivery: 0 | 1
  /** F2 预诊原始 JSON（AI 降级为空） */
  diagnoseJson: string | null
  repairable: number
  recycleRef: string | null
  status: number
  finishTime: string | null
  createTime: string
}

/** F2 预诊结构（RepairDiagnoseContract.Response，Sprint 0 冻结） */
export interface RepairDiagnose {
  possibleFaults: { fault: string; confidence: number }[]
  suggestParts: string[]
  repairableProbability: number
  suggestion: 'REPAIR' | 'RECYCLE'
}

/** 工单详情（RepairOrderVO：含预诊解析、配件明细） */
export interface RepairOrderDetail {
  id: string
  orderNo: string
  userId: string
  staffId: string | null
  areaId: string
  faultType: string
  faultDesc: string
  address: string
  appointStart: string
  appointEnd: string
  oldForNew: 0 | 1
  gasDelivery: 0 | 1
  diagnose: RepairDiagnose | null
  repairable: number
  scienceNote: string | null
  recycleRef: string | null
  status: number
  statusDesc: string
  cancelReason: string | null
  finishTime: string | null
  createTime: string
  parts: { partName: string; partModel: string; quantity: number; price: number }[]
}

/** 维修-回收双单溯源（RepairTraceVO，C2 归档） */
export interface RepairTrace {
  repairOrderId: string
  repairOrderNo: string
  repairStatus: number
  repairStatusDesc: string
  repairable: number
  faultType: string
  repairCreateTime: string
  repairFinishTime: string | null
  recycleOrderId: string | null
  recycleOrderNo: string | null
  recycleStatus: number | null
  recycleCreateTime: string | null
  /** 双向关联校验：recycle.repair_ref 是否指回本维修单 */
  bidirectional: boolean | null
}

export const pageRepairOrders = (q: PageQuery) => request.get<PageResult<RepairOrder>>('/admin/repair/orders', q)
export const getRepairOrder = (id: string) => request.get<RepairOrderDetail>(`/admin/repair/orders/${id}`)
export const getRepairTrace = (id: string) => request.get<RepairTrace>(`/admin/repair/orders/${id}/trace`)

// ---------------- 积分商城（C3，AdminMallController） ----------------

export interface MallProduct {
  id: string
  productName: string
  coverUrl: string | null
  pointsPrice: number
  stock: number
  status: 0 | 1
  description: string | null
  createTime: string
}

export interface MallProductSave {
  id?: string
  productName: string
  coverUrl?: string
  pointsPrice: number
  stock?: number
  status?: 0 | 1
  description?: string
}

/** 兑换单状态：0已兑换 1已发货 2已完成 3已取消 */
export const EXCHANGE_STATUS_TEXT: Record<number, string> = {
  0: '已兑换', 1: '已发货', 2: '已完成', 3: '已取消',
}

export interface MallExchange {
  id: string
  exchangeNo: string
  productId: string
  productName: string
  pointsCost: number
  quantity: number
  carbonLogId: string | null
  status: number
  statusDesc: string
  address: string
  createTime: string
}

export const pageMallProducts = (q: PageQuery) => request.get<PageResult<MallProduct>>('/admin/mall/products', q)
export const saveMallProduct = (data: MallProductSave) => request.post<MallProduct>('/admin/mall/products', data)
export const changeMallProductStatus = (id: string, onSale: boolean) =>
  request.put<void>(`/admin/mall/products/${id}/status?onSale=${onSale}`)
export const adjustMallStock = (id: string, delta: number) =>
  request.put<void>(`/admin/mall/products/${id}/stock?delta=${delta}`)
export const pageMallExchanges = (q: PageQuery) => request.get<PageResult<MallExchange>>('/admin/mall/exchanges', q)
export const shipExchange = (id: string) => request.put<void>(`/admin/mall/exchanges/${id}/ship`)
export const completeExchange = (id: string) => request.put<void>(`/admin/mall/exchanges/${id}/complete`)

// ---------------- 订单售后（C4，AdminComplaintController） ----------------

/** 售后类型：1投诉 2退款 3追责 4补发积分 5纠纷仲裁 */
export const COMPLAINT_TYPE_TEXT: Record<number, string> = {
  1: '投诉', 2: '退款', 3: '追责', 4: '补发积分', 5: '纠纷仲裁',
}
/** 售后状态：0待处理 1已处理 2已驳回 */
export const COMPLAINT_STATUS_TEXT: Record<number, string> = { 0: '待处理', 1: '已处理', 2: '已驳回' }

export interface Complaint {
  id: string
  orderType: 'RECYCLE' | 'KITCHEN' | 'REPAIR' | 'MALL'
  orderId: string
  userId: string
  type: number
  content: string
  status: number
  handleResult: string | null
  handlerId: string | null
  handleTime: string | null
  createTime: string
}

export interface ComplaintHandle {
  approved: boolean
  handleResult?: string
  /** 补发积分（受理时可选，走账本 REISSUE） */
  compensatePoints?: number
  /** 追责扣分（受理时可选，走账本 DEDUCT） */
  deductPoints?: number
}

export const pageComplaints = (q: PageQuery) => request.get<PageResult<Complaint>>('/admin/complaints', q)
export const handleComplaint = (id: string, data: ComplaintHandle) =>
  request.put<Complaint>(`/admin/complaints/${id}/handle`, data)

// ---------------- 风控中心（C4，AdminRiskController） ----------------

/** 告警状态：0待复核 1通过(解冻发放) 2驳回(违规扣除) */
export const RISK_STATUS_TEXT: Record<number, string> = { 0: '待复核', 1: '已通过', 2: '已驳回' }

export interface RiskAlert {
  id: string
  bizType: string
  bizId: string
  userId: string
  areaId: string
  reason: string
  frozenPoints: number
  freezeLogId: string | null
  status: number
  createTime: string
}

export interface BrushRisk {
  userId: string
  orderCount: number
  totalPoints: number
}

export const pageRiskAlerts = (q: PageQuery) => request.get<PageResult<RiskAlert>>('/admin/risk/alerts', q)
export const reviewRiskAlert = (id: string, pass: boolean) =>
  request.put<RiskAlert>(`/admin/risk/alerts/${id}/review?pass=${pass}`)
export const brushCheck = (hours: number, threshold: number) =>
  request.get<BrushRisk[]>('/admin/risk/brush-check', { hours, threshold })

// ---------------- 排班运维（C4，AdminOpsController） ----------------

export const SHIFT_TEXT: Record<number, string> = { 1: '白班', 2: '晚班' }
export const SCHEDULE_BIZ_TEXT: Record<number, string> = { 0: '正常', 1: '节假日', 2: '跨片区支援' }
export const LEAVE_STATUS_TEXT: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已驳回' }
export const MAINT_TARGET_TEXT: Record<number, string> = { 1: '清运车辆', 2: '设备' }

export interface StaffSchedule {
  id: string
  staffId: string
  areaId: string
  workDate: string
  shiftType: number
  bizType: number
  supportAreaId: string | null
  remark: string | null
}

export interface ScheduleSave {
  /** 雪花 ID 字符串透传（Number() 丢精度会查无此员工） */
  staffId: string
  workDate: string
  shiftType: number
  bizType: number
  supportAreaId?: string
  remark?: string
}

export interface LeaveRequest {
  id: string
  staffId: string
  startDate: string
  endDate: string
  reason: string
  status: number
  auditBy: string | null
  auditRemark: string | null
  auditTime: string | null
  createTime: string
}

export interface DeviceMaintenance {
  id: string
  targetType: number
  targetNo: string
  content: string
  cost: number | null
  maintDate: string
  operatorId: string
  createTime: string
}

export interface MaintenanceSave {
  targetType: number
  targetNo: string
  content: string
  cost?: number
  maintDate: string
}

export const pageSchedules = (q: PageQuery) => request.get<PageResult<StaffSchedule>>('/admin/ops/schedules', q)
export const createSchedule = (data: ScheduleSave) => request.post<StaffSchedule>('/admin/ops/schedules', data)
export const deleteSchedule = (id: string) => request.delete<void>(`/admin/ops/schedules/${id}`)
export const pageLeaves = (q: PageQuery) => request.get<PageResult<LeaveRequest>>('/admin/ops/leaves', q)
export const auditLeave = (id: string, approved: boolean, auditRemark?: string) =>
  request.put<LeaveRequest>(`/admin/ops/leaves/${id}/audit`, { approved, auditRemark })
export const pageMaintenances = (q: PageQuery) =>
  request.get<PageResult<DeviceMaintenance>>('/admin/ops/maintenances', q)
export const registerMaintenance = (data: MaintenanceSave) =>
  request.post<DeviceMaintenance>('/admin/ops/maintenances', data)

// ---------------- 数据大屏（C5，AdminDashboardController） ----------------

export interface AreaRank {
  areaId: string
  areaName: string
  /** 片区中心经纬度（驾驶舱地图标注；V53 起 biz_area 维护） */
  lng: number | null
  lat: number | null
  recycleOrders: number
  recycleWeight: number
  kitchenBags: number
  /** 碳减排量 kgCO2（RECYCLE 账本流水口径，流水为空时后端按碳系数折算） */
  carbonReduced: number
}

export interface MonthlyReport {
  month: string
  recycleOrders: number
  recycleWeight: number
  kitchenBags: number
  repairOrders: number
  carbonReduced: number
}

/** 大屏总览（DashboardOverviewVO，后端 1s 缓存快照） */
export interface DashboardOverview {
  recycleTotalOrders: number
  recycleFinishedOrders: number
  recycleTotalWeight: number
  recycleTodayOrders: number
  kitchenTotalBags: number
  kitchenTodayBags: number
  repairTotalOrders: number
  repairFinishedOrders: number
  repairTransferredOrders: number
  carbonReducedTotal: number
  areaRanking: AreaRank[]
  monthlyReport: MonthlyReport[]
  generatedAt: string
}

export const getDashboardOverview = () =>
  request.get<DashboardOverview>('/admin/dashboard/overview', undefined, { silent: true, allowDuplicate: true })
/** 台账/月报导出地址（EasyExcel 文件流，需带 Authorization 头下载） */
export const DASHBOARD_EXPORT_URL = `${import.meta.env.VITE_API_BASE}/admin/dashboard/export`

// ---------------- Agent 驾驶舱（C5/F8，AdminAgentController） ----------------

/** 风险等级：1低(自动执行) 2中(单人审批) 3高(双人复核) */
export const RISK_LEVEL_TEXT: Record<number, string> = { 1: '低', 2: '中', 3: '高' }
/** 决策状态：0待审批 1已通过 2已驳回 3已执行 4执行失败 */
export const DECISION_STATUS_TEXT: Record<number, string> = {
  0: '待审批', 1: '已通过', 2: '已驳回', 3: '已执行', 4: '执行失败',
}

export interface AgentDecision {
  id: string
  agentId: string
  actionType: string
  targetUri: string
  paramsJson: string
  evidencesJson: string
  riskLevel: number
  status: number
  decisionHash: string
  prevHash: string | null
  firstApprover: string | null
  firstApproveTime: string | null
  secondApprover: string | null
  secondApproveTime: string | null
  rejectReason: string | null
  executeResult: string | null
  executeTime: string | null
  createTime: string
}

/** 驾驶舱聚合（AgentCockpitVO：吞吐/待审风险分布/哈希链健康） */
export interface AgentCockpit {
  agents: { agentId: string; total: number; pending: number; executed: number; failed: number; todayCount: number }[]
  pendingByRisk: Record<number, number>
  chainValid: boolean
  chainLength: number
  lastDecisionHash: string | null
}

export const pageAgentDecisions = (q: PageQuery) =>
  request.get<PageResult<AgentDecision>>('/admin/agent/decisions', q)
export const getAgentDecision = (id: string) => request.get<AgentDecision>(`/admin/agent/decisions/${id}`)
export const getAgentCockpit = () => request.get<AgentCockpit>('/admin/agent/cockpit')
/** HITL 审批（12.2 冻结契约：单接口 pass/reason，驳回必填原因） */
export const approveDecision = (id: string, pass: boolean, reason?: string) =>
  request.post<AgentDecision>(`/agent/decision/${id}/approve`, { pass, reason })

// ---------------- 员工片区（GapFill V9，AdminStaffController / AdminAreaController） ----------------

/** 员工行（BizStaff，Long 序列化为 string） */
export interface StaffRow {
  id: string
  userId: string
  staffName: string
  phone: string
  /** 1回收员 2环卫员 3技术员 */
  staffType: number
  areaId: string
  /** 0待审核 1已通过 2已驳回 */
  auditStatus: number
  /** 0禁用 1正常 */
  status: number
  /** 申请备注（用户自助申请时填写） */
  applyReason?: string | null
  /** 审核备注/驳回原因（管理员审核时填写） */
  auditRemark?: string | null
  createTime: string
}

/** 员工新增（StaffCreateDTO：新增即进入待审核；areaId 雪花 ID 以字符串传输防精度丢失） */
export interface StaffCreate {
  userId: number
  staffName: string
  phone?: string
  staffType: number
  areaId: string
}

/** 片区行（BizArea，Long 序列化为 string） */
export interface AreaRow {
  id: string
  areaName: string
  address: string | null
  /** 片区中心经纬度（驾驶舱地图标注；V53 起 biz_area 维护） */
  lng: number | null
  lat: number | null
  cleanThreshold: number | null
  householdCount: number | null
  /** 0停用 1启用 */
  status: number
  createTime: string
}

/** 片区新增/修改（AreaSaveDTO） */
export interface AreaSave {
  id?: string
  areaName: string
  address?: string
  lng?: number
  lat?: number
  cleanThreshold?: number
  householdCount?: number
  status?: number
}

export const STAFF_TYPE_TEXT: Record<number, string> = { 1: '回收员', 2: '环卫员', 3: '技术员' }
export const STAFF_AUDIT_TEXT: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已驳回' }
export const AREA_STATUS_TEXT: Record<number, string> = { 0: '停用', 1: '启用' }

export const pageStaff = (q: PageQuery) => request.get<PageResult<StaffRow>>('/admin/staff', q)
export const createStaff = (data: StaffCreate) => request.post<StaffRow>('/admin/staff', data)
export const auditStaff = (id: string, approved: boolean, remark?: string) =>
  request.put<void>(`/admin/staff/${id}/audit`, { approved, remark })
export const changeStaffStatus = (id: string, enable: boolean) =>
  request.put<void>(`/admin/staff/${id}/status?enable=${enable}`)
export const transferStaffArea = (id: string, areaId: string) =>
  request.put<void>(`/admin/staff/${id}/area?areaId=${areaId}`)

// ---------------- 垃圾站点管理（V43） ----------------

/** 站点类型 */
export type StationType = 'RECYCLE' | 'KITCHEN' | 'COLLECT'

/** 站点行（BizStation，Long 序列化为 string） */
export interface StationRow {
  id: string
  name: string
  stationType: StationType
  lng: number
  lat: number
  address: string | null
  openHours: string | null
  /** 1 开放 0 停用 */
  status: number
  createTime: string
}

export interface StationSave {
  id?: string
  name: string
  stationType: StationType
  lng: number
  lat: number
  address?: string
  openHours?: string
  status?: number
}

export const pageStations = (q: PageQuery & { keyword?: string; stationType?: StationType }) =>
  request.get<PageResult<StationRow>>(`/admin/stations`, q)
export const saveStation = (data: StationSave) =>
  request.post<StationRow>(`/admin/stations`, data)
export const updateStation = (id: string, data: StationSave) =>
  request.put<StationRow>(`/admin/stations/${id}`, data)
export const removeStation = (id: string) =>
  request.delete<void>(`/admin/stations/${id}`)
export const pageAreas = (q: PageQuery) => request.get<PageResult<AreaRow>>('/admin/areas', q)
export const saveArea = (data: AreaSave, id?: string) =>
  id ? request.put<AreaRow>(`/admin/areas/${id}`, data) : request.post<AreaRow>('/admin/areas', data)
export const removeArea = (id: string) => request.delete<void>(`/admin/areas/${id}`)

// ---------------- 跳蚤市场运营（C16，FleaOpsController /api/ops/flea/**） ----------------

/** 闲置商品状态：0下架 1在售 2已售出 3违规下架 */
export const FLEA_ITEM_STATUS_TEXT: Record<number, string> = {
  0: '已下架', 1: '在售', 2: '已售出', 3: '违规下架',
}

export interface FleaItem {
  id: string
  userId: string
  sellerNickname: string
  sellerAvatar: string | null
  title: string
  description: string
  /** REUSABLE/TOY/BOOK/ELECTRONIC/HOME/OTHER */
  category: string
  price: number
  imageUrl: string | null
  status: number
  statusDesc: string
  soldCount: number
  createTime: string
}

export const pageFleaItems = (q: PageQuery) => request.get<PageResult<FleaItem>>('/ops/flea/items', q)
export const forbiddenFleaItem = (id: string) => request.put<void>(`/ops/flea/items/${id}/forbidden`)

/** 发布实物商品入参（FleaItemCreateDTO：卖家=当前管理员账号，imageUrl 可选封面） */
export interface FleaItemCreate {
  title: string
  description: string
  /** REUSABLE/TOY/BOOK/ELECTRONIC/HOME/OTHER */
  category: string
  price: number
  imageUrl?: string
}

/** 管理员发布实物（FleaOpsController POST /items：C2C 积分托管交易，封面走 /file/upload?biz=flea） */
export const createFleaItem = (data: FleaItemCreate) => request.post<FleaItem>('/ops/flea/items', data)

// ---------------- 徽章管理（积分市场合并，AdminBadgeController /api/admin/badge/**） ----------------

/** 徽章行（BadgeVO，Long 序列化为 string；owned 仅用户端登录态回填） */
export interface BadgeRow {
  id: string
  code: string
  name: string
  description: string
  price: number
  iconUrl: string | null
  sort: number
  /** 0下架 1上架 */
  status: 0 | 1
  owned?: boolean
}

/** 徽章保存入参（BadgeSaveDTO：id 空=新增，非空=编辑；iconUrl 走 /file/upload?biz=badge） */
export interface BadgeSave {
  id?: string
  code: string
  name: string
  description: string
  price: number
  iconUrl?: string
  sort?: number
  status: 0 | 1
}

export const pageBadges = (q: PageQuery & { keyword?: string; status?: 0 | 1 }) =>
  request.get<PageResult<BadgeRow>>('/admin/badge/list', q)
export const saveBadge = (data: BadgeSave) => request.post<BadgeRow>('/admin/badge/save', data)
export const changeBadgeStatus = (id: string, onSale: boolean) =>
  request.put<void>(`/admin/badge/${id}/status?onSale=${onSale}`)
