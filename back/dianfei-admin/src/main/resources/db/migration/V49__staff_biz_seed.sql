-- ============================================================
-- V49 · 业务种子数据：片区 / 员工账号 / 报价 / 积分规则 / 演示订单
-- 背景：V2/V3 仅建表无业务数据，员工端登录页快捷账号（R1001/S2001/T3001）落不了库，
--       完成登记核算因无报价/积分规则报错。本迁移补齐最小可用数据，
--       使员工端与客户端展示真实数据而非 Mock/硬编码。
-- 账号：R1001 回收员 / S2001 环卫员 / T3001 技术员 / user001 演示市民，密码均 123456（BCrypt $2b）。
--      V39 起 sys_user 已移除 phone 列、email 为登录凭证（此处为演示邮箱，账密登录不受影响）。
-- 幂等：全部 ON DUPLICATE KEY UPDATE（重复执行不报错、不覆盖既有业务数据）。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1. 片区（biz_area，id 1001-1003 避开雪花大 ID） ----------
INSERT INTO biz_area (id, area_name, address, clean_threshold, household_count, status, create_time, deleted)
VALUES (1001, '绿洲花园片区', '绿洲花园小区及周边道路', 100, 1200, 1, NOW(), 0),
       (1002, '滨江新区片区', '滨江新区沿江路一带', 100, 950, 1, NOW(), 0),
       (1003, '科技园片区', '科技创新园区及公寓区', 80, 600, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE area_name = VALUES(area_name);

-- ---------- 2. 账号（sys_user，id 9001-9004 避开 admin=1/agent=2） ----------
INSERT INTO sys_user (id, username, password, nickname, email, role, area_id, principal_type, status, create_time, deleted)
VALUES (9001, 'R1001', '$2b$10$zNwB8Q0gs8kbCJyqyCcja.hCpaKSeLmzR4RUP4Jga5UUfnVUJ/uXW', '回收员·张建国', 'r1001@demo.dianfei.cn', 'RECYCLER', 1001, 'HUMAN', 1, NOW(), 0),
       (9002, 'S2001', '$2b$10$zNwB8Q0gs8kbCJyqyCcja.hCpaKSeLmzR4RUP4Jga5UUfnVUJ/uXW', '环卫员·李卫东', 's2001@demo.dianfei.cn', 'SANITATION', 1002, 'HUMAN', 1, NOW(), 0),
       (9003, 'T3001', '$2b$10$zNwB8Q0gs8kbCJyqyCcja.hCpaKSeLmzR4RUP4Jga5UUfnVUJ/uXW', '技术员·王工', 't3001@demo.dianfei.cn', 'TECHNICIAN', 1003, 'HUMAN', 1, NOW(), 0),
       (9004, 'user001', '$2b$10$zNwB8Q0gs8kbCJyqyCcja.hCpaKSeLmzR4RUP4Jga5UUfnVUJ/uXW', '演示市民', 'user001@demo.dianfei.cn', 'USER', NULL, 'HUMAN', 1, NOW(), 0)
ON DUPLICATE KEY UPDATE username = VALUES(username);

-- ---------- 3. 员工档案（biz_staff，与 sys_user 同 ID 对齐，已通过审核） ----------
INSERT INTO biz_staff (id, user_id, staff_name, phone, staff_type, area_id, audit_status, status, apply_scene, create_time, deleted)
VALUES (9001, 9001, '张建国', '13900001001', 1, 1001, 1, 1, 0, NOW(), 0),
       (9002, 9002, '李卫东', '13900001002', 2, 1002, 1, 1, 0, NOW(), 0),
       (9003, 9003, '王工', '13900001003', 3, 1003, 1, 1, 0, NOW(), 0)
ON DUPLICATE KEY UPDATE staff_name = VALUES(staff_name);

-- ---------- 4. 回收报价（biz_recycle_price，五品类生效报价，完成登记核算依赖） ----------
INSERT INTO biz_recycle_price (id, category, price_per_kg, status, create_time, deleted)
VALUES (7001, 'METAL', 8.00, 1, NOW(), 0),
       (7002, 'PLASTIC', 3.00, 1, NOW(), 0),
       (7003, 'PAPER', 2.00, 1, NOW(), 0),
       (7004, 'FABRIC', 1.50, 1, NOW(), 0),
       (7005, 'E_WASTE', 15.00, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE price_per_kg = VALUES(price_per_kg);

-- ---------- 5. 积分规则（biz_carbon_rule：回收按 kg、厨余按袋、维修按件） ----------
INSERT INTO biz_carbon_rule (id, rule_code, order_type, category, points_per_kg, points_per_bag, carbon_per_kg, carbon_per_bag, status, create_time, deleted)
VALUES (7101, 'RECYCLE_METAL', 'RECYCLE', 'METAL', 10.00, NULL, 0.8000, NULL, 1, NOW(), 0),
       (7102, 'RECYCLE_PLASTIC', 'RECYCLE', 'PLASTIC', 5.00, NULL, 0.5000, NULL, 1, NOW(), 0),
       (7103, 'RECYCLE_PAPER', 'RECYCLE', 'PAPER', 4.00, NULL, 0.4000, NULL, 1, NOW(), 0),
       (7104, 'RECYCLE_FABRIC', 'RECYCLE', 'FABRIC', 3.00, NULL, 0.3000, NULL, 1, NOW(), 0),
       (7105, 'RECYCLE_E_WASTE', 'RECYCLE', 'E_WASTE', 20.00, NULL, 1.5000, NULL, 1, NOW(), 0),
       (7106, 'KITCHEN_DEFAULT', 'KITCHEN', 'DEFAULT', NULL, 2.00, NULL, 0.4000, 1, NOW(), 0),
       (7107, 'REPAIR_REPLACE', 'REPAIR', 'REPAIR_REPLACE', NULL, 30.00, NULL, 5.0000, 1, NOW(), 0)
ON DUPLICATE KEY UPDATE points_per_kg = VALUES(points_per_kg), points_per_bag = VALUES(points_per_bag);

-- ---------- 6. 演示市民碳账户（biz_carbon_account，user001） ----------
INSERT INTO biz_carbon_account (id, user_id, points_balance, frozen_points, total_carbon_reduced, create_time, deleted)
VALUES (9001, 9004, 0, 0, 0, NOW(), 0)
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

-- ---------- 7. 今日演示订单（员工端登录即可见：回收 2 单 / 维修 1 单 / 清运任务 1 单） ----------
-- 7.1 回收订单：绿洲花园片区（R1001 可见），待接单
INSERT INTO biz_recycle_order (id, order_no, user_id, staff_id, area_id, category, est_weight, address, appoint_time, status, create_time, deleted)
VALUES (800001, 'RO900001', 9004, NULL, 1001, 'PLASTIC', 5.00, '绿洲花园 3 栋 2 单元 502', DATE_ADD(NOW(), INTERVAL 2 HOUR), 0, NOW(), 0),
       (800002, 'RO900002', 9004, NULL, 1001, 'PAPER', 8.00, '绿洲花园 7 栋 1 单元 301', DATE_ADD(NOW(), INTERVAL 4 HOUR), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

-- 7.2 维修订单：科技园片区（T3001 可见），待接单
INSERT INTO biz_repair_order (id, order_no, user_id, staff_id, area_id, fault_type, fault_desc, address, appoint_start, appoint_end, status, create_time, deleted)
VALUES (800003, 'RP900003', 9004, NULL, 1003, '照明灯具', '客厅吊灯不亮，已换灯泡仍不亮', '科技园公寓 2 栋 1101', DATE_ADD(NOW(), INTERVAL 3 HOUR), DATE_ADD(NOW(), INTERVAL 4 HOUR), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

-- 7.3 厨余清运任务：滨江新区片区（S2001 可见），今日待执行
INSERT INTO biz_kitchen_task (id, area_id, task_date, total_bags, staff_id, status, vehicle_no, create_time, deleted)
VALUES (800004, 1002, CURDATE(), 60, 9002, 0, 'DFQY-001', NOW(), 0)
ON DUPLICATE KEY UPDATE total_bags = VALUES(total_bags);
