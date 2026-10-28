-- =====================================================================
-- Track E 修复：biz_ai_verify_log.image_url 扩为 TEXT（B05 全链路实测缺陷）
-- 背景：前端拍照/上传图片以 data:image/png;base64,... 提交，VARCHAR(500) 必然超长
--       → MySQL Data too long 5000 系统异常；AI 审核链路无法落库
-- 修复：image_url 改为 TEXT（NOT NULL 语义保留），存量数据不受影响
-- =====================================================================
ALTER TABLE biz_ai_verify_log
  MODIFY image_url TEXT NOT NULL COMMENT '图片（data URL 或内网地址）';
