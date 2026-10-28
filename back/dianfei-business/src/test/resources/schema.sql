-- H2（MySQL 兼容模式）测试库：Track B carbon 域表（对齐 back/db/init_trackB.sql）
CREATE TABLE IF NOT EXISTS biz_carbon_account (
  id                   BIGINT PRIMARY KEY,
  user_id              BIGINT         NOT NULL,
  points_balance       BIGINT         NOT NULL DEFAULT 0,
  frozen_points        BIGINT         NOT NULL DEFAULT 0,
  total_carbon_reduced DECIMAL(12, 4) NOT NULL DEFAULT 0,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_account_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS biz_carbon_log (
  id             BIGINT PRIMARY KEY,
  account_id     BIGINT         NOT NULL,
  order_id       BIGINT,
  order_type     VARCHAR(16),
  type           TINYINT        NOT NULL,
  points         BIGINT         NOT NULL,
  balance_after  BIGINT         NOT NULL,
  rule_code      VARCHAR(64),
  carbon_reduced DECIMAL(12, 4) NOT NULL DEFAULT 0,
  biz_key        VARCHAR(128),
  remark         VARCHAR(255),
  prev_hash      CHAR(64),
  curr_hash      CHAR(64),
  anchor_id      BIGINT,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_log_biz_key UNIQUE (biz_key)
);
CREATE INDEX IF NOT EXISTS idx_log_account ON biz_carbon_log (account_id);
CREATE INDEX IF NOT EXISTS idx_anchor ON biz_carbon_log (anchor_id);

CREATE TABLE IF NOT EXISTS biz_carbon_rule (
  id             BIGINT PRIMARY KEY,
  rule_code      VARCHAR(64)    NOT NULL,
  order_type     VARCHAR(16)    NOT NULL,
  category       VARCHAR(32)    NOT NULL,
  points_per_kg  DECIMAL(10, 2),
  points_per_bag DECIMAL(10, 2),
  carbon_per_kg  DECIMAL(10, 4),
  carbon_per_bag DECIMAL(10, 4),
  status         TINYINT        NOT NULL DEFAULT 1,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_rule_type_category UNIQUE (order_type, category)
);

CREATE TABLE IF NOT EXISTS biz_carbon_anchor (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  anchor_date  DATE      NOT NULL,
  merkle_root  CHAR(64)  NOT NULL,
  leaf_count   INT       NOT NULL,
  external_ref VARCHAR(255),
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_anchor_date UNIQUE (anchor_date)
);
