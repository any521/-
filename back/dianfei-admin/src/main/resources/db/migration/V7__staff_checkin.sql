-- ============================================================
-- Batch B04 DDL · 员工签到（对齐 12_api.md / tasks/batch_B04_staff_end.md，MySQL 8.0 / InnoDB / utf8mb4）
-- 说明：Flyway V7，本批次独占编号（15_parallel_batches.md §3.1）。
-- 公共字段：id/create_time/update_time/create_by/update_by/deleted（逻辑删除）；
--   create_by/update_by 为 VARCHAR(64)（存操作人用户名，对齐 common.BaseEntity）。
-- 依赖 Track B 已冻结表：biz_staff（staff_id 关联，员工端接口按 biz_staff.user_id 解析身份）。
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS biz_staff_checkin (
  id           BIGINT PRIMARY KEY,
  staff_id     BIGINT       NOT NULL COMMENT '关联 biz_staff',
  check_date   DATE         NOT NULL COMMENT '打卡日期',
  on_time      DATETIME     COMMENT '上班打卡时间',
  off_time     DATETIME     COMMENT '下班打卡时间',
  on_location  VARCHAR(255) COMMENT '上班打卡定位描述',
  off_location VARCHAR(255) COMMENT '下班打卡定位描述',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_checkin_staff_date (staff_id, check_date),
  KEY idx_checkin_date (check_date)
) COMMENT '员工签到';
