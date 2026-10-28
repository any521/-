// ============================================================
// 用户端业务 API（对接 A 认证 / B 回收垃圾投放积分 / C 维修商城 / E AI）
// 联调版：全部按后端真实契约调用（RecyclePrice/RecycleOrderVO/PageVO/
// KitchenReport/RepairOrderVO/MallExchangeVO/CarbonAccountVO/AiVerifyVO/
// VoiceParseVO），实体差异在本层适配为页面模型，页面零改。
// ============================================================
import type { PageQuery, PageResult, OrderStatus } from '@shared/http/types'
import type { AddressValue, DfRequestConfig } from '@shared/index'
import { RECYCLE_CATEGORIES } from '@shared/data/baseData'
import { request } from './request'

// ---------------- 适配工具 ----------------

/** 后端 PageVO（total/pages/list）→ 前端 PageResult 归一 */
interface PageVO<T> {
  total: number
  pages?: number
  list: T[]
}

const toPage = <S, T>(p: PageVO<S>, q: PageQuery, map: (s: S) => T): PageResult<T> => ({
  total: Number(p?.total ?? 0),
  pageNum: q.pageNum,
  pageSize: q.pageSize,
  list: (p?.list ?? []).map(map),
})

/** LocalDateTime（ISO 'T' 分隔）→ 页面展示 'yyyy-MM-dd HH:mm' */
const fmtTime = (t?: string | null) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

/** 预约时段 '09:00-11:00' → LocalDateTime 起/止（后端 JacksonConfig 全局 yyyy-MM-dd HH:mm:ss，空格分隔） */
const slotStart = (date: string, slot: string) => `${date} ${slot.split('-')[0] ?? '09:00'}:00`
const slotEnd = (date: string, slot: string) => `${date} ${slot.split('-')[1] ?? '11:00'}:00`

/** 后端大写业务编码 → 前端小写 code（E_WASTE 与 electronics 不同名，需显式映射；其余大写编码 toLowerCase 即匹配） */
const CATEGORY_CODE_ALIAS: Record<string, string> = { E_WASTE: 'electronics' }

/** 品类 code/名称互认（后端 category 字典驱动，防御式双向匹配；兼容大写业务编码如 PAPER/E_WASTE） */
const catOf = (v?: string | null) => {
  if (!v) return undefined
  const alias = CATEGORY_CODE_ALIAS[v]
  return RECYCLE_CATEGORIES.find((c) => c.code === alias || c.code === v.toLowerCase() || c.name === v)
}

const addressText = (a: AddressValue) => `${a.areaName ?? ''} ${a.detail ?? ''}`.trim()

/** 订单地址文本清洗：剔除历史脏数据「未选择片区」前缀（旧版地址簿曾把提示文案写入 areaName 快照） */
const cleanAddress = (addr?: string | null): string =>
  (addr ?? '').replace(/^未选择片区\s*/, '').trim()

/** 片区 code 'A{id}' → 后端 areaId（雪花 ID 超 JS 安全整数，字符串透传不丢失精度）；无片区返回 null */
const areaIdOf = (a: AddressValue): string | null => {
  const c = String(a.areaCode ?? '').trim()
  if (!c) return null
  return c.startsWith('A') ? c.slice(1) : c
}

// ---------------- 首页（/api/home/summary，HomeController G7） ----------------
export interface HomeSummary {
  /** 累计积分 */
  points: number
  /** 累计碳减排 kgCO₂ */
  totalCarbon: number
  /** 本月碳减排 */
  monthCarbon: number
  /** 最新公告标题（仅公告 noticeType=1，通知走站内信） */
  notice: string
  /** 最新公告发布时间（公告身份，用于关闭记忆；无公告为 null） */
  noticePublishTime?: string | null
}

export const getHomeSummary = () => request.get<HomeSummary>('/home/summary')

/** 首页平台环保数据（/home/stats：累计回收订单/碳减排/注册用户数/维修次数，复用大屏聚合口径） */
export interface HomeStats {
  /** 累计回收订单数 */
  recycleOrders: number
  /** 累计碳减排 kgCO2 */
  carbonReduced: number
  /** 注册用户数 */
  totalUsers: number
  /** 维修服务次数 */
  repairOrders: number
}

export const getHomeStats = () => request.get<HomeStats>('/home/stats')

/** 实时报价（配套辅助：报价查询） */
export interface CategoryPrice {
  code: string
  name: string
  icon: string
  price: number
  /** 相对昨日报价涨跌 %（每日联网同步前价快照，无历史为 0） */
  trend: number
  /** 最近一次报价更新时间（yyyy-MM-dd HH:mm，每日 08:05 联网同步） */
  updateTime?: string
  /** 该品类行情地区（联网落库时记录；失败保留上次成功值） */
  city?: string
}

/** 后端 RecyclePrice 实体 */
interface RecyclePriceVO {
  id: string
  category: string
  pricePerKg: number
  status: number
  /** 联网行情地区（可空，未联网同步过为 null） */
  city?: string | null
  /** 较昨日报价（元/kg，每日同步前价快照，无历史为 null） */
  prevPricePerKg?: number | null
  updateTime?: string | null
}

/** 报价元信息（定位城市 + 最近同步时间，每日联网同步展示用） */
export interface PriceMeta {
  /** 定位城市（AMap IP 定位，失败回退配置默认） */
  city?: string
  /** 最近一次联网同步时间（yyyy-MM-dd HH:mm） */
  lastSyncTime?: string
}

/** 报价元信息（GET /recycle/price/meta，未同步过时字段为 null） */
export const getPriceMeta = () => request.get<PriceMeta>('/recycle/price/meta')

/** 手动联网同步报价结果（POST /ai/market-price/sync，异步提交立即返回） */
export interface PriceSyncResult {
  city: string
  updated: number
  skipped: number
  /** 同步是否已提交执行（false=已有同步进行中，本次未提交） */
  started?: boolean
}

/** 手动联网更新内置 8 品类报价（city 可空=IP 定位默认；不可回收/搜索不到标 0） */
export const syncPrices = (city?: string) =>
  request.post<PriceSyncResult>(
    '/ai/market-price/sync',
    city ? { city } : {},
    { timeout: 300000, allowDuplicate: true },
  )

/** 自定义物品联网询价结果（POST /ai/market-price/query，city 可空=IP 定位默认，不落库） */
export interface ItemPriceQuery {
  item: string
  /** 查询城市（IP 定位，失败回退配置默认，如 北京） */
  city?: string
  /** 当日报价（元/公斤）；null=联网未检索到 */
  pricePerKg: number | null
  unit: string
  /** 定价依据/来源说明 */
  note: string
  /** 命中来源标题 */
  source: string
  /** 降级标记（正则/mock 提取，非 LLM 提炼） */
  degraded: boolean
  /** 是否来自缓存（页面加载/重复读取，非本次联网查询） */
  cached?: boolean
}

/** 自定义物品联网询价（如 易拉罐/纸箱，每次实时联网，不缓存） */
export const queryItemPrice = (item: string, city?: string) =>
  request.post<ItemPriceQuery>(
    '/ai/market-price/query',
    city ? { item, city } : { item },
    { timeout: 60000, allowDuplicate: true },
  )

/** IP 定位当前城市（POST /ai/market-price/locate，定位按钮兜底） */
export const locateCity = () => request.post<string>('/ai/market-price/locate')

export async function getPrices(): Promise<CategoryPrice[]> {
  const rows = await request.get<RecyclePriceVO[]>('/recycle/price')
  return (rows ?? [])
    .filter((r) => r.status !== 0)
    .map((r) => {
      const cat = catOf(r.category)
      const price = Number(r.pricePerKg ?? 0)
      const prev = Number(r.prevPricePerKg ?? 0)
      return {
        code: cat?.code ?? r.category,
        name: cat?.name ?? r.category,
        icon: cat?.icon ?? 'recycle-recycle-symbol',
        price,
        trend: prev > 0 ? Number(((price - prev) / prev * 100).toFixed(1)) : 0,
        updateTime: fmtTime(r.updateTime) || undefined,
        city: r.city || undefined,
      }
    })
}

// ---------------- 可回收预约（B） ----------------
// onsite02 独占分区：服务费字段扩展（禁止其他批次修改本文件）
export interface RecycleBookForm {
  address: AddressValue
  categories: string[]
  /** 预估重量 kg */
  estimateWeight: number
  /** 预约日期 yyyy-MM-dd */
  date: string
  timeSlot: string
  remark?: string
  /** 承诺服务费积分（onsite02：0/缺省=不使用积分支付，完成后结算给工作人员） */
  servicePoints?: number
}

export interface MyOrder {
  /** 雪花 ID 后端序列化为 string，前端不得转 Number（精度丢失致详情查无此单） */
  id: string
  orderNo: string
  /** RECYCLE / REPAIR / KITCHEN / MALL / FLEA（LoopFill B1 四源 + C18 跳蚤并入） */
  bizType: 'RECYCLE' | 'REPAIR' | 'KITCHEN' | 'MALL' | 'FLEA'
  title: string
  status: OrderStatus
  /** 各源状态原文案（MALL 已兑换/已发货、KITCHEN 待审核/已通过、REPAIR 已转回收等），优先于 ORDER_STATUS_TEXT */
  statusDesc?: string
  amount: number
  points: number
  carbon: number
  appointTime: string
  address: string
  staffName: string
  staffPhone: string
  createTime: string
  // onsite02 独占分区：服务费结算展示字段（仅 RECYCLE 有值，无服务费为 0/undefined）
  servicePoints?: number
  settleStatus?: 0 | 1 | 2
  settlePoints?: number
  settleResult?: string
}

/** 后端 RecycleOrderVO（Long 序列化为字符串，含接单员工姓名/电话） */
interface RecycleOrderVO {
  id: string
  orderNo: string
  staffName?: string
  /** 接单回收员电话（biz_staff.phone，未接单为空） */
  staffPhone?: string
  category: string
  estWeight: number
  weight?: number | null
  address: string
  appointTime: string
  status: number
  statusDesc?: string
  amount?: number | null
  createTime: string
  // onsite02 独占分区：服务费结算字段（Long 序列化为字符串）
  servicePoints?: string | number | null
  settleStatus?: number | null
  settlePoints?: string | number | null
  settleResult?: string | null
}

