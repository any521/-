-- ============================================================
-- onsite02 · 服务积分闭环（契约 C-O2）：上门服务完成即结算，积分双向交易
-- 1) 回收/维修订单追加用户承诺服务费积分 + 结算状态；
-- 2) 服务费档位参数（sys_config 热更，管理端参数配置页可调）：
--    service.recycle.points / service.repair.points，逗号分隔多档，0=不提供积分支付。
-- 结算转账走账本唯一入口 CarbonLedgerOps.transfer（rule_code=TRANSFER_SERVICE）。
-- ============================================================
ALTER TABLE biz_recycle_order
  ADD COLUMN service_points BIGINT NOT NULL DEFAULT 0 COMMENT '用户承诺服务费积分（0=不使用积分支付）',
  ADD COLUMN settle_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未结算 1已结算 2免结算/异常转人工';

ALTER TABLE biz_repair_order
  ADD COLUMN service_points BIGINT NOT NULL DEFAULT 0 COMMENT '用户承诺服务费积分（0=不使用积分支付）',
  ADD COLUMN settle_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未结算 1已结算 2免结算/异常转人工';

INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9201, 'service.recycle.points', '100', '回收上门服务费档位（积分）', '逗号分隔多档；0=不提供积分支付（onsite02）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'service.recycle.points');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9202, 'service.repair.points', '200', '维修上门服务费档位（积分）', '逗号分隔多档；0=不提供积分支付（onsite02）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'service.repair.points');
