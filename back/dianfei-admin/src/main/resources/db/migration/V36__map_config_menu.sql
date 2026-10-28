-- ============================================================
-- V36 · 地图配置专属菜单：新增「地图配置」菜单项（parent=100 系统管理）
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (109, 100, '地图配置', 2, 'map-config', 'system/mapConfig', 'system:map-config:list', 'Location', 9, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 109
ON DUPLICATE KEY UPDATE role_id = role_id;