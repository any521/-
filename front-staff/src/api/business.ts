// ============================================================
// 员工端业务 API（对接 B 回收/垃圾投放接单完成、C 维修报告/转回收）
// 已按后端真实契约全量适配（VO 差异在本层消化，页面零改结构）：
//   - 今日订单按角色分流三端点：回收员 /staff/today-orders（RecycleOrderVO，含客户姓名/电话）、
//     环卫员 /staff/kitchen/tasks（KitchenTask，含真实片区名 areaName）、技术员 /staff/repair/orders(+/pending)（PageVO<RepairOrderVO>，含客户姓名/电话）
//   - 完成登记 PUT /staff/orders/{id}/finish（items.category + transferTo 必填，出参 RecycleFinishVO）
//   - 维修报告 PUT /staff/repair/orders/{id}/report（repairable 1/2 + parts 并入报告，无独立配件端点 → 本地草稿合并提交）
//   - 转回收 POST /staff/repair/orders/{id}/transfer-recycle（无请求体，出参 RepairTransferVO）
//   - 异常上报 POST /staff/exception（orderId 必填 + reason，后端返 Void → 本地生成回执号）
// 注意：后端雪花 ID 序列化为字符串，本层 id 一律 string 防精度丢失
// ============================================================
import { OrderStatus } from '@shared/http/types'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import type { PageQuery, PageResult } from '@shared/http/types'
import { request } from './request'

// ---------------- 接单台（B/C 派单结果，按角色差异化） ----------------
/** 员工侧订单（回收/厨余清运/维修三类，按角色下发，后端三实体统一适配） */
export interface StaffOrder {
  /** 雪花 ID（字符串防精度丢失） */
  id: string
  orderNo: string
  bizType: 'RECYCLE' | 'KITCHEN' | 'REPAIR'
  status: OrderStatus
  title: string
  customerName: string
  customerPhone: string
  address: string
  /** 所属片区 ID（雪花字符串；换片区/跨片区接单判定用） */
  areaId: string
  /** 所属片区名（后端 VO 填充；旧数据兜底为片区 ID） */
  areaName: string
  /** 订单经纬度（下单定位；旧数据为空，导航前 geocode 兜底） */
  lng?: number | null
  lat?: number | null
  appointTime: string
  /** 预估重量 kg（回收/清运） */
  estimateWeight: number
  /** F2 AI 预诊（维修单，低置信为空） */
  aiPrediagnosis: string
  remark: string
  /** 用户承诺报酬积分（无报酬订单为 0） */
  servicePoints?: number
  createTime: string
}

/** 后端分页壳（PageVO：total/pages/list，勿写成 records） */
interface PageVO<T> {
  total: number
  pages?: number
  list: T[]
}

/** 后端 RecycleOrderVO 行（雪花 Long 序列化为字符串，含客户/员工信息） */
interface RecycleOrderRow {
  id: string
  orderNo: string
  category: string
  estWeight: number | null
  weight: number | null
  quantity: number | null
  /** 客户姓名（sys_user.nickname/username） */
  customerName?: string | null
  /** 客户电话（sys_user.phone，未填写为空） */
  customerPhone?: string | null
  address: string
  /** 所属片区 ID（雪花字符串） */
  areaId: string
  /** 所属片区名（AreaService 按省/市/县/镇/村生成） */
  areaName?: string | null
  /** 下单定位坐标（旧数据为空） */
  lng?: number | null
  lat?: number | null
  appointTime: string
  /** 0待接单 1已接单 2进行中 3完成 4取消 5异常 */
  status: number
  amount: number | null
  transferTo: string | null
  exceptionReason: string | null
  /** 用户承诺报酬积分（>=1 时完成结算转账给员工；Long 序列化为字符串） */
  servicePoints?: string | number | null
  createTime: string
}

/** 后端 KitchenTask 实体行（清运任务，0待执行 1已完成，含真实片区名） */
interface KitchenTaskRow {
  id: string
  areaId: string
  /** 真实片区名（后端 fillAreaName 填充） */
  areaName?: string | null
  taskDate: string
  totalBags: number
  status: number
  totalWeight: number | null
  vehicleNo: string | null
  destination: string | null
  createTime: string
}