const toMyOrder = (o: RecycleOrderVO): MyOrder => {
  const names = String(o.category ?? '')
    .split(',')
    .map((c) => catOf(c)?.name ?? c)
    .join('/')
  return {
    id: String(o.id),
    orderNo: o.orderNo,
    bizType: 'RECYCLE',
    title: `可回收预约 · ${names} 约${Number(o.weight ?? o.estWeight ?? 0)}kg`,
    status: Number(o.status) as OrderStatus,
    statusDesc: o.statusDesc,
    amount: Number(o.amount ?? 0),
    points: 0,
    carbon: 0,
    appointTime: fmtTime(o.appointTime),
    address: cleanAddress(o.address),
    staffName: o.staffName || '—',
    staffPhone: o.staffPhone || '',
    createTime: fmtTime(o.createTime),
    // onsite02：服务费结算展示字段（无服务费为 0，详情页按 settleStatus 分支展示）
    servicePoints: Number(o.servicePoints ?? 0),
    settleStatus: (o.settleStatus ?? 0) as 0 | 1 | 2,
    settlePoints: Number(o.settlePoints ?? 0),
    settleResult: o.settleResult ?? undefined,
  }
}

// ---------------- 订单聚合（LoopFill B1：维修/垃圾投放/兑换三源映射） ----------------
/** 后端 RepairOrderVO（列表/详情均返回 VO；Long 序列化为字符串，含接单员工姓名/电话） */
interface RepairOrderVO {
  id: string
  orderNo: string
  faultType?: string
  faultDesc?: string
  address?: string
  appointStart?: string
  appointEnd?: string
  status: number
  statusDesc?: string
  createTime: string
  staffName?: string
  /** 接单技术员电话（biz_staff.phone，未接单为空） */
  staffPhone?: string
  servicePoints?: string | number | null
  settleStatus?: number | null
  settlePoints?: string | number | null
  settleResult?: string | null
}

/** 后端 MallExchangeVO（兑换单列表/详情；Long 序列化为字符串） */
interface MallExchangeVO2 {
  id: string
  exchangeNo: string
  productName?: string
  pointsCost?: number
  quantity?: number
  status: number
  statusDesc?: string
  address?: string
  createTime?: string
}

/** REPAIR 状态文案（RepairStatusEnum：0待接单 1已接单 2检修中 3已完成 4已取消 5已转回收） */
const REPAIR_STATUS_TEXT: Record<number, string> = {
  0: '待接单', 1: '已接单', 2: '检修中', 3: '已完成', 4: '已取消', 5: '已转回收',
}
/** REPAIR 状态 → OrderStatus（数值 0-4 同构；5 已转回收 ≈ 已完成态，避免红色「异常」误展示） */
const REPAIR_STATUS_MAP: Record<number, OrderStatus> = { 0: 0, 1: 1, 2: 2, 3: 3, 4: 4, 5: 3 }

/** KITCHEN 状态 → OrderStatus（0 待审核 → 进行中；1/2 → 已完成；3 已驳回 → 取消） */
const KITCHEN_STATUS_MAP: Record<number, OrderStatus> = { 0: 2, 1: 3, 2: 3, 3: 4 }

/** MALL 状态文案（0已兑换 1已发货 2已完成 3已取消） */
const MALL_STATUS_TEXT: Record<number, string> = { 0: '已兑换', 1: '已发货', 2: '已完成', 3: '已取消' }
/** MALL 状态 → OrderStatus（已兑换≈待处理、已发货≈已接单、已完成、已取消） */
const MALL_STATUS_MAP: Record<number, OrderStatus> = { 0: 0, 1: 1, 2: 3, 3: 4 }
/** OrderStatus → MALL 后端状态（状态 Tab 反向过滤映射；进行中/异常无对应 → 空） */
const ORDER_TO_MALL_STATUS: Record<number, number | undefined> = { 0: 0, 1: 1, 3: 2, 4: 3 }

/** FLEA 状态文案（0待发货 1待收货 2已完成 3已取消） */
const FLEA_STATUS_TEXT: Record<number, string> = { 0: '待发货', 1: '待收货', 2: '已完成', 3: '已取消' }
/** FLEA 状态 → OrderStatus（待发货≈待接单、待收货≈已接单、已完成、已取消；与 MALL 同构） */
const FLEA_STATUS_MAP: Record<number, OrderStatus> = { 0: 0, 1: 1, 2: 3, 3: 4 }
/** OrderStatus → FLEA 后端状态（状态 Tab 反向过滤映射） */
const ORDER_TO_FLEA_STATUS: Record<number, number | undefined> = { 0: 0, 1: 1, 3: 2, 4: 3 }

const toRepairOrder = (o: RepairOrderVO): MyOrder => {
  const raw = Number(o.status ?? 0)
  return {
    id: String(o.id),
    orderNo: o.orderNo,
    bizType: 'REPAIR',
    title: `维修预约 · ${o.faultType ?? ''}`,
    status: REPAIR_STATUS_MAP[raw] ?? 3,
    statusDesc: REPAIR_STATUS_TEXT[raw] ?? String(raw),
    amount: 0,
    points: 0,
    carbon: 0,
    appointTime: fmtTime(o.appointStart),
    address: cleanAddress(o.address),
    staffName: o.staffName || '—',
    staffPhone: o.staffPhone || '',
    createTime: fmtTime(o.createTime),
    servicePoints: Number(o.servicePoints ?? 0),
    settleStatus: (o.settleStatus ?? 0) as 0 | 1 | 2,
    settlePoints: Number(o.settlePoints ?? 0),
    settleResult: o.settleResult ?? undefined,
  }
}

const toKitchenOrder = (o: KitchenReportVO): MyOrder => {
  const raw = Number(o.status ?? 0)
  return {
    id: String(o.id),
    orderNo: `K${o.id}`,
    bizType: 'KITCHEN',
    title: `垃圾投放 · ${Number(o.bagCount ?? 0)} 袋`,
    status: KITCHEN_STATUS_MAP[raw] ?? 3,
    statusDesc: KITCHEN_STATUS_TEXT[raw] ?? String(raw),
    amount: 0,
    // 垃圾投放单积分 = 袋数 × 2（契约默认值）
    points: Number(o.bagCount ?? 0) * 2,
    carbon: 0,
    appointTime: fmtTime(o.reportTime),
    address: o.pointName ?? '',
    staffName: '—',
    staffPhone: '',
    // 后端无独立 createTime 契约字段：排序以投放时间为准
    createTime: fmtTime(o.reportTime),
  }
}

const toMallOrder = (o: MallExchangeVO2): MyOrder => {
  const raw = Number(o.status ?? 0)
  return {
    id: String(o.id),
    orderNo: o.exchangeNo,
    bizType: 'MALL',
    title: `商城兑换 · ${o.productName ?? ''}`,
    status: MALL_STATUS_MAP[raw] ?? 4,
    statusDesc: o.statusDesc || MALL_STATUS_TEXT[raw] || String(raw),
    amount: 0,
    // 兑换单为扣分：显示负积分
    points: -Number(o.pointsCost ?? 0),
    carbon: 0,
    appointTime: '',
    address: cleanAddress(o.address),
    staffName: '—',
    staffPhone: '',
    createTime: fmtTime(o.createTime),
  }
}

/** C18：FLEA 买家订单并入「我的订单」聚合（buyer 视角；待发货→待接单、待收货→已接单等映射） */
const toFleaBuyerOrder = (o: FleaOrderVO2): MyOrder => {
  const raw = Number(o.status ?? 0)
  return {
    id: String(o.id),
    orderNo: o.orderNo ?? '',
    bizType: 'FLEA',
    title: `闲置 · ${o.itemTitle ?? ''}`,
    status: FLEA_STATUS_MAP[raw] ?? 4,
    statusDesc: o.statusDesc || FLEA_STATUS_TEXT[raw] || String(raw),
    amount: 0,
    // 下单冻结 price 积分，收货后结算给卖家：均为支出 → 负积分
    points: -Number(o.price ?? 0),
    carbon: 0,
    appointTime: '',
    address: cleanAddress(o.address),
    staffName: '—',
    staffPhone: '',
    createTime: fmtTime(o.createTime),
  }
}

/** C18：后端 FleaOrderVO 真实字段（buyer 视角，Long 序列化为字符串；U4 遗留接口字段名不齐，聚合用独立对齐接口） */
interface FleaOrderVO2 {
  id: string
  orderNo: string
  itemTitle?: string
  itemImage?: string
  price: number
  status: number
  statusDesc?: string
  address?: string
  createTime?: string
  buyerId?: string
  buyerNickname?: string
  sellerId?: string
  sellerNickname?: string
}

export async function createRecycleOrder(form: RecycleBookForm): Promise<{ orderNo: string }> {
  const vo = await request.post<RecycleOrderVO>('/recycle/orders', {
    category: form.categories.join(','),
    estWeight: form.estimateWeight,
    quantity: 1,
    address: addressText(form.address),
    areaId: areaIdOf(form.address),
    // 坐标随单上传：areaId 缺省时后端按经纬度自动创建/匹配片区
    lng: form.address.lng ?? null,
    lat: form.address.lat ?? null,
    appointTime: slotStart(form.date, form.timeSlot),
    // onsite02：承诺服务费积分（0/缺省=不使用）
    servicePoints: form.servicePoints ?? 0,
  })
  return { orderNo: vo.orderNo }
}

/** 订单查询扩展（LoopFill B1：bizType 类型过滤，不传=全部） */
export interface MyOrderQuery extends PageQuery {
  bizType?: MyOrder['bizType']
}

/** 单源失败静默降级（silent 防 Loading 闪烁，skipErrorToast 防重复提示） */
const safePage = async <T>(p: Promise<T>): Promise<T | null> => {
  try {
    return await p
  } catch {
    return null
  }
}

