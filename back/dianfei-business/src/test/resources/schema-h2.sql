-- ============================================================
-- Track B 单测 Schema（H2 MySQL 兼容模式）
-- 与 back/db/init_trackB.sql 字段严格对齐；索引改为独立语句以兼容 H2 语法
-- ============================================================

-- ---------- B1 片区与员工 ----------

CREATE TABLE IF NOT EXISTS sys_user (
  id                BIGINT PRIMARY KEY,
  username          VARCHAR(32)  NOT NULL,
  password          VARCHAR(100) NOT NULL,
  nickname          VARCHAR(32),
  phone             VARCHAR(20),
  email             VARCHAR(64),
  avatar            VARCHAR(255),
  role              VARCHAR(32)  NOT NULL DEFAULT 'USER',
  area_id           BIGINT,
  principal_type    VARCHAR(16)  NOT NULL DEFAULT 'HUMAN',
  real_name         VARCHAR(32),
  id_card           VARCHAR(32),
  real_name_status  TINYINT      NOT NULL DEFAULT 0,
  real_name_remark  VARCHAR(255),
  status            TINYINT      NOT NULL DEFAULT 1,
  create_time    DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted        TINYINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_username ON sys_user (username);
CREATE INDEX IF NOT EXISTS idx_user_phone ON sys_user (phone);
CREATE INDEX IF NOT EXISTS idx_user_area ON sys_user (area_id);

CREATE TABLE IF NOT EXISTS biz_area (
  id              BIGINT PRIMARY KEY,
  area_name       VARCHAR(64)  NOT NULL,
  address         VARCHAR(255),
  lng             DECIMAL(10, 6),
  lat             DECIMAL(10, 6),
  clean_threshold INT          NOT NULL DEFAULT 100,
  household_count INT          NOT NULL DEFAULT 0,
  status          TINYINT      NOT NULL DEFAULT 1,
  create_time     DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted         TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS biz_staff (
  id           BIGINT PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  staff_name   VARCHAR(32)  NOT NULL,
  phone        VARCHAR(20),
  staff_type   TINYINT      NOT NULL,
  area_id      BIGINT       NOT NULL,
  audit_status TINYINT      NOT NULL DEFAULT 0,
  status       TINYINT      NOT NULL DEFAULT 1,
  apply_scene  TINYINT      NOT NULL DEFAULT 0,
  apply_reason VARCHAR(255),
  audit_remark VARCHAR(255),
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_staff_area ON biz_staff (area_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_staff_user ON biz_staff (user_id);

-- ---------- B2 可信碳账本 ----------

CREATE TABLE IF NOT EXISTS biz_carbon_account (
  id                   BIGINT PRIMARY KEY,
  user_id              BIGINT         NOT NULL,
  points_balance       BIGINT         NOT NULL DEFAULT 0,
  frozen_points        BIGINT         NOT NULL DEFAULT 0,
  total_carbon_reduced DECIMAL(12, 4) NOT NULL DEFAULT 0,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_account_user ON biz_carbon_account (user_id);

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
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_log_biz_key ON biz_carbon_log (biz_key);
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
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_type_category ON biz_carbon_rule (order_type, category);

CREATE TABLE IF NOT EXISTS biz_carbon_anchor (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  anchor_date  DATE      NOT NULL,
  merkle_root  CHAR(64)  NOT NULL,
  leaf_count   INT       NOT NULL,
  external_ref VARCHAR(255),
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_anchor_date ON biz_carbon_anchor (anchor_date);

-- ---------- B3 可回收物回收 ----------

CREATE TABLE IF NOT EXISTS biz_recycle_order (
  id           BIGINT PRIMARY KEY,
  order_no     VARCHAR(32)    NOT NULL,
  user_id      BIGINT         NOT NULL,
  staff_id     BIGINT,
  area_id      BIGINT         NOT NULL,
  category     VARCHAR(32)    NOT NULL,
  est_weight   DECIMAL(10, 2),
  weight       DECIMAL(10, 2),
  quantity     INT,
  address      VARCHAR(255)   NOT NULL,
  lng          DECIMAL(10, 6),
  lat          DECIMAL(10, 6),
  appoint_time DATETIME       NOT NULL,
  status       TINYINT        NOT NULL DEFAULT 0,
  amount       DECIMAL(10, 2),
  transfer_to  VARCHAR(64),
  repair_ref   BIGINT,
  cancel_reason VARCHAR(255),
  exception_reason VARCHAR(255),
  service_points BIGINT,
  settle_status TINYINT NOT NULL DEFAULT 0,
  finish_time  DATETIME,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_order_no ON biz_recycle_order (order_no);
CREATE INDEX IF NOT EXISTS idx_user_status ON biz_recycle_order (user_id, status);
CREATE INDEX IF NOT EXISTS idx_staff_appoint ON biz_recycle_order (staff_id, appoint_time);
CREATE INDEX IF NOT EXISTS idx_order_area ON biz_recycle_order (area_id);

CREATE TABLE IF NOT EXISTS biz_recycle_price (
  id           BIGINT PRIMARY KEY,
  category     VARCHAR(32)    NOT NULL,
  price_per_kg DECIMAL(10, 2) NOT NULL,
  status       TINYINT        NOT NULL DEFAULT 1,
  city         VARCHAR(64),
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_price_category ON biz_recycle_price (category);

CREATE TABLE IF NOT EXISTS biz_recycle_item (
  id          BIGINT PRIMARY KEY,
  order_id    BIGINT         NOT NULL,
  category    VARCHAR(32)    NOT NULL,
  weight      DECIMAL(10, 2) NOT NULL,
  amount      DECIMAL(10, 2) NOT NULL,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_item_order ON biz_recycle_item (order_id);

-- ---------- B4 垃圾投放与清运 ----------

CREATE TABLE IF NOT EXISTS biz_kitchen_report (
  id           BIGINT PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  area_id      BIGINT       NOT NULL,
  point_name   VARCHAR(64)  NOT NULL,
  bag_count    INT          NOT NULL,
  photo_url    VARCHAR(255),
  lng          DECIMAL(10, 6),
  lat          DECIMAL(10, 6),
  report_time  DATETIME     NOT NULL,
  status       TINYINT      NOT NULL DEFAULT 0,
  audit_remark VARCHAR(255),
  audit_time   DATETIME,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_report_area_time ON biz_kitchen_report (area_id, report_time);
CREATE INDEX IF NOT EXISTS idx_report_user ON biz_kitchen_report (user_id);

CREATE TABLE IF NOT EXISTS biz_kitchen_task (
  id           BIGINT PRIMARY KEY,
  area_id      BIGINT       NOT NULL,
  task_date    DATE         NOT NULL,
  total_bags   INT          NOT NULL,
  vehicle_id   BIGINT,
  staff_id     BIGINT,
  status       TINYINT      NOT NULL DEFAULT 0,
  total_weight DECIMAL(10, 2),
  vehicle_no   VARCHAR(32),
  destination  VARCHAR(128),
  proof_url    VARCHAR(255),
  finish_time  DATETIME,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_area_date ON biz_kitchen_task (area_id, task_date);
CREATE INDEX IF NOT EXISTS idx_task_staff ON biz_kitchen_task (staff_id);

CREATE TABLE IF NOT EXISTS biz_vehicle (
  id          BIGINT PRIMARY KEY,
  vehicle_no  VARCHAR(32) NOT NULL,
  area_id     BIGINT,
  capacity    INT,
  status      TINYINT     NOT NULL DEFAULT 1,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_vehicle_no ON biz_vehicle (vehicle_no);

-- ---------- B4 风控告警 ----------

CREATE TABLE IF NOT EXISTS biz_risk_alert (
  id            BIGINT PRIMARY KEY,
  biz_type      VARCHAR(32) NOT NULL,
  biz_id        BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  area_id       BIGINT,
  reason        VARCHAR(255),
  frozen_points BIGINT      NOT NULL DEFAULT 0,
  freeze_log_id BIGINT,
  status        TINYINT     NOT NULL DEFAULT 0,
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_alert_status ON biz_risk_alert (status);

-- ---------- C3 积分商城 ----------

CREATE TABLE IF NOT EXISTS biz_mall_product (
  id           BIGINT PRIMARY KEY,
  product_name VARCHAR(64)  NOT NULL,
  cover_url    VARCHAR(255),
  points_price BIGINT       NOT NULL,
  stock        INT          NOT NULL DEFAULT 0,
  status       TINYINT      NOT NULL DEFAULT 0,
  description  VARCHAR(500),
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_product_status ON biz_mall_product (status);

CREATE TABLE IF NOT EXISTS biz_mall_exchange (
  id            BIGINT PRIMARY KEY,
  exchange_no   VARCHAR(32) NOT NULL,
  user_id       BIGINT      NOT NULL,
  product_id    BIGINT      NOT NULL,
  product_name  VARCHAR(64) NOT NULL,
  points_cost   BIGINT      NOT NULL,
  quantity      INT         NOT NULL DEFAULT 1,
  carbon_log_id BIGINT,
  status        TINYINT     NOT NULL DEFAULT 0,
  address       VARCHAR(255),
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_exchange_no ON biz_mall_exchange (exchange_no);
CREATE INDEX IF NOT EXISTS idx_exchange_user ON biz_mall_exchange (user_id);
CREATE INDEX IF NOT EXISTS idx_exchange_product ON biz_mall_exchange (product_id);

-- ---------- C4 后台管控与风控 ----------

CREATE TABLE IF NOT EXISTS biz_complaint (
  id            BIGINT PRIMARY KEY,
  order_type    VARCHAR(16)  NOT NULL,
  order_id      BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  type          TINYINT      NOT NULL,
  content       VARCHAR(500) NOT NULL,
  status        TINYINT      NOT NULL DEFAULT 0,
  handle_result VARCHAR(500),
  handler_id    BIGINT,
  handle_time   DATETIME,
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_complaint_status ON biz_complaint (status);
CREATE INDEX IF NOT EXISTS idx_complaint_user ON biz_complaint (user_id);

CREATE TABLE IF NOT EXISTS biz_schedule (
  id              BIGINT PRIMARY KEY,
  staff_id        BIGINT  NOT NULL,
  area_id         BIGINT  NOT NULL,
  work_date       DATE    NOT NULL,
  shift_type      TINYINT NOT NULL DEFAULT 1,
  biz_type        TINYINT NOT NULL DEFAULT 0,
  support_area_id BIGINT,
  remark          VARCHAR(255),
  create_time     DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted         TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_schedule_area_date ON biz_schedule (area_id, work_date);

CREATE TABLE IF NOT EXISTS biz_leave_request (
  id           BIGINT PRIMARY KEY,
  staff_id     BIGINT       NOT NULL,
  start_date   DATE         NOT NULL,
  end_date     DATE         NOT NULL,
  reason       VARCHAR(255) NOT NULL,
  status       TINYINT      NOT NULL DEFAULT 0,
  audit_by     BIGINT,
  audit_remark VARCHAR(255),
  audit_time   DATETIME,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_leave_staff ON biz_leave_request (staff_id);
CREATE INDEX IF NOT EXISTS idx_leave_status ON biz_leave_request (status);

CREATE TABLE IF NOT EXISTS biz_device_maintenance (
  id          BIGINT PRIMARY KEY,
  target_type TINYINT      NOT NULL,
  target_no   VARCHAR(64)  NOT NULL,
  content     VARCHAR(500) NOT NULL,
  cost        DECIMAL(10, 2) DEFAULT 0,
  maint_date  DATE         NOT NULL,
  operator_id BIGINT,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_maint_target ON biz_device_maintenance (target_type, target_no);

-- ---------- C1 电路维修工单 ----------

CREATE TABLE IF NOT EXISTS biz_repair_order (
  id            BIGINT PRIMARY KEY,
  order_no      VARCHAR(32)  NOT NULL,
  user_id       BIGINT       NOT NULL,
  staff_id      BIGINT,
  area_id       BIGINT       NOT NULL,
  fault_type    VARCHAR(32)  NOT NULL,
  fault_desc    VARCHAR(500),
  address       VARCHAR(255) NOT NULL,
  lng           DECIMAL(10, 6),
  lat           DECIMAL(10, 6),
  appoint_start DATETIME     NOT NULL,
  appoint_end   DATETIME     NOT NULL,
  old_for_new   TINYINT      NOT NULL DEFAULT 0,
  gas_delivery  TINYINT      NOT NULL DEFAULT 0,
  diagnose_json TEXT,
  repairable    TINYINT      NOT NULL DEFAULT 0,
  report_json   TEXT,
  science_note  VARCHAR(500),
  recycle_ref   BIGINT,
  status        TINYINT      NOT NULL DEFAULT 0,
  cancel_reason VARCHAR(255),
  service_points BIGINT,
  settle_status TINYINT NOT NULL DEFAULT 0,
  finish_time   DATETIME,
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_repair_order_no ON biz_repair_order (order_no);
CREATE INDEX IF NOT EXISTS idx_repair_user_status ON biz_repair_order (user_id, status);
CREATE INDEX IF NOT EXISTS idx_repair_area ON biz_repair_order (area_id);

CREATE TABLE IF NOT EXISTS biz_repair_part (
  id          BIGINT PRIMARY KEY,
  order_id    BIGINT         NOT NULL,
  part_name   VARCHAR(64)    NOT NULL,
  part_model  VARCHAR(64),
  quantity    INT            NOT NULL DEFAULT 1,
  price       DECIMAL(10, 2) NOT NULL DEFAULT 0,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_part_order ON biz_repair_part (order_id);

-- ---------- C5 Agent 决策单 ----------

CREATE TABLE IF NOT EXISTS biz_agent_decision (
  id                  BIGINT PRIMARY KEY,
  agent_id            VARCHAR(64)  NOT NULL,
  action_type         VARCHAR(64)  NOT NULL,
  target_uri          VARCHAR(255),
  params_json         TEXT,
  evidences_json      TEXT,
  risk_level          TINYINT      NOT NULL,
  status              TINYINT      NOT NULL DEFAULT 0,
  decision_hash       CHAR(64),
  prev_hash           CHAR(64),
  first_approver      BIGINT,
  first_approve_time  DATETIME,
  second_approver     BIGINT,
  second_approve_time DATETIME,
  reject_reason       VARCHAR(255),
  execute_result      VARCHAR(500),
  execute_time        DATETIME,
  create_time         DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted             TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_decision_status ON biz_agent_decision (status);
CREATE INDEX IF NOT EXISTS idx_decision_agent ON biz_agent_decision (agent_id);
CREATE INDEX IF NOT EXISTS idx_decision_risk ON biz_agent_decision (risk_level, status);

-- ---------- B5 站内信 ----------

CREATE TABLE IF NOT EXISTS biz_message (
  id          BIGINT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  msg_type    VARCHAR(32) NOT NULL,
  title       VARCHAR(64) NOT NULL,
  content     VARCHAR(500),
  read_flag   TINYINT     NOT NULL DEFAULT 0,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_message_user ON biz_message (user_id, read_flag);

-- ---------- Onsite04 回收站点（V24，厨余 50m 投放校验依赖） ----------

CREATE TABLE IF NOT EXISTS biz_station (
  id           BIGINT PRIMARY KEY,
  name         VARCHAR(64)   NOT NULL,
  station_type VARCHAR(16)   NOT NULL,
  lng          DECIMAL(10, 6) NOT NULL,
  lat          DECIMAL(10, 6) NOT NULL,
  address      VARCHAR(255),
  open_hours   VARCHAR(64),
  status       TINYINT       NOT NULL DEFAULT 1,
  deleted      TINYINT       NOT NULL DEFAULT 0,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_station_type ON biz_station (station_type);

