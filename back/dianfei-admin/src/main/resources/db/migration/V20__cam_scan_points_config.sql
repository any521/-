-- ============================================================
-- CAM cam04 · 扫描识别积分参数（契约 C6-9，sys_config 可热更）
-- 单次额度 carbon.scan.points（默认 1）、日上限 carbon.scan.daily_limit（默认 10）；
-- 发放走账本唯一入口 CarbonLedgerOps.grantScan（rule_code=SCAN_CLASSIFY）。
-- ============================================================
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9113, 'carbon.scan.points', '1', '扫描识别单次积分', 'CAM 精分类识别积分（C6-9）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'carbon.scan.points');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9114, 'carbon.scan.daily_limit', '10', '扫描识别日积分上限', 'CAM 识别积分 Redis 日频控（C6-9）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'carbon.scan.daily_limit');
