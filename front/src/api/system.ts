// ============================================================
// 系统管理域 API（用户/角色/菜单/字典/参数/公告/操作日志，对接 Track A）
// 联调期已对齐后端真实路径（/system/role/page、/system/menu/tree、
// /system/dict/*、/system/config/*、/system/notice/*、/system/log/oper/page）；
// 后端实体字段与页面 VO 差异在本层适配（roleCode↔roleKey、config↔param、
// dictLabel↔label、costMs↔costTime…），页面零改动。
// 用户 CRUD 已对接 V8 后真实接口（分页返 roleName）
// ============================================================
import type { PageQuery, PageResult } from '@shared/http/types'
import { request } from './request'

export interface SysUser {
  id: number
  username: string
  nickname: string
  email: string
  /** 头像 URL（Upgrade U1：有则 <img> 展示，无则图标兜底） */
  avatar?: string
  roleName: string
  role?: string
  status: 0 | 1
  /** 实名状态 0未认证 1待审 2已通过 3已驳回（GapFill B1） */
  realNameStatus?: 0 | 1 | 2 | 3
  /** 审核备注（驳回原因） */
  realNameRemark?: string
  createTime: string
}

export interface SysRole {
  id: number
  roleName: string
  roleKey: string
  remark: string
  status: 0 | 1
  createTime: string
}

export interface SysMenu {
  /** 雪花 Long 序列化为字符串，必须保持字符串（Number 转换会丢精度） */
  id: string
  parentId: string
  title: string
  path: string
  component: string
  perms: string
  icon: string
  orderNum: number
  /** 1 目录 / 2 菜单 / 3 按钮（M-02 起透传，菜单表单/操作列判断用） */
  menuType?: 1 | 2 | 3
  visible?: 0 | 1
  status?: 0 | 1
  children?: SysMenu[]
}

export interface DictItem {
  /** 数据项 id（雪花 Long 序列化为字符串，保持字符串防精度丢失） */
  id?: string
  label: string
  value: string
  sort: number
}

export interface SysDict {
  id: string
  dictName: string
  dictType: string
  items: DictItem[]
  remark: string
  status?: 0 | 1
}

export interface SysParam {
  id: number
  paramName: string
  paramKey: string
  paramValue: string
  remark: string
}

export interface SysNotice {
  id: number
  title: string
  type: '通知' | '公告'
  /** 内容（通知群发站内信正文） */
  content?: string
  /** 目标端：ALL / USER / STAFF / ADMIN */
  target?: string
  status: 0 | 1
  createBy: string
  createTime: string
}

export interface OperLog {
  id: number
  title: string
  method: string
  operName: string
  operIp: string
  status: 0 | 1
  costTime: number
  operTime: string
}

// ---------------- 用户（已对接 V8 后真实接口：POST 补 role 码映射） ----------------
export const pageUsers = (q: PageQuery) => request.get<PageResult<SysUser>>('/system/users', q)
export const saveUser = (data: Partial<SysUser>) => {
  // roleName 中文 → role 码；编辑回传后端 role 码（避免未收录中文名被降级为 USER）
  const ROLE_CODE: Record<string, string> = { 管理员: 'ADMIN', 用户: 'USER', 回收员: 'RECYCLER', 环卫员: 'SANITATION', 技术员: 'TECHNICIAN', 运营专员: 'USER' }
  const body = { ...data, role: ROLE_CODE[data.roleName ?? ''] ?? data.role ?? 'USER' }
  // C5：新增用户返回一次性初始密码（编辑返回 null）
  return request.post<string | null>('/system/users', body)
}
export const removeUser = (id: number) => request.delete(`/system/users/${id}`)
// 实名审核（GapFill B1）：pass=true 通过 / false 驳回（写 remark）
export const auditUserRealName = (id: number, pass: boolean, remark?: string) =>
  request.put(`/system/users/${id}/real-name-audit`, { pass, remark })

// ---------------- 角色（后端 SysRole.roleCode ↔ 页面 roleKey） ----------------
type RoleRow = SysRole & { roleCode?: string }