/** 前端五源聚合：回收 + 维修 + 垃圾投放 + 兑换 + 跳蚤买家（C18），按 createTime 降序归并截断（LoopFill B1） */
export async function pageMyOrders(q: MyOrderQuery): Promise<PageResult<MyOrder>> {
  const { pageNum, pageSize = 10, bizType, status } = q
  // MALL 后端状态为 0-3 原始值：状态 Tab 反向映射（进行中/异常无对应 → 空）
  const mallStatus = status !== undefined ? ORDER_TO_MALL_STATUS[Number(status)] : undefined
  // C18：FLEA 同构反向映射
  const fleaStatus = status !== undefined ? ORDER_TO_FLEA_STATUS[Number(status)] : undefined
  const [r, rp, k, m, f] = await Promise.all([
    !bizType || bizType === 'RECYCLE'
      ? safePage(request.get<PageVO<RecycleOrderVO>>('/recycle/orders', { pageNum, pageSize, ...(status !== undefined ? { status } : {}) }, { silent: true, skipErrorToast: true }))
      : Promise.resolve(null),
    !bizType || bizType === 'REPAIR'
      ? safePage(request.get<PageVO<RepairOrderVO>>('/repair/orders', { pageNum, pageSize, ...(status !== undefined ? { status } : {}) }, { silent: true, skipErrorToast: true }))
      : Promise.resolve(null),
    // 垃圾投放后端无 status 过滤：归一化后仅匹配 进行中/已完成 Tab，其余 Tab 天然不展示
    !bizType || bizType === 'KITCHEN'
      ? safePage(request.get<PageVO<KitchenReportVO>>('/kitchen/reports', { pageNum, pageSize }, { silent: true, skipErrorToast: true }))
      : Promise.resolve(null),
    !bizType || bizType === 'MALL'
      ? (mallStatus !== undefined
          ? safePage(request.get<PageVO<MallExchangeVO2>>('/mall/exchanges', { pageNum, pageSize, status: mallStatus }, { silent: true, skipErrorToast: true }))
          : Promise.resolve(null))
      : Promise.resolve(null),
    // C18：跳蚤买家订单并入聚合（buyer 视角，fleaStatus 反向映射）
    !bizType || bizType === 'FLEA'
      ? (fleaStatus !== undefined
          ? safePage(request.get<PageVO<FleaOrderVO2>>('/flea/orders/buyer', { pageNum, pageSize, status: fleaStatus }, { silent: true, skipErrorToast: true }))
          : Promise.resolve(null))
      : Promise.resolve(null),
  ])
  const all = [
    ...(r?.list ?? []).map(toMyOrder),
    ...(rp?.list ?? []).map(toRepairOrder),
    ...(k?.list ?? []).map(toKitchenOrder),
    ...(m?.list ?? []).map(toMallOrder),
    ...(f?.list ?? []).map(toFleaBuyerOrder),
  ]
  const total =
    Number(r?.total ?? 0) + Number(rp?.total ?? 0) + Number(k?.total ?? 0)
    + Number(m?.total ?? 0) + Number(f?.total ?? 0)
  return {
    total,
    pageNum,
    pageSize,
    list: all
      .sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''))
      .slice(0, pageSize),
  }
}

/** 订单详情按 bizType 分发（KITCHEN/FLEA 后端无独立详情端点，列表过滤兜底） */
export const getOrderDetail = async (
  id: number | string,
  bizType: MyOrder['bizType'] = 'RECYCLE',
): Promise<MyOrder> => {
  const raw = String(id)
  switch (bizType) {
    case 'REPAIR':
      return toRepairOrder(await request.get<RepairOrderVO>(`/repair/orders/${raw}`))
    case 'MALL':
      return toMallOrder(await request.get<MallExchangeVO2>(`/mall/exchanges/${raw}`))
    case 'KITCHEN': {
      const p = await request.get<PageVO<KitchenReportVO>>('/kitchen/reports', { pageNum: 1, pageSize: 50 })
      const row = (p?.list ?? []).find((o) => String(o.id) === raw)
      if (!row) throw new Error('垃圾投放记录不存在')
      return toKitchenOrder(row)
    }
    case 'FLEA': {
      const p = await request.get<PageVO<FleaOrderVO2>>('/flea/orders/buyer', { pageNum: 1, pageSize: 50 })
      const row = (p?.list ?? []).find((o) => String(o.id) === raw)
      if (!row) throw new Error('闲置交易不存在')
      return toFleaBuyerOrder(row)
    }
    default:
      return toMyOrder(await request.get<RecycleOrderVO>(`/recycle/orders/${raw}`))
  }
}

export const cancelOrder = (id: number | string, reason = '用户取消') =>
  request.put(`/recycle/orders/${id}/cancel?reason=${encodeURIComponent(reason)}`)

/** 订单改期（/recycle/orders/{id}/reschedule，真实可用） */
export const rescheduleOrder = (id: number | string, date: string, timeSlot: string) =>
  request.post(`/recycle/orders/${id}/reschedule`, { date, timeSlot })

// ---------------- 订单评价 / 员工实时定位（GapFill B2，V2/V3） ----------------
export interface ReviewSubmit {
  /** RECYCLE / KITCHEN / REPAIR */
  orderType: string
  orderId: string | number
  /** 星级 1-5 */
  rating: number
  comment?: string
}

export const submitReview = (data: ReviewSubmit) => request.post('/reviews', data)

export interface ReviewCheck {
  reviewed: boolean
  rating: number | null
}

export const checkReviewed = (orderType: string, orderId: string | number) =>
  request.get<ReviewCheck>('/reviews/check', { orderType, orderId })

/** 后端 StaffLocationVO（无接单/无上报时 data=null） */
export interface StaffLocationVO {
  lng: number
  lat: number
  staffName: string
  staffUserId: string
}

export const getStaffLocation = (orderId: string | number) =>
  request.get<StaffLocationVO | null>(`/recycle/orders/${orderId}/staff-location`)

// ---------------- 垃圾投放（B，工作人员审核发分） ----------------
export interface KitchenDropForm {
  bags: number
  photoBase64: string
  /** 投放定位（必填：后端校验距垃圾回收站 50m 内才允许投放） */
  lng: number
  lat: number
}

export interface KitchenDropResult {
  /** 审核结果（一律转工作人员审核） */
  auditResult: '待审核'
  points: number
  reason: string
}

/** 后端 KitchenReport 实体（0待审核 1已通过 2已复核 3已驳回） */
interface KitchenReportVO {
  id: string
  bagCount: number
  status: number
}

export async function submitKitchenDrop(form: KitchenDropForm): Promise<KitchenDropResult> {
  const r = await request.post<KitchenReportVO>('/kitchen/reports', {
    bagCount: form.bags,
    photoUrl: form.photoBase64,
    pointName: '小区集中投放点',
    lng: form.lng,
    lat: form.lat,
  })
  return {
    auditResult: '待审核',
    points: 0,
    reason: '投放上报成功，待工作人员审核通过后发放积分',
  }
}

// ---------------- 维修预约（C，F2 预诊） ----------------
// onsite02 独占分区：服务费字段扩展（禁止其他批次修改本文件）
export interface RepairBookForm {
  deviceType: string
  faultDesc: string
  address: AddressValue
  date: string
  timeSlot: string
  /** 以旧换新（0/1，可选，未传默认 0） */
  oldForNew?: 0 | 1
  /** 天然气配送顺带（0/1，可选，未传默认 0） */
  gasDelivery?: 0 | 1
  /** 承诺服务费积分（onsite02：0/缺省=不使用积分支付，完成后结算给工作人员） */
  servicePoints?: number
}

export interface RepairBookResult {
  orderNo: string
  /** F2 AI 预诊结论（RAG+LLM，低置信时为空走人工） */
  aiPrediagnosis: string
}

/** 后端 RepairOrderVO（diagnose 为 F2 预诊契约响应） */
interface RepairOrderVOResp {
  orderNo: string
  diagnose?: {
    possibleFaults?: { fault: string; confidence: number }[]
    suggestParts?: string[]
    repairableProbability?: number
    suggestion?: string
  } | null
}

export async function createRepairOrder(form: RepairBookForm): Promise<RepairBookResult> {
  const vo = await request.post<RepairOrderVOResp>('/repair/orders', {
    faultType: form.deviceType,
    faultDesc: form.faultDesc,
    address: addressText(form.address),
    areaId: areaIdOf(form.address),
    // 坐标随单上传：areaId 缺省时后端按经纬度自动创建/匹配片区
    lng: form.address.lng ?? null,
    lat: form.address.lat ?? null,
    appointStart: slotStart(form.date, form.timeSlot),
    appointEnd: slotEnd(form.date, form.timeSlot),
    // 增值服务（V6：后端 RepairOrderCreateDTO 字段名一致，未传默认 0）
    oldForNew: form.oldForNew ?? 0,
    gasDelivery: form.gasDelivery ?? 0,
    // onsite02：承诺服务费积分（0/缺省=不使用）
    servicePoints: form.servicePoints ?? 0,
  })
  const d = vo.diagnose
  const top = d?.possibleFaults?.[0]
  return {
    orderNo: vo.orderNo,
    aiPrediagnosis: d?.suggestion
      ? `${top ? `疑似${top.fault}（置信 ${top.confidence}）· ` : ''}${d.suggestion}`
      : '',
  }
}

// ---------------- 积分商城（C） ----------------
// U2：MallProduct 补 description；cover 改真实 URL（空串由渲染层 SvgIcon 兜底，移除 'carbon-shopping-bag' 硬编码）
export interface MallProduct {
  id: number
  name: string
  cover: string
  points: number
  stock: number
  /** 商品简介（详情弹层展示，空串兜底「暂无简介」） */
  description: string
}

/** 后端 MallProduct 实体 */
interface MallProductVO {
  id: string
  productName: string
  coverUrl: string
  pointsPrice: number
  stock: number
  status: number
  description?: string | null
}

