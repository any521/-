-- ------------------------------------------------------------------
-- V52: 科普下载日志改为每用户每文章唯一（付费购买记录）
-- 防止并发双击下载时双插日志、双扣费（资金安全）；
-- 免费/官方/重复下载不再落日志，仅累计下载次数与作者收益统计
-- ------------------------------------------------------------------

-- 清理历史重复行：保留每用户每文章最早一条（首次付费记录）
DELETE t1 FROM biz_science_download_log t1
INNER JOIN biz_science_download_log t2
    ON t1.user_id = t2.user_id AND t1.article_id = t2.article_id AND t1.id > t2.id;

-- 普通索引升级为唯一索引
ALTER TABLE biz_science_download_log
    DROP INDEX idx_user_article,
    ADD UNIQUE KEY uk_user_article (user_id, article_id);
