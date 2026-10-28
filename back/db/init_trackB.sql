-- ============================================================
-- Track B DDL · 垃圾积分线（对齐 10_database.md，MySQL 8.0 / InnoDB / utf8mb4）
-- 说明：Sprint 0 冻结后并入 db/init.sql；本文件只含 Track B 负责的表。
-- 公共字段：id/create_time/update_time/create_by/update_by/deleted（逻辑删除）
--   create_by/update_by 为 VARCHAR(64)（存操作人用户名，对齐 common.BaseEntity）。
-- 碳流水/锚定表（biz_carbon_log / biz_carbon_anchor）生产环境使用仅 INSERT 权限账号（04.8）；
--   锚定任务账号额外授予 GRANT UPDATE(anchor_id) ON biz_carbon_log（仅可回填锚定引用列）。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- B1 片区与员工 ----------

CREATE TABLE IF NOT EXISTS biz_area (
  id              BIGINT PRIMARY KEY,
  area_name       VARCHAR(64)  NOT NULL,
  address         VARCHAR(255),
  clean_threshold INT          NOT NULL DEFAULT 100 COMMENT '厨余清运阈值(袋)，达到即生成次日清运任务',
  household_count INT          NOT NULL DEFAULT 0   COMMENT '片区户数，风控户均基数',
  status          TINYINT      NOT NULL DEFAULT 1   COMMENT '0停用 1启用',
  create_time     DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted         TINYINT      NOT NULL DEFAULT 0
) COMMENT '片区';

CREATE TABLE IF NOT EXISTS biz_staff (
  id           BIGINT PRIMARY KEY,
  user_id      BIGINT       NOT NULL COMMENT '关联 sys_user',
  staff_name   VARCHAR(32)  NOT NULL,
  phone        VARCHAR(20),
  staff_type   TINYINT      NOT NULL COMMENT '1回收员 2环卫员 3技术员',
  area_id      BIGINT       NOT NULL COMMENT '绑定片区',
  audit_status TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  apply_scene  TINYINT      NOT NULL DEFAULT 0 COMMENT '来源：0管理端创建 1用户自助申请（V21，G1）',
  apply_reason VARCHAR(255) COMMENT '申请备注/驳回原因（V21，G1）',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT      NOT NULL DEFAULT 0,
  KEY idx_staff_area (area_id),
  UNIQUE KEY uk_staff_user (user_id)
) COMMENT '员工（三类）';

-- ---------- B2 可信碳账本 ----------

CREATE TABLE IF NOT EXISTS biz_carbon_account (
  id                   BIGINT PRIMARY KEY,
  user_id              BIGINT         NOT NULL,
  points_balance       BIGINT         NOT NULL DEFAULT 0 COMMENT '可用积分',
  frozen_points        BIGINT         NOT NULL DEFAULT 0 COMMENT '冻结积分（风控）',
  total_carbon_reduced DECIMAL(12, 4) NOT NULL DEFAULT 0 COMMENT '累计减排量 kgCO2',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_account_user (user_id)
) COMMENT '碳积分账户';

CREATE TABLE IF NOT EXISTS biz_carbon_log (
  id             BIGINT PRIMARY KEY,
  account_id     BIGINT         NOT NULL,
  order_id       BIGINT         COMMENT '来源单 ID',
  order_type     VARCHAR(16)    COMMENT 'RECYCLE/KITCHEN/REPAIR/MALL',
  type           TINYINT        NOT NULL COMMENT '1获得 2消费 3补发 4扣减 5清零 6冻结 7解冻',
  points         BIGINT         NOT NULL COMMENT '正=入账 负=出账',
  balance_after  BIGINT         NOT NULL,
  rule_code      VARCHAR(64)    COMMENT '命中的规则编码',
  carbon_reduced DECIMAL(12, 4) NOT NULL DEFAULT 0 COMMENT '本笔减排量 kgCO2',
  biz_key        VARCHAR(128)   COMMENT '幂等键：order_type:order_id:type，同单不重复发分',
  remark         VARCHAR(255)   COMMENT '备注（调整原因等）',
  prev_hash      CHAR(64)       COMMENT '上一条哈希（10.4）',
  curr_hash      CHAR(64)       COMMENT 'SHA-256(prev_hash + payload)',
  anchor_id      BIGINT         COMMENT '关联每日锚定',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_log_biz_key (biz_key),
  KEY idx_log_account (account_id),
  KEY idx_anchor (anchor_id)
) COMMENT '碳积分流水（哈希链，仅 INSERT）';

