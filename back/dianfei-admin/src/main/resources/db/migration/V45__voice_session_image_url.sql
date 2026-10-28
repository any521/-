-- =====================================================================
-- V45 · 语音管家照片持久化
-- 用户上传的原图先经 /api/file/upload?biz=voice 存入文件服务（sys_file_object 登记），
-- URL 随会话轨迹落库 biz_voice_session.image_url，供留痕/审计/回显
-- =====================================================================

ALTER TABLE biz_voice_session
  ADD COLUMN image_url VARCHAR(500) NULL COMMENT '本轮上传照片 URL（/api/file/preview/voice/...）' AFTER order_id;
