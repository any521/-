-- ============================================================
-- 功能更名：厨余投放 → 垃圾投放（用户端页面/后端报错/管理端菜单已统一，
-- 此处仅同步库内用户可见文案；不动业务键、品类字典与表结构）
-- ============================================================

-- 参数配置名（管理端"参数设置"页展示）
UPDATE sys_config SET config_name = '垃圾投放开始时段（时）'
WHERE config_key = 'kitchen.report.start-hour';
UPDATE sys_config SET config_name = '垃圾投放结束时段（时）'
WHERE config_key = 'kitchen.report.end-hour';
UPDATE sys_config SET config_name = '垃圾投放上报偏离片区均值告警倍数'
WHERE config_key = 'risk.kitchen.deviation-times';

-- 管理端菜单名（原"厨余清运"，页面实为投放记录管理）
UPDATE sys_menu SET menu_name = '垃圾投放' WHERE id = 202;

-- 环保成就描述（徽章名"厨余归位"保留，品类语义不变）
UPDATE eco_achievement SET description = '完成第一次垃圾投放' WHERE code = 'KITCHEN_FIRST';
