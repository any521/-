-- ============================================================
-- V19 · 员工片区菜单种子（GapFill B3 交付）：业务管理目录新增「员工片区」菜单 + 按钮权限
-- 背景：缺口 V9 管理端员工/片区管理页——后端 AdminStaffController/AdminAreaController
--       已就绪（/api/admin/staff、/api/admin/areas），需菜单种子驱动动态路由与 v-permission。
-- 写法参照 V15/V8：INSERT + ON DUPLICATE KEY UPDATE + sys_role_menu 补绑 ADMIN（role_id=1）。
-- 编号：208 与 201-207 同级业务目录（父 200，sort 8 接排班运维之后）；20801/20802 按钮码。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 业务管理：员工片区菜单（父 200，sort 8 接排班运维之后） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (208, 200, '员工片区', 2, 'staff-area', 'business/staff-area', 'business:staff:list', 'UserFilled', 8, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 按钮权限：员工维护 + 片区维护（父 208） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (20801, 208, '员工维护', 3, NULL, NULL, 'business:staff:save', NULL, 1, 1, 1, NOW(), 0),
       (20802, 208, '片区维护', 3, NULL, NULL, 'business:area:save', NULL, 2, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (208, 20801, 20802)
ON DUPLICATE KEY UPDATE role_id = role_id;
