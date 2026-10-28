-- ============================================================
-- V17 · 实名认证（GapFill B1 交付）
-- 背景：sys_user 已有 real_name/id_card 列（V1 L26-27），无审核状态字段；
--       用户端实名认证页（mobile/pc realname.vue）需 status/remark 支撑
--       提交→待审→审核通过/驳回 状态机，管理端 user.vue 需实名列 + 审核按钮码。
-- 注意：V17 编号保留；V1~V16 已应用禁止改动。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- sys_user 加实名审核状态（0未认证 1待审 2已通过 3已驳回） ----------
ALTER TABLE sys_user
    ADD COLUMN real_name_status TINYINT NOT NULL DEFAULT 0 COMMENT '实名状态 0未认证 1待审 2已通过 3已驳回' AFTER id_card,
    ADD COLUMN real_name_remark VARCHAR(255) COMMENT '审核备注（驳回原因）' AFTER real_name_status;

-- ---------- 用户管理（父 101）新增「实名审核」按钮码 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (10101, 101, '实名审核', 3, NULL, NULL, 'system:user:audit', NULL, 5, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑新按钮 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (10101)
ON DUPLICATE KEY UPDATE role_id = role_id;
