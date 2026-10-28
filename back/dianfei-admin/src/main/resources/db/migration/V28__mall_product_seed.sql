-- ============================================================
-- Upgrade U2 · 积分商城种子数据（缺口 D3/D4）
-- 8 条种子商品：cover_url 留空由管理端补传，description 中文简介 ≤200 字；
-- ON DUPLICATE KEY UPDATE 结尾幂等（id 9001-9008 避开业务雪花号）。
-- ============================================================
INSERT INTO biz_mall_product
  (id, product_name, cover_url, points_price, stock, status, description, create_time, update_time, create_by, deleted)
VALUES
  (9001, '环保帆布袋', NULL, 300, 300, 1, '棉麻混纺，买菜购物随身装，可反复使用 500+ 次', NOW(), NOW(), 'seed', 0),
  (9002, '不锈钢吸管杯', NULL, 500, 200, 1, '304 不锈钢 + 硅胶吸管，告别一次性塑料杯', NOW(), NOW(), 'seed', 0),
  (9003, '再生纸笔记本', NULL, 150, 500, 1, '废纸再生浆制作，环保墨水印刷，100 页', NOW(), NOW(), 'seed', 0),
  (9004, '竹纤维毛巾', NULL, 200, 300, 1, '天然竹纤维，抑菌亲肤，替代化纤毛巾', NOW(), NOW(), 'seed', 0),
  (9005, '分类垃圾桶贴纸', NULL, 80, 500, 1, '四分类标识贴纸，贴在桶身一目了然', NOW(), NOW(), 'seed', 0),
  (9006, '编织环保购物袋', NULL, 250, 200, 1, '手工编织款，结实耐用，可折叠收纳', NOW(), NOW(), 'seed', 0),
  (9007, '种子盆栽套装', NULL, 400, 150, 1, '向日葵/薄荷种子 + 可降解花盆，阳台种点绿', NOW(), NOW(), 'seed', 0),
  (9008, '碳减排徽章', NULL, 50, 1000, 1, '完成首次回收的纪念徽章，收藏你的减碳足迹', NOW(), NOW(), 'seed', 0)
ON DUPLICATE KEY UPDATE product_name = product_name;
