-- =====================================================================
-- Track E · AI 与开放线增量 DDL（在 init.sql / init_trackB.sql / init_trackC.sql 之后执行）
-- 表：biz_ai_verify_log（F1 审核日志）/ biz_voice_session（F7 语音会话）/ biz_mcp_audit（F10 调用审计）
-- 说明：biz_agent_decision 已在 init_trackC.sql 冻结；biz_carbon_anchor 已在 init_trackB.sql 冻结
-- =====================================================================

-- F1 视觉审核日志（三层管线落库：pHash 防重 + 模型识别 + 阈值决策）
CREATE TABLE IF NOT EXISTS biz_ai_verify_log (
  id           BIGINT PRIMARY KEY,
  biz_type     VARCHAR(16)  NOT NULL COMMENT '业务来源：RECYCLE 回收完成照 / KITCHEN 垃圾投放照',
  biz_id       BIGINT       NOT NULL COMMENT '来源单 ID（回收单/投放上报）',
  user_id      BIGINT       COMMENT '提交用户',
  image_url    TEXT       NOT NULL COMMENT '图片（data URL 或内网地址；base64 超长故用 TEXT）',
  phash        CHAR(16)     COMMENT '感知哈希（64bit hex，防重复图）',
  exif_time    DATETIME     COMMENT 'EXIF 拍摄时间（缺失为 NULL，过旧强制人审）',
  model_name   VARCHAR(64)  COMMENT '实际命中模型 provider/model；人审兜底为 NULL',
  result_json  TEXT         COMMENT 'AI 结构化出参快照',
  category     VARCHAR(32)  COMMENT '识别品类',
  confidence   DECIMAL(4,3) COMMENT '识别置信度 0-1',
  purity       DECIMAL(4,3) COMMENT '纯净度 0-1',
  decision     VARCHAR(16)  NOT NULL COMMENT 'AUTO_PASS 自动通过 / MANUAL_REVIEW 积分冻结待人审 / REJECT 驳回',
  reject_reason VARCHAR(255) COMMENT '驳回/转人审原因（重复图/违规项/低置信/AI 不可用）',
  degraded     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=走了降级链（备选模型/mock/AI 宕机转人审）',
  elapsed_ms   INT          COMMENT '端到端耗时（P95 ≤ 5000 监控口径）',
  trace_id     VARCHAR(32)  COMMENT '请求追踪 ID（贯穿 Java/Python 日志）',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted      TINYINT      NOT NULL DEFAULT 0,
  KEY idx_verify_biz (biz_type, biz_id),
  KEY idx_verify_phash (phash),
  KEY idx_verify_user (user_id, create_time)
) COMMENT 'F1 AI 视觉审核日志';

-- F7 语音会话（多轮槽位补全；ASR/TTS 在云端网关，本表存文本轨迹）
CREATE TABLE IF NOT EXISTS biz_voice_session (
  id           BIGINT PRIMARY KEY,
  session_id   VARCHAR(64) NOT NULL COMMENT '会话 ID（一次语音交互多轮共享）',
  user_id      BIGINT      NOT NULL,
  turn_no      INT         NOT NULL DEFAULT 1 COMMENT '轮次',
  user_text    VARCHAR(500) COMMENT 'ASR 转写/用户输入文本',
  intent       VARCHAR(32)  COMMENT '识别意图：BOOK_RECYCLING/QUERY_ORDER/…/UNKNOWN',
  slots_json   VARCHAR(500) COMMENT '槽位快照 {category, appoint_time, address}',
  reply_text   VARCHAR(500) COMMENT 'TTS 播报/回复文本',
  confidence   DECIMAL(4,3),
  degraded     TINYINT     NOT NULL DEFAULT 0 COMMENT '1=解析降级（回退文本输入）',
  order_id     BIGINT      COMMENT '成单后回填回收单 ID',
  image_url    VARCHAR(500) COMMENT '本轮上传照片 URL（/api/file/preview/voice/...，V45）',
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted      TINYINT     NOT NULL DEFAULT 0,
  KEY idx_voice_session (session_id, turn_no),
  KEY idx_voice_user (user_id, create_time)
) COMMENT 'F7 语音碳管家会话轨迹';