CREATE TABLE IF NOT EXISTS biz_carbon_rule (
  id             BIGINT PRIMARY KEY,
  rule_code      VARCHAR(64)    NOT NULL COMMENT '规则编码，如 RECYCLE_METAL',
  order_type     VARCHAR(16)    NOT NULL COMMENT 'RECYCLE/KITCHEN/REPAIR',
  category       VARCHAR(32)    NOT NULL COMMENT '品类（字典驱动），厨余用 DEFAULT',
  points_per_kg  DECIMAL(10, 2) COMMENT '每公斤积分（回收/维修）',
  points_per_bag DECIMAL(10, 2) COMMENT '每袋积分（厨余）',
  carbon_per_kg  DECIMAL(10, 4) COMMENT '每公斤减排 kgCO2',
  carbon_per_bag DECIMAL(10, 4) COMMENT '每袋减排 kgCO2',
  status         TINYINT        NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_rule_type_category (order_type, category)
) COMMENT '碳积分规则（配置化，零代码新增品类）';

CREATE TABLE IF NOT EXISTS biz_carbon_anchor (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  anchor_date  DATE      NOT NULL,
  merkle_root  CHAR(64)  NOT NULL,
  leaf_count   INT       NOT NULL,
  external_ref VARCHAR(255) COMMENT '外部存证凭证',
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_anchor_date (anchor_date)
) COMMENT '每日 Merkle 锚定（10.5，仅 INSERT）';

-- ---------- B3 可回收物回收 ----------

CREATE TABLE IF NOT EXISTS biz_recycle_order (
  id           BIGINT PRIMARY KEY,
  order_no     VARCHAR(32)    NOT NULL COMMENT '雪花号，前缀 RO',
  user_id      BIGINT         NOT NULL,
  staff_id     BIGINT         COMMENT '指派回收员',
  area_id      BIGINT         NOT NULL COMMENT '订单所属片区（@DataScope 过滤）',
  category     VARCHAR(32)    NOT NULL COMMENT '品类（字典驱动）',
  est_weight   DECIMAL(10, 2) COMMENT '用户预估重量 kg',
  weight       DECIMAL(10, 2) COMMENT '实收重量 kg（完成登记）',
  quantity     INT            COMMENT '件数',
  address      VARCHAR(255)   NOT NULL,
  lng          DECIMAL(10, 6),
  lat          DECIMAL(10, 6),
  appoint_time DATETIME       NOT NULL COMMENT '预约上门时间',
  status       TINYINT        NOT NULL DEFAULT 0 COMMENT '0待接单 1已接单 2进行中 3完成 4取消 5异常',
  amount       DECIMAL(10, 2) COMMENT '核算回款金额',
  transfer_to  VARCHAR(64)    COMMENT '转运去向（溯源）',
  repair_ref   BIGINT         COMMENT '联动来源维修订单 ID',
  cancel_reason VARCHAR(255),
  exception_reason VARCHAR(255) COMMENT '异常上报原因',
  service_points BIGINT COMMENT '用户承诺服务费积分（onsite02：0=不使用积分支付，完成时转账给回收员）',
  settle_status  TINYINT NOT NULL DEFAULT 0 COMMENT '服务费结算状态（onsite02：0未结算 1已结算 2免结算/异常转人工）',
  finish_time  DATETIME,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_status (user_id, status),
  KEY idx_staff_appoint (staff_id, appoint_time),
  KEY idx_order_area (area_id)
) COMMENT '可回收物上门回收订单（10.2）';

