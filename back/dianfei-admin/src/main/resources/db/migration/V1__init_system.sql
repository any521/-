-- ============================================================
-- V1 · Track A 系统表与种子数据（源自 db/init.sql，对齐 10_database.md）
-- MySQL 8.0 / InnoDB / utf8mb4；公共字段 id/create_time/update_time/create_by/update_by/deleted
-- Flyway 约定：目标库由数据源连接串指定，不在迁移脚本中 CREATE DATABASE / USE。
-- 脚本保持幂等（IF NOT EXISTS / ON DUPLICATE KEY），兼容对既有库 baseline 后首跑。
-- 碳流水/锚定表生产环境使用仅 INSERT 权限账号（04.8 / D16）。
-- ============================================================
SET NAMES utf8mb4;

-- ============================================================
-- §1 Track A · 系统表
-- ============================================================

-- ---------- A3 认证 ----------

CREATE TABLE IF NOT EXISTS sys_user (
  id             BIGINT PRIMARY KEY,
  username       VARCHAR(32)  NOT NULL,
  password       VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  nickname       VARCHAR(32),
  phone          VARCHAR(20),
  avatar         VARCHAR(255),
  role           VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT 'USER/RECYCLER/SANITATION/TECHNICIAN/ADMIN/AGENT',
  area_id        BIGINT       COMMENT '员工所属片区（@DataScope 依据）',
  principal_type VARCHAR(16)  NOT NULL DEFAULT 'HUMAN' COMMENT 'HUMAN/AGENT（04.8 身份三分）',
  real_name      VARCHAR(32)  COMMENT '实名姓名',
  id_card        VARCHAR(32)  COMMENT '身份证号',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  create_time    DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted        TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_phone (phone),
  KEY idx_user_area (area_id)
) COMMENT '系统用户';

CREATE TABLE IF NOT EXISTS sys_oauth_client (
  id            BIGINT PRIMARY KEY,
  client_id     VARCHAR(64)  NOT NULL,
  client_secret VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  client_name   VARCHAR(64)  NOT NULL,
  redirect_uri  VARCHAR(500) COMMENT '回调地址，逗号分隔',
  scopes        VARCHAR(255) COMMENT '授权范围，逗号分隔',
  rate_limit    INT          NOT NULL DEFAULT 60 COMMENT '每分钟限流（08 章：60 req/min）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  create_time   DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted       TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_oauth_client_id (client_id)
) COMMENT 'MCP 第三方 OAuth2 客户端';

-- ---------- A4 系统管理 ----------

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT PRIMARY KEY,
  role_code   VARCHAR(32) NOT NULL COMMENT '角色编码，如 ADMIN',
  role_name   VARCHAR(32) NOT NULL,
  remark      VARCHAR(255),
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted     TINYINT     NOT NULL DEFAULT 0,
  UNIQUE KEY uk_role_code (role_code)
) COMMENT '角色';

CREATE TABLE IF NOT EXISTS sys_menu (
  id          BIGINT PRIMARY KEY,
  parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单，0 为根',
  menu_name   VARCHAR(32)  NOT NULL,
  menu_type   TINYINT      NOT NULL COMMENT '1目录 2菜单 3按钮',
  path        VARCHAR(128) COMMENT '前端路由路径',
  component   VARCHAR(128) COMMENT '前端组件',
  perm        VARCHAR(64)  COMMENT '权限码，如 system:role:add',
  icon        VARCHAR(64),
  sort        INT          NOT NULL DEFAULT 0,
  visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '0隐藏 1显示',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted     TINYINT      NOT NULL DEFAULT 0,
  KEY idx_menu_parent (parent_id)
) COMMENT '菜单/权限';

CREATE TABLE IF NOT EXISTS sys_role_menu (
  id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) COMMENT '角色-菜单关联';

CREATE TABLE IF NOT EXISTS sys_dict_type (
  id          BIGINT PRIMARY KEY,
  dict_type   VARCHAR(64) NOT NULL COMMENT '字典类型，如 recycle_category',
  dict_name   VARCHAR(64) NOT NULL,
  remark      VARCHAR(255),
  status      TINYINT     NOT NULL DEFAULT 1,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted     TINYINT     NOT NULL DEFAULT 0,
  UNIQUE KEY uk_dict_type (dict_type)
) COMMENT '字典类型';

