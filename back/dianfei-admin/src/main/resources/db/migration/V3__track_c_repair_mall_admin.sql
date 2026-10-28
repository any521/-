-- ============================================================
-- Track C DDL · 维修商城后台线（对齐 10_database.md，MySQL 8.0 / InnoDB / utf8mb4）
-- 说明：Flyway V3，源自 db/init_trackC.sql；本文件只含 Track C 负责的表。
-- 公共字段：id/create_time/update_time/create_by/update_by/deleted（逻辑删除）；create_by/update_by 为 VARCHAR(64) 存操作人用户名（对齐 common.BaseEntity）
--   create_by/update_by 为 VARCHAR(64)（存操作人用户名，对齐 common.BaseEntity）。
-- 依赖 Track B 已冻结表：biz_recycle_order（repair_ref 联动）、biz_carbon_*、biz_risk_alert、biz_staff/biz_area/biz_vehicle
-- biz_agent_decision 全量入链（D17），生产环境使用仅 INSERT + UPDATE(status) 受限账号。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- C1 电路维修工单 ----------

CREATE TABLE IF NOT EXISTS biz_repair_order (
  id            BIGINT PRIMARY KEY,
  order_no      VARCHAR(32)  NOT NULL COMMENT '雪花号，前缀 RP',
  user_id       BIGINT       NOT NULL,
  staff_id      BIGINT       COMMENT '指派维修技术员',
  area_id       BIGINT       NOT NULL COMMENT '订单所属片区（@DataScope 过滤）',
  fault_type    VARCHAR(32)  NOT NULL COMMENT '故障类型（字典驱动）：插排/小家电/照明/其他',
  fault_desc    VARCHAR(500) COMMENT '故障描述（自由备注，AI 不可用时的降级入口）',
  address       VARCHAR(255) NOT NULL,
  lng           DECIMAL(10, 6),
  lat           DECIMAL(10, 6),
  appoint_start DATETIME     NOT NULL COMMENT '上门时段-起',
  appoint_end   DATETIME     NOT NULL COMMENT '上门时段-止',
  old_for_new   TINYINT      NOT NULL DEFAULT 0 COMMENT '以旧换新意向 0否 1是',
  gas_delivery  TINYINT      NOT NULL DEFAULT 0 COMMENT '天然气配送顺带 0否 1是',
  diagnose_json TEXT         COMMENT 'F2 预诊结果 JSON（可能故障/建议配件/可修复概率），AI 降级时为空',
  repairable    TINYINT      NOT NULL DEFAULT 0 COMMENT '可修复性判定 0未判定 1可修复 2不可修复',
  report_json   TEXT         COMMENT '检修报告 JSON（故障定位/处理过程/更换配件/科普）',
  science_note  VARCHAR(500) COMMENT '科普留痕（面向用户的用电安全讲解摘要）',
  recycle_ref   BIGINT       COMMENT '联动生成的回收订单 ID（与 biz_recycle_order.repair_ref 双向）',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待接单 1已接单 2检修中 3已完成 4已取消 5已转回收',
  cancel_reason VARCHAR(255),
  finish_time   DATETIME,
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_repair_order_no (order_no),
  KEY idx_repair_user_status (user_id, status),
  KEY idx_repair_staff_appoint (staff_id, appoint_start),
  KEY idx_repair_area (area_id),
  KEY idx_repair_recycle_ref (recycle_ref)
) COMMENT '电路维修工单（11.2）';

CREATE TABLE IF NOT EXISTS biz_repair_part (
  id          BIGINT PRIMARY KEY,
  order_id    BIGINT         NOT NULL COMMENT '关联维修工单',
  part_name   VARCHAR(64)    NOT NULL COMMENT '配件名称',
  part_model  VARCHAR(64)    COMMENT '型号',
  quantity    INT            NOT NULL DEFAULT 1,
  price       DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '配件单价（元）',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0,
  KEY idx_part_order (order_id)
) COMMENT '维修配件登记';

-- ---------- C3 积分商城 ----------

CREATE TABLE IF NOT EXISTS biz_mall_product (
  id           BIGINT PRIMARY KEY,
  product_name VARCHAR(64)  NOT NULL,
  cover_url    VARCHAR(255) COMMENT '商品图（MinIO）',
  points_price BIGINT       NOT NULL COMMENT '所需积分',
  stock        INT          NOT NULL DEFAULT 0 COMMENT '库存（条件更新防超卖）',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0下架 1上架',
  description  VARCHAR(500),
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  KEY idx_product_status (status)
) COMMENT '积分商城商品';

CREATE TABLE IF NOT EXISTS biz_mall_exchange (
  id            BIGINT PRIMARY KEY,
  exchange_no   VARCHAR(32) NOT NULL COMMENT '雪花号，前缀 MO',
  user_id       BIGINT      NOT NULL,
  product_id    BIGINT      NOT NULL,
  product_name  VARCHAR(64) NOT NULL COMMENT '商品名快照',
  points_cost   BIGINT      NOT NULL COMMENT '本单扣减积分总额',
  quantity      INT         NOT NULL DEFAULT 1,
  carbon_log_id BIGINT      COMMENT '关联扣分流水（溯源）',
  status        TINYINT     NOT NULL DEFAULT 0 COMMENT '0已兑换 1已发货 2已完成 3已取消(积分退回)',
  address       VARCHAR(255) COMMENT '收货地址（实物）',
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_exchange_no (exchange_no),
  KEY idx_exchange_user (user_id),
  KEY idx_exchange_product (product_id)
) COMMENT '积分兑换订单';