export async function pageMallProducts(q: PageQuery): Promise<PageResult<MallProduct>> {
  const p = await request.get<PageVO<MallProductVO>>('/mall/products', q)
  return toPage(p, q, (m) => ({
    id: Number(m.id),
    name: m.productName,
    cover: m.coverUrl || '',
    points: Number(m.pointsPrice ?? 0),
    stock: Number(m.stock ?? 0),
    description: m.description ?? '',
  }))
}

/** 后端 MallExchangeVO（Long 序列化为字符串；balanceAfter 仅兑换/取消动作返回，列表为 null） */
interface MallExchangeVO {
  id: string
  exchangeNo: string
  productId: string
  productName: string
  pointsCost: number
  quantity: number
  carbonLogId?: string | null
  status: number
  statusDesc?: string
  address?: string | null
  balanceAfter?: number | null
  createTime: string
}

/** 我的兑换单页面模型（LoopFill B2） */
export interface MallExchangeItem {
  id: number
  exchangeNo: string
  productName: string
  /** 本单扣减积分总额 */
  pointsCost: number
  quantity: number
  /** 0已兑换 1已发货 2已完成 3已取消 */
  status: number
  statusDesc: string
  address: string
  /** 关联扣分流水 ID（溯源，存在才展示入口） */
  carbonLogId?: string
  /** 动作后积分余额（仅兑换/取消动作返回） */
  balanceAfter?: number | null
  createTime: string
}

export const EXCHANGE_STATUS_TEXT: Record<number, string> = {
  0: '已兑换',
  1: '已发货',
  2: '已完成',
  3: '已取消',
}

const toExchangeItem = (e: MallExchangeVO): MallExchangeItem => ({
  id: Number(e.id),
  exchangeNo: e.exchangeNo ?? '',
  productName: e.productName ?? '',
  pointsCost: Number(e.pointsCost ?? 0),
  quantity: Number(e.quantity ?? 1),
  status: Number(e.status ?? 0),
  statusDesc: e.statusDesc ?? EXCHANGE_STATUS_TEXT[Number(e.status ?? 0)] ?? '',
  address: e.address ?? '',
  carbonLogId: e.carbonLogId ?? undefined,
  balanceAfter: e.balanceAfter != null ? Number(e.balanceAfter) : null,
  createTime: fmtTime(e.createTime),
})

/**
 * 积分兑换（LoopFill B2：实物商品必填收货地址；虚拟商品/未选地址传空串，后端不强制）
 * @returns 兑换单号 + 动作后积分余额
 */
export async function exchangeProduct(productId: number, address = ''): Promise<{ orderNo: string; restPoints: number }> {
  const vo = await request.post<MallExchangeVO>('/mall/exchange', { productId, quantity: 1, address })
  return { orderNo: vo.exchangeNo, restPoints: Number(vo.balanceAfter ?? 0) }
}

/** 我的兑换单分页（status 可选过滤） */
export async function pageMyExchanges(q: PageQuery, status?: number): Promise<PageResult<MallExchangeItem>> {
  const p = await request.get<PageVO<MallExchangeVO>>('/mall/exchanges', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
    ...(status !== undefined ? { status } : {}),
  })
  return toPage(p, q, toExchangeItem)
}

/** 兑换单详情（限本人） */
export const getExchangeDetail = async (id: number): Promise<MallExchangeItem> =>
  toExchangeItem(await request.get<MallExchangeVO>(`/mall/exchanges/${id}`))

/** 取消兑换（仅待发货；积分退回+库存返还），返回含 balanceAfter */
export async function cancelExchange(id: number): Promise<MallExchangeItem> {
  const vo = await request.put<MallExchangeVO>(`/mall/exchanges/${id}/cancel`)
  return toExchangeItem(vo)
}

// ---------------- 成就徽章（积分市场合并升级：虚拟收藏品，积分兑换即到账） ----------------
export interface Badge {
  id: number
  code: string
  name: string
  description: string
  /** 兑换所需积分 */
  price: number
  /** 徽章图标/封面 */
  iconUrl: string
  sort: number
  /** 0下架 1上架 */
  status: number
  /** 当前用户是否已兑换（登录态回填） */
  owned: boolean
}

/** 后端 BadgeVO */
interface BadgeVO {
  id: string
  code: string
  name: string
  description: string
  price: number
  iconUrl?: string | null
  sort: number
  status: number
  owned?: boolean | null
}

const toBadge = (b: BadgeVO): Badge => ({
  id: Number(b.id),
  code: b.code ?? '',
  name: b.name ?? '',
  description: b.description ?? '',
  price: Number(b.price ?? 0),
  iconUrl: b.iconUrl ?? '',
  sort: Number(b.sort ?? 0),
  status: Number(b.status ?? 1),
  owned: Boolean(b.owned),
})

/** 上架徽章分页（sort 升序；登录态回填 owned） */
export async function pageBadges(q: PageQuery, keyword?: string): Promise<PageResult<Badge>> {
  const p = await request.get<PageVO<BadgeVO>>('/badge/list', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
    ...(keyword ? { keyword } : {}),
  })
  return toPage(p, q, toBadge)
}

/** 后端 BadgeExchangeVO */
interface BadgeExchangeVO {
  id: string
  exchangeNo: string
  badgeId: string
  badgeName: string
  badgeCode?: string | null
  badgeIcon?: string | null
  pointsCost: number
  status: number
  balanceAfter?: number | null
  createTime: string
}

/** 我的徽章页面模型 */
export interface MyBadge {
  id: number
  exchangeNo: string
  badgeId: number
  badgeName: string
  /** 徽章编码（前端匹配内置 SVG 图标） */
  badgeCode: string
  badgeIcon: string
  pointsCost: number
  /** 0已兑换（虚拟商品即到账） */
  status: number
  /** 动作后积分余额（仅兑换动作返回） */
  balanceAfter?: number | null
  createTime: string
}

const toMyBadge = (e: BadgeExchangeVO): MyBadge => ({
  id: Number(e.id),
  exchangeNo: e.exchangeNo ?? '',
  badgeId: Number(e.badgeId),
  badgeName: e.badgeName ?? '',
  badgeCode: e.badgeCode ?? '',
  badgeIcon: e.badgeIcon ?? '',
  pointsCost: Number(e.pointsCost ?? 0),
  status: Number(e.status ?? 0),
  balanceAfter: e.balanceAfter != null ? Number(e.balanceAfter) : null,
  createTime: fmtTime(e.createTime),
})

// ---------------- 内置徽章 SVG 图标映射（front-user/src/assets/icons/badge/，与 V46 种子 code 对齐） ----------------

/** 徽章编码 → 内置 SVG 图标名（SvgIcon name="badge-xxx"，iconDirs 含 assets/icons/badge） */
export const BADGE_ICON_MAP: Record<string, string> = {
  GREEN_PIONEER: 'badge-green-pioneer',
  SORT_MASTER: 'badge-sort-master',
  RECYCLE_STAR: 'badge-recycle-star',
  KITCHEN_GUARD: 'badge-kitchen-guard',
  STREAK_KNIGHT: 'badge-streak-knight',
  LOW_CARB_WALKER: 'badge-low-carb-walker',
  TRASH_TO_GOLD: 'badge-trash-to-gold',
  ECO_AMBASSADOR: 'badge-eco-ambassador',
  CARBON_PIONEER: 'badge-carbon-pioneer',
  ECO_MASTER: 'badge-eco-master',
}

/** 徽章编码 → 内置 SVG 图标名（未内置编码回退星标） */
export function badgeIconName(code?: string | null): string {
  return (code ? BADGE_ICON_MAP[code.toUpperCase()] : undefined) ?? 'common-star'
}

/** 积分兑换徽章（虚拟商品即到账）@returns 兑换单号 + 动作后积分余额 */
export async function exchangeBadge(badgeId: number): Promise<{ orderNo: string; restPoints: number }> {
  const vo = await request.post<BadgeExchangeVO>(`/badge/${badgeId}/exchange`)
  return { orderNo: vo.exchangeNo, restPoints: Number(vo.balanceAfter ?? 0) }
}

/** 我的徽章分页（兑换时间倒序） */
export async function pageMyBadges(q: PageQuery): Promise<PageResult<MyBadge>> {
  const p = await request.get<PageVO<BadgeExchangeVO>>('/badge/mine', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
  })
  return toPage(p, q, toMyBadge)
}

// ---------------- 低碳账单（B 碳账本，F3 可信验证） ----------------
export interface CarbonAccount {
  points: number
  totalCarbon: number
  monthCarbon: number
  /** 碳减排全站排名百分位（超过 X% 的用户） */
  rankPercent: number
}

/** 后端 CarbonAccountVO */
interface CarbonAccountVO {
  pointsBalance: number
  frozenPoints: number
  totalCarbonReduced: number
  monthCarbon?: number
  rankPercent?: number
}

export async function getCarbonAccount(): Promise<CarbonAccount> {
  const a = await request.get<CarbonAccountVO>('/carbon/account')
  return {
    points: Number(a.pointsBalance ?? 0),
    totalCarbon: Number(a.totalCarbonReduced ?? 0),
    monthCarbon: Number(a.monthCarbon ?? 0),
    rankPercent: Number(a.rankPercent ?? 100),
  }
}

export interface CarbonLog {
  /** 雪花 ID 保持字符串，禁止 Number()（超过 2^53 会精度丢失导致证书查不到） */
  id: string
  bizType: string
  desc: string
  points: number
  carbon: number
  /** 哈希链锚定标记（F3） */
  anchored: boolean
  createTime: string
}

/** 后端 CarbonLog 实体（哈希链流水） */
interface CarbonLogVO {
  id: string
  orderType: string
  type: number
  points: number
  carbonReduced: number
  remark: string
  anchorId?: string | null
  createTime: string
}

const ORDER_TYPE_TEXT: Record<string, string> = {
  RECYCLE: '可回收交售',
  KITCHEN: '垃圾投放',
  REPAIR: '维修下单返积分',
  MALL: '商城兑换',
  MANUAL: '人工调整',
}

