-- =====================================================================
-- V13 · AI 供应商输出上限 max_tokens
-- ---------------------------------------------------------------------
-- 结构化出参（3D spec / 诊断 JSON）变长后，默认 4096 输出 token 易截断
-- （现象：JSON 解析报 Expecting ',' delimiter 等格式错误）。
-- 新增 max_tokens 列，请求体按供应商配置携带；长输出供应商（DeepSeek/Ark）默认 8192。
-- =====================================================================
SET NAMES utf8mb4;

ALTER TABLE ai_provider
  ADD COLUMN max_tokens INT NOT NULL DEFAULT 4096 COMMENT '单次输出上限 token；长结构化 JSON（3D spec 等）建议 8192';

-- 长结构化输出供应商：放宽输出上限，避免截断
UPDATE ai_provider SET max_tokens = 8192 WHERE name IN ('deepseek', 'ark-responses');