/** 后端 RepairOrderVO 行（0待接单 1已接单 2检修中 3已完成 4已取消 5已转回收，含客户信息） */
interface RepairOrderRow {
  id: string
  orderNo: string
  faultType: string
  faultDesc: string
  /** 客户姓名（sys_user.nickname/username） */
  customerName?: string | null
  /** 客户电话（sys_user.phone，未填写为空） */
  customerPhone?: string | null
  address: string
  /** 所属片区 ID（雪花字符串） */
  areaId: string
  /** 所属片区名（AreaService 按省/市/县/镇/村生成） */
  areaName?: string | null
  /** 报修定位坐标（旧数据为空） */
  lng?: number | null
  lat?: number | null
  appointStart: string
  appointEnd: string
  status: number
  /** F2 预诊结果 JSON（AI 降级为空） */
  diagnoseJson: string | null
  /** 用户承诺报酬积分（>=1 时完成结算转账给员工；Long 序列化为字符串） */
  servicePoints?: string | number | null
  createTime: string
}

/** F2 预诊契约响应（RepairOrderVO.diagnose / diagnoseJson 反序列化） */
interface DiagnoseResp {
  possibleFaults?: { fault: string; confidence: number }[]
  suggestion?: string
}

const categoryName = (code: string) =>
  RECYCLE_CATEGORIES.find((c) => c.code === code)?.name ?? code

const fmtTime = (t: string | null | undefined) => (t ?? '').replace('T', ' ').slice(0, 16)

/** 预诊 JSON/对象 → 展示文案（低置信/降级为空） */
function diagnoseText(d: DiagnoseResp | string | null | undefined): string {
  if (!d) return ''
  let obj: DiagnoseResp
  if (typeof d === 'string') {
    try { obj = JSON.parse(d) as DiagnoseResp } catch { return '' }
  } else obj = d
  if (!obj.suggestion) return ''
  const top = obj.possibleFaults?.[0]
  return `${top ? `疑似${top.fault}（置信 ${top.confidence}）· ` : ''}${obj.suggestion}`
}

function fromRecycle(row: RecycleOrderRow): StaffOrder {
  return {
    id: row.id,
    orderNo: row.orderNo,
    bizType: 'RECYCLE',
    status: row.status as OrderStatus,
    title: `上门回收 · ${categoryName(row.category)}${row.estWeight ? ` 约${row.estWeight}kg` : ''}`,
    // 后端 VO 已带客户姓名/电话；旧数据/降级时兜底
    customerName: row.customerName || '预约客户',
    customerPhone: row.customerPhone || '',
    address: row.address,
    areaId: row.areaId ?? '',
    areaName: row.areaName || `片区${row.areaId ?? ''}`,
    lng: row.lng ?? null,
    lat: row.lat ?? null,
    appointTime: fmtTime(row.appointTime),
    estimateWeight: Number(row.estWeight ?? 0),
    aiPrediagnosis: '',
    remark: row.exceptionReason ?? '',
    servicePoints: row.servicePoints != null ? Number(row.servicePoints) : 0,
    createTime: fmtTime(row.createTime),
  }
}

function fromKitchen(row: KitchenTaskRow): StaffOrder {
  return {
    id: row.id,
    orderNo: `KC${row.id}`,
    bizType: 'KITCHEN',
    // 清运任务系统聚合直派（无抢单环节）：0待执行→已接单 1已完成→已完成
    status: row.status === 1 ? OrderStatus.FINISHED : OrderStatus.ACCEPTED,
    title: `厨余清运 · 集中点 ${row.totalBags} 袋`,
    // 后端已返回真实片区名；旧数据兜底为片区 ID
    customerName: row.areaName || '集中投放点',
    customerPhone: '',
    address: row.areaName ? `${row.areaName} 集中投放点` : `片区${row.areaId} 集中投放点`,
    areaId: row.areaId ?? '',
    areaName: row.areaName || `片区${row.areaId ?? ''}`,
    appointTime: row.taskDate,
    estimateWeight: Number(row.totalWeight ?? 0),
    aiPrediagnosis: '',
    remark: row.destination ? `去向：${row.destination}` : '',
    createTime: fmtTime(row.createTime),
  }
}

