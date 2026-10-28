-- ============================================================
-- V16 · 回收订单操作按钮权限（M-03 交付）
-- 背景：V6 菜单种子仅有 business:recycle:list（menu 201）；recycle.vue 详情/改派/报价
--       按钮使用 business:recycle:detail/reassign/price，缺权限码会导致按钮被隐藏。
-- 注意：V16 编号保留；V15 已预留给 M-02（权限种子补齐），V1~V14 已应用禁止改动。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 回收订单操作按钮（recycle.vue 使用 business:recycle:detail/reassign/price） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (20101, 201, '订单详情', 3, NULL, NULL, 'business:recycle:detail',   NULL, 1, 1, 1, NOW(), 0),
       (20102, 201, '订单改派', 3, NULL, NULL, 'business:recycle:reassign', NULL, 2, 1, 1, NOW(), 0),
       (20103, 201, '报价配置', 3, NULL, NULL, 'business:recycle:price',    NULL, 3, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色补绑新按钮 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (20101, 20102, 20103)
ON DUPLICATE KEY UPDATE role_id = role_id;
