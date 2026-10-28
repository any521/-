-- 文件访问与 AI 异步任务元数据；CREATE IF NOT EXISTS 允许已初始化环境安全接管。
CREATE TABLE IF NOT EXISTS sys_file_object (
    id BIGINT PRIMARY KEY,
    storage_path VARCHAR(512) NOT NULL,
    owner_id BIGINT NOT NULL,
    biz VARCHAR(32) NOT NULL,
    public_access TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_file_storage_path (storage_path),
    KEY idx_file_owner (owner_id, biz)
) COMMENT '文件对象权限元数据';

CREATE TABLE IF NOT EXISTS biz_ai_3d_task (
    id BIGINT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    image_url TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    spec_json TEXT,
    error VARCHAR(1000),
    create_time DATETIME,
    update_time DATETIME,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ai_3d_task_id (task_id),
    KEY idx_ai_3d_user (user_id, create_time)
) COMMENT 'AI 3D 异步任务';
