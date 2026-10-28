-- ============================================================
-- 积分市场合并升级：成就徽章（积分兑换虚拟收藏品）
-- 2 表：biz_badge（徽章定义，内置 10 枚种子，管理员可维护）/
--       biz_badge_exchange（徽章兑换记录，虚拟商品兑换即到账，无物流状态机）
-- 扣分一律走碳账本 CarbonLedgerService.consume（order_type=BADGE，幂等键
-- BADGE:{exchangeId}:CONSUME），不旁路哈希链与账户锁；
-- 管理端菜单 210（徽章管理）挂业务管理 200 下 sort=10；
-- 本迁移不动 init.sql 与 V1~V45。
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_badge (
  id          BIGINT PRIMARY KEY,
  code        VARCHAR(32) NOT NULL UNIQUE COMMENT '徽章编码，稳定标识',
  name        VARCHAR(32) NOT NULL,
  description VARCHAR(128) NOT NULL,
  price       BIGINT      NOT NULL COMMENT '兑换所需积分 ≥1',
  icon_url    VARCHAR(255) COMMENT '徽章图标/封面（/api/file/upload?biz=badge）',
  sort        INT         NOT NULL DEFAULT 0,
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64), deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_badge_status (status)
) COMMENT '成就徽章（积分兑换虚拟收藏品）';

CREATE TABLE IF NOT EXISTS biz_badge_exchange (
  id            BIGINT PRIMARY KEY,
  exchange_no   VARCHAR(32) NOT NULL UNIQUE COMMENT '雪花号，前缀 BE',
  user_id       BIGINT      NOT NULL,
  badge_id      BIGINT      NOT NULL,
  badge_name    VARCHAR(32) NOT NULL COMMENT '徽章名快照',
  badge_icon    VARCHAR(255) COMMENT '徽章图标快照',
  points_cost   BIGINT      NOT NULL COMMENT '本单扣减积分',
  carbon_log_id BIGINT      COMMENT '关联扣分流水（溯源）',
  status        TINYINT     NOT NULL DEFAULT 0 COMMENT '0已兑换（虚拟商品即到账）',
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64), deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_badge_ex_user_badge (user_id, badge_id),
  KEY idx_badge_ex_user (user_id)
) COMMENT '徽章兑换记录';

-- 内置 10 枚成就徽章种子（id 9501-9510 避开业务雪花号；icon_url 留空由管理端补传）
INSERT INTO biz_badge (id, code, name, description, price, icon_url, sort, status, create_time, update_time, create_by, deleted) VALUES
  (9501, 'GREEN_PIONEER',   '绿动先锋',    '迈出环保第一步，兑换即获首枚成长徽章',            50,   NULL, 1, 1, NOW(), NOW(), 'seed', 0),
  (9502, 'SORT_MASTER',     '分类达人',    '垃圾分类信手拈来，做社区的分类表率',              80,   NULL, 2, 1, NOW(), NOW(), 'seed', 0),
  (9503, 'RECYCLE_STAR',    '回收之星',    '坚持预约回收，让可回收物重获新生',                150,  NULL, 3, 1, NOW(), NOW(), 'seed', 0),
  (9504, 'KITCHEN_GUARD',   '厨余卫士',    '厨余归位每一天，为低碳堆肥出一份力',              150,  NULL, 4, 1, NOW(), NOW(), 'seed', 0),
  (9505, 'STREAK_KNIGHT',   '连续之星',    '风雨无阻的坚持，连续签到 7 天以上',                200,  NULL, 5, 1, NOW(), NOW(), 'seed', 0),
  (9506, 'LOW_CARB_WALKER', '低碳行者',    '低碳足迹遍布生活，出行节约每一克碳',              300,  NULL, 6, 1, NOW(), NOW(), 'seed', 0),
  (9507, 'TRASH_TO_GOLD',   '变废为宝',    '让闲置流动起来，二手交易循环再利用',              400,  NULL, 7, 1, NOW(), NOW(), 'seed', 0),
  (9508, 'ECO_AMBASSADOR',  '环保使者',    '传播环保理念，带动身边人一起行动',                500,  NULL, 8, 1, NOW(), NOW(), 'seed', 0),
  (9509, 'CARBON_PIONEER',  '碳汇先锋',    '累计减排量领先，向碳中和目标大步迈进',            800,  NULL, 9, 1, NOW(), NOW(), 'seed', 0),
  (9510, 'ECO_MASTER',      '环保大师',    '全维度环保达人，点废成金的最高荣誉',              1000, NULL, 10, 1, NOW(), NOW(), 'seed', 0)
ON DUPLICATE KEY UPDATE name = name;

-- 管理端菜单：徽章管理（业务管理 200 下 sort=10，视图 front/src/views/business/badge.vue）
SET NAMES utf8mb4;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (210, 200, '徽章管理', 2, 'badge', 'business/badge', 'business:badge:list', 'Aim', 10, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- 徽章维护按钮（新增/编辑/上下架）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (21001, 210, '徽章维护', 3, NULL, NULL, 'business:badge:save', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ADMIN 角色补绑
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (210, 21001)
ON DUPLICATE KEY UPDATE role_id = role_id;
