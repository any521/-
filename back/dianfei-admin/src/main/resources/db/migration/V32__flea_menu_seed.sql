-- ============================================================
-- V32 · 跳蚤市场管理端菜单（C16 补页面缺口）
-- FleaOpsController 接口 /api/ops/flea/** 已于 Upgrade U3 提供，
-- 本迁移补齐菜单（业务管理 200 下 sort=8）与违规下架按钮权限码，
-- ADMIN 角色绑定；前端视图 front/src/views/business/flea.vue。
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (208, 200, '跳蚤市场', 2, 'flea', 'business/flea', 'business:flea:list', 'ShoppingBag', 8, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- 违规下架按钮（仅 status=1 在售商品显示，pageOps 返回 statusDesc 供列展示）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (20801, 208, '违规下架', 3, NULL, NULL, 'business:flea:forbidden', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ADMIN 角色补绑
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (208, 20801)
ON DUPLICATE KEY UPDATE role_id = role_id;