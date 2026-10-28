-- ============================================================
-- V51 · 科普专栏种子数据真实化
-- 背景：V42 种子文章（id 1001-1012）引用了不存在的封面图/PDF 文件
--      （/api/file/preview/science/*.jpg、pdf_file_id 10011-10022），
--      且 author_id=0、阅读/下载量均为编造数字 —— 属于 mock 数据。
-- 本迁移：
--   1. 在 sys_file_object 登记 12 张封面 + 12 个 PDF 真实文件；
--   2. 将 12 篇种子文章的封面/PDF/作者/统计修正为真实数据（统计归零，
--      由真实阅读/下载行为积累）；分类 code 统一为中文（与前端提交一致）。
-- 文件物理位置：upload/science/2026/08/seed_cover_XX.jpg / seed_article_XX.pdf
-- 幂等：V42 未执行（新库）或已执行（存量库）均可安全重跑。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------------
-- 1. 登记真实文件对象（biz 与 FileController 登记规则一致：science-cover / science-article）
-- ------------------------------------------------------------------
INSERT INTO sys_file_object (id, storage_path, owner_id, biz, public_access, create_time, deleted) VALUES
(51001, 'science/2026/08/seed_cover_01.jpg', 1, 'science-cover', 0, NOW(), 0),
(51002, 'science/2026/08/seed_cover_02.jpg', 1, 'science-cover', 0, NOW(), 0),
(51003, 'science/2026/08/seed_cover_03.jpg', 1, 'science-cover', 0, NOW(), 0),
(51004, 'science/2026/08/seed_cover_04.jpg', 1, 'science-cover', 0, NOW(), 0),
(51005, 'science/2026/08/seed_cover_05.jpg', 1, 'science-cover', 0, NOW(), 0),
(51006, 'science/2026/08/seed_cover_06.jpg', 1, 'science-cover', 0, NOW(), 0),
(51007, 'science/2026/08/seed_cover_07.jpg', 1, 'science-cover', 0, NOW(), 0),
(51008, 'science/2026/08/seed_cover_08.jpg', 1, 'science-cover', 0, NOW(), 0),
(51009, 'science/2026/08/seed_cover_09.jpg', 1, 'science-cover', 0, NOW(), 0),
(51010, 'science/2026/08/seed_cover_10.jpg', 1, 'science-cover', 0, NOW(), 0),
(51011, 'science/2026/08/seed_cover_11.jpg', 1, 'science-cover', 0, NOW(), 0),
(51012, 'science/2026/08/seed_cover_12.jpg', 1, 'science-cover', 0, NOW(), 0),
(51013, 'science/2026/08/seed_article_01.pdf', 1, 'science-article', 0, NOW(), 0),
(51014, 'science/2026/08/seed_article_02.pdf', 1, 'science-article', 0, NOW(), 0),
(51015, 'science/2026/08/seed_article_03.pdf', 1, 'science-article', 0, NOW(), 0),
(51016, 'science/2026/08/seed_article_04.pdf', 1, 'science-article', 0, NOW(), 0),
(51017, 'science/2026/08/seed_article_05.pdf', 1, 'science-article', 0, NOW(), 0),
(51018, 'science/2026/08/seed_article_06.pdf', 1, 'science-article', 0, NOW(), 0),
(51019, 'science/2026/08/seed_article_07.pdf', 1, 'science-article', 0, NOW(), 0),
(51020, 'science/2026/08/seed_article_08.pdf', 1, 'science-article', 0, NOW(), 0),
(51021, 'science/2026/08/seed_article_09.pdf', 1, 'science-article', 0, NOW(), 0),
(51022, 'science/2026/08/seed_article_10.pdf', 1, 'science-article', 0, NOW(), 0),
(51023, 'science/2026/08/seed_article_11.pdf', 1, 'science-article', 0, NOW(), 0),
(51024, 'science/2026/08/seed_article_12.pdf', 1, 'science-article', 0, NOW(), 0)
ON DUPLICATE KEY UPDATE storage_path = storage_path;

-- ------------------------------------------------------------------
-- 2. 修正 12 篇种子文章为真实数据
--    author_id=1（系统管理员，真实存在）；统计归零由真实行为积累；
--    封面/PDF 指向 V51 登记的真实文件；分类 code 与前端保持一致（中文）。
-- ------------------------------------------------------------------
UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_01.jpg',
    pdf_file_id = 51013,
    pdf_filename = '垃圾分类科普指南.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_01.pdf',
    categories = '[{"code":"垃圾分类","name":"垃圾分类"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1001 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_02.jpg',
    pdf_file_id = 51014,
    pdf_filename = '电子垃圾的危害与回收.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_02.pdf',
    categories = '[{"code":"电子垃圾","name":"电子垃圾"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1002 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_03.jpg',
    pdf_file_id = 51015,
    pdf_filename = '塑料瓶的循环利用.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_03.pdf',
    categories = '[{"code":"资源回收","name":"资源回收"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1003 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_04.jpg',
    pdf_file_id = 51016,
    pdf_filename = '低碳生活小贴士.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_04.pdf',
    categories = '[{"code":"生活方式","name":"生活方式"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1004 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_05.jpg',
    pdf_file_id = 51017,
    pdf_filename = '厨余垃圾的正确处理.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_05.pdf',
    categories = '[{"code":"厨余垃圾","name":"厨余垃圾"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1005 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_06.jpg',
    pdf_file_id = 51018,
    pdf_filename = '旧衣物回收再利用.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_06.pdf',
    categories = '[{"code":"资源回收","name":"资源回收"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1006 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_07.jpg',
    pdf_file_id = 51019,
    pdf_filename = '废旧电池的危害.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_07.pdf',
    categories = '[{"code":"有害垃圾","name":"有害垃圾"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1007 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_08.jpg',
    pdf_file_id = 51020,
    pdf_filename = '快递包装的可持续解决方案.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_08.pdf',
    categories = '[{"code":"包装回收","name":"包装回收"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1008 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_09.jpg',
    pdf_file_id = 51021,
    pdf_filename = '海洋塑料污染与应对.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_09.pdf',
    categories = '[{"code":"海洋保护","name":"海洋保护"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1009 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_10.jpg',
    pdf_file_id = 51022,
    pdf_filename = '二手物品交换指南.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_10.pdf',
    categories = '[{"code":"循环经济","name":"循环经济"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1010 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_11.jpg',
    pdf_file_id = 51023,
    pdf_filename = '新能源汽车回收挑战.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_11.pdf',
    categories = '[{"code":"汽车回收","name":"汽车回收"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1011 AND deleted = 0;

UPDATE biz_science_article
SET author_id = 1,
    cover_url = '/api/file/preview/science/2026/08/seed_cover_12.jpg',
    pdf_file_id = 51024,
    pdf_filename = '家庭节能小窍门.pdf',
    pdf_storage_path = 'science/2026/08/seed_article_12.pdf',
    categories = '[{"code":"节能减排","name":"节能减排"}]',
    view_count = 0, download_count = 0, like_count = 0,
    update_time = NOW()
WHERE id = 1012 AND deleted = 0;
