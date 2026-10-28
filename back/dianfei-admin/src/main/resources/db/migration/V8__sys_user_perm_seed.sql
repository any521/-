-- ============================================================
-- V8 · 用户管理按钮权限种子（batch B01 / G1 系统用户 CRUD 交付）
-- 背景：V1 菜单种子仅有 system:user:list（menu 101）；user.vue 按钮
--       使用 system:user:add/edit/remove，缺权限码会导致按钮被隐藏。
-- 注意：V8 编号保留；V7 已预留给 B04（员工签到建表），V1~V6 已应用禁止改动。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 用户管理按钮权限（user.vue 使用 system:user:add/edit/remove） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (10101, 101, '用户新增', 3, NULL, NULL, 'system:user:add',    NULL, 1, 1, 1, NOW(), 0),
       (10102, 101, '用户编辑', 3, NULL, NULL, 'system:user:edit',   NULL, 2, 1, 1, NOW(), 0),
       (10103, 101, '用户删除', 3, NULL, NULL, 'system:user:remove', NULL, 3, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑新按钮 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (10101, 10102, 10103)
ON DUPLICATE KEY UPDATE role_id = role_id;