export async function pageCarbonLogs(q: PageQuery): Promise<PageResult<CarbonLog>> {
  const p = await request.get<PageVO<CarbonLogVO>>('/carbon/logs', q)
  return toPage(p, q, (l) => ({
    id: String(l.id),
    bizType: ORDER_TYPE_TEXT[l.orderType] ?? l.orderType,
    desc: l.remark ?? '',
    // 后端 CarbonLog.points 已带符号：正=入账（签到+5），负=出账（兑换-5、扣减-3）
    // 不再前端翻转，避免把 GAIN（type=1）误判为支出
    points: Number(l.points ?? 0),
    carbon: Number(l.carbonReduced ?? 0),
    anchored: l.anchorId != null && l.anchorId !== '',
    createTime: fmtTime(l.createTime),
  }))
}

export interface CarbonBill {
  month: string
  /** 月度碳减排曲线 */
  days: { date: string; carbon: number }[]
  totalCarbon: number
  totalPoints: number
  /** 相当于植树 n 棵 */
  treeEquivalent: number
}

/** 月度账单（/carbon/bill，month 可选，缺省查当月） */
export const getCarbonBill = (month?: string) => request.get<CarbonBill>('/carbon/bill', { month })

// ---------------- AI（E：F1 云端视觉 / F7 语音成单） ----------------
export interface ImageVerifyResult {
  pass: boolean
  category: string
  confidence: number
  advice: string
}

/** 3D spec 原语（对齐 ai-back schemas.verify.SpecPrimitive，img2threejs 风格） */
/** 面/侧面纹理绘制元素（region 归一化 0-1，左上原点，按序绘制） */
export interface DecalItem {
  type: 'text' | 'circle' | 'rect' | 'stripe'
  text?: string
  color?: string
  region?: [number, number, number, number]
  /** 文字高度占 region 高度比例 */
  fontSize?: number
  /** rect 描边模式（条码框/标签框） */
  border?: boolean
}

/** box 单面贴图材质 */
export interface FaceMaterial {
  bg?: string
  items?: DecalItem[]
}

/** 侧面环绕标签（cylinder/cone/lathe/tube） */
export interface SideLabel {
  bg?: string
  items?: DecalItem[]
}

export interface SpecPrimitive {
  type: 'box' | 'cylinder' | 'sphere' | 'cone' | 'plane'
  size?: [number, number, number]
  radius?: number
  height?: number
  pos?: [number, number, number]
  rot?: [number, number, number]
  color?: string
  roughness?: number
  metalness?: number
  transparent?: boolean
  opacity?: number
  /** 材质模拟：纸/塑料/金属/玻璃（PBR 参数 + 纤维/微噪凹凸） */
  surface?: 'none' | 'paper' | 'plastic' | 'metal' | 'glass'
  /** box 六面贴图材质（+X,-X,+Y,-Y,+Z,-Z） */
  materials?: FaceMaterial[]
  /** 侧面环绕标签 */
  label?: SideLabel
}

/** 车削体：轮廓点 [半径,高度] 绕 Y 轴旋转（瓶身/杯身曲线） */
export interface LatheSpec {
  points: [number, number][]
  pos?: [number, number, number]
  rot?: [number, number, number]
  color?: string
  roughness?: number
  metalness?: number
  transparent?: boolean
  opacity?: number
  surface?: 'none' | 'paper' | 'plastic' | 'metal' | 'glass'
  label?: SideLabel
}

/** 管道：路径点 + 半径（弯曲吸管/把手） */
export interface TubeSpec {
  curve: [number, number, number][]
  radius: number
  pos?: [number, number, number]
  rot?: [number, number, number]
  color?: string
  roughness?: number
  metalness?: number
  transparent?: boolean
  opacity?: number
  surface?: 'none' | 'paper' | 'plastic' | 'metal' | 'glass'
}

/** 表面贴花：文字/logo/图标（CanvasTexture 绘制） */
export interface DecalSpec {
  text?: string
  shape?: 'text' | 'circle' | 'rect' | 'stripe'
  color?: string
  bg?: string
  pos?: [number, number, number]
  rot?: [number, number, number]
  size?: [number, number]
  fontSize?: number
}

export interface ModelSpec {
  item?: string
  primitives: SpecPrimitive[]
  lathes?: LatheSpec[]
  tubes?: TubeSpec[]
  decals?: DecalSpec[]
  /** 整体包围盒比例 [宽,高,深]（高=1） */
  aspect?: [number, number, number]
  tick?: 'none' | 'float' | 'sway' | 'spin'
}

/** 3D 构建质量模式：standard=单链路（默认）/ advanced=多轮自纠 / extreme=渲染闭环（前端三档重建入口均开放） */
export type Build3dMode = 'standard' | 'advanced' | 'extreme'

/** 3D 构建任务状态（Java 异步任务：提交即返回，1s 轮询） */
export interface Build3dTaskVO {
  taskId: string
  /** RUNNING 构建中 / DONE 完成（spec 可能为 null=回退预设模型库）/ FAILED 失败 */
  status: 'RUNNING' | 'DONE' | 'FAILED'
  spec?: ModelSpec | null
  error?: string | null
  /** 实际执行模式（extreme 降级 advanced 时回显） */
  mode?: string
}

/** 提交 3D 构建任务（img2threejs 风格）：立即返回 taskId，不阻塞识别链路 */
export async function submitBuild3d(
  photoBase64: string,
  mode: Build3dMode = 'standard',
): Promise<{ taskId: string }> {
  const vo = await request.post<Build3dTaskVO>('/ai/verify/3d-build', {
    imageUrl: photoBase64,
    mode,
  })
  return { taskId: vo.taskId }
}

/** 轮询 3D 构建任务状态 */
export const getBuild3dTask = (taskId: string) =>
  request.get<Build3dTaskVO>(`/ai/verify/3d-build/${taskId}`)

/** 后端 AiVerifyVO（三层审核：AUTO_PASS/MANUAL_REVIEW/REJECT） */
interface AiVerifyVO {
  decision: string
  category: string
  categoryMatch?: boolean | null
  confidence: number
  rejectReason?: string | null
  degraded?: boolean | null
}

/** F6 端侧不可用时的云端拍照识别降级（F1，后端 /ai/verify） */
export async function aiVerifyImage(photoBase64: string, scene: 'recycle' | 'kitchen'): Promise<ImageVerifyResult> {
  const vo = await request.post<AiVerifyVO>('/ai/verify', {
    bizType: scene === 'kitchen' ? 'KITCHEN' : 'RECYCLE',
    imageUrl: photoBase64,
  })
  const cat = catOf(vo.category)
  const rejected = vo.decision === 'REJECT'
  return {
    pass: vo.decision === 'AUTO_PASS',
    // REJECT 时 category 为空：显示驳回态而非误导性的「未识别」
    category: cat?.name ?? vo.category ?? (rejected ? '图片被驳回' : '未识别'),
    confidence: Number(vo.confidence ?? 0),
    // AR 识别只展示类别，不拼接内置参考价（价格以实时报价页/下单页为准）
    advice:
      vo.rejectReason ||
      (vo.decision === 'MANUAL_REVIEW'
        ? '置信度不足已转人工复核'
        : `识别为「${cat?.name ?? vo.category}」`),
  }
}

// ---------------- CAM 云端精分类（契约 C6-2，/ai/classify） ----------------
/** 精分类结果（裁剪目标图 → 具体物品名/8 类中文枚举/报价，字段冻结对齐 C6-2） */
export interface ClassifyDetailResult {
  itemName: string
  category: string
  confidence: number
  purity: number
  advice: string
  /** 当日报价 元/kg（厨余/未知为 null，前端用 RECYCLE_CATEGORIES 兜底） */
  pricePerKg?: number | null
  /** 链路降级标记（ai-back 3001 时 Java 侧返回 3001，前端保留大类不展示卡片） */
  degraded?: boolean | null
  elapsedMs?: number
  modelName?: string
}

/**
 * CAM 云端精分类（/ai/classify）：上传裁剪目标区域 base64（≤300KB，C6-3 隐私铁律），
 * 返回具体物品名 + 8 类中文枚举 + 报价；失败/超限/频控由 request 层统一 Toast 并抛错（C6-5）。
 */
export async function classifyDetail(
  imageBase64: string,
  categoryHint?: string,
  config?: DfRequestConfig,
): Promise<ClassifyDetailResult> {
  return request.post<ClassifyDetailResult>(
    '/ai/classify',
    {
      imageUrl: imageBase64,
      categoryHint: categoryHint ?? null,
    },
    config,
  )
}

export interface VoiceParseResult {
  intent: 'RECYCLE_BOOK' | 'KITCHEN_DROP' | 'QUERY_BILL' | 'UNKNOWN'
  /** 解析出的槽位（品类/时间等） */
  slots: Record<string, string>
  reply: string
  /** V44 图片扫描升级：识别后图片（标注检测框的 JPEG base64），未携带图片/识别失败为 null */
  annotatedImage?: string | null
  /** V44 图片扫描升级：检测明细（空=未携带图片/无目标） */
  detections?: VoiceDetectionVO[]
}

/** V44 图片扫描升级：检测项（识别后图片上框出的目标） */
export interface VoiceDetectionVO {
  className: string
  confidence: number
  bbox: number[]
}

/** 后端 VoiceParseVO（F7 意图契约：BOOK_RECYCLING/QUERY_ORDER/QUERY_CARBON_ACCOUNT/...） */
interface VoiceParseVO {
  sessionId: string
  intent: string
  slots?: { category?: string; appointTime?: string; address?: string } | null
  replyText: string
  annotatedImageBase64?: string | null
  detections?: VoiceDetectionVO[] | null
}

const INTENT_MAP: Record<string, VoiceParseResult['intent']> = {
  BOOK_RECYCLING: 'RECYCLE_BOOK',
  QUERY_CARBON_ACCOUNT: 'QUERY_BILL',
  QUERY_ORDER: 'QUERY_BILL',
}

/** 会话 ID localStorage 键（同一次打开语音管家复用，刷新/重进后历史仍可回显） */
const VOICE_SESSION_KEY = 'voice-steward-session-id'

