-- ============================================================
-- Onsite04 · 高德地图（缺口 G4）：回收站点表 + 高德 Key 配置
-- biz_station：地图导航目标（RECYCLE 回收站 / KITCHEN 厨余站 / COLLECT 集散点）；
-- sys_config 种子对齐 V20 规范（config_key/config_value/config_name/remark/deleted + WHERE NOT EXISTS），
-- id 9203/9204 延续 9201/9202 段；管理端「参数配置」页可热更新（ConfigController PUT 即生效）。
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_station (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  name         VARCHAR(64)   NOT NULL COMMENT '站点名称',
  station_type VARCHAR(16)   NOT NULL COMMENT 'RECYCLE 回收站 / KITCHEN 厨余站 / COLLECT 集散点',
  lng          DECIMAL(10, 6) NOT NULL,
  lat          DECIMAL(10, 6) NOT NULL,
  address      VARCHAR(255)  COMMENT '地址（供地理编码校准）',
  open_hours   VARCHAR(64)   COMMENT '营业时间文案',
  status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1开放 0停用',
  deleted      TINYINT       NOT NULL DEFAULT 0,
  create_time  DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
  KEY idx_station_type (station_type)
) COMMENT '回收站点（onsite04，地图导航目标）';

INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9203, 'amap.web_key', '', '高德Web端Key（JS API）', '前端 JS API 2.0 用，经 /api/map/config 下发；管理端「参数配置」页配置（onsite04）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'amap.web_key');
INSERT INTO sys_config (id, config_key, config_value, config_name, remark, deleted)
SELECT 9204, 'amap.server_key', '', '高德服务端Key（Web服务）', '后端代理高德 REST 用，禁止下发前端；管理端「参数配置」页配置（onsite04）', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'amap.server_key');

INSERT INTO biz_station (name, station_type, lng, lat, address, open_hours, status)
VALUES
  ('城东再生资源回收站', 'RECYCLE', 120.1551, 30.2741, '城东街道 12 号', '08:00-20:00', 1),
  ('城西厨余垃圾集中站', 'KITCHEN', 120.0872, 30.3012, '城西环卫基地', '05:00-23:00', 1),
  ('城南废品集散点', 'COLLECT', 120.1234, 30.2589, '城南工业园 3 幢', '08:30-18:00', 1)
ON DUPLICATE KEY UPDATE name = name;
