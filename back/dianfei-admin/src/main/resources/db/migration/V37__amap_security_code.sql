-- ============================================================
-- V37 · 高德安全密钥（jscode / securityJsCode）：
-- 高德新版 Web 服务 API 与 JS API 2.0 均要求 key 与安全密钥配对，
-- key 缺失安全密钥时请求返回无法授权。ajouter 种子空值，
-- 由管理端「参数配置」页（mapConfig.vue）配置，保存即热更新（ConfigService 同键刷新）。
-- 后端 REST 代理把 security_code 作为 jscode 参数随请求追加；
-- 前端 JS API 2.0 加载前注入 window._AMapSecurityConfig.securityJsCode。
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9207, 'amap.security_code', '', '高德安全密钥', '即管理控制台「安全密钥 jscode」；后端代理高德 REST 追加 jscode 参数、前端 JS API 2.0 注入 securityJsCode；必须与 key 配对使用（V37）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'amap.security_code');