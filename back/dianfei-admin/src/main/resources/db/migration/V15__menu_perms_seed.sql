-- ============================================================
-- V15 · 按钮权限种子补齐（M-02 交付）：补齐既有页面缺失码 + 菜单/字典/公告新增码
-- 背景：ProfileVO.perms 完全由 sys_role_menu → sys_menu.perm（menu_type=3）收集，
--       无 *:*:* 通配；前端 v-permission 对缺失码直接移除 DOM。
--       经全量比对 11 个既有页面按钮码未种子化（notice/param/ops/risk/aftersale/mall/decision），
--       本迁移一次性补齐 C3 表 18 条按钮码（10301-10303 / 10401-10403 / 10501 /
--       10601-10603 / 20401-20402 / 20501 / 20601 / 20701-20703 / 30201）。
-- 注意：V15 编号保留；V16 已预留给 M-03（回收订单操作权限）。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 系统管理：菜单/字典/参数/公告按钮（父 103/104/105/106） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (10301, 103, '菜单新增', 3, NULL, NULL, 'system:menu:add',       NULL, 1, 1, 1, NOW(), 0),
       (10302, 103, '菜单编辑', 3, NULL, NULL, 'system:menu:edit',      NULL, 2, 1, 1, NOW(), 0),
       (10303, 103, '菜单删除', 3, NULL, NULL, 'system:menu:remove',    NULL, 3, 1, 1, NOW(), 0),
       (10401, 104, '字典新增', 3, NULL, NULL, 'system:dict:add',       NULL, 1, 1, 1, NOW(), 0),
       (10402, 104, '字典编辑', 3, NULL, NULL, 'system:dict:edit',      NULL, 2, 1, 1, NOW(), 0),
       (10403, 104, '字典删除', 3, NULL, NULL, 'system:dict:remove',    NULL, 3, 1, 1, NOW(), 0),
       (10501, 105, '参数修改', 3, NULL, NULL, 'system:param:edit',     NULL, 1, 1, 1, NOW(), 0),
       (10601, 106, '公告发布', 3, NULL, NULL, 'system:notice:add',     NULL, 1, 1, 1, NOW(), 0),
       (10602, 106, '公告编辑', 3, NULL, NULL, 'system:notice:edit',    NULL, 2, 1, 1, NOW(), 0),
       (10603, 106, '公告删除', 3, NULL, NULL, 'system:notice:remove',  NULL, 3, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 业务管理：商城/风控/售后/排班按钮（父 204/205/206/207） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (20401, 204, '商品新增', 3, NULL, NULL, 'business:mall:add',          NULL, 1, 1, 1, NOW(), 0),
       (20402, 204, '商品编辑', 3, NULL, NULL, 'business:mall:edit',         NULL, 2, 1, 1, NOW(), 0),
       (20501, 205, '告警复核', 3, NULL, NULL, 'business:risk:audit',        NULL, 1, 1, 1, NOW(), 0),
       (20601, 206, '售后处置', 3, NULL, NULL, 'business:aftersale:handle',  NULL, 1, 1, 1, NOW(), 0),
       (20701, 207, '排班新增', 3, NULL, NULL, 'business:ops:add',           NULL, 1, 1, 1, NOW(), 0),
       (20702, 207, '排班删除', 3, NULL, NULL, 'business:ops:delete',        NULL, 2, 1, 1, NOW(), 0),
       (20703, 207, '请假审核', 3, NULL, NULL, 'business:ops:audit',         NULL, 3, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 智能运营：Agent 驾驶舱按钮（父 302） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (30201, 302, '决策审批', 3, NULL, NULL, 'smart:decision:approve', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑新按钮 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (10301, 10302, 10303, 10401, 10402, 10403, 10501, 10601, 10602, 10603,
                                        20401, 20402, 20501, 20601, 20701, 20702, 20703, 30201)
ON DUPLICATE KEY UPDATE role_id = role_id;
