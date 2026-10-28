-- 报价地区字段（D15 增强）：联网行情落库时记录查询城市；
-- 单品类查询失败不落库 → 该行 city/update_time 保留上次成功值，前端每类展示「地区 · 更新时间」
ALTER TABLE biz_recycle_price
    ADD COLUMN city VARCHAR(64) NULL COMMENT '联网行情地区（查询该品类时所在城市，失败保留上次）' AFTER status;
