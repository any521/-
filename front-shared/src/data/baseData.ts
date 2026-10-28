// ============================================================
// 共享基础数据（对齐冻结契约的字典/品类，三端通用）
// 业务规则配置化（决策 D5）：品类/报价/积分规则均来自字典
// ============================================================

/** 可回收品类（biz_category 冻结样例，配置化可扩展） */
export interface RecycleCategory {
  code: string
  name: string
  icon: string
  /** 当日报价 元/kg */
  price: number
  /** 每 kg 折算碳减排 kgCO₂ */
  carbonFactor: number
}

export const RECYCLE_CATEGORIES: RecycleCategory[] = [
  { code: 'paper', name: '废纸类', icon: 'recycle-paper', price: 1.2, carbonFactor: 1.4 },
  { code: 'plastic', name: '塑料类', icon: 'recycle-lotion-bottle', price: 1.6, carbonFactor: 2.1 },
  { code: 'metal', name: '金属类', icon: 'recycle-can', price: 3.8, carbonFactor: 4.3 },
  { code: 'fabric', name: '织物类', icon: 'recycle-shirt', price: 0.8, carbonFactor: 3.2 },
  { code: 'appliance', name: '家电类', icon: 'recycle-tv', price: 6.5, carbonFactor: 8.7 },
  { code: 'electronics', name: '电子产品', icon: 'recycle-phone', price: 12.0, carbonFactor: 12.5 },
  { code: 'glass', name: '玻璃类', icon: 'recycle-bottle', price: 0.3, carbonFactor: 0.6 },
  { code: 'kitchen', name: '厨余垃圾', icon: 'recycle-kitchen', price: 0, carbonFactor: 0.5 },
]

/** 片区列表（从后端 API /api/staff/areas 动态加载，自动创建新片区） */
export interface Area {
  id: number
  code: string
  name: string
  address?: string
}

/** 预约时段 */
export const TIME_SLOTS = ['09:00-11:00', '11:00-13:00', '14:00-16:00', '16:00-18:00', '19:00-21:00']

/** 垃圾投放时段提示（memory.md 业务规则：每晚 20-23 点） */
export const KITCHEN_DROP_HOURS = { start: 20, end: 23 }
export function isKitchenDropOpen(now = new Date()): boolean {
  const h = now.getHours()
  return h >= KITCHEN_DROP_HOURS.start && h < KITCHEN_DROP_HOURS.end
}

/** 维修品类 */
export const REPAIR_TYPES = [
  { code: 'small_appliance', name: '小家电' },
  { code: 'phone_digital', name: '手机数码' },
  { code: 'kitchen_appliance', name: '厨电' },
  { code: 'lighting_circuit', name: '灯具电路' },
]

/** 员工角色（三类员工，memory.md 术语表） */
export const STAFF_ROLES = [
  { code: 'RECYCLER', name: '再生资源回收员' },
  { code: 'SANITATION', name: '环卫收运员' },
  { code: 'TECHNICIAN', name: '电路维修技术员' },
] as const

/** IM 内置表情包（onsite03 独占新增，其他批次勿触碰本文件） */
export interface ImSticker {
  /** 消息 content 存 key，渲染时映射 svgName */
  key: string
  /** 对应 SVG 图标名（SvgIcon name="sticker-xxx"） */
  svgName: string
  name: string
}

export const IM_STICKERS: ImSticker[] = [
  { key: 'st_01', svgName: 'sticker-smile', name: '微笑' },
  { key: 'st_02', svgName: 'sticker-happy', name: '开心' },
  { key: 'st_03', svgName: 'sticker-laugh', name: '笑哭' },
  { key: 'st_04', svgName: 'sticker-hug', name: '抱抱' },
  { key: 'st_05', svgName: 'sticker-thumbs-up', name: '赞' },
  { key: 'st_06', svgName: 'sticker-clap', name: '鼓掌' },
  { key: 'st_07', svgName: 'sticker-thanks', name: '感谢' },
  { key: 'st_08', svgName: 'sticker-handshake', name: '握手' },
  { key: 'st_09', svgName: 'sticker-muscle', name: '加油' },
  { key: 'st_10', svgName: 'sticker-sweat', name: '辛苦' },
  { key: 'st_11', svgName: 'sticker-clock', name: '准时' },
  { key: 'st_12', svgName: 'sticker-check', name: '好的' },
  { key: 'st_13', svgName: 'sticker-question', name: '疑问' },
  { key: 'st_14', svgName: 'sticker-thinking', name: '思考' },
  { key: 'st_15', svgName: 'sticker-eco-heart', name: '环保' },
  { key: 'st_16', svgName: 'sticker-sprout', name: '绿芽' },
]

/** 按 key 取表情（聊天页渲染 STICKER 消息） */
export const imStickerOf = (key: string) => IM_STICKERS.find((s) => s.key === key)

export type StaffRole = (typeof STAFF_ROLES)[number]['code']