/** 会话 ID（首次生成后持久化到 localStorage，多轮补槽复用） */
export const getVoiceSessionId = (): string => {
  const cached = localStorage.getItem(VOICE_SESSION_KEY)
  if (cached) return cached
  const id = `web-${Date.now()}-${Math.floor(Math.random() * 1e4)}`
  localStorage.setItem(VOICE_SESSION_KEY, id)
  return id
}

/** 后端 VoiceSession 实体（biz_voice_session 逐轮轨迹，Long 序列化为字符串） */
interface VoiceSessionVO {
  sessionId: string
  turnNo: number
  userText: string
  intent: string
  slotsJson?: string | null
  replyText: string
  confidence?: number | null
  degraded?: number | null
  orderId?: string | null
  /** 本轮用户上传照片 URL（/api/file/preview/voice/...，未传图/保存失败为 null） */
  imageUrl?: string | null
}

/** 会话历史逐轮记录（用户文本 + 管家回复） */
export interface VoiceSessionTurn {
  userText: string
  replyText: string
  /** 本轮照片持久化 URL（voice 私有目录，展示需追加 access_token） */
  imageUrl?: string
}

/** F7 会话轨迹回显（GET /ai/voice/session/{sessionId}，无历史返回空数组） */
export async function aiVoiceSessionHistory(): Promise<VoiceSessionTurn[]> {
  const turns = await request.get<VoiceSessionVO[]>('/ai/voice/session/' + encodeURIComponent(getVoiceSessionId()))
  return (turns ?? []).map((t) => ({
    userText: t.userText ?? '',
    replyText: t.replyText ?? '',
    imageUrl: t.imageUrl ?? undefined,
  }))
}

/** F7 语音意图解析成单（ASR 文本 → 意图+槽位）；可选携带图片走 V44 图片扫描（识别后图片回传），
 * imageUrl 为照片持久化：前端先上传文件服务拿 URL，后端随会话轨迹落库 */
export async function aiVoiceParse(text: string, imageBase64?: string, imageUrl?: string): Promise<VoiceParseResult> {
  const vo = await request.post<VoiceParseVO>('/ai/voice/parse', {
    sessionId: getVoiceSessionId(),
    text,
    imageBase64: imageBase64 ?? null,
    imageUrl: imageUrl ?? null,
  })
  const slots: Record<string, string> = {}
  if (vo.slots?.category) slots.category = catOf(vo.slots.category)?.name ?? vo.slots.category
  if (vo.slots?.appointTime) slots.timeSlot = vo.slots.appointTime
  if (vo.slots?.address) slots.address = vo.slots.address
  return {
    intent: INTENT_MAP[vo.intent] ?? 'UNKNOWN',
    slots,
    reply: vo.replyText ?? '抱歉没听清，您可以说"帮我约个废纸回收"',
    annotatedImage: vo.annotatedImageBase64 ?? null,
    detections: vo.detections ?? [],
  }
}

/** F7 云端 ASR（/api/ai/voice/asr）：WAV 16k 单声道 16bit PCM base64 → 转写文本（空串=静音）。
 * Chrome 系 Web Speech API 走 Google 服务器国内不可达，改本地 vosk 链路；
 * 429/3001 由 request 层统一 toast 并抛 BizError。 */
export async function aiVoiceAsr(audioBase64: string): Promise<string> {
  const text = await request.post<string>('/ai/voice/asr', { audioBase64 })
  return text ?? ''
}

// ---------------- 实名认证（GapFill B1，V1：V17 状态机） ----------------
export interface RealNameStatus {
  /** 0未认证 1待审 2已通过 3已驳回 */
  status: 0 | 1 | 2 | 3
  /** 审核备注（驳回原因） */
  remark?: string
  realName?: string
}

export const submitRealName = (realName: string, idCard: string) =>
  request.put('/user/real-name', { realName, idCard })
export const getRealNameStatus = () => request.get<RealNameStatus>('/user/real-name-status')

// ---------------- 消息中心（GapFill B1，V4：MessageController 已就绪） ----------------
export interface UserMessage {
  /** 雪花 ID（Long 序列化为字符串，避免 JS Number 精度丢失） */
  id: string
  /** 消息类型（派单/完成/积分到账等） */
  msgType: string
  title: string
  content: string
  /** 0未读 1已读 */
  readFlag: 0 | 1
  createTime: string
}

/** 后端 BizMessage 实体（Long 序列化为字符串） */
interface BizMessageVO {
  id: string
  msgType: string
  title: string
  content: string
  readFlag: number
  createTime: string
}

const toUserMessage = (m: BizMessageVO): UserMessage => ({
  id: String(m.id),
  msgType: m.msgType ?? '',
  title: m.title ?? '',
  content: m.content ?? '',
  readFlag: Number(m.readFlag ?? 0) as 0 | 1,
  createTime: fmtTime(m.createTime),
})

export async function pageMessages(q: PageQuery): Promise<PageResult<UserMessage>> {
  const p = await request.get<PageVO<BizMessageVO>>('/message/list', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
    ...(q.readFlag !== undefined ? { readFlag: q.readFlag } : {}),
  })
  return toPage(p, q, toUserMessage)
}

export const getUnreadCount = async (): Promise<number> => {
  const n = await request.get<number | string>('/message/unread-count')
  return Number(n ?? 0)
}
export const markMessageRead = (id: string) => request.put(`/message/${id}/read`)
export const markAllMessagesRead = () => request.put('/message/read-all')

// ---------------- 消息分类映射（U5：前端 tab 过滤用，未知类型归「全部」） ----------------
/** 消息类型 → 消息页分类 tab 文本（订单蓝/积分金/交易绿，未知灰） */
export const MSG_GROUP_TEXT: Record<string, string> = {
  ORDER_CREATED: '订单',
  DISPATCHED: '订单',
  ORDER_FINISHED: '订单',
  FLEA_NEW_ORDER: '交易',
  FLEA_SHIPPED: '交易',
  FLEA_FINISHED: '交易',
  FLEA_CANCELLED: '交易',
  FLEA_REVIEWED: '交易',
  ITEM_OFF_SHELF: '交易',
  POINTS_ARRIVED: '积分',
  POINTS_FROZEN: '积分',
  KITCHEN_PENDING: '订单',
  AUDIT_REJECTED: '订单',
  NOTICE: '公告',
}

/** 消息页 tab 列表（key 'ALL'=全部，过滤时按 msgGroupOf 匹配） */
export const MSG_TABS = [
  { key: 'ALL', text: '全部' },
  { key: '订单', text: '订单' },
  { key: '积分', text: '积分' },
  { key: '交易', text: '交易' },
] as const

/** 消息类型 → 所属 tab 文本（未知类型回空串，仅出现在「全部」） */
export const msgGroupOf = (msgType: string): string => MSG_GROUP_TEXT[msgType] ?? ''

// ---------------- 投诉反馈（GapFill B1，V5：ComplaintController 已就绪） ----------------
export interface ComplaintItem {
  id: number
  /** RECYCLE / KITCHEN / REPAIR / MALL */
  orderType: string
  orderId: string
  /** 1投诉 2退款 3追责 4补发积分 5纠纷仲裁 */
  type: number
  content: string
  /** 0待处理 1已处理 2已驳回 */
  status: 0 | 1 | 2
  handleResult?: string
  handleTime?: string
  createTime: string
}

/** 后端 Complaint 实体 */
interface ComplaintVO {
  id: string
  orderType: string
  orderId: string
  type: number
  content: string
  status: number
  handleResult?: string | null
  handleTime?: string | null
  createTime: string
}

export const COMPLAINT_TYPE_TEXT: Record<number, string> = {
  1: '投诉',
  2: '退款',
  3: '追责',
  4: '补发积分',
  5: '纠纷仲裁',
}
export const COMPLAINT_STATUS_TEXT: Record<number, string> = {
  0: '待处理',
  1: '已处理',
  2: '已驳回',
}

const toComplaintItem = (c: ComplaintVO): ComplaintItem => ({
  id: Number(c.id),
  orderType: c.orderType ?? '',
  orderId: String(c.orderId ?? ''),
  type: Number(c.type ?? 0),
  content: c.content ?? '',
  status: Number(c.status ?? 0) as 0 | 1 | 2,
  handleResult: c.handleResult ?? undefined,
  handleTime: c.handleTime ? fmtTime(c.handleTime) : undefined,
  createTime: fmtTime(c.createTime),
})

/** 提交售后申请（orderId 未填时传 0，后端必填兜底；雪花 ID 保持字符串，Number 会丢精度） */
export async function submitComplaint(data: {
  orderType: string
  orderId?: string
  type: number
  content: string
}): Promise<ComplaintItem> {
  const c = await request.post<ComplaintVO>('/complaints', {
    orderType: data.orderType,
    orderId: data.orderId ?? '0',
    type: data.type,
    content: data.content,
  })
  return toComplaintItem(c)
}

export async function pageMyComplaints(q: PageQuery): Promise<PageResult<ComplaintItem>> {
  const p = await request.get<PageVO<ComplaintVO>>('/complaints', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
    ...(q.status !== undefined ? { status: q.status } : {}),
  })
  return toPage(p, q, toComplaintItem)
}

export const getComplaint = async (id: number): Promise<ComplaintItem> =>
  toComplaintItem(await request.get<ComplaintVO>(`/complaints/${id}`))

// ---------------- CAM 扫描识别积分（cam04，契约 C6-9） ----------------

/** 后端 ScanPointsVO（long 字段序列化为字符串，Number() 归一） */
export interface ScanPointsResult {
  /** 本次发放积分（命中日上限为 0） */
  points: number
  /** 当日累计发放次数（含本次） */
  dailyTotal: number
  /** 日上限（sys_config carbon.scan.daily_limit） */
  dailyLimit: number
  /** 是否命中日上限（前端静默处理，不提示） */
  hitLimit: boolean
}

/**
 * CAM 扫描识别积分发放（/carbon/scan-points，精分类成功后自动调用；
 * 失败/3001 静默降级，不阻断识别流程，契约 C6-5）
 */
