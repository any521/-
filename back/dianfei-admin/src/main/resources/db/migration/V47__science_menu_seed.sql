-- ============================================================
-- V47 · 科普专栏管理菜单种子：业务管理目录新增「科普专栏」菜单 + 按钮权限
-- 后端 ScienceAdminController（/api/system/science）已就绪，
-- 管理端页面 front/src/views/business/science/index.vue（列表/审核/上传/积分）。
-- 编号：211 与 201-210 同级业务目录（父 200，sort 11 接徽章管理之后）；21101 按钮码。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 业务管理：科普专栏菜单（父 200，sort 11） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (211, 200, '科普专栏', 2, 'science', 'business/science', 'business:science:list', 'Reading', 11, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 按钮权限：科普维护（上传/审核/上下架/积分，父 211） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (21101, 211, '科普维护', 3, NULL, NULL, 'business:science:save', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (211, 21101)
ON DUPLICATE KEY UPDATE role_id = role_id;