function fromRepair(row: RepairOrderRow): StaffOrder {
  return {
    id: row.id,
    orderNo: row.orderNo,
    bizType: 'REPAIR',
    // 维修态映射：5已转回收对员工侧视为完结
    status: (row.status === 5 ? OrderStatus.FINISHED : row.status) as OrderStatus,
    title: `上门维修 · ${row.faultType || '家电检修'}`,
    // 后端 VO 已带客户姓名/电话；旧数据/降级时兜底
    customerName: row.customerName || '报修客户',
    customerPhone: row.customerPhone || '',
    address: row.address,
    areaId: row.areaId ?? '',
    areaName: row.areaName || `片区${row.areaId ?? ''}`,
    lng: row.lng ?? null,
    lat: row.lat ?? null,
    appointTime: `${fmtTime(row.appointStart)}${row.appointEnd ? ` ~ ${fmtTime(row.appointEnd).slice(11)}` : ''}`,
    estimateWeight: 0,
    aiPrediagnosis: diagnoseText(row.diagnoseJson),
    remark: row.faultDesc ?? '',
    servicePoints: row.servicePoints != null ? Number(row.servicePoints) : 0,
    createTime: fmtTime(row.createTime),
  }
}

/** 列表缓存：id → 订单（详情/接单/完成按 bizType 路由到对应后端端点） */
const orderCache = new Map<string, StaffOrder>()

/** 今日订单（全员工可见：三类任务合并全量，与接单台同源；接单/操作仍按身份限权） */
export async function getTodayOrders(status?: OrderStatus): Promise<StaffOrder[]> {
  // 全量拉取三端点（回收/维修分页拉全 + 清运任务全量），不再按角色分流——
  // 任何在岗员工均可浏览全部任务与详情，身份不符的接单按钮/后端校验负责拦截
  const [recycles, repairs, kitchens] = await Promise.all([
    fetchAllPages<RecycleOrderRow>('/staff/orders/page', fromRecycle),
    fetchAllPages<RepairOrderRow>('/staff/repair/orders/all', fromRepair),
    request
      .get<KitchenTaskRow[]>('/staff/kitchen/tasks')
      .then((rows) => rows.map(fromKitchen))
      .catch(() => [] as StaffOrder[]),
  ])
  const list = [...recycles, ...repairs, ...kitchens].sort(
    (a, b) => (b.createTime > a.createTime ? 1 : -1),
  )
  list.forEach((o) => orderCache.set(o.id, o))
  return status === undefined ? list : list.filter((o) => o.status === status)
}

/** 分页拉全（回收/维修全量端点共用；total 耗尽或达到上限即止） */
async function fetchAllPages<T>(
  url: string,
  mapper: (row: T) => StaffOrder,
  pageSize = 50,
  maxPages = 20,
): Promise<StaffOrder[]> {
  const list: StaffOrder[] = []
  for (let pageNum = 1; pageNum <= maxPages; pageNum++) {
    const page = await request.get<PageVO<T>>(url, { pageNum, pageSize })
    const rows = page.list ?? []
    list.push(...rows.map(mapper))
    if (list.length >= page.total || rows.length < pageSize) break
  }
  return list
}

/** 全量订单（跨片区 + 全状态：回收/维修分页拉全 + 清运任务全量，三类合并；接单仍限本片区/对应工种） */
export async function getAllOrders(status?: OrderStatus): Promise<StaffOrder[]> {
  // 三端点并发拉取：回收单全量、维修单全量（后端已对在岗员工放开查看）、清运任务全量
  const [recycles, repairs, kitchens] = await Promise.all([
    fetchAllPages<RecycleOrderRow>('/staff/orders/page', fromRecycle),
    fetchAllPages<RepairOrderRow>('/staff/repair/orders/all', fromRepair),
    request
      .get<KitchenTaskRow[]>('/staff/kitchen/tasks')
      .then((rows) => rows.map(fromKitchen))
      .catch(() => [] as StaffOrder[]),
  ])
  const list = [...recycles, ...repairs, ...kitchens].sort(
    (a, b) => (b.createTime > a.createTime ? 1 : -1),
  )
  list.forEach((o) => orderCache.set(o.id, o))
  return status === undefined ? list : list.filter((o) => o.status === status)
}

export async function getStaffOrderDetail(id: string): Promise<StaffOrder> {
  const cached = orderCache.get(id)
  if (cached?.bizType === 'KITCHEN') return cached // 后端无清运任务详情端点
  if (cached?.bizType === 'REPAIR') {
    const vo = await request.get<RepairOrderRow & { diagnose?: DiagnoseResp | null }>(
      `/staff/repair/orders/${id}`,
    )
    return { ...fromRepair(vo), aiPrediagnosis: diagnoseText(vo.diagnose ?? vo.diagnoseJson) }
  }
  const vo = await request.get<RecycleOrderRow>(`/staff/orders/${id}`)
  return fromRecycle(vo)
}