-- ---------- C4 后台管控与风控 ----------

CREATE TABLE IF NOT EXISTS biz_complaint (
  id            BIGINT PRIMARY KEY,
  order_type    VARCHAR(16)  NOT NULL COMMENT 'RECYCLE/KITCHEN/REPAIR/MALL',
  order_id      BIGINT       NOT NULL COMMENT '来源单 ID',
  user_id       BIGINT       NOT NULL,
  type          TINYINT      NOT NULL COMMENT '1投诉 2退款 3追责 4补发积分 5纠纷仲裁',
  content       VARCHAR(500) NOT NULL,
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2已驳回',
  handle_result VARCHAR(500) COMMENT '处理结论（退款金额/补发积分数/仲裁结果）',
  handler_id    BIGINT       COMMENT '处理人',
  handle_time   DATETIME,
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted       TINYINT NOT NULL DEFAULT 0,
  KEY idx_complaint_status (status),
  KEY idx_complaint_user (user_id)
) COMMENT '订单售后（投诉/退款/追责/补发/仲裁）';

CREATE TABLE IF NOT EXISTS biz_schedule (
  id              BIGINT PRIMARY KEY,
  staff_id        BIGINT  NOT NULL,
  area_id         BIGINT  NOT NULL COMMENT '排班片区',
  work_date       DATE    NOT NULL,
  shift_type      TINYINT NOT NULL DEFAULT 1 COMMENT '1白班 2晚班',
  biz_type        TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1节假日 2跨片区支援',
  support_area_id BIGINT  COMMENT '支援目标片区（biz_type=2）',
  remark          VARCHAR(255),
  create_time     DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted         TINYINT NOT NULL DEFAULT 0,
  -- 逻辑删除兼容：不用唯一键（否则已删行占位导致重排冲突），员工+日期+班次防重由 ScheduleService 校验
  KEY idx_schedule_staff_date (staff_id, work_date, shift_type),
  KEY idx_schedule_area_date (area_id, work_date)
) COMMENT '员工排班（节假日/跨片区调配）';

CREATE TABLE IF NOT EXISTS biz_leave_request (
  id           BIGINT PRIMARY KEY,
  staff_id     BIGINT       NOT NULL,
  start_date   DATE         NOT NULL,
  end_date     DATE         NOT NULL,
  reason       VARCHAR(255) NOT NULL,
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  audit_by     BIGINT       COMMENT '审核人',
  audit_remark VARCHAR(255),
  audit_time   DATETIME,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT NOT NULL DEFAULT 0,
  KEY idx_leave_staff (staff_id),
  KEY idx_leave_status (status)
) COMMENT '员工请假申请（后台审核）';

CREATE TABLE IF NOT EXISTS biz_device_maintenance (
  id          BIGINT PRIMARY KEY,
  target_type TINYINT      NOT NULL COMMENT '1清运车辆 2设备',
  target_no   VARCHAR(64)  NOT NULL COMMENT '车牌号/设备编号',
  content     VARCHAR(500) NOT NULL COMMENT '运维内容（保养/维修/年检）',
  cost        DECIMAL(10, 2) DEFAULT 0 COMMENT '费用（元）',
  maint_date  DATE         NOT NULL,
  operator_id BIGINT       COMMENT '登记人',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT NOT NULL DEFAULT 0,
  KEY idx_maint_target (target_type, target_no)
) COMMENT '车辆设备运维登记';

-- ---------- C5 Agent 驾驶舱 ----------

CREATE TABLE IF NOT EXISTS biz_agent_decision (
  id                  BIGINT PRIMARY KEY,
  agent_id            VARCHAR(64)  NOT NULL COMMENT '发起 Agent（principal_type=AGENT 系统账号）',
  action_type         VARCHAR(64)  NOT NULL COMMENT '动作类型：DISPATCH_ADJUST/PRICE_CHANGE/POINTS_ADJUST 等',
  target_uri          VARCHAR(255) COMMENT '目标资源 URI',
  params_json         TEXT         COMMENT '动作参数快照 JSON',
  evidences_json      TEXT         COMMENT '依据链 JSON（source/description/dataRef 数组）',
  risk_level          TINYINT      NOT NULL COMMENT '1低(自动执行) 2中(运营审批) 3高(双人复核)',
  status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回 3已执行 4执行失败',
  decision_hash       CHAR(64)     COMMENT '决策单内容哈希（入链）',
  prev_hash           CHAR(64)     COMMENT '上一条决策哈希（链式，对齐 D17 全量入链）',
  first_approver      BIGINT       COMMENT '一审人（中/高风险）',
  first_approve_time  DATETIME,
  second_approver     BIGINT       COMMENT '二审人（高风险双人复核）',
  second_approve_time DATETIME,
  reject_reason       VARCHAR(255),
  execute_result      VARCHAR(500) COMMENT '执行结果回写',
  execute_time        DATETIME,
  create_time         DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted             TINYINT NOT NULL DEFAULT 0,
  KEY idx_decision_status (status),
  KEY idx_decision_agent (agent_id),
  KEY idx_decision_risk (risk_level, status)
) COMMENT 'Agent 决策单（HITL 审批，全量入链）';
