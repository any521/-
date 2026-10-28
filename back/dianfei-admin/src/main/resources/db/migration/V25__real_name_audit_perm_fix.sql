-- ============================================================
-- V25 · 修复实名审核按钮权限（V17 缺陷）
-- 根因：V8 已占用 sys_menu id=10101（用户新增 system:user:add），
--       V17 复用 id=10101 插入「实名审核」被 ON DUPLICATE KEY UPDATE 吞掉，
--       perm 从未写入 → ADMIN 无 system:user:audit 权限码 → 前端 v-permission
--       移除审核按钮，管理端无法操作实名审核。
-- 修复：改用未占用的 id=10104 插入按钮并补绑 ADMIN 角色（10101 保持「用户新增」不动）。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 用户管理（父 101）新增「实名审核」按钮码（id=10104，避开 V8 占用的 10101-10103） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (10104, 101, '实名审核', 3, NULL, NULL, 'system:user:audit', NULL, 4, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑新按钮 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (10104)
ON DUPLICATE KEY UPDATE role_id = role_id;