export async function pageRoles(q: PageQuery): Promise<PageResult<SysRole>> {
  const page = await request.get<PageResult<RoleRow>>('/system/role/page', q)
  return { ...page, list: (page.list ?? []).map((r) => ({ ...r, roleKey: r.roleKey ?? r.roleCode ?? '' })) }
}

export const saveRole = (data: Partial<SysRole>) => {
  const body = { ...data, roleCode: data.roleKey }
  return data.id ? request.put('/system/role', body) : request.post('/system/role', body)
}
export const removeRole = (id: number) => request.delete(`/system/role/${id}`)

// ---- 分配菜单（M-02：全量覆盖语义，提交需含半选父级） ----
// ---- 分配菜单（M-02：全量覆盖语义，提交需含半选父级；menuIds 为雪花 id 字符串，勿转 Number） ----
export const assignRoleMenus = (roleId: number, menuIds: string[]) =>
  request.post('/system/role/assign-menu', { roleId, menuIds })
export const getRoleMenuIds = async (id: number): Promise<string[]> =>
  (await request.get<(string | number)[]>(`/system/role/${id}/menu-ids`))?.map(String) ?? []

// ---------------- 菜单（后端 MenuTreeVO → 页面 SysMenu） ----------------
interface MenuRow {
  id: number | string
  parentId: number | string
  menuName: string
  menuType: number
  path: string
  component: string
  perm: string
  icon: string
  sort: number
  visible: number
  status: number
  children?: MenuRow[]
}

const toSysMenu = (m: MenuRow): SysMenu => ({
  id: String(m.id),
  parentId: String(m.parentId),
  title: m.menuName,
  path: m.path,
  component: m.component ?? '',
  perms: m.perm ?? '',
  icon: m.icon ?? '',
  orderNum: m.sort ?? 0,
  menuType: m.menuType as 1 | 2 | 3 | undefined,
  visible: m.visible as 0 | 1 | undefined,
  status: m.status as 0 | 1 | undefined,
  ...(m.children?.length ? { children: m.children.map(toSysMenu) } : {}),
})

export const listMenus = async (): Promise<SysMenu[]> =>
  (await request.get<MenuRow[]>('/system/menu/tree')).map(toSysMenu)

// ---- 菜单 CRUD（M-02：MenuDTO 字段与后端一一对应；id 保持字符串防精度丢失） ----
export interface MenuForm {
  id?: string
  parentId: string
  menuName: string
  menuType: 1 | 2 | 3
  path?: string
  component?: string
  perm?: string
  icon?: string
  sort?: number
  visible?: 0 | 1
  status?: 0 | 1
}

export const saveMenu = (data: MenuForm) =>
  data.id ? request.put('/system/menu', data) : request.post('/system/menu', data)
export const removeMenu = (id: string) => request.delete(`/system/menu/${id}`)

// ---------------- 字典（类型分页 + 数据项；后端 dictLabel/dictValue） ----------------
type DictDataRow = Partial<DictItem> & { dictLabel?: string; dictValue?: string; sort?: number }

export async function getDictByType(type: string): Promise<DictItem[]> {
  const rows = await request.get<DictDataRow[]>(`/system/dict/data/${type}`)
  return (rows ?? []).map((r, i) => ({
    id: r.id != null ? String(r.id) : undefined,
    label: r.label ?? r.dictLabel ?? '',
    value: r.value ?? r.dictValue ?? '',
    sort: r.sort ?? i + 1,
  }))
}

export async function pageDicts(q: PageQuery): Promise<PageResult<SysDict>> {
  const page = await request.get<PageResult<SysDict>>('/system/dict/type/page', q)
  // 后端类型分页不含数据项：逐类型补拉（类型数少）
  const list = await Promise.all(
    (page.list ?? []).map(async (d) => ({ ...d, items: d.items ?? (await getDictByType(d.dictType)) })),
  )
  return { ...page, list }
}

// ---- 字典类型/数据 CRUD（M-02：DTO 字段与后端一一对应；id 保持字符串防精度丢失） ----
export interface DictTypeForm {
  id?: string
  dictType: string
  dictName: string
  remark?: string
  status?: 0 | 1
}

export interface DictDataForm {
  id?: string
  dictType: string
  dictLabel: string
  dictValue: string
  sort?: number
  remark?: string
  status?: 0 | 1
}

export const saveDictType = (data: DictTypeForm) =>
  data.id ? request.put('/system/dict/type', data) : request.post('/system/dict/type', data)
export const removeDictType = (id: string) => request.delete(`/system/dict/type/${id}`)
export const saveDictData = (data: DictDataForm) =>
  data.id ? request.put('/system/dict/data', data) : request.post('/system/dict/data', data)
// 注意：后端删除数据项实际路径为 /data/id/{id}（与类型删除区分）
export const removeDictData = (id: string) => request.delete(`/system/dict/data/id/${id}`)

// ---------------- 参数（后端 SysConfig ↔ 页面 SysParam） ----------------
type ConfigRow = Partial<SysParam> & { configName?: string; configKey?: string; configValue?: string; remark?: string }

export async function pageParams(q: PageQuery): Promise<PageResult<SysParam>> {
  const page = await request.get<PageResult<ConfigRow>>('/system/config/page', q)
  return {
    ...page,
    list: (page.list ?? []).map((r) => ({
      id: r.id ?? 0,
      paramName: r.paramName ?? r.configName ?? '',
      paramKey: r.paramKey ?? r.configKey ?? '',
      paramValue: r.paramValue ?? r.configValue ?? '',
      remark: r.remark ?? '',
    })),
  }
}

export const saveParam = (d: Partial<SysParam>) => {
  const body = { id: d.id, configName: d.paramName, configKey: d.paramKey, configValue: d.paramValue, remark: d.remark }
  return d.id ? request.put('/system/config', body) : request.post('/system/config', body)
}

// ---------------- 公告（后端 noticeType 1通知/2公告 ↔ 页面中文枚举） ----------------
type NoticeRow = Partial<SysNotice> & { noticeType?: number; publishTime?: string }

export async function pageNotices(q: PageQuery): Promise<PageResult<SysNotice>> {
  const page = await request.get<PageResult<NoticeRow>>('/system/notice/page', q)
  return {
    ...page,
    list: (page.list ?? []).map((r) => ({
      id: r.id ?? 0,
      title: r.title ?? '',
      type: r.type ?? (r.noticeType === 2 ? '公告' : '通知'),
      status: r.status ?? 1,
      createBy: r.createBy ?? 'admin',
      createTime: r.createTime ?? r.publishTime ?? '',
    })),
  }
}

export const saveNotice = (d: Partial<SysNotice>) => {
  const body = { ...d, noticeType: d.type === '公告' ? 2 : 1 }
  return d.id ? request.put('/system/notice', body) : request.post('/system/notice', body)
}

// ---- 公告删除（M-02） ----
export const removeNotice = (id: number) => request.delete(`/system/notice/${id}`)

// ---------------- 操作日志（后端 module/operation/costMs ↔ 页面 title/costTime） ----------------
type OperLogRow = Partial<OperLog> & { module?: string; operation?: string; costMs?: number }

export async function pageOperLogs(q: PageQuery): Promise<PageResult<OperLog>> {
  const page = await request.get<PageResult<OperLogRow>>('/system/log/oper/page', q)
  return {
    ...page,
    list: (page.list ?? []).map((r) => ({
      id: r.id ?? 0,
      title: r.title ?? [r.module, r.operation].filter(Boolean).join(' · '),
      method: r.method ?? '',
      operName: r.operName ?? '',
      operIp: r.operIp ?? '',
      status: r.status ?? 1,
      costTime: r.costTime ?? r.costMs ?? 0,
      operTime: r.operTime ?? '',
    })),
  }
}

// ---------------- 登录日志（B3，LogController /system/log/login/page，菜单 108） ----------------

/** 登录日志行（sys_login_log） */
export interface LoginLog {
  id: number
  username: string
  /** USER / STAFF / ADMIN */
  clientType: string
  loginIp: string
  userAgent: string
  /** 0 成功 / 1 失败 */
  status: 0 | 1
  /** 失败原因 */
  msg: string | null
  loginTime: string
}

export const pageLoginLogs = (q: PageQuery) =>
  request.get<PageResult<LoginLog>>('/system/log/login/page', q)
