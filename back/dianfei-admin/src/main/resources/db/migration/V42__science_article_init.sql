-- ============================================================
-- V42 · 科普专栏 UGC 系统初始化（biz_science_article + 评分/下载/收益表）
-- 包含 4 张业务表 + 12 篇系统种子文章数据
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------------
-- 1. 科普文章主表 (biz_science_article)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_science_article (
    -- 基础信息
    id              BIGINT PRIMARY KEY,
    title           VARCHAR(200)  NOT NULL COMMENT '文章标题',
    description     TEXT          COMMENT '文章简介',
    content         TEXT          COMMENT '文章内容摘要（富文本，可选）',
    cover_url       VARCHAR(500)  COMMENT '封面图片 URL',
    
    -- PDF 文件信息
    pdf_file_id     BIGINT COMMENT 'PDF 文件 ID（关联 sys_file_object 表）',
    pdf_filename    VARCHAR(255) COMMENT '原始 PDF 文件名',
    pdf_storage_path VARCHAR(500) COMMENT 'PDF 存储路径（用于预览 URL 拼接）',
    
    -- 内容与分类
    categories      JSON COMMENT '分类标签数组 [{"code": "垃圾分类", "name": "垃圾分类"}]',
    tags            VARCHAR(500) COMMENT '扩展标签逗号分隔',
    
    -- 作者信息
    author_id       BIGINT NOT NULL COMMENT '作者用户 ID',
    author_name     VARCHAR(64) NOT NULL COMMENT '作者昵称（冗余字段，防修改用户名影响展示）',
    
    -- 状态与权限
    is_system       TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统文章：0=用户文章，1=系统文章',
    allow_download  TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许下载：0=禁止，1=允许',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态：0=待审，1=已发布，2=已下架，3=被驳回',
    is_top          TINYINT NOT NULL DEFAULT 0 COMMENT '置顶排序（数字越大越靠前）',
    
    -- 积分配置（管理员后台配置每篇文章的收益点数）
    download_points INT NOT NULL DEFAULT 0 COMMENT '单次下载所需积分',
    base_points     DECIMAL(6,2) NOT NULL DEFAULT 0.50 COMMENT '基础积分单价（作者每次下载收益 = downloadPoints × basePoints × multiplier）',
    multiplier      DECIMAL(6,2) NOT NULL DEFAULT 1.00 COMMENT '热门系数',
    
    -- 统计指标
    view_count      INT NOT NULL DEFAULT 0 COMMENT '阅读次数',
    download_count  INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    like_count      INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    
    -- 审核信息
    audit_remark    VARCHAR(500) COMMENT '审核意见（通过/驳回原因）',
    audit_time      DATETIME COMMENT '审核时间',
    audit_by        VARCHAR(32) COMMENT '审核人 ID',
    
    -- 时间戳
    publish_time    DATETIME COMMENT '发布时间',
    create_time     DATETIME,
    update_time     DATETIME,
    create_by       VARCHAR(32),
    update_by       VARCHAR(32),
    deleted         TINYINT NOT NULL DEFAULT 0,
    
    KEY idx_author (author_id),
    KEY idx_status (status),
    KEY idx_is_system (is_system),
    KEY idx_publish_time (publish_time),
    KEY idx_view_count (view_count),
    KEY idx_download_count (download_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科普文章主表';

-- ------------------------------------------------------------------
-- 2. 文章评分表 (biz_science_rating)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_science_rating (
    id              BIGINT PRIMARY KEY,
    article_id      BIGINT NOT NULL COMMENT '文章 ID',
    user_id         BIGINT NOT NULL COMMENT '用户 ID',
    score           TINYINT NOT NULL COMMENT '评分：1-5 星',
    comment         VARCHAR(500) COMMENT '短评（可选）',
    create_time     DATETIME,
    UNIQUE KEY uk_article_user (article_id, user_id),
    KEY idx_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章评分表';

-- ------------------------------------------------------------------
-- 3. 下载积分记录表 (biz_science_download_log)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_science_download_log (
    id              BIGINT PRIMARY KEY,
    article_id      BIGINT NOT NULL COMMENT '文章 ID',
    user_id         BIGINT NOT NULL COMMENT '下载用户 ID',
    points_cost     INT NOT NULL COMMENT '消耗积分',
    download_token  VARCHAR(255) COMMENT '下载凭证 Token（一次性消费凭据，Redis）',
    create_time     DATETIME,
    KEY idx_user_article (user_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下载积分扣减日志';

-- ------------------------------------------------------------------
-- 4. 作者积分收益统计表 (biz_science_author_income)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_science_author_income (
    id              BIGINT PRIMARY KEY,
    article_id      BIGINT NOT NULL COMMENT '文章 ID（唯一约束）',
    author_id       BIGINT NOT NULL COMMENT '作者 ID',
    total_downloads INT NOT NULL DEFAULT 0 COMMENT '总下载次数',
    total_points    INT NOT NULL DEFAULT 0 COMMENT '总获得积分',
    last_income_time DATETIME COMMENT '最近一次收益时间',
    create_time     DATETIME,
    update_time     DATETIME,
    UNIQUE KEY uk_article (article_id),
    KEY idx_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者积分收益统计';

-- ------------------------------------------------------------------
-- 5. 文章点赞表 (新增表，满足点赞/取消点赞 API)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_science_like (
    id              BIGINT PRIMARY KEY,
    article_id      BIGINT NOT NULL COMMENT '文章 ID',
    user_id         BIGINT NOT NULL COMMENT '用户 ID',
    create_time     DATETIME,
    UNIQUE KEY uk_article_user (article_id, user_id),
    KEY idx_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章点赞记录';

-- ------------------------------------------------------------------
-- 6. 系统种子数据 (12 篇初始科普文章，均为 is_system=1)
-- ------------------------------------------------------------------
INSERT INTO biz_science_article (id, title, description, cover_url, pdf_file_id, pdf_filename, 
    author_id, author_name, is_system, allow_download, download_points, status, 
    publish_time, view_count, categories) VALUES
(1001, '垃圾分类科普指南', '详细介绍四类垃圾的标准和识别方法，帮助居民快速掌握分类技巧', '/api/file/preview/science/classification_guide.jpg', 10011, '垃圾分类科普指南.pdf', 
    0, '平台官方', 1, 1, 10, 1, NOW(), 1234, '[{"code":"classification","name":"垃圾分类"}]'),
(1002, '电子垃圾的危害与回收', '电子产品含有重金属，正确回收可减少土壤和水源污染', '/api/file/preview/science/e_waste.jpg', 10012, '电子垃圾危害与回收.pdf', 
    0, '平台官方', 1, 1, 15, 1, NOW(), 987, '[{"code":"e_waste","name":"电子垃圾"}]'),
(1003, '塑料瓶的循环利用', '一个塑料瓶可以回收再生成多种产品，延长使用寿命', '/api/file/preview/science/plastic_bottle.jpg', 10013, '塑料瓶循环利用.pdf', 
    0, '平台官方', 1, 1, 10, 1, NOW(), 1456, '[{"code":"recycling","name":"资源回收"}]'),
(1004, '低碳生活小贴士', '从日常小事做起，减少碳足迹，践行环保生活方式', '/api/file/preview/science/low_carbon.jpg', 10014, '低碳生活小贴士.pdf', 
    0, '平台官方', 1, 1, 5, 1, NOW(), 2341, '[{"code":"lifestyle","name":"生活方式"}]'),
(1005, '厨余垃圾的正确处理', '厨余垃圾可以变废为宝，制作有机肥料，回归自然循环', '/api/file/preview/science/kitchen_waste.jpg', 10015, '厨余垃圾处理.pdf', 
    0, '平台官方', 1, 1, 10, 1, NOW(), 1678, '[{"code":"kitchen","name":"厨余垃圾"}]'),
(1006, '旧衣物回收再利用', '旧衣服也能找到新归宿，捐赠、改造或纤维再生', '/api/file/preview/science/clothes.jpg', 10016, '旧衣物回收.pdf', 
    0, '平台官方', 1, 1, 8, 1, NOW(), 1123, '[{"code":"recycling","name":"资源回收"}]'),
(1007, '废旧电池的危害', '一节电池可使 1 平方米土壤失去价值，请妥善投放有害垃圾箱', '/api/file/preview/science/batteries.jpg', 10017, '废旧电池危害.pdf', 
    0, '平台官方', 1, 1, 12, 1, NOW(), 1890, '[{"code":"hazardous","name":"有害垃圾"}]'),
(1008, '快递包装的可持续解决方案', '减少快递包装浪费，推动绿色物流，提倡循环纸箱使用', '/api/file/preview/science/parcel_box.jpg', 10018, '快递包装可持续方案.pdf', 
    0, '平台官方', 1, 1, 10, 1, NOW(), 1567, '[{"code":"packaging","name":"包装回收"}]'),
(1009, '海洋塑料污染与应对', '每年 800 万吨塑料流入海洋，保护海洋生态从我做起', '/api/file/preview/science/ocean_plastic.jpg', 10019, '海洋塑料污染.pdf', 
    0, '平台官方', 1, 1, 15, 1, NOW(), 2134, '[{"code":"ocean","name":"海洋保护"}]'),
(1010, '二手物品交换指南', '延长产品寿命，减少资源浪费，闲置物品循环利用的 N 种方式', '/api/file/preview/science/second_hand.jpg', 10020, '二手物品交换指南.pdf', 
    0, '平台官方', 1, 1, 8, 1, NOW(), 1789, '[{"code":"exchange","name":"循环经济"}]'),
(1011, '新能源汽车回收挑战', '动力电池回收的环保意义与技术挑战，构建闭环产业链', '/api/file/preview/science/ev_battery.jpg', 10021, '新能源汽车回收挑战.pdf', 
    0, '平台官方', 1, 1, 20, 1, NOW(), 945, '[{"code":"auto","name":"汽车回收"}]'),
(1012, '家庭节能小窍门', '家电使用习惯改善能源效率，一年省下一笔电费', '/api/file/preview/science/energy_saving.jpg', 10022, '家庭节能小窍门.pdf', 
    0, '平台官方', 1, 1, 10, 1, NOW(), 2456, '[{"code":"energy","name":"节能减排"}]');

-- ------------------------------------------------------------------
-- 7. 数据库版本记录（Flyway 自动维护 flyway_schema_history，无需手动插入）
-- ------------------------------------------------------------------
-- 本迁移文件大小：< 1MB，建议配合 git-lfs 管理 PDF 大文件
-- 后续批次可添加更多分类字典、积分规则等辅助数据
