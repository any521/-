-- ============================================================
-- V21 · 工作人员自助申请（G1，onsite01 交付）
-- 背景：biz_staff 仅管理端创建（V2），无用户自助申请来源标记与申请备注；
--       「我的」页申请成为回收员/维修技术员需要 apply_scene/apply_reason 承载
--       来源与备注，审核通过后由 AdminStaffController 既有接口完成。
-- 注意：V21 编号保留；V1~V20 已应用禁止改动。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- biz_staff 加自助申请来源 / 申请备注 ----------
ALTER TABLE biz_staff
  ADD COLUMN apply_scene TINYINT NOT NULL DEFAULT 0 COMMENT '来源：0管理端创建 1用户自助申请',
  ADD COLUMN apply_reason VARCHAR(255) COMMENT '申请备注/驳回原因（复用审核接口的驳回原因时同步写入）',
  ADD KEY idx_staff_user_status (user_id, audit_status);

-- ---------- area_id 放宽可空（自助申请可不选片区，审核通过后由平台分配） ----------
-- 偏差记录：V2 建表 area_id NOT NULL，与「自助申请 areaId 可空=待分配」契约冲突，此处随 V21 一并放宽
ALTER TABLE biz_staff
  MODIFY COLUMN area_id BIGINT NULL COMMENT '绑定片区（用户自助申请可空，审核通过后由平台分配）';
