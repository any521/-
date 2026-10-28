-- ============================================================
-- LoopFill B6 · 创意 C2：变废为宝 DIY 社区 + 片区环保排行榜
-- 说明：Flyway V27；公共字段对齐 10_database.md 惯例（id/create_time/update_time/create_by/update_by/deleted）。
--   image_url 采用 MEDIUMTEXT（对齐 V9 图片 base64 文本先例，前端压缩 ≤300KB 后直存，无需文件服务）。
--   biz_diy_like 不做逻辑删（物理删除，uk(post_id,user_id) 保证防重复点赞与重复点赞计数一致）。
-- ============================================================
SET NAMES utf8mb4;

-- 变废为宝作品帖（逻辑删）
CREATE TABLE IF NOT EXISTS biz_diy_post (
  id          BIGINT PRIMARY KEY,
  user_id     BIGINT        NOT NULL,
  title       VARCHAR(64)   NOT NULL,
  content     VARCHAR(500)  NOT NULL,
  image_url   MEDIUMTEXT    COMMENT '作品图（base64 压缩 ≤300KB 或 MinIO URL，选填）',
  likes       INT           NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数，列表排序用）',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  deleted     TINYINT       NOT NULL DEFAULT 0,
  KEY idx_diy_user (user_id),
  KEY idx_diy_likes (likes),
  KEY idx_diy_time (create_time)
) COMMENT '变废为宝 DIY 作品';

-- 点赞记录（防重复：uk(post_id, user_id)，物理删除）
CREATE TABLE IF NOT EXISTS biz_diy_like (
  id          BIGINT PRIMARY KEY,
  post_id     BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  create_time DATETIME,
  UNIQUE KEY uk_diy_like (post_id, user_id),
  KEY idx_diy_like_user (user_id)
) COMMENT 'DIY 作品点赞';
