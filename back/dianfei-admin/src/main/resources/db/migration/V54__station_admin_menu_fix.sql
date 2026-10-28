-- ============================================================
-- V54 · 站点管理菜单修复：V43 的 ID 209 与跳蚤市场冲突（V32/V33 先占用 209），
-- ON DUPLICATE KEY UPDATE menu_name = menu_name 静默吞掉插入，站点管理从未入库；
-- 改用未占用 ID 212（业务管理 200，sort 9 接员工片区之后），并清理 V43 残留按钮 20901。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 清理 V43 残留：20901 按钮（父 209 实为跳蚤市场，勿挂错） ----------
DELETE FROM sys_role_menu WHERE menu_id = 20901;
DELETE FROM sys_menu WHERE id = 20901;

-- ---------- 业务管理：站点管理菜单（父 200，sort 9） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (212, 200, '站点管理', 2, 'station', 'business/station', 'business:station:list', 'Location', 9, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 按钮权限：站点维护（父 212） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (21201, 212, '站点维护', 3, NULL, NULL, 'business:station:save', NULL, 1, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (212, 21201)
ON DUPLICATE KEY UPDATE role_id = role_id;
