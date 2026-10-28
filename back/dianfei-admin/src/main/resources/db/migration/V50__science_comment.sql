-- ============================================================
-- V50 · 科普专栏评论表（补齐用户端 GET /api/science/comments 缺失的存储）
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS biz_science_comment (
    id          BIGINT PRIMARY KEY,
    article_id  BIGINT NOT NULL COMMENT '文章 ID',
    user_id     BIGINT NOT NULL COMMENT '评论用户 ID',
    content     VARCHAR(500) NOT NULL COMMENT '评论内容',
    status      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=展示，0=屏蔽',
    create_time DATETIME,
    update_time DATETIME,
    create_by   VARCHAR(32),
    update_by   VARCHAR(32),
    deleted     TINYINT NOT NULL DEFAULT 0,
    KEY idx_article (article_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科普文章评论表';
