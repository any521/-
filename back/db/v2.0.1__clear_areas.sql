-- ============================================================
-- V2.0.1__clear_areas.sql (Flyway: 清空所有硬编码片区数据)
-- 目的：删除 init_trackB.sql 中的示例片区（梧桐苑小区、滨江花园等）
-- 原因：改为根据地图位置自动创建新片区（省/市/县/镇/村）
-- ============================================================
-- S-17 修正：此文件为历史参考脚本，不参与 Flyway 迁移（迁移脚本位于
-- dianfei-admin/src/main/resources/db/migration）。sys_log 表不存在，
-- 实际操作日志表名为 sys_oper_log。下方原 INSERT INTO sys_log 语句已注释。
-- ============================================================

-- 1. 清空所有启用状态的片区（ID 将自动递增生成新值）
DELETE FROM biz_area WHERE deleted = 0;

-- 2. 重置自增 ID 起始值（可选，避免 ID 跳跃过大）
-- 注意：MySQL 默认 AUTO_INCREMENT 会在 INSERT 时自动生成，此处仅做示例
ALTER TABLE biz_area AUTO_INCREMENT = 1;

-- 3. 记录日志（原 INSERT INTO sys_log 表不存在，已注释；如需审计请改用 sys_oper_log）
-- INSERT INTO sys_oper_log (title, business_type, method, request_method, operator_type,
--                           oper_name, oper_url, oper_ip, oper_param, json_result,
--                           status, error_msg, oper_time, create_by, create_time)
-- VALUES ('清空片区数据', 3, '', 'GET', 0, 'system', '', '', '',
--         '{"msg":"V2.0 升级：移除硬编码片区，改为自动创建模式"}', 0, '', NOW(), 'system', NOW());
