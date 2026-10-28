-- =====================================================================
-- V10 · biz_ai_verify_log.biz_id 放宽为 NULL
-- ---------------------------------------------------------------------
-- 背景：/api/ai/verify 既服务于「下单后图片审核」（携带来源单 bizId），
-- 也服务于「下单前品类识别」（前端拍照驱动 3D 模型预览，此时无订单 ID）。
-- 任务文档 B05 验收示例 body 未带 bizId，原契约意图即允许为空；
-- pHash 防重按 user_id 维度（idx_verify_user），bizId 可空不影响去重。
-- Python verify_service 仅在日志中引用 biz_id，不参与业务逻辑。
-- =====================================================================

ALTER TABLE biz_ai_verify_log
  MODIFY COLUMN biz_id BIGINT NULL COMMENT '来源单 ID（回收单/投放上报）；下单前品类识别场景为 NULL';
