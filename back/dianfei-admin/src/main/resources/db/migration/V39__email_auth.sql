-- ============================================================
-- V39 · 邮箱验证码认证（去掉手机号）
-- 背景：验证码从短信 Mock 固定码改为真实邮箱验证码（163 SMTP）；
--       sys_user 以 email 为注册/登录凭证，移除 phone 列。
--       biz_staff / 订单等业务联系电话字段独立存在，不受影响。
-- 注意：存量用户 username 为手机号的（无邮箱可回填），email 保持 NULL，
--       不影响既有账密登录；新注册/验证码登录一律使用邮箱。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- sys_user 新增 email 列 + 唯一索引（NULL 不参与唯一，多行 NULL 合法） ----------
ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(64) COMMENT '登录邮箱（注册/登录凭证）' AFTER nickname;

CREATE UNIQUE INDEX uk_user_email ON sys_user (email);

-- ---------- 移除 phone 列及其索引 ----------
DROP INDEX idx_user_phone ON sys_user;
ALTER TABLE sys_user DROP COLUMN phone;