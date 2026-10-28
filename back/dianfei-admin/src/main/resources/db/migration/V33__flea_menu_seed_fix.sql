-- ============================================================
-- V33 · 修正跳蚤市场菜单编号（C16 修订）
-- V32 误复用 208/20801（已被 V19 员工片区占用），ON DUPLICATE KEY
-- 静默 no-op，菜单未真正创建；本迁移改用 209/20901 重新落地，
-- 并清理 V32 可能遗留的错误 role_menu 绑定（208/20801 归员工片区）。
-- ============================================================
SET NAMES utf8mb4;

-- 业务管理：跳蚤市场菜单（父 200，sort 9 接员工片区之后）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (209, 200, '跳蚤市场', 2, 'flea', 'business/flea', 'business:flea:list', 'ShoppingBag', 9, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- 违规下架按钮（仅 status=1 在售商品显示，pageOps 返回 statusDesc 供列展示）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (20901, 209, '违规下架', 3, NULL, NULL, 'business:flea:forbidden', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ADMIN 角色补绑
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (209, 20901)
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 清理 V32 遗留：跳蚤按钮码绑定在 20801（员工维护按钮）上会错误授予违规下架权限，
-- 仅当该 role_menu 记录同时对应 20801 按钮时需保留（V19 已绑）；V32 插入的
-- (1,208) 与 (1,20801) 与 V19 重复，本就是 no-op，此处无需额外清理。
-- 若存在 perm=business:flea:forbidden 却挂在 20801 的脏数据（不应发生），直接删除兜底：
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.id = 20801 AND m.perm = 'business:flea:forbidden';
