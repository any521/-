-- ============================================================
-- V30 · 安全加固：废弃内置默认口令 admin/admin123（C5）
-- 将仍使用旧弱口令 BCrypt hash 的内置账号重置为随机强口令 hash
-- （与 V1 新种子一致；旧口令 admin123 即时失效，初始口令见部署清单）
-- 幂等：已更新过/未使用的行不受影响
-- ============================================================
SET NAMES utf8mb4;

UPDATE sys_user
SET password = '$2b$10$rE80aYKVPRAW9ESWnNiy6uUkzAkFzkXaGmdYzSwF61wNgzVErp9HO'
WHERE username IN ('admin', 'agent-dispatch')
  AND password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2';
