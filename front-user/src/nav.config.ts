// ============================================================
// 两端同源导航数据（4 菜单：首页 / 主要页面 / 特色页面 / 消息）
// PC 侧栏（PcNav.vue）与移动端抽屉（App.vue）共用；
// 图标名对应 front-user/src/assets/icons 下的 sprite（icon-[dir]-[name]）
// ============================================================

export interface NavMenuItem {
  label: string
  icon: string
  /** PC 端路径（含 /pc 前缀） */
  pc: string
  /** 移动端路径（含 /mobile 前缀） */
  mobile: string
}

export interface NavGroup {
  title: string
  items: NavMenuItem[]
}

/** 菜单1：首页（直达） */
export const HOME_ITEM: NavMenuItem = {
  label: '首页',
  icon: 'nav-home',
  pc: '/pc/home',
  mobile: '/mobile/home',
}

/** 菜单4：消息（直达，带未读角标） */
export const MESSAGE_ITEM: NavMenuItem = {
  label: '消息',
  icon: 'carbon-email',
  pc: '/pc/user/message',
  mobile: '/mobile/user/message',
}

/** 菜单2：主要页面（4 子分组） */
export const mainGroups: NavGroup[] = [
  {
    title: '回收服务',
    items: [
      { label: '预约回收', icon: 'recycle-recycle-symbol', pc: '/pc/recycle/book', mobile: '/mobile/recycle/book' },
      { label: '垃圾投放', icon: 'kitchen-leafy-green', pc: '/pc/kitchen/drop', mobile: '/mobile/kitchen/drop' },
      { label: '维修服务', icon: 'repair-wrench', pc: '/pc/repair/book', mobile: '/mobile/repair/book' },
      { label: '回收站点', icon: 'nav-map', pc: '/pc/map/stations', mobile: '/mobile/map/stations' },
    ],
  },
  {
    title: '智能助手',
    items: [
      // 注意：AI 两页 PC/移动端路径前缀不一致（/pc/ai/* ↔ /mobile/*）
      { label: 'AR 分类', icon: 'ai-camera', pc: '/pc/ai/ar-scan', mobile: '/mobile/ar-scan' },
      { label: '语音管家', icon: 'ai-microphone', pc: '/pc/ai/voice-steward', mobile: '/mobile/voice-steward' },
      { label: '在线聊天', icon: 'nav-speech-balloon', pc: '/pc/im', mobile: '/mobile/im' },
    ],
  },
  {
    title: '低碳中心',
    items: [
      { label: '低碳账单', icon: 'carbon-carbon-leaf', pc: '/pc/carbon/bill', mobile: '/mobile/carbon/bill' },
      { label: '科普专栏', icon: 'nav-books', pc: '/pc/science', mobile: '/mobile/science' },
      { label: '环保树', icon: 'carbon-tree', pc: '/pc/eco/tree', mobile: '/mobile/eco/tree' },
      { label: '签到成就', icon: 'carbon-sprout', pc: '/pc/eco/sign', mobile: '/mobile/eco/sign' },
      { label: '碳减排证书', icon: 'carbon-user-cert', pc: '/pc/carbon/certificate', mobile: '/mobile/carbon/certificate' },
    ],
  },
  {
    title: '个人中心',
    items: [
      { label: '我的', icon: 'nav-user', pc: '/pc/mine', mobile: '/mobile/mine' },
      { label: '实名认证', icon: 'carbon-checked', pc: '/pc/user/realname', mobile: '/mobile/user/realname' },
      { label: '地址簿', icon: 'nav-location-pin', pc: '/pc/address-book', mobile: '/mobile/address-book' },
      { label: '反馈投诉', icon: 'common-lightbulb', pc: '/pc/user/complaint', mobile: '/mobile/user/complaint' },
      { label: '设置密码', icon: 'common-lock', pc: '/pc/user/password', mobile: '/mobile/user/password' },
      { label: '工作人员申请', icon: 'nav-file', pc: '/pc/staff/apply', mobile: '/mobile/staff/apply' },
    ],
  },
]

/** 菜单3：特色页面（3 子分组） */
export const featureGroups: NavGroup[] = [
  {
    title: '商城交易',
    items: [
      { label: '积分市场', icon: 'nav-shopping', pc: '/pc/mall', mobile: '/mobile/mall' },
      { label: '实时报价', icon: 'nav-money-bag', pc: '/pc/prices', mobile: '/mobile/prices' },
      { label: '我的订单', icon: 'nav-memo', pc: '/pc/orders', mobile: '/mobile/orders' },
      { label: '我的徽章', icon: 'nav-trophy', pc: '/pc/mall/badges', mobile: '/mobile/mall/badges' },
      { label: '我的兑换单', icon: 'carbon-shopping-bag', pc: '/pc/mall/exchange', mobile: '/mobile/mall/exchange' },
    ],
  },
  {
    title: '社区互动',
    items: [
      { label: '变废为宝社区', icon: 'nav-star', pc: '/pc/community', mobile: '/mobile/community' },
      { label: '环保排行榜', icon: 'nav-chart', pc: '/pc/community/rank', mobile: '/mobile/community/rank' },
    ],
  },
  {
    title: '绿色足迹',
    items: [
      { label: '垃圾投放记录', icon: 'recycle-trash-bin', pc: '/pc/kitchen/records', mobile: '/mobile/kitchen/records' },
      // PC 端无独立速查页：科普专栏含搜索+分类浏览，功能覆盖速查
      { label: '分类知识速查', icon: 'nav-search', pc: '/pc/science', mobile: '/mobile/science/knowledge' },
    ],
  },
]

/** 4 菜单顺序（底栏渲染用；main/feature 为抽屉入口） */
export const TAB_MENUS = [
  { key: 'home', label: '首页', icon: 'nav-home' },
  { key: 'main', label: '主要页面', icon: 'nav-star' },
  { key: 'feature', label: '特色页面', icon: 'nav-books' },
  { key: 'message', label: '消息', icon: 'carbon-email' },
] as const

export type TabMenuKey = (typeof TAB_MENUS)[number]['key']
