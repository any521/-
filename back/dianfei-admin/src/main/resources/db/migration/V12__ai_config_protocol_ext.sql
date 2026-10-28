-- =====================================================================
-- V12 · AI 供应商协议扩展（火山引擎 Responses + 自定义全参数模板）
-- ---------------------------------------------------------------------
-- 新增三列，支持：
--   protocol=responses —— 火山引擎 Ark Responses API（POST {base}/responses，
--                        body {model,input:[{role,content:[{type,input_text|input_image,...}]}]}，
--                        响应 output[0].content[0].text），内置模板无需额外填写
--   protocol=custom    —— 全参数自定义：method + request_path + body_template
--                        （JSON 模板，占位符 {{model}}/{{messages}}/{{input}}/{{text}}/{{image}}/{{temperature}}）
--                        + response_text_path（响应文本提取点路径），理论上兼容任意 LLM 协议
-- 旧协议（openai/anthropic）不受影响，新列默认值保证向后兼容。
-- =====================================================================
SET NAMES utf8mb4;

ALTER TABLE ai_provider
  ADD COLUMN method VARCHAR(8) NOT NULL DEFAULT 'POST' COMMENT '请求方法：POST/GET（custom/responses 协议）',
  ADD COLUMN body_template TEXT COMMENT '请求体 JSON 模板（custom 协议；占位符 {{model}}/{{messages}}/{{input}}/{{text}}/{{image}}/{{temperature}}，替换时按 JSON 编码注入）',
  ADD COLUMN response_text_path VARCHAR(255) COMMENT '响应文本提取点路径（点号分隔，如 output.0.content.0.text / choices.0.message.content）';
