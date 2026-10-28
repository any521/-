-- ============================================================
-- V57 · 变废为宝作品管理菜单种子：业务管理目录新增「变废为宝作品」菜单 + 按钮权限
-- 后端 DiyAdminController（/api/system/diy）已就绪，
-- 管理端页面 front/src/views/business/diy/index.vue（列表/搜索/删除）。
-- 编号：213 与 201-212 同级业务目录（父 200，sort 12 接科普专栏之后）；21301 按钮码。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 业务管理：变废为宝作品菜单（父 200，sort 12） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (213, 200, '变废为宝作品', 2, 'diy', 'business/diy', 'business:diy:list', 'Bulb', 12, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 按钮权限：作品维护（删除，父 213） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (21301, 213, '作品维护', 3, NULL, NULL, 'business:diy:save', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (213, 21301)
ON DUPLICATE KEY UPDATE role_id = role_id;
