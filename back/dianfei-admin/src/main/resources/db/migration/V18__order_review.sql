-- ============================================================
-- V18 · 订单评价表（GapFill B2 交付，缺口 V2）
-- 背景：订单评价原为前端本地 Mock 态（onRate 仅置 rated=true，无后端调用）；
--       StaffPerformanceService.praiseRate 无评价数据源恒 0。本迁移建表支撑
--       评价落库闭环 + 绩效好评率按员工订单评价聚合。
-- 注意：V18 编号保留；V1~V17 已应用禁止改动。
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS biz_order_review (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_type  VARCHAR(16)  NOT NULL COMMENT 'RECYCLE / KITCHEN / REPAIR',
    order_id    BIGINT       NOT NULL COMMENT '业务订单 ID',
    user_id     BIGINT       NOT NULL COMMENT '评价用户',
    staff_id    BIGINT       COMMENT '服务员工（订单接单员工）',
    rating      TINYINT      NOT NULL COMMENT '星级 1-5',
    comment     VARCHAR(500) COMMENT '评价内容（可选）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review (order_type, order_id, user_id),
    KEY idx_staff (staff_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单评价';