CREATE TABLE IF NOT EXISTS sys_dict_data (
  id          BIGINT PRIMARY KEY,
  dict_type   VARCHAR(64)  NOT NULL,
  dict_label  VARCHAR(64)  NOT NULL COMMENT '显示名，如 金属',
  dict_value  VARCHAR(64)  NOT NULL COMMENT '值，如 METAL',
  sort        INT          NOT NULL DEFAULT 0,
  remark      VARCHAR(255),
  status      TINYINT      NOT NULL DEFAULT 1,
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted     TINYINT      NOT NULL DEFAULT 0,
  KEY idx_dict_data_type (dict_type)
) COMMENT '字典数据（业务品类由此驱动，D5）';

CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT PRIMARY KEY,
  config_key   VARCHAR(64)  NOT NULL COMMENT '参数键，如 kitchen.report.start-hour',
  config_value VARCHAR(500) NOT NULL,
  config_name  VARCHAR(64)  NOT NULL,
  remark       VARCHAR(255),
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted      TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_config_key (config_key)
) COMMENT '参数配置（热更新：改库即刷 Redis 缓存）';

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  module         VARCHAR(32),
  operation      VARCHAR(64),
  method         VARCHAR(128),
  params         VARCHAR(2000),
  result         VARCHAR(2000),
  status         TINYINT COMMENT '0成功 1失败',
  error_msg      VARCHAR(2000),
  cost_ms        BIGINT,
  oper_id        BIGINT,
  oper_name      VARCHAR(32),
  principal_type VARCHAR(16) COMMENT 'HUMAN/AGENT/MCP',
  oper_ip        VARCHAR(64),
  oper_time      DATETIME,
  KEY idx_oper_log_time (oper_time),
  KEY idx_oper_log_user (oper_id)
) COMMENT '操作日志（切面异步落库，永久归档 D7）';

CREATE TABLE IF NOT EXISTS sys_login_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(32),
  client_type VARCHAR(16) COMMENT 'USER/STAFF/ADMIN',
  login_ip    VARCHAR(64),
  user_agent  VARCHAR(500),
  status      TINYINT COMMENT '0成功 1失败',
  msg         VARCHAR(255) COMMENT '失败原因',
  login_time  DATETIME,
  KEY idx_login_log_time (login_time),
  KEY idx_login_log_username (username)
) COMMENT '登录日志';

CREATE TABLE IF NOT EXISTS sys_notice (
  id           BIGINT PRIMARY KEY,
  title        VARCHAR(128) NOT NULL,
  content      TEXT,
  notice_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '1公告 2通知',
  target       VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL/USER/STAFF/ADMIN',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '0下线 1发布',
  publish_time DATETIME,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(32), update_by VARCHAR(32),
  deleted      TINYINT      NOT NULL DEFAULT 0
) COMMENT '公告';

-- ---------- Track A 种子数据 ----------

-- 管理员：admin / admin123（BCrypt）；Agent 系统账号：agent-dispatch（不可人类登录）
INSERT INTO sys_user (id, username, password, nickname, role, principal_type, status, create_time, deleted)
VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '平台管理员', 'ADMIN', 'HUMAN', 1, NOW(), 0),
       (2, 'agent-dispatch', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '调度 Agent', 'AGENT', 'AGENT', 1, NOW(), 0)
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO sys_role (id, role_code, role_name, remark, status, create_time, deleted)
VALUES (1, 'ADMIN', '平台管理员', '全部权限', 1, NOW(), 0),
       (2, 'USER', '普通用户', '用户端', 1, NOW(), 0),
       (3, 'RECYCLER', '再生资源回收员', '员工端', 1, NOW(), 0),
       (4, 'SANITATION', '环卫收运员', '员工端', 1, NOW(), 0),
       (5, 'TECHNICIAN', '电路维修技术员', '员工端', 1, NOW(), 0)