/** 接单端点按业务类型路由（离线队列重放也用它拼 URL） */
export const acceptUrlOf = (order: StaffOrder) =>
  order.bizType === 'REPAIR' ? `/staff/repair/orders/${order.id}/accept` : `/staff/orders/${order.id}/accept`

/** 接单（后端 PUT；弱网时由 useOffline 入队重放） */
export const acceptOrder = (order: StaffOrder) => request.put(acceptUrlOf(order))

/**
 * 上门开始（1已接单→2进行中/检修中）：登记/报告提交前自动补调，
 * 避免员工接单后直接核验登记被后端 finish 状态校验拒绝（finish 要求进行中）。
 * 清运任务无开始环节、订单已是进行中/其他状态时直接跳过。
 */
export async function ensureStarted(order: StaffOrder) {
  if (order.bizType === 'KITCHEN' || order.status !== OrderStatus.ACCEPTED) return
  await request.put(
    order.bizType === 'REPAIR' ? `/staff/repair/orders/${order.id}/start` : `/staff/orders/${order.id}/start`,
  )
}

// ---------------- 片区（自助换片区 / 列表缓存） ----------------

/** 片区行（biz_area：雪花 Long 序列化为字符串，status 1 启用） */
export interface AreaRow {
  id: string
  areaName: string
  status: number
}

/** 全部启用片区（GET /api/staff/areas，供换片区下拉） */
export const listAreas = () => request.get<AreaRow[]>('/staff/areas')

/** 员工自助换片区（PUT /api/staff/me/area，目标片区必须启用） */
export const changeMyArea = (areaId: string) => request.put<void>('/staff/me/area', { areaId })

/** 上门核验重量录入 + 物资登记完成（回收员/环卫员） */
export interface FinishOrderForm {
  /** 各品类实收：code + 核验重量 kg（厨余则单条 kitchen + 总重） */
  items: { code: string; name: string; weight: number }[]
  /** 转运去向（回收单溯源，后端必填） */
  transferTo?: string
  /** 清运车号（厨余归档） */
  vehicleNo?: string
  /** 清运终点（厨余溯源台账） */
  destination?: string
  /** 现场照片（登记凭证，后端暂不收，联调期接 MinIO 直传） */
  photoBase64?: string
  remark?: string
}

export interface FinishOrderResult {
  /** 应付金额（回收） */
  amount: number
  /** 用户所得积分（厨余为当日上报统一发分，员工侧无返回） */
  points: number
  /** 折算碳减排 kgCO₂（入碳账本） */
  carbon: number
  /** 碳流水 ID（/carbon/verify 可验证哈希链） */
  carbonLogId?: string
  // onsite02 独占分区：结算结果字段（禁止其他批次修改本文件）
  /** 报酬结算状态：0未结算 1已结算 2异常转人工（无报酬订单为 undefined/0） */
  settleStatus?: number
  /** 本次入账报酬积分 */
  settlePoints?: number
  /** 结算结果文案（后端生成，无报酬为 null） */
  settleResult?: string
  /** 员工本次回收积分（与用户相同，同步入账） */
  staffPoints?: number
  /** 员工入账后碳账户余额 */
  staffBalanceAfter?: number
}

/** 后端完成登记出参（RecycleFinishVO） */
interface RecycleFinishVO {
  orderId: string
  orderNo: string
  totalWeight: number
  amount: number
  points: number
  balanceAfter: number
  carbonReduced: number
  carbonLogId: string
  // onsite02 独占分区：报酬结算字段（Long 序列化为字符串）
  settleStatus?: number | null
  settlePoints?: string | number | null
  settleResult?: string | null
  /** 员工本次回收积分（与用户相同，Long 序列化为字符串） */
  staffPoints?: string | number | null
  /** 员工入账后碳账户余额 */
  staffBalanceAfter?: string | number | null
}