CREATE TABLE IF NOT EXISTS biz_recycle_price (
  id           BIGINT PRIMARY KEY,
  category     VARCHAR(32)    NOT NULL COMMENT '品类（字典驱动）',
  price_per_kg DECIMAL(10, 2) NOT NULL COMMENT '每公斤回收报价（元）',
  status       TINYINT        NOT NULL DEFAULT 1 COMMENT '0下架 1生效',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_price_category (category)
) COMMENT '可回收物报价';

CREATE TABLE IF NOT EXISTS biz_recycle_item (
  id          BIGINT PRIMARY KEY,
  order_id    BIGINT         NOT NULL,
  category    VARCHAR(32)    NOT NULL,
  weight      DECIMAL(10, 2) NOT NULL,
  amount      DECIMAL(10, 2) NOT NULL COMMENT '按报价核算金额',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0,
  KEY idx_item_order (order_id)
) COMMENT '回收物资明细';

-- ---------- B4 垃圾投放与清运 ----------

CREATE TABLE IF NOT EXISTS biz_kitchen_report (
  id          BIGINT PRIMARY KEY,
  user_id     BIGINT       NOT NULL,
  area_id     BIGINT       NOT NULL,
  point_name  VARCHAR(64)  NOT NULL COMMENT '投放点',
  bag_count   INT          NOT NULL,
  photo_url   VARCHAR(255) COMMENT '投放照片（MinIO）',
  report_time DATETIME     NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1风控冻结待复核 2已复核',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0,
  KEY idx_report_area_time (area_id, report_time),
  KEY idx_report_user (user_id)
) COMMENT '垃圾投放上报';

CREATE TABLE IF NOT EXISTS biz_kitchen_task (
  id           BIGINT PRIMARY KEY,
  area_id      BIGINT       NOT NULL,
  task_date    DATE         NOT NULL COMMENT '清运日期（上报次日）',
  total_bags   INT          NOT NULL COMMENT '聚合袋数',
  vehicle_id   BIGINT       COMMENT '派车建议',
  staff_id     BIGINT       COMMENT '执行环卫员',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待执行 1已完成',
  total_weight DECIMAL(10, 2) COMMENT '归档总重量 kg',
  vehicle_no   VARCHAR(32)  COMMENT '实际车号',
  destination  VARCHAR(128) COMMENT '终点（溯源）',
  proof_url    VARCHAR(255) COMMENT '凭证照片',
  finish_time  DATETIME,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_task_area_date (area_id, task_date),
  KEY idx_task_staff (staff_id)
) COMMENT '厨余清运任务';

CREATE TABLE IF NOT EXISTS biz_vehicle (
  id          BIGINT PRIMARY KEY,
  vehicle_no  VARCHAR(32) NOT NULL,
  area_id     BIGINT,
  capacity    INT COMMENT '容量（袋）',
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '0停用 1可用',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_vehicle_no (vehicle_no)
) COMMENT '清运车辆';

-- ---------- B4 风控告警 ----------

CREATE TABLE IF NOT EXISTS biz_risk_alert (
  id            BIGINT PRIMARY KEY,
  biz_type      VARCHAR(32) NOT NULL COMMENT 'KITCHEN_REPORT 等',
  biz_id        BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  area_id       BIGINT,
  reason        VARCHAR(255) COMMENT '触发原因（偏离倍数等）',
  frozen_points BIGINT      NOT NULL DEFAULT 0 COMMENT '冻结积分数',
  freeze_log_id BIGINT      COMMENT '关联冻结流水',
  status        TINYINT     NOT NULL DEFAULT 0 COMMENT '0待复核 1通过(解冻) 2驳回(扣减)',
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0,
  KEY idx_alert_status (status)
) COMMENT '风控告警记录（供后台复核）';

-- ---------- B5 站内信 ----------

CREATE TABLE IF NOT EXISTS biz_message (
  id          BIGINT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  msg_type    VARCHAR(32) NOT NULL COMMENT 'DISPATCHED/ORDER_FINISHED/POINTS_ARRIVED/POINTS_FROZEN',
  title       VARCHAR(64) NOT NULL,
  content     VARCHAR(500),
  read_flag   TINYINT     NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0,
  KEY idx_message_user (user_id, read_flag)
) COMMENT '站内信（B5 关键节点触发：派单/完成/到账/冻结）';

