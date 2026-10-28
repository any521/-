-- ============================================================
-- V5 · 修复历史种子中文被 latin1 连接有损写入为字面 '?' 的脏数据
-- 背景：早期在 latin1 字符集环境下写入的种子中文被 MySQL 损失性替换为 '?'
--       （现表已 utf8mb4，新写入正常）。本迁移仅修复含 '?' 的行（LIKE '%?%' 守卫），
--       新装库与已被人工改名/改值的行自动跳过，幂等可重跑。
-- 值来源：V1__init_system.sql / V4__track_e_ai_open.sql 种子定义
-- ============================================================
SET NAMES utf8mb4;

-- ---------- sys_role（种子 1-5；测试自建角色意图未知，不修） ----------
UPDATE sys_role SET role_name = '平台管理员', remark = '全部权限' WHERE id = 1 AND role_name LIKE '%?%';
UPDATE sys_role SET role_name = '普通用户',   remark = '用户端'   WHERE id = 2 AND role_name LIKE '%?%';
UPDATE sys_role SET role_name = '再生资源回收员', remark = '员工端' WHERE id = 3 AND role_name LIKE '%?%';
UPDATE sys_role SET role_name = '环卫收运员', remark = '员工端'   WHERE id = 4 AND role_name LIKE '%?%';
UPDATE sys_role SET role_name = '电路维修技术员', remark = '员工端' WHERE id = 5 AND role_name LIKE '%?%';

-- ---------- sys_menu ----------
UPDATE sys_menu SET menu_name = '系统管理' WHERE id = 100 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '用户管理' WHERE id = 101 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '角色管理' WHERE id = 102 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '菜单管理' WHERE id = 103 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '字典管理' WHERE id = 104 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '参数配置' WHERE id = 105 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '公告管理' WHERE id = 106 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '操作日志' WHERE id = 107 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '登录日志' WHERE id = 108 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '角色新增' WHERE id = 10201 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '角色编辑' WHERE id = 10202 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '角色删除' WHERE id = 10203 AND menu_name LIKE '%?%';
UPDATE sys_menu SET menu_name = '分配菜单' WHERE id = 10204 AND menu_name LIKE '%?%';

-- ---------- sys_dict_type ----------
UPDATE sys_dict_type SET dict_name = '可回收物品类' WHERE id = 1 AND dict_name LIKE '%?%';
UPDATE sys_dict_type SET dict_name = '维修品类'     WHERE id = 2 AND dict_name LIKE '%?%';

-- ---------- sys_dict_data ----------
UPDATE sys_dict_data SET dict_label = '金属' WHERE id = 11 AND dict_label LIKE '%?%';
UPDATE sys_dict_data SET dict_label = '塑料' WHERE id = 12 AND dict_label LIKE '%?%';
UPDATE sys_dict_data SET dict_label = '纸类' WHERE id = 13 AND dict_label LIKE '%?%';
UPDATE sys_dict_data SET dict_label = '织物' WHERE id = 14 AND dict_label LIKE '%?%';
UPDATE sys_dict_data SET dict_label = '电子废弃物' WHERE id = 15 AND dict_label LIKE '%?%';
UPDATE sys_dict_data SET dict_label = '小家电' WHERE id = 21 AND dict_label LIKE '%?%';
UPDATE sys_dict_data SET dict_label = '电路板' WHERE id = 22 AND dict_label LIKE '%?%';

-- ---------- sys_config（V1 基础参数） ----------
UPDATE sys_config SET config_name = '厨余投放开始时段（时）' WHERE id = 1 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = '厨余投放结束时段（时）' WHERE id = 2 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = '环卫清运开始时段（时）' WHERE id = 3 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = '环卫清运结束时段（时）' WHERE id = 4 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = '厨余上报偏离片区均值告警倍数' WHERE id = 5 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'MCP 单客户端每分钟限流' WHERE id = 6 AND config_name LIKE '%?%';

-- ---------- sys_config（V4 AI/派单参数，含 remark） ----------
UPDATE sys_config SET config_name = 'F1 自动通过置信度阈值', remark = '>=该值且无违规自动通过' WHERE id = 9101 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F1 转人审置信度阈值', remark = '低于 pass 高于该值转人审' WHERE id = 9102 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F1 pHash 防重汉明距离阈值', remark = '<=该值判定重复图直接驳回' WHERE id = 9103 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F1 EXIF 拍摄时间时效（小时）', remark = '超时或缺失强制人审' WHERE id = 9104 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F4 Timefold 求解时限（秒）', remark = '超时即取当前最优解返回' WHERE id = 9105 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F4 时间窗容忍（分钟）', remark = '预约时间±该值为软时间窗' WHERE id = 9106 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F4 单员工日承载上限（kg）', remark = '超载记硬约束违反' WHERE id = 9107 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F4 单点服务时长（分钟）', remark = '上门收货平均耗时' WHERE id = 9108 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F4 班次开始（时）', remark = '路线推演出发时刻' WHERE id = 9109 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F5 定价系数下限', remark = 'AI 报价/积分系数低于该值即夹逼' WHERE id = 9110 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F5 定价系数上限', remark = 'AI 报价/积分系数高于该值即夹逼' WHERE id = 9111 AND config_name LIKE '%?%';
UPDATE sys_config SET config_name = 'F7 语音单默认片区 ID', remark = '无地址簿时语音成单落此片区' WHERE id = 9112 AND config_name LIKE '%?%';

-- ---------- sys_user（仅 agent 系统账号昵称；admin 昵称已被人工改过，守卫自动跳过） ----------
UPDATE sys_user SET nickname = '调度 Agent' WHERE id = 2 AND nickname LIKE '%?%';