export async function finishOrder(order: StaffOrder, form: FinishOrderForm): Promise<FinishOrderResult> {
  // 已接单但未点“上门开始”时先补 start（1→2），再完成登记（2→3）
  await ensureStarted(order)
  if (order.bizType === 'KITCHEN') {
    // 环卫员归档：总重/车号/终点 → 当日上报统一发分（员工侧不返回积分）
    await request.put<KitchenTaskRow>(`/staff/kitchen/tasks/${order.id}/finish`, {
      totalWeight: form.items[0]?.weight ?? 0,
      vehicleNo: form.vehicleNo || undefined,
      destination: form.destination || '厨余处置中心',
    })
    return { amount: 0, points: 0, carbon: 0 }
  }
  const vo = await request.put<RecycleFinishVO>(`/staff/orders/${order.id}/finish`, {
    items: form.items.map((it) => ({ category: it.code, weight: it.weight })),
    transferTo: form.transferTo || '片区分拣中心',
  })
  return {
    amount: Number(vo.amount),
    points: vo.points,
    carbon: Number(vo.carbonReduced),
    carbonLogId: vo.carbonLogId,
    // onsite02：报酬结算结果（无报酬订单后端返回 null/undefined，页面不展示）
    settleStatus: vo.settleStatus ?? undefined,
    settlePoints: vo.settlePoints != null ? Number(vo.settlePoints) : undefined,
    settleResult: vo.settleResult ?? undefined,
    // 员工同步获得的回收积分（与用户相同）
    staffPoints: vo.staffPoints != null ? Number(vo.staffPoints) : undefined,
    staffBalanceAfter: vo.staffBalanceAfter != null ? Number(vo.staffBalanceAfter) : undefined,
  }
}

// ---------------- 异常上报 ----------------
export interface AbnormalForm {
  /** 后端必填（订单转状态 5 异常）：通用无单异常暂无后端端点 */
  orderId: string
  /** 异常类型（字典）：联系不上/地址错误/货不符/设备故障/其他 */
  type: string
  desc: string
  photoBase64?: string
}

export async function reportAbnormal(form: AbnormalForm): Promise<{ ticketNo: string }> {
  // 后端契约：orderId + reason，返 Void；类型并入 reason，回执号本地生成
  await request.post('/staff/exception', {
    orderId: form.orderId,
    reason: `【${form.type}】${form.desc}`,
  })
  return { ticketNo: `EX${Date.now().toString().slice(-8)}` }
}

// ---------------- 智能路线导航（对接 StaffRouteController，联调期切调度中心 F4） ----------------
export interface RouteStop {
  orderId: string
  seq: number
  address: string
  customerName: string
  appointTime: string
  distanceKm: number
  /** 途经点状态：待上门/已完成 */
  done: boolean
}

export interface RoutePlan {
  totalKm: number
  totalMin: number
  /** 调度算法版本 */
  algorithm: string
  stops: RouteStop[]
}

export const getTodayRoute = () => request.get<RoutePlan>('/staff/route/today')

// ---------------- 维修报告 / 配件登记 / 转回收（C，已对齐后端真实契约） ----------------
export interface RepairReportForm {
  /** 故障描述（支持语音口述转写） */
  faultDesc: string
  /** 处理方案 */
  solution: string
  /** 维修费 元 */
  fee: number
  /** 是否修复成功；失败可走转回收（映射后端 repairable 1/2） */
  success: boolean
  /** 科普留痕（面向用户的用电安全/环保知识讲解摘要，可选） */
  scienceNote?: string
}

export interface RepairPart {
  name: string
  count: number
  /** 单价 元 */
  price: number
}

/** 后端无独立配件端点（parts 并入检修报告 DTO）：配件页先存本地草稿，随报告合并提交 */
const partsDraftKey = (orderId: string) => `df_staff_parts_draft_${orderId}`

export function saveRepairPartsDraft(orderId: string, parts: RepairPart[]) {
  localStorage.setItem(partsDraftKey(orderId), JSON.stringify(parts))
}

export function loadRepairPartsDraft(orderId: string): RepairPart[] {
  try {
    return JSON.parse(localStorage.getItem(partsDraftKey(orderId)) ?? '[]') as RepairPart[]
  } catch {
    return []
  }
}

/** 提交检修报告（PUT，parts 草稿合并；可修复→完结发分，不可修复→待转回收） */
export async function submitRepairReport(order: StaffOrder, form: RepairReportForm) {
  // 已接单但未点“开始检修”时先补 start（1→2），再提交报告（要求检修中）
  await ensureStarted(order)
  const parts = loadRepairPartsDraft(order.id)
  await request.put(`/staff/repair/orders/${order.id}/report`, {
    repairable: form.success ? 1 : 2,
    reportContent: `故障：${form.faultDesc}；处理：${form.solution}${form.fee ? `；维修费 ${form.fee} 元` : ''}`,
    scienceNote: form.scienceNote?.trim() ?? '',
    parts: parts.map((p) => ({ partName: p.name, quantity: p.count, price: p.price })),
  })
  localStorage.removeItem(partsDraftKey(order.id))
}

