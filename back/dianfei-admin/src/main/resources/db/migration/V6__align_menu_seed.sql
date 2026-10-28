-- ============================================================
-- V6 · 菜单种子对齐前端契约（修复点击不生效 + 补齐功能化图标）
-- 问题：V1 种子 component 路径与 views 实际文件不符（system/user/index →
--       system/user.vue），导致动态路由未注册、点击菜单无响应；
--       且缺少业务/智能两组菜单，图标仅 100 有值其余 NULL（前端全部回退同一个图标）。
-- 对齐对象：front/src/mock/index.ts MENUS（联调契约）
-- 图标均为 @element-plus/icons-vue 组件名（main.ts 已全局注册，SVG 渲染）
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 修正 component/path 对齐实际视图 ----------
UPDATE sys_menu SET component = 'system/user'   WHERE id = 101;
UPDATE sys_menu SET component = 'system/role'   WHERE id = 102;
UPDATE sys_menu SET component = 'system/menu'   WHERE id = 103;
UPDATE sys_menu SET component = 'system/dict'   WHERE id = 104;
UPDATE sys_menu SET component = 'system/param', path = 'param'     WHERE id = 105;
UPDATE sys_menu SET component = 'system/notice' WHERE id = 106;
UPDATE sys_menu SET component = 'system/operlog', path = 'operlog' WHERE id = 107;
-- 108 登录日志：前端无对应视图（mock 契约亦无），软删
UPDATE sys_menu SET deleted = 1 WHERE id = 108 AND deleted = 0;

-- ---------- 目录排序对齐 mock（后端按 sort 升序） ----------
UPDATE sys_menu SET sort = 1 WHERE id = 100;   -- 系统管理
UPDATE sys_menu SET sort = 2 WHERE id = 200;   -- 业务管理
UPDATE sys_menu SET sort = 3 WHERE id = 300;   -- 智能运营

-- ---------- 逐菜单配置功能化图标（Element Plus SVG） ----------
UPDATE sys_menu SET icon = 'User'       WHERE id = 101;   -- 用户管理
UPDATE sys_menu SET icon = 'UserFilled' WHERE id = 102;   -- 角色管理
UPDATE sys_menu SET icon = 'Menu'       WHERE id = 103;   -- 菜单管理
UPDATE sys_menu SET icon = 'Collection' WHERE id = 104;   -- 字典管理
UPDATE sys_menu SET icon = 'Tools'      WHERE id = 105;   -- 参数设置
UPDATE sys_menu SET icon = 'Bell'       WHERE id = 106;   -- 公告管理
UPDATE sys_menu SET icon = 'Document'   WHERE id = 107;   -- 操作日志

-- ---------- 业务管理（目录 200 + 7 子菜单） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (200, 0, '业务管理', 1, '/business', NULL, NULL, 'Van', 2, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (201, 200, '回收订单', 2, 'recycle', 'business/recycle', 'business:recycle:list', 'Box', 1, 1, 1, NOW(), 0),
       (202, 200, '厨余清运', 2, 'kitchen', 'business/kitchen', 'business:kitchen:list', 'Food', 2, 1, 1, NOW(), 0),
       (203, 200, '维修工单', 2, 'repair', 'business/repair', 'business:repair:list', 'SetUp', 3, 1, 1, NOW(), 0),
       (204, 200, '积分商城', 2, 'mall', 'business/mall', 'business:mall:list', 'ShoppingCart', 4, 1, 1, NOW(), 0),
       (205, 200, '风控预警', 2, 'risk', 'business/risk', 'business:risk:list', 'Warning', 5, 1, 1, NOW(), 0),
       (206, 200, '订单售后', 2, 'aftersale', 'business/aftersale', 'business:aftersale:list', 'Service', 6, 1, 1, NOW(), 0),
       (207, 200, '排班运维', 2, 'schedule', 'business/schedule', 'business:schedule:list', 'Calendar', 7, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- 智能运营（目录 300 + 2 子菜单） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (300, 0, '智能运营', 1, '/smart', NULL, NULL, 'Monitor', 3, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (301, 300, '数据大屏', 2, 'screen', 'smart/screen', 'smart:screen:list', 'DataLine', 1, 1, 1, NOW(), 0),
       (302, 300, 'Agent 驾驶舱', 2, 'cockpit', 'smart/cockpit', 'smart:cockpit:list', 'Cpu', 2, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ---------- ADMIN 角色拥有全部菜单 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE deleted = 0
ON DUPLICATE KEY UPDATE role_id = role_id;
