-- ============================================================
-- Upgrade U3 · 跳蚤市场后端线（缺口 D5/D6）
-- 2 表：biz_flea_item（闲置商品，状态机 0下架 1在售 2已售出 3违规下架）/
--       biz_flea_order（跳蚤订单，状态机 0待发货 1待收货 2已完成 3已取消）
-- 积分托管结算一律走碳账本 CarbonLedgerOps freeze/unfreeze/transfer，不旁路哈希链；
-- 防双卖由订单域条件更新（WHERE status=1）驱动；本迁移不动 init.sql 与 V1~V28。
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_flea_item (
  id          BIGINT PRIMARY KEY,
  user_id     BIGINT      NOT NULL COMMENT '卖家',
  title       VARCHAR(64) NOT NULL,
  description VARCHAR(500) NOT NULL,
  category    VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT 'REUSABLE/TOY/BOOK/ELECTRONIC/HOME/OTHER',
  price       BIGINT      NOT NULL COMMENT '积分定价 ≥1',
  image_url   VARCHAR(255) COMMENT '图片（/api/file/upload?biz=flea）',
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '0下架 1在售 2已售出 3违规下架',
  create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64), deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_flea_user (user_id), KEY idx_flea_status_price (status, price)
) COMMENT '跳蚤市场闲置商品';

CREATE TABLE IF NOT EXISTS biz_flea_order (
  id             BIGINT PRIMARY KEY,
  order_no       VARCHAR(32) NOT NULL UNIQUE COMMENT '雪花号，前缀 FO',
  user_id        BIGINT      NOT NULL COMMENT '买家',
  seller_id      BIGINT      NOT NULL COMMENT '卖家',
  item_id        BIGINT      NOT NULL,
  item_title     VARCHAR(64) NOT NULL COMMENT '商品名快照',
  item_image     VARCHAR(255) COMMENT '商品图快照',
  price          BIGINT      NOT NULL COMMENT '成交积分快照',
  address        VARCHAR(255) NOT NULL COMMENT '买家收货地址',
  status         TINYINT     NOT NULL DEFAULT 0 COMMENT '0待发货 1待收货 2已完成 3已取消',
  rating         TINYINT     COMMENT '评价 1-5（完成后可评一次）',
  comment        VARCHAR(200) COMMENT '评价内容',
  freeze_log_id  BIGINT      COMMENT '下单冻结流水（取消解冻凭据）',
  create_time    DATETIME, ship_time DATETIME, receive_time DATETIME, cancel_time DATETIME,
  update_time    DATETIME, create_by VARCHAR(64), update_by VARCHAR(64), deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_flea_order_buyer (user_id), KEY idx_flea_order_seller (seller_id), KEY idx_flea_order_item (item_id)
) COMMENT '跳蚤订单';
