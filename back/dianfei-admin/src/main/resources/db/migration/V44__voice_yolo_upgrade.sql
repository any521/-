-- =====================================================================
-- V44 · 语音管家升级与 YOLO 图片检测（LLM+RAG + YOLOv8 ONNX 标注）
-- ---------------------------------------------------------------------
-- 目标 1:确保语音管家使用真实 LLM+RAG（非 Mock）
--   - 新增 VolcEngine Ark 供应商配置（视觉多模态）
--   - voice_parse 路由链调整：deepseek→mock 降级仍有效
--   - RAG 检索上下文已在 voice_service.py 实现
-- 
-- 目标 2:新增 YOLO 图片检测后端接口
--   - POST /api/ai/yolo/detect → ai-back /v1/yolo/detect
--   - 使用前端现有 onnx 模型（yolo-recycle-v0.3.1.onnx）
--   - 返回标注图 base64 + 检测结果列表
-- 
-- 目标 3:管理端 UI（待后续开发）
--   - AI 供应商页面已存在（V11），可直接录入密钥
--   - 无需新表或迁移
-- =====================================================================

-- ---------- 补充供应商：VolcEngine Ark（火山方舟）----------
-- 用于视觉能力时作为 fallback；chat 能力可单独使用
INSERT INTO ai_provider (id, name, vendor, protocol, base_url, request_path, api_key, api_key_env, models, capabilities, extra_headers, timeout_seconds, max_tokens, enabled, sort, remark, create_time, deleted)
VALUES
  (5, 'ark', 'volcengine', 'openai', 'https://ark.cn-beijing.volces.com/api/v3', NULL, NULL, 'ARK_API_KEY',
   '["doubao-seed-2-0-mini-260428"]', '["vision","chat"]', NULL, 15, 8192, 1, 4, '火山方舟（多模态视觉+对话）', NOW(), 0)
ON DUPLICATE KEY UPDATE name = name;

-- ---------- 可选扩展：联网搜索服务 ----------
-- 当用户询问"近期回收价格"等实时信息时，由 web_search 模块调用
-- 注意：需在 provider 表中增加一条 name='web-search' 的记录，见下方注释
-- INSERT INTO ai_provider ... 参考 providers.json 中的 tavily/bocha

-- ---------- 调整 voice_parse 路由链（可选）----------
-- 若希望语音管家优先使用火山的多模态理解能力（如上传垃圾图后语音追问），
-- 可将 ark 加入 chain；否则保持 deepseek 为主（成本低、响应快）
UPDATE ai_route SET chain = '[{"provider":"deepseek","model":"deepseek-chat"},{"provider":"ark","model":"doubao-seed-2-0-mini-260428"},{"provider":"mock","model":"mock-chat"}]' 
WHERE capability = 'voice_parse' AND deleted = 0;

-- ---------- 新增 YOLO 能力路由（可选，供其他业务扩展）----------
INSERT INTO ai_route (id, capability, chain, fallback, max_retries, retry_temperature_decay, enabled, remark, create_time, deleted)
VALUES
  (7, 'yolo_detect', '[{"provider":"mock","model":"mock-yolo"}]', 'INTERNAL_ERROR', 0, 1.00, 1, 'YOLO 垃圾分类检测（本地 ONNX）', NOW(), 0)
ON DUPLICATE KEY UPDATE capability = capability;

-- ---------- 数据库备注说明 ----------
-- 1. 密钥录入方式：
--    - 管理端菜单 → AI 供应商 → 编辑记录
--    - api_key 列填明文（后端 AES 加密入库）
--    - 或直接设置 api_key_env = "DEEPSEEK_API_KEY"（推荐生产环境）
-- 2. 环境变量优先级：api_key_env 非空 → 代码直接读取；api_key 为空则回退解密列
-- 3. ai-back 启动时会拉取 /api/ai/config/runtime，自动刷新缓存
-- 4. 无需重启 Java 或 Python 服务即可生效
