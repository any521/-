-- =====================================================================
-- V11 · AI 模型配置管理（CCSwitch 风格：管理端动态配置多供应商 + 能力路由）
-- ---------------------------------------------------------------------
-- 替代 ai-back/config/{providers,routing}.json 静态文件：
--   ai_provider  —— 供应商声明（协议/密钥/模型/能力），支持 openai/anthropic/custom 协议
--   ai_route     —— 能力→模型路由链 + 降级兜底
-- Python ai-back 经 Java 内部 API /api/ai/config/runtime 拉取（X-Internal-Token 鉴权），
-- 内存缓存 + TTL 热更新；管理端改库即生效，无需重启。
-- 密钥安全：api_key 列 AES 加密存储（dianfei.ai.config-aes-key），
--          也可仅填 api_key_env 从环境变量读取（开发环境优先）。
-- =====================================================================
SET NAMES utf8mb4;

-- ---------- 供应商表 ----------
CREATE TABLE IF NOT EXISTS ai_provider (
  id            BIGINT       PRIMARY KEY,
  name          VARCHAR(64)  NOT NULL COMMENT '供应商唯一名（如 qwen-vl）',
  vendor        VARCHAR(32)  NOT NULL COMMENT '厂商标识：alibaba/zhipu/deepseek/openai/anthropic/custom/internal',
  protocol      VARCHAR(16)  NOT NULL DEFAULT 'openai' COMMENT '请求协议：openai/anthropic/custom',
  base_url      VARCHAR(255) NOT NULL COMMENT 'API 基址（不含请求路径）',
  request_path  VARCHAR(128) COMMENT '请求路径；空=按协议推导（openai→/chat/completions, anthropic→/v1/messages）',
  api_key       TEXT         COMMENT '密钥（AES 加密存储）；与 api_key_env 二选一或都填',
  api_key_env   VARCHAR(64)  COMMENT '密钥环境变量名；填写后优先从环境变量读取，不入库',
  models        JSON         COMMENT '支持的模型列表（["qwen-vl-max","qwen-vl-plus"]）',
  capabilities  JSON         COMMENT '能力标签（["vision","chat"]）',
  extra_headers JSON         COMMENT '自定义请求头（custom 协议或需追加认证头时使用）',
  timeout_seconds INT        NOT NULL DEFAULT 8 COMMENT '调用超时（秒）',
  enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  sort          INT          NOT NULL DEFAULT 0 COMMENT '展示排序',
  remark        VARCHAR(255),
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted       TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_provider_name (name, deleted),
  KEY idx_provider_enabled (enabled, sort)
) COMMENT 'AI 供应商配置（管理端动态维护）';

-- ---------- 能力路由表 ----------
CREATE TABLE IF NOT EXISTS ai_route (
  id            BIGINT       PRIMARY KEY,
  capability    VARCHAR(64)  NOT NULL COMMENT '能力标识：visual_verify/repair_diagnose/voice_parse/...',
  chain         JSON         NOT NULL COMMENT '路由链 [{"provider":"qwen-vl","model":"qwen-vl-max"},...]',
  fallback      VARCHAR(64)  NOT NULL DEFAULT 'MANUAL' COMMENT '链耗尽后降级代号：MANUAL_REVIEW/KNOWLEDGE_RULE/...',
  max_retries   INT          NOT NULL DEFAULT 1 COMMENT '单供应商出参校验失败重试次数',
  retry_temperature_decay DECIMAL(3,2) NOT NULL DEFAULT 0.50 COMMENT '重试温度衰减系数',
  enabled       TINYINT      NOT NULL DEFAULT 1,
  remark        VARCHAR(255),
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted       TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_route_capability (capability, deleted)
) COMMENT 'AI 能力路由（管理端动态维护）';

