-- ============================================================
-- LoopFill B5 · 创意 C1：环保成就签到 + 环保树（蚂蚁森林式成长）
-- 3 表：eco_sign_in（每日签到）/ eco_achievement（成就定义种子 4 类）/
--       eco_user_achievement（用户成就，uk 防重复领取）
-- 配置：eco.sign-in-points=5（单次签到积分）、eco.sign-streak-days=7（连续签到解锁阈值）
-- 积分入账一律走账本唯一入口 CarbonLedgerOps.grantEco（rule_code=ECO_SIGN/ECO_ACHV_*），
-- 不旁路哈希链与账户锁；本迁移不动 init.sql 与 V1~V25。
-- ============================================================
CREATE TABLE IF NOT EXISTS eco_sign_in (
  id          BIGINT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  sign_date   DATE        NOT NULL,
  points      INT         NOT NULL DEFAULT 5,
  create_time DATETIME,
  UNIQUE KEY uk_sign_user_date (user_id, sign_date),
  KEY idx_sign_user (user_id)
) COMMENT '环保每日签到';

-- 成就定义（种子 4 类，code 唯一；id 固定小值，claim 幂等键依赖其稳定）
CREATE TABLE IF NOT EXISTS eco_achievement (
  id          BIGINT PRIMARY KEY,
  code        VARCHAR(32) NOT NULL UNIQUE COMMENT 'RECYCLE_FIRST/KITCHEN_FIRST/RECYCLE_TEN/SIGN_SEVEN',
  name        VARCHAR(32) NOT NULL,
  description VARCHAR(128) NOT NULL,
  points      INT         NOT NULL COMMENT '达成奖励积分',
  sort        INT         NOT NULL DEFAULT 0
) COMMENT '环保成就定义';

INSERT INTO eco_achievement (id, code, name, description, points, sort) VALUES
  (1, 'RECYCLE_FIRST', '废有所值', '完成第一笔回收订单', 20, 1),
  (2, 'KITCHEN_FIRST', '厨余归位', '完成第一次厨余投放', 20, 2),
  (3, 'RECYCLE_TEN', '回收达人', '累计完成 10 笔回收订单', 100, 3),
  (4, 'SIGN_SEVEN', '七日坚持', '连续签到 7 天', 50, 4)
ON DUPLICATE KEY UPDATE name = name;

-- 用户成就领取（防重复：uk(user_id, achievement_id)；claimed 0未领取 1已领取）
CREATE TABLE IF NOT EXISTS eco_user_achievement (
  id             BIGINT PRIMARY KEY,
  user_id        BIGINT      NOT NULL,
  achievement_id BIGINT      NOT NULL,
  claimed        TINYINT     NOT NULL DEFAULT 0 COMMENT '0未领取 1已领取',
  create_time    DATETIME,
  UNIQUE KEY uk_ua (user_id, achievement_id),
  KEY idx_ua_user (user_id)
) COMMENT '用户环保成就';

INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9205, 'eco.sign-in-points', '5', '环保每日签到积分', 'LoopFill B5 创意 C1：单次签到发放积分（走碳账本 grantEco）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'eco.sign-in-points');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9206, 'eco.sign-streak-days', '7', '连续签到解锁成就天数', 'LoopFill B5 创意 C1：连续签到达该值自动解锁 SIGN_SEVEN', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'eco.sign-streak-days');
