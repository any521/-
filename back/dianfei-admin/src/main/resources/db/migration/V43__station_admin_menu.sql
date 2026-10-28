-- ============================================================
-- V43 · 垃圾站点管理菜单种子：业务管理目录新增「站点管理」菜单 + 按钮权限
-- 后端 AdminStationController（/api/admin/stations）已就绪，
-- 管理员地图选点（AddressPicker）设置垃圾站点，用户端据此展示最近站点与最优路线。
-- 写法参照 V19：INSERT + ON DUPLICATE KEY UPDATE + sys_role_menu 补绑 ADMIN（role_id=1）。
-- 编号：209 与 201-208 同级业务目录（父 200，sort 9）；20901 按钮码。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 业务管理：站点管理菜单（父 200，sort 9 接员工片区之后） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (209, 200, '站点管理', 2, 'station', 'business/station', 'business:station:list', 'Location', 9, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 按钮权限：站点维护（父 209） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (20901, 209, '站点维护', 3, NULL, NULL, 'business:station:save', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (209, 20901)
ON DUPLICATE KEY UPDATE role_id = role_id;
