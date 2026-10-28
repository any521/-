-- =====================================================================
-- V35 · AI 供应商联网搜索配置（客服 AGENT-03 / 语音助手行情增强）
-- ---------------------------------------------------------------------
-- 沿用 V12/V13/V14 扩列先例：ai_provider 新增两列承载联网搜索配置。
-- 约定：维护一行专用记录 name='web-search'（vendor='search'）——
--   启用开关 web_search_enabled（0 停用 1 启用）
--   provider 选择 web_search_provider（bocha / tavily；空=auto 自动选择）
--   密钥复用 api_key 列（AES 加密存储）与 api_key_env（环境变量优先）
-- Python ai-back 经 /api/ai/config/runtime 拉取（30s TTL 热更新），无需重启。
-- =====================================================================
SET NAMES utf8mb4;

ALTER TABLE ai_provider
  ADD COLUMN web_search_provider VARCHAR(32) COMMENT '联网搜索服务商：bocha / tavily；空=auto（bocha 优先，无则 tavily）',
  ADD COLUMN web_search_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '联网搜索启用开关：0停用 1启用（仅 name=web-search 行生效）';

-- 专用承载行：vendor='search' 使其不参与 LLM 路由；密钥经 api_key 列加密入库
INSERT INTO ai_provider (id, name, vendor, protocol, base_url, request_path, api_key, api_key_env, models, capabilities, extra_headers, timeout_seconds, enabled, sort, remark, create_time, deleted)
VALUES
  (5, 'web-search', 'search', 'custom', 'https://api.bochaai.com', NULL, NULL, 'BOCHA_API_KEY',
   '["web-search"]', '["search"]', NULL, 5, 1, 98, '联网搜索（客服/语音行情；web_search_provider 选 bocha/tavily，密钥走 api_key 或 api_key_env）', NOW(), 0)
ON DUPLICATE KEY UPDATE name = name;