-- ---------- 种子：从 config/providers.json + routing.json 迁移 ----------
-- 密钥留空（用户在管理端录入明文，后端加密入库）；api_key_env 保留开发环境变量名
INSERT INTO ai_provider (id, name, vendor, protocol, base_url, request_path, api_key, api_key_env, models, capabilities, extra_headers, timeout_seconds, enabled, sort, remark, create_time, deleted)
VALUES
  (1, 'qwen-vl',  'alibaba',  'openai',    'https://dashscope.aliyuncs.com/compatible-mode/v1', NULL, NULL, 'QWEN_API_KEY',
   '["qwen-vl-max","qwen-vl-plus"]', '["vision","chat"]', NULL, 8, 1, 1, '通义千问 VL（视觉+对话）', NOW(), 0),
  (2, 'glm-4v',   'zhipu',    'openai',    'https://open.bigmodel.cn/api/paas/v4', NULL, NULL, 'GLM_API_KEY',
   '["glm-4v-plus","glm-4-flash"]', '["vision","chat"]', NULL, 8, 1, 2, '智谱 GLM-4V（视觉+对话）', NOW(), 0),
  (3, 'deepseek', 'deepseek', 'openai',    'https://api.deepseek.com/v1', NULL, NULL, 'DEEPSEEK_API_KEY',
   '["deepseek-chat","deepseek-reasoner"]', '["chat","reasoning"]', NULL, 15, 1, 3, 'DeepSeek（对话+推理）', NOW(), 0),
  (4, 'mock',     'internal', 'custom',    'internal://mock', NULL, NULL, '',
   '["mock-vision","mock-chat"]', '["vision","chat","reasoning"]', NULL, 1, 1, 99, '内置 Mock（联调/降级演练）', NOW(), 0)
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO ai_route (id, capability, chain, fallback, max_retries, retry_temperature_decay, enabled, remark, create_time, deleted)
VALUES
  (1, 'visual_verify',   '[{"provider":"qwen-vl","model":"qwen-vl-max"},{"provider":"glm-4v","model":"glm-4v-plus"},{"provider":"mock","model":"mock-vision"}]', 'MANUAL_REVIEW',  1, 0.50, 1, 'F1 视觉审核', NOW(), 0),
  (2, 'repair_diagnose', '[{"provider":"deepseek","model":"deepseek-chat"},{"provider":"glm-4v","model":"glm-4-flash"},{"provider":"mock","model":"mock-chat"}]', 'KNOWLEDGE_RULE', 1, 0.50, 1, 'F2 维修预诊', NOW(), 0),
  (3, 'voice_parse',     '[{"provider":"deepseek","model":"deepseek-chat"},{"provider":"mock","model":"mock-chat"}]', 'TEXT_INPUT',      1, 0.50, 1, 'F7 语音解析', NOW(), 0),
  (4, 'forecast',        '[{"provider":"mock","model":"mock-chat"}]', 'MOVING_AVERAGE', 0, 1.00, 1, 'F9 回收量预测', NOW(), 0),
  (5, 'pricing',         '[{"provider":"deepseek","model":"deepseek-chat"},{"provider":"mock","model":"mock-chat"}]', 'BASE_PRICE',      0, 1.00, 1, 'F5 动态定价', NOW(), 0),
  (6, 'agent_plan',      '[{"provider":"deepseek","model":"deepseek-reasoner"},{"provider":"deepseek","model":"deepseek-chat"},{"provider":"mock","model":"mock-chat"}]', 'RULE_ENGINE', 1, 0.50, 1, 'F8 Agent 规划', NOW(), 0)
ON DUPLICATE KEY UPDATE capability = capability;

-- ---------- 管理端菜单：系统管理目录(100)下追加 AI 供应商 + AI 路由 ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES
  (109, 100, 'AI 供应商', 2, 'ai-provider', 'system/ai-provider', 'system:ai:provider:list', 'Connection', 8, 1, 1, NOW(), 0),
  (110, 100, 'AI 路由',   2, 'ai-route',   'system/ai-route',   'system:ai:route:list',   'Share',      9, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ADMIN 角色补授新菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (109, 110) AND deleted = 0
ON DUPLICATE KEY UPDATE role_id = role_id;