/** 后端转回收联动出参（RepairTransferVO） */
interface RepairTransferVO {
  repairOrderNo: string
  recycleOrderId: string
  recycleOrderNo: string
}

/** 维修转回收联动（POST 无请求体：同事务生成回收单 + repair_ref 双向追溯） */
export async function transferToRecycle(orderId: string): Promise<{ recycleOrderNo: string }> {
  const vo = await request.post<RepairTransferVO>(`/staff/repair/orders/${orderId}/transfer-recycle`)
  return { recycleOrderNo: vo.recycleOrderNo }
}

// ---------------- 实时位置上报（GapFill B2：接单后每 30s 上报，后端 Redis 存 2h） ----------------
export interface StaffLocation {
  lng: number
  lat: number
}

export const reportStaffLocation = (loc: StaffLocation) => request.put('/staff/location', loc)

// ---------------- 绩效（对接 StaffPerformanceController） ----------------
export interface Performance {
  month: string
  /** 完成单数 */
  orders: number
  /** 核验总重 kg */
  weight: number
  /** 经手金额 元 */
  amount: number
  /** 带动碳减排 kgCO₂ */
  carbon: number
  /** 片区排名 */
  rank: number
  /** 好评率 % */
  praiseRate: number
  /** 近 7 日单数曲线 */
  days: { date: string; orders: number }[]
}

export const getPerformance = (month?: string) =>
  request.get<Performance>('/staff/performance', month ? { month } : {})

// ---------------- 我的排班 / 请假（对接 StaffOpsController /staff/ops，C4） ----------------

/** 排班行（biz_schedule：areaId/supportAreaId 雪花 Long 序列化为 string） */
export interface ScheduleItem {
  id: string
  staffId: string
  areaId: string
  workDate: string
  /** 1白班 2晚班 */
  shiftType: number
  /** 0正常 1节假日 2跨片区支援 */
  bizType: number
  supportAreaId: string | null
  remark: string | null
}

/** 请假行（biz_leave_request：0待审 1已批 2已驳） */
export interface LeaveRequestItem {
  id: string
  staffId: string
  startDate: string
  endDate: string
  reason: string
  status: 0 | 1 | 2
  auditBy: string | null
  auditRemark: string | null
  auditTime: string | null
  createTime: string
}

/** 请假申请入参（LeaveCreateDTO：startDate/endDate/reason，后端区间重叠拦截） */
export interface LeaveSubmitForm {
  startDate: string
  endDate: string
  reason: string
}

/** 我的排班（日期区间可选，返回 List） */
export const mySchedules = (dateFrom?: string, dateTo?: string) =>
  request.get<ScheduleItem[]>('/staff/ops/schedules', dateFrom || dateTo ? { dateFrom, dateTo } : {})

/** 提交请假（重叠拦截 → request 层统一 Toast） */
export const submitLeave = (data: LeaveSubmitForm) => request.post<LeaveRequestItem>('/staff/ops/leaves', data)

/** 我的请假（分页，status 可选过滤） */
export const pageMyLeaves = (q: PageQuery) => request.get<PageResult<LeaveRequestItem>>('/staff/ops/leaves', q)

// ---------------- 投放审核（B4：工作人员审核垃圾投放，通过即发分/驳回） ----------------

/** 待审核投放记录行（KitchenRecordVO：id 雪花 Long 序列化为 string） */
export interface KitchenAuditItem {
  id: string
  areaName: string
  userName: string
  bags: number
  photoUrl: string | null
  /** 审核文案（待审核/已通过/已复核/已驳回） */
  aiAuditResult: string
  /** 应得积分 = 袋数 × 每袋积分 */
  points: number
  createTime: string
}

/** 投放审核入参（pass true 通过发分 / false 驳回，remark 驳回原因透传用户） */
export interface KitchenAuditForm {
  pass: boolean
  remark?: string
}

/** 本片区待审核投放列表（GET /api/staff/kitchen/reports，@DataScope 片区隔离） */
export const listPendingKitchenReports = () => request.get<KitchenAuditItem[]>('/staff/kitchen/reports')

/** 投放审核（POST /api/staff/kitchen/reports/{id}/audit，通过即发分账本幂等；驳回不发分） */
export const auditKitchenReport = (id: string, form: KitchenAuditForm) =>
  request.post<void>(`/staff/kitchen/reports/${id}/audit`, form)