export async function scanPoints(category: string, itemName: string): Promise<ScanPointsResult> {
  const vo = await request.post<ScanPointsResult>('/carbon/scan-points', { category, itemName })
  return {
    points: Number(vo.points ?? 0),
    dailyTotal: Number(vo.dailyTotal ?? 0),
    dailyLimit: Number(vo.dailyLimit ?? 0),
    hitLimit: Boolean(vo.hitLimit),
  }
}

// ---------------- 垃圾投放记录（LoopFill B4 G3，KitchenController 已就绪） ----------------
export interface KitchenRecordItem {
  /** 后端 Long 序列化为 string，保持字符串避免精度丢失 */
  id: string
  pointName: string
  bagCount: number
  reportTime: string
  /** 0待审核 1已通过 2已复核 3已驳回 */
  status: 0 | 1 | 2 | 3
}

/** 后端 KitchenReport 实体（GET /kitchen/reports，Long 序列化为字符串） */
interface KitchenReportVO {
  id: string
  pointName: string
  bagCount: number
  reportTime: string
  status: number
}

export const KITCHEN_STATUS_TEXT: Record<number, string> = {
  0: '待审核',
  1: '已通过',
  2: '已复核',
  3: '已驳回',
}

/** 我的垃圾投放记录分页 */
export async function pageKitchenReports(q: PageQuery): Promise<PageResult<KitchenRecordItem>> {
  const p = await request.get<PageVO<KitchenReportVO>>('/kitchen/reports', q)
  return toPage(p, q, (r) => ({
    id: String(r.id),
    pointName: r.pointName ?? '小区集中投放点',
    bagCount: Number(r.bagCount ?? 0),
    reportTime: fmtTime(r.reportTime),
    status: Number(r.status ?? 0) as 0 | 1 | 2 | 3,
  }))
}

/** 后端 KitchenMyStatsVO（GET /kitchen/stats/me） */
interface KitchenMyStatsVO {
  totalReports: number
  totalBags: number
  todayBags: number
  frozenReports: number
}

export interface KitchenMyStats {
  totalReports: number
  totalBags: number
  todayBags: number
  frozenReports: number
}

/** 个人垃圾投放累计（总上报/总袋数/今日袋数/待审核笔数） */
export async function getKitchenMyStats(): Promise<KitchenMyStats> {
  const s = await request.get<KitchenMyStatsVO>('/kitchen/stats/me')
  return {
    totalReports: Number(s.totalReports ?? 0),
    totalBags: Number(s.totalBags ?? 0),
    todayBags: Number(s.todayBags ?? 0),
    frozenReports: Number(s.frozenReports ?? 0),
  }
}

// ---------------- 碳减排可信证书（LoopFill B4 G7，GET /carbon/certificate/{logId}） ----------------
/** 后端 CarbonCertificateVO（Long 序列化为字符串；proof 为 Merkle 验证路径） */
interface CarbonCertificateVO {
  logId: string
  bizType: string
  remark: string
  points: number
  carbonReduced: number
  hashValid: boolean
  chainLinked: boolean
  anchored: boolean
  anchorDate?: string
  merkleRoot?: string
  rootMatched?: boolean | null
  proof?: { hash: string; left: boolean }[]
  valid: boolean
  createTime: string
}

export interface CarbonCertificate {
  /** 雪花 ID 保持字符串，避免 JS Number 精度丢失 */
  logId: string
  bizType: string
  remark: string
  points: number
  carbonReduced: number
  hashValid: boolean
  chainLinked: boolean
  anchored: boolean
  anchorDate: string
  merkleRoot: string
  /** 按验证路径重算根与锚定根一致（未锚定时为 null） */
  rootMatched: boolean | null
  proof: { hash: string; left: boolean }[]
  /** 综合结论：链完整且（已锚定时）可验证到根 */
  valid: boolean
  createTime: string
}

/** 碳减排可信证书（限本人，非本人后端 403）；logId 必须是未截断的雪花字符串 */
export async function getCarbonCertificate(logId: string): Promise<CarbonCertificate> {
  const c = await request.get<CarbonCertificateVO>(`/carbon/certificate/${logId}`)
  return {
    logId: String(c.logId),
    bizType: ORDER_TYPE_TEXT[c.bizType] ?? c.bizType ?? '',
    remark: c.remark ?? '',
    points: Number(c.points ?? 0),
    carbonReduced: Number(c.carbonReduced ?? 0),
    hashValid: Boolean(c.hashValid),
    chainLinked: Boolean(c.chainLinked),
    anchored: Boolean(c.anchored),
    anchorDate: c.anchorDate ?? '',
    merkleRoot: c.merkleRoot ?? '',
    rootMatched: c.rootMatched ?? null,
    proof: c.proof ?? [],
    valid: Boolean(c.valid),
    createTime: fmtTime(c.createTime),
  }
}

// ---------------- 变废为宝社区（LoopFill B6 创意 C2，/diy/*） ----------------
/** 后端 DiyPost 实体 / DiyPostVO（Long 序列化为字符串；likedByMe 为当前用户已赞态） */
interface DiyPostVO {
  id: string
  userId: string
  nickname?: string
  title: string
  content: string
  imageUrl?: string | null
  likes: number
  likedByMe?: boolean
  createTime?: string
}

export interface DiyPost {
  /** 雪花 ID（Long 序列化为字符串，避免 JS Number 精度丢失） */
  id: string
  userId: string
  nickname: string
  title: string
  content: string
  imageUrl?: string
  likes: number
  likedByMe: boolean
  createTime: string
}

/** 后端 DiyLikeResultVO（点赞/取消动作结果） */
interface DiyLikeResultVO {
  liked: boolean
  likes: number
}

export interface DiyLikeResult {
  liked: boolean
  likes: number
}

/** 后端 DiyRankVO（片区行） */
interface DiyRankVO {
  areaId: string
  areaName: string
  recycleKg: number
  kitchenBags: number
  carbonKg: number
  totalScore: number
}

/** 后端 DiyRankResultVO（榜单 + 我的片区排名） */
interface DiyRankResultVO {
  list: DiyRankVO[]
  myAreaId?: string | null
  myAreaRank?: number | null
}

export interface DiyRankItem {
  areaId: string
  areaName: string
  /** 回收实收重量 kg */
  recycleKg: number
  /** 垃圾投放袋数 */
  kitchenBags: number
  /** 碳减排 kg */
  carbonKg: number
  /** 加权总分 = recycleKg + kitchenBags×2 + carbonKg */
  totalScore: number
}

export interface DiyRankData {
  list: DiyRankItem[]
  myAreaId?: string
  /** 我的片区排名（1 起；未上榜为 null） */
  myAreaRank: number | null
}

/** 发布变废为宝作品（后端 @Idempotent 防重；标题 ≤64、内容 ≤500、图片选填） */
export function publishDiyPost(data: { title: string; content: string; imageUrl?: string }) {
  return request.post<DiyPostVO>('/diy/posts', {
    title: data.title,
    content: data.content,
    imageUrl: data.imageUrl ?? '',
  })
}

/** 作品列表分页（点赞数倒序） */
export async function pageDiyPosts(q: PageQuery): Promise<PageResult<DiyPost>> {
  const p = await request.get<PageVO<DiyPostVO>>('/diy/posts', q)
  return toPage(p, q, (x) => ({
    id: String(x.id),
    userId: String(x.userId),
    nickname: x.nickname ?? '环保达人',
    title: x.title ?? '',
    content: x.content ?? '',
    imageUrl: x.imageUrl ?? undefined,
    likes: Number(x.likes ?? 0),
    likedByMe: Boolean(x.likedByMe),
    createTime: fmtTime(x.createTime),
  }))
}

/** 点赞/取消点赞（幂等切换，返回最新 liked/likes） */
export async function likeDiyPost(id: string): Promise<DiyLikeResult> {
  const r = await request.post<DiyLikeResultVO>(`/diy/posts/${id}/like`)
  return { liked: Boolean(r.liked), likes: Number(r.likes ?? 0) }
}

/** 片区环保榜（回收 kg + 垃圾投放袋数 + 碳减排 kg 三指标聚合 + 我的排名） */
export async function getDiyRank(): Promise<DiyRankData> {
  const r = await request.get<DiyRankResultVO>('/diy/rank')
  return {
    list: (r.list ?? []).map((x) => ({
      areaId: String(x.areaId),
      areaName: x.areaName ?? '',
      recycleKg: Number(x.recycleKg ?? 0),
      kitchenBags: Number(x.kitchenBags ?? 0),
      carbonKg: Number(x.carbonKg ?? 0),
      totalScore: Number(x.totalScore ?? 0),
    })),
    myAreaId: r.myAreaId ?? undefined,
    myAreaRank: r.myAreaRank != null ? Number(r.myAreaRank) : null,
  }
}

// ---------------- 环保成就 · 签到 + 环保树（LoopFill B5 创意 C1，/api/eco/*） ----------------
/** 后端 EcoSignStatusVO.MonthDay（yyyy-MM-dd） */
interface EcoMonthDayVO {
  date: string
  signed: boolean
}

/** 后端 EcoSignStatusVO */
interface EcoSignStatusVO {
  monthDays: EcoMonthDayVO[]
  streakDays: number
  totalSigns: number
  signPoints: number
}

/** 签到状态（今日是否已签由 monthDays 推导，供 mine 入口角标） */
export interface EcoSignStatus {
  monthDays: { date: string; signed: boolean }[]
  streakDays: number
  totalSigns: number
  signPoints: number
  todaySigned: boolean
}

/** 成就卡片 */
export interface EcoAchievement {
  code: string
  name: string
  description: string
  points: number
  /** 未达成灰 / 已达成未领取橙 / 已领取绿 */
  unlocked: boolean
  claimed: boolean
}

/** 签到结果 */
export interface EcoSignResult {
  points: number
  streakDays: number
}

/** 领取结果 */
export interface EcoClaimResult {
  code: string
  points: number
}

