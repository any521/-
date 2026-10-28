-- ------------------------------------------------------------------
-- V53: 驾驶舱地图标注 + 片区户数补偿 + 跳蚤市场并入积分商城
-- 1) biz_area 增加 lng/lat（Agent 驾驶舱高德地图片区标注；自动创建片区落坐标）
-- 2) 种子片区（V49）户数补偿：household_count=0 的记录按片区名回填实际户数，
--    修复管理端「员工片区-片区列表」户数显示 0（自动创建片区亦改为默认 300）
-- 3) 菜单合并（对齐用户端「积分商城」双栏）：209 跳蚤市场停用并入 204 积分商城；
--    20901 违规下架按钮改挂 204 保留 business:flea:forbidden；
--    新增 20403 闲置商品管理按钮补 business:flea:list（209 停用后权限不丢）
-- ------------------------------------------------------------------
SET NAMES utf8mb4;

-- ---------- 1. 片区经纬度 ----------
ALTER TABLE biz_area
    ADD COLUMN lng DECIMAL(10, 6) NULL COMMENT '片区中心经度' AFTER address,
    ADD COLUMN lat DECIMAL(10, 6) NULL COMMENT '片区中心纬度' AFTER lng;

-- 种子片区坐标（V49 三个演示片区，杭州城区近似中心点）
UPDATE biz_area SET lng = 120.153600, lat = 30.287500 WHERE area_name = '绿洲花园片区' AND lng IS NULL;
UPDATE biz_area SET lng = 120.166500, lat = 30.305600 WHERE area_name = '滨江新区片区' AND lng IS NULL;
UPDATE biz_area SET lng = 120.118800, lat = 30.269600 WHERE area_name = '科技园片区' AND lng IS NULL;

-- ---------- 2. 片区户数补偿（仅回填仍为 0 的记录，不覆盖已维护值） ----------
UPDATE biz_area SET household_count = 1200 WHERE area_name = '绿洲花园片区' AND household_count = 0;
UPDATE biz_area SET household_count = 950  WHERE area_name = '滨江新区片区' AND household_count = 0;
UPDATE biz_area SET household_count = 600  WHERE area_name = '科技园片区' AND household_count = 0;

-- ---------- 3. 菜单合并：跳蚤市场并入积分商城 ----------
-- 209 跳蚤市场停用（业务并入 204 积分商城「闲置好物」Tab）
UPDATE sys_menu SET status = 0 WHERE id = 209;

-- 20901 违规下架按钮改挂 204（权限码 business:flea:forbidden 随父菜单保留）
UPDATE sys_menu SET parent_id = 204, sort = 4 WHERE id = 20901;

-- 20403 闲置商品管理按钮（补 209 停用后丢失的 business:flea:list 列表/发布权限）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (20403, 204, '闲置商品管理', 3, NULL, NULL, 'business:flea:list', NULL, 3, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ADMIN 角色补绑 20403
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (20403)
ON DUPLICATE KEY UPDATE role_id = role_id;