ON DUPLICATE KEY UPDATE role_code = role_code;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perm, icon, sort, visible, status, create_time, deleted)
VALUES (100, 0, '系统管理', 1, '/system', NULL, NULL, 'Setting', 90, 1, 1, NOW(), 0),
       (101, 100, '用户管理', 2, 'user', 'system/user/index', 'system:user:list', NULL, 1, 1, 1, NOW(), 0),
       (102, 100, '角色管理', 2, 'role', 'system/role/index', 'system:role:list', NULL, 2, 1, 1, NOW(), 0),
       (103, 100, '菜单管理', 2, 'menu', 'system/menu/index', 'system:menu:list', NULL, 3, 1, 1, NOW(), 0),
       (104, 100, '字典管理', 2, 'dict', 'system/dict/index', 'system:dict:list', NULL, 4, 1, 1, NOW(), 0),
       (105, 100, '参数配置', 2, 'config', 'system/config/index', 'system:config:list', NULL, 5, 1, 1, NOW(), 0),
       (106, 100, '公告管理', 2, 'notice', 'system/notice/index', 'system:notice:list', NULL, 6, 1, 1, NOW(), 0),
       (107, 100, '操作日志', 2, 'oper-log', 'system/log/oper', 'system:oper-log:list', NULL, 7, 1, 1, NOW(), 0),
       (108, 100, '登录日志', 2, 'login-log', 'system/log/login', 'system:login-log:list', NULL, 8, 1, 1, NOW(), 0),
       (10201, 102, '角色新增', 3, NULL, NULL, 'system:role:add', NULL, 1, 1, 1, NOW(), 0),
       (10202, 102, '角色编辑', 3, NULL, NULL, 'system:role:edit', NULL, 2, 1, 1, NOW(), 0),
       (10203, 102, '角色删除', 3, NULL, NULL, 'system:role:remove', NULL, 3, 1, 1, NOW(), 0),
       (10204, 102, '分配菜单', 3, NULL, NULL, 'system:role:assign', NULL, 4, 1, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE menu_name = menu_name;

-- ADMIN 角色拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
ON DUPLICATE KEY UPDATE role_id = role_id;

INSERT INTO sys_dict_type (id, dict_type, dict_name, status, create_time, deleted)
VALUES (1, 'recycle_category', '可回收物品类', 1, NOW(), 0),
       (2, 'repair_category', '维修品类', 1, NOW(), 0)
ON DUPLICATE KEY UPDATE dict_type = dict_type;

INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort, status, create_time, deleted)
VALUES (11, 'recycle_category', '金属', 'METAL', 1, 1, NOW(), 0),
       (12, 'recycle_category', '塑料', 'PLASTIC', 2, 1, NOW(), 0),
       (13, 'recycle_category', '纸类', 'PAPER', 3, 1, NOW(), 0),
       (14, 'recycle_category', '织物', 'FABRIC', 4, 1, NOW(), 0),
       (15, 'recycle_category', '电子废弃物', 'E_WASTE', 5, 1, NOW(), 0),
       (21, 'repair_category', '小家电', 'SMALL_APPLIANCE', 1, 1, NOW(), 0),
       (22, 'repair_category', '电路板', 'CIRCUIT_BOARD', 2, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE dict_type = dict_type;

INSERT INTO sys_config (id, config_key, config_value, config_name, create_time, deleted)
VALUES (1, 'kitchen.report.start-hour', '20', '厨余投放开始时段（时）', NOW(), 0),
       (2, 'kitchen.report.end-hour', '23', '厨余投放结束时段（时）', NOW(), 0),
       (3, 'kitchen.clean.start-hour', '5', '环卫清运开始时段（时）', NOW(), 0),
       (4, 'kitchen.clean.end-hour', '6', '环卫清运结束时段（时）', NOW(), 0),
       (5, 'risk.kitchen.deviation-times', '5', '厨余上报偏离片区均值告警倍数', NOW(), 0),
       (6, 'mcp.rate-limit.per-minute', '60', 'MCP 单客户端每分钟限流', NOW(), 0)
ON DUPLICATE KEY UPDATE config_key = config_key;
