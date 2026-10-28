-- ============================================================
-- 用户地址簿 biz_user_address（正式版地址填写）
-- 预约回收/维修/商城收货/跳蚤收货统一来源：省市区 + 详细地址 + 定位经纬度，
-- 可选关联片区 area_id（派单用）与联系人信息；is_default 同用户至多一个。
-- ============================================================

CREATE TABLE IF NOT EXISTS biz_user_address (
  id            BIGINT PRIMARY KEY,
  user_id       BIGINT       NOT NULL COMMENT '所属用户',
  contact_name  VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '联系人',
  contact_phone VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '联系电话',
  province      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '省',
  city          VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '市',
  district      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '区/县',
  detail        VARCHAR(128) NOT NULL DEFAULT '' COMMENT '详细地址（道路/小区/楼栋门牌）',
  lng           DECIMAL(10,6)          COMMENT '经度（定位/地理编码）',
  lat           DECIMAL(10,6)          COMMENT '纬度',
  area_id       BIGINT                 COMMENT '关联片区（biz_area，派单归属，可空）',
  tag           VARCHAR(16)  NOT NULL DEFAULT '家' COMMENT '标签：家/公司/学校',
  is_default    TINYINT      NOT NULL DEFAULT 0 COMMENT '0非默认 1默认（同用户至多一个）',
  create_time   DATETIME,
  update_time   DATETIME,
  create_by     VARCHAR(64),
  update_by     VARCHAR(64),
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
  KEY idx_addr_user (user_id, deleted)
) COMMENT '用户地址簿';