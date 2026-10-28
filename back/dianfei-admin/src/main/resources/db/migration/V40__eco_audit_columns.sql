-- ============================================================
-- 修复 eco 三表缺 BaseEntity 审计列（V26 建表遗漏）
-- eco_sign_in / eco_user_achievement 缺 update_time/create_by/update_by/deleted；
-- eco_achievement 缺全部审计列。实体继承 BaseEntity，MyBatis-Plus
-- 全字段 SELECT 报 Unknown column '...'，补列后既有数据 deleted 置 0。
-- ============================================================

ALTER TABLE eco_sign_in
  ADD COLUMN update_time DATETIME     AFTER create_time,
  ADD COLUMN create_by  VARCHAR(64)  AFTER update_time,
  ADD COLUMN update_by  VARCHAR(64)  AFTER create_by,
  ADD COLUMN deleted    TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除' AFTER update_by;

ALTER TABLE eco_user_achievement
  ADD COLUMN update_time DATETIME     AFTER create_time,
  ADD COLUMN create_by  VARCHAR(64)  AFTER update_time,
  ADD COLUMN update_by  VARCHAR(64)  AFTER create_by,
  ADD COLUMN deleted    TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除' AFTER update_by;

ALTER TABLE eco_achievement
  ADD COLUMN create_time DATETIME,
  ADD COLUMN update_time DATETIME,
  ADD COLUMN create_by  VARCHAR(64),
  ADD COLUMN update_by  VARCHAR(64),
  ADD COLUMN deleted    TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除';