/** 每日签到（POST /eco/sign-in，重复签到后端报错） */
export async function signIn(): Promise<EcoSignResult> {
  const r = await request.post<{ points: number; streakDays: number }>('/eco/sign-in')
  return { points: Number(r.points ?? 0), streakDays: Number(r.streakDays ?? 0) }
}

/** 签到状态（GET /eco/sign-in/status，本月日历 + 连续天数） */
export async function getEcoSignStatus(): Promise<EcoSignStatus> {
  const s = await request.get<EcoSignStatusVO>('/eco/sign-in/status')
  const monthDays = (s.monthDays ?? []).map((d) => ({ date: d.date, signed: Boolean(d.signed) }))
  const today = new Date().toISOString().slice(0, 10)
  return {
    monthDays,
    streakDays: Number(s.streakDays ?? 0),
    totalSigns: Number(s.totalSigns ?? 0),
    signPoints: Number(s.signPoints ?? 0),
    todaySigned: monthDays.some((d) => d.date === today && d.signed),
  }
}

/** 成就列表（GET /eco/achievements，全量 + 达成态） */
export async function pageEcoAchievements(): Promise<EcoAchievement[]> {
  const list = await request.get<{
    code: string
    name: string
    description: string
    points: number
    unlocked: boolean
    claimed: boolean
  }[]>('/eco/achievements')
  return (list ?? []).map((a) => ({
    code: a.code,
    name: a.name,
    description: a.description,
    points: Number(a.points ?? 0),
    unlocked: Boolean(a.unlocked),
    claimed: Boolean(a.claimed),
  }))
}

/** 领取成就奖励（POST /eco/achievements/{code}/claim，幂等） */
export async function claimEcoAchievement(code: string): Promise<EcoClaimResult> {
  const r = await request.post<{ code: string; points: number }>(`/eco/achievements/${code}/claim`)
  return { code: r.code, points: Number(r.points ?? 0) }
}

// ---------------- 跳蚤市场（Upgrade U4，契约对齐 upgrade_batch03 U3） ----------------
/** 跳蚤分类字典（与后端 FleaCategoryEnum 一致） */
export const FLEA_CATEGORIES = [
  { code: 'REUSABLE', name: '可回收再利用' },
  { code: 'TOY', name: '玩具' },
  { code: 'BOOK', name: '书籍' },
  { code: 'ELECTRONIC', name: '电子产品' },
  { code: 'HOME', name: '家居' },
  { code: 'OTHER', name: '其他' },
] as const

/** 商品状态文案（0下架 1在售 2已售出 3违规下架） */
export const FLEA_ITEM_STATUS_TEXT: Record<number, string> = {
  0: '已下架', 1: '在售', 2: '已售出', 3: '违规下架',
}

/** 订单状态文案（0待发货 1待收货 2已完成 3已取消） */
export const FLEA_ORDER_STATUS_TEXT: Record<number, string> = {
  0: '待发货', 1: '待收货', 2: '已完成', 3: '已取消',
}

/** 后端 FleaItemVO（Long 序列化为字符串；soldCount 为卖家累计已售出数） */
interface FleaItemVO {
  id: string
  sellerId?: string | number
  sellerName?: string
  title: string
  description: string
  category: string
  categoryText?: string
  price: number
  imageUrl?: string
  status: number
  soldCount?: number
  createTime?: string
}

export interface FleaItem {
  /** 雪花 ID（Long 序列化为字符串，Number 化会精度丢失致详情查无记录） */
  id: string
  sellerId: string
  sellerName: string
  title: string
  description: string
  category: string
  categoryText: string
  price: number
  imageUrl: string
  /** 0下架 1在售 2已售出 3违规下架 */
  status: 0 | 1 | 2 | 3
  /** 卖家累计已售出数 */
  soldCount: number
  createTime: string
}

const toFleaItem = (i: FleaItemVO): FleaItem => {
  const cat = FLEA_CATEGORIES.find((c) => c.code === i.category)
  return {
    id: String(i.id ?? ''),
    sellerId: String(i.sellerId ?? ''),
    sellerName: i.sellerName || '环保达人',
    title: i.title ?? '',
    description: i.description ?? '',
    category: i.category ?? 'OTHER',
    categoryText: cat?.name ?? i.categoryText ?? '其他',
    price: Number(i.price ?? 0),
    imageUrl: i.imageUrl ?? '',
    status: Number(i.status ?? 1) as FleaItem['status'],
    soldCount: Number(i.soldCount ?? 0),
    createTime: fmtTime(i.createTime),
  }
}

/** 后端 FleaOrderVO（Long 序列化为字符串；买家视角 buyerName 为对端，卖家视角反之） */
interface FleaOrderVO {
  id: string
  orderNo: string
  itemTitle?: string
  itemImage?: string
  price: number
  status: number
  address?: string
  buyerName?: string
  sellerName?: string
  rating?: number
  comment?: string
  createTime?: string
  shipTime?: string
  receiveTime?: string
  cancelTime?: string
}

export interface FleaOrder {
  /** 雪花 ID（Long 序列化为字符串，Number 化会精度丢失致操作查无记录） */
  id: string
  orderNo: string
  itemTitle: string
  itemImage: string
  price: number
  /** 0待发货 1待收货 2已完成 3已取消 */
  status: 0 | 1 | 2 | 3
  address: string
  /** 对端昵称（买家视角=卖家；卖家视角=买家） */
  peerName: string
  rating?: number
  comment?: string
  createTime: string
  shipTime?: string
  receiveTime?: string
  cancelTime?: string
}

const toFleaOrder = (o: FleaOrderVO): FleaOrder => ({
  id: String(o.id ?? ''),
  orderNo: o.orderNo ?? '',
  itemTitle: o.itemTitle ?? '',
  itemImage: o.itemImage ?? '',
  price: Number(o.price ?? 0),
  status: Number(o.status ?? 0) as FleaOrder['status'],
  address: o.address ?? '',
  peerName: o.buyerName || o.sellerName || '—',
  rating: o.rating != null ? Number(o.rating) : undefined,
  comment: o.comment ?? undefined,
  createTime: fmtTime(o.createTime),
  shipTime: o.shipTime ? fmtTime(o.shipTime) : undefined,
  receiveTime: o.receiveTime ? fmtTime(o.receiveTime) : undefined,
  cancelTime: o.cancelTime ? fmtTime(o.cancelTime) : undefined,
})

// ---- 商品 ----
/** 发布闲置（@Idempotent 防重；title ≤64、description ≤500、price ≥1、imageUrl 为上传返回 URL） */
export function publishFleaItem(data: {
  title: string
  description: string
  category: string
  price: number
  imageUrl?: string
}) {
  return request.post<FleaItemVO>('/flea/items', {
    title: data.title,
    description: data.description,
    category: data.category,
    price: data.price,
    imageUrl: data.imageUrl ?? '',
  })
}

/** 在售商品分页（category/keyword 可选过滤；状态恒为在售） */
export async function pageFleaItems(
  q: PageQuery,
  category?: string,
  keyword?: string,
): Promise<PageResult<FleaItem>> {
  const p = await request.get<PageVO<FleaItemVO>>('/flea/items', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
    ...(category ? { category } : {}),
    ...(keyword ? { keyword } : {}),
  })
  return toPage(p, q, toFleaItem)
}

/** 我的发布分页（本人全部状态；U4 追加契约 GET /flea/items/mine，U3 后端需补端，缺失时页面静默降级空列表） */
export async function pageFleaMineItems(q: PageQuery): Promise<PageResult<FleaItem>> {
  const p = await request.get<PageVO<FleaItemVO>>('/flea/items/mine', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
  })
  return toPage(p, q, toFleaItem)
}

/** 商品详情（已删/违规下架 → 404「商品不存在」） */
export const getFleaItem = async (id: string): Promise<FleaItem> =>
  toFleaItem(await request.get<FleaItemVO>(`/flea/items/${id}`))

/** 下架闲置（仅本人 + 在售） */
export const offFleaItem = (id: string) => request.put(`/flea/items/${id}/off`)

// ---- 订单（Long 序列化为字符串，雪花 ID 直传防精度丢失） ----
/** 下单（积分冻结托管；返回冻结积分） */
export async function createFleaOrder(
  itemId: string,
  address: string,
): Promise<{ orderNo: string; frozenPoints: number }> {
  const vo = await request.post<{ orderNo: string; frozenPoints?: number | string }>('/flea/orders', {
    itemId,
    address,
  })
  return { orderNo: vo.orderNo, frozenPoints: Number(vo.frozenPoints ?? 0) }
}

/** 我买到的分页（status 可选过滤） */
export async function pageFleaBuyerOrders(q: PageQuery, status?: number): Promise<PageResult<FleaOrder>> {
  const p = await request.get<PageVO<FleaOrderVO>>('/flea/orders/buyer', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
    ...(status !== undefined ? { status } : {}),
  })
  return toPage(p, q, toFleaOrder)
}

/** 我卖出的分页（status 可选过滤） */
export async function pageFleaSellerOrders(q: PageQuery, status?: number): Promise<PageResult<FleaOrder>> {
  const p = await request.get<PageVO<FleaOrderVO>>('/flea/orders/seller', {
    pageNum: q.pageNum,
    pageSize: q.pageSize,
    ...(status !== undefined ? { status } : {}),
  })
  return toPage(p, q, toFleaOrder)
}

/** 取消订单（仅买家 + 待发货；积分退回） */
export const cancelFleaOrder = (id: string) => request.put(`/flea/orders/${id}/cancel`)

/** 发货（仅卖家 + 待发货） */
export const shipFleaOrder = (id: string) => request.put(`/flea/orders/${id}/ship`)

/** 确认收货（仅买家 + 待收货；积分结算给卖家） */
export const receiveFleaOrder = (id: string) => request.put(`/flea/orders/${id}/receive`)

/** 评价（仅买家 + 已完成 + 未评过；rating 1-5、comment ≤200） */
export const reviewFleaOrder = (id: string, rating: number, comment?: string) =>
  request.put(`/flea/orders/${id}/review`, { rating, comment: comment ?? '' })
