-- ============================================================
-- V48 · 厨余投放人工审核：biz_kitchen_report 加投放坐标与审核字段 + 历史状态修正
-- 业务改造（B4）：投放需在垃圾回收站 50m 内，一律转工作人员审核，审核通过才发分。
-- 新状态机：0 待审核（新上报默认）/ 1 已通过（审核通过即发分）/ 2 已复核（历史保留）/ 3 已驳回。
-- 历史迁移：旧 0「正常（归档发分）」→ 1 已通过；旧 1「风控冻结待复核」→ 0 待审核（人工审核接管）。
-- 后端：KitchenReportService.report（50m 校验）/ audit（审核发分）；StaffKitchenController /reports。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 投放坐标（50m 站点校验 + 片区自动匹配依据） ----------
ALTER TABLE biz_kitchen_report
  ADD COLUMN lng DECIMAL(10, 6) COMMENT '投放经度（50m 站点校验）' AFTER photo_url,
  ADD COLUMN lat DECIMAL(10, 6) COMMENT '投放纬度' AFTER lng;

-- ---------- 审核字段 ----------
ALTER TABLE biz_kitchen_report
  ADD COLUMN audit_remark VARCHAR(255) COMMENT '审核驳回原因' AFTER status,
  ADD COLUMN audit_time DATETIME COMMENT '审核时间' AFTER audit_remark;

-- ---------- 历史状态修正（CASE 基于旧值计算互不干扰；2 已复核保留） ----------
UPDATE biz_kitchen_report SET status = CASE status WHEN 0 THEN 1 WHEN 1 THEN 0 ELSE status END
WHERE status IN (0, 1);

-- ---------- 状态注释同步 ----------
ALTER TABLE biz_kitchen_report
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已复核 3已驳回';