-- F10 MCP 全量调用审计（08 章：每次工具调用均落审计，敏感操作可回溯）
CREATE TABLE IF NOT EXISTS biz_mcp_audit (
  id           BIGINT PRIMARY KEY,
  client_id    VARCHAR(64) NOT NULL COMMENT 'OAuth2 客户端（sys_oauth_client.client_id）',
  user_id      BIGINT      NOT NULL COMMENT '被代理的用户',
  tool_name    VARCHAR(64) NOT NULL COMMENT 'MCP 工具名：query_points/book_recycling/…',
  scope        VARCHAR(64) COMMENT '命中作用域',
  params_json  TEXT        COMMENT '调用入参快照',
  result_code  INT         COMMENT '业务结果码（200 成功 / 429 限流 / 403 越权…）',
  result_brief VARCHAR(500) COMMENT '结果摘要',
  need_confirm TINYINT     NOT NULL DEFAULT 0 COMMENT '1=下单类工具需用户端二次确认',
  confirm_state TINYINT    COMMENT '确认状态：0 待确认 1 已确认 2 已拒绝/超时（仅下单类）',
  curr_hash    CHAR(64)    COMMENT '审计链哈希（HashChainUtil 逐条链接）',
  prev_hash    CHAR(64),
  trace_id     VARCHAR(32),
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted      TINYINT     NOT NULL DEFAULT 0,
  KEY idx_mcp_client (client_id, create_time),
  KEY idx_mcp_user (user_id, create_time)
) COMMENT 'F10 MCP 调用审计链';

-- F1/F10 相关默认参数（sys_config 可热更）
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9101, 'ai.verify.pass-threshold', '0.85', 'F1 自动通过置信度阈值', '>=该值且无违规自动通过', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.verify.pass-threshold');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9102, 'ai.verify.review-threshold', '0.60', 'F1 转人审置信度阈值', '低于 pass 高于该值转人审', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.verify.review-threshold');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9103, 'ai.verify.phash-hamming', '5', 'F1 pHash 防重汉明距离阈值', '<=该值判定重复图直接驳回', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.verify.phash-hamming');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9104, 'ai.verify.exif-max-hours', '72', 'F1 EXIF 拍摄时间时效（小时）', '超时或缺失强制人审', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.verify.exif-max-hours');

-- F10 演示用 OAuth2 客户端（密钥明文 demo-mcp-secret，仅开发/演示；生产替换 BCrypt 密文）
INSERT INTO sys_oauth_client (id, client_id, client_secret, client_name, redirect_uri, scopes, rate_limit, status, deleted)
SELECT 9201, 'demo-ai-assistant',
       '$2a$12$Squ81QalgPCNJpFJyVBdZu86zhHLdPCmE5ro.HQovt5kUFWBduWEK',
       '演示 AI 助手', 'https://demo.example.com/callback', 'read,write', 60, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_oauth_client WHERE client_id = 'demo-ai-assistant');

-- F4 派单求解参数（sys_config 可热更，E6）
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9105, 'dispatch.solver-seconds', '2', 'F4 Timefold 求解时限（秒）', '超时即取当前最优解返回', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'dispatch.solver-seconds');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9106, 'dispatch.window-tolerance-min', '90', 'F4 时间窗容忍（分钟）', '预约时间±该值为软时间窗', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'dispatch.window-tolerance-min');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9107, 'dispatch.capacity-kg', '200', 'F4 单员工日承载上限（kg）', '超载记硬约束违反', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'dispatch.capacity-kg');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9108, 'dispatch.service-min', '10', 'F4 单点服务时长（分钟）', '上门收货平均耗时', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'dispatch.service-min');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9109, 'dispatch.shift-start-hour', '8', 'F4 班次开始（时）', '路线推演出发时刻', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'dispatch.shift-start-hour');

-- F5 动态定价护栏（sys_config 可热更，E7；Java 侧对 AI 系数二次夹逼防涨跌幅越界）
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9110, 'ai.pricing.factor-min', '0.8', 'F5 定价系数下限', 'AI 报价/积分系数低于该值即夹逼', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.pricing.factor-min');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9111, 'ai.pricing.factor-max', '1.2', 'F5 定价系数上限', 'AI 报价/积分系数高于该值即夹逼', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.pricing.factor-max');

-- F7 语音成单默认片区（sys_config 可热更，E7；正式版由地址簿/定位反推）
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9112, 'ai.voice.default-area-id', '1', 'F7 语音单默认片区 ID', '无地址簿时语音成单落此片区', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.voice.default-area-id');
