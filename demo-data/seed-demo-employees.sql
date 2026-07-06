SET NAMES utf8mb4;

INSERT INTO sys_department (id, name, organization_id, parent_id, pos, create_time, update_time, create_user, update_user, resource, resource_id) VALUES
('demo_d_sales', '销售部', '100001', '101944022951526400', 10, 1781379000000, 1781379000000, 'admin', 'admin', 'MANUAL', 'demo_d_sales'),
('demo_d_delivery', '交付实施部', '100001', '101944022951526400', 20, 1781379000000, 1781379000000, 'admin', 'admin', 'MANUAL', 'demo_d_delivery'),
('demo_d_finance', '财务部', '100001', '101944022951526400', 30, 1781379000000, 1781379000000, 'admin', 'admin', 'MANUAL', 'demo_d_finance'),
('demo_d_legal', '法务部', '100001', '101944022951526400', 40, 1781379000000, 1781379000000, 'admin', 'admin', 'MANUAL', 'demo_d_legal'),
('demo_d_cs', '客户成功部', '100001', '101944022951526400', 50, 1781379000000, 1781379000000, 'admin', 'admin', 'MANUAL', 'demo_d_cs')
ON DUPLICATE KEY UPDATE name = VALUES(name), parent_id = VALUES(parent_id), pos = VALUES(pos), update_time = VALUES(update_time), update_user = VALUES(update_user);

INSERT INTO sys_user (id, name, phone, email, password, language, last_organization_id, gender, create_time, update_time, create_user, update_user) VALUES
('demo_u_sales_mgr', '林晓峰', '13710001001', 'linxf@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'1', 1781379000000, 1781379000000, 'admin', 'admin'),
('demo_u_sales_01', '周雨晴', '13710001002', 'zhouyq@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000001, 1781379000001, 'admin', 'admin'),
('demo_u_sales_02', '陈嘉豪', '13710001003', 'chenjh@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000002, 1781379000002, 'admin', 'admin'),
('demo_u_sales_03', '王思涵', '13710001004', 'wangsh@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'1', 1781379000003, 1781379000003, 'admin', 'admin'),
('demo_u_sales_04', '赵明远', '13710001005', 'zhaomy@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000004, 1781379000004, 'admin', 'admin'),
('demo_u_sales_05', '李若彤', '13710001006', 'lirut@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000005, 1781379000005, 'admin', 'admin'),
('demo_u_delivery_mgr', '许承泽', '13710001007', 'xucz@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'1', 1781379000006, 1781379000006, 'admin', 'admin'),
('demo_u_delivery_01', '沈佳宁', '13710001008', 'shenjn@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000007, 1781379000007, 'admin', 'admin'),
('demo_u_finance', '韩知夏', '13710001009', 'hanzx@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000008, 1781379000008, 'admin', 'admin'),
('demo_u_legal', '顾言', '13710001010', 'guyan@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'1', 1781379000009, 1781379000009, 'admin', 'admin'),
('demo_u_cs_01', '何星辰', '13710001011', 'hexingc@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000010, 1781379000010, 'admin', 'admin'),
('demo_u_cs_02', '唐一诺', '13710001012', 'tangyn@cordys-demo.local', (SELECT password FROM (SELECT password FROM sys_user WHERE id = 'admin') p), 'zh_CN', '100001', b'0', 1781379000011, 1781379000011, 'admin', 'admin')
ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone), email = VALUES(email), last_organization_id = VALUES(last_organization_id), update_time = VALUES(update_time), update_user = VALUES(update_user);

INSERT INTO sys_user_extend (id, avatar, platform_info) VALUES
('demo_u_sales_mgr', NULL, NULL),
('demo_u_sales_01', NULL, NULL),
('demo_u_sales_02', NULL, NULL),
('demo_u_sales_03', NULL, NULL),
('demo_u_sales_04', NULL, NULL),
('demo_u_sales_05', NULL, NULL),
('demo_u_delivery_mgr', NULL, NULL),
('demo_u_delivery_01', NULL, NULL),
('demo_u_finance', NULL, NULL),
('demo_u_legal', NULL, NULL),
('demo_u_cs_01', NULL, NULL),
('demo_u_cs_02', NULL, NULL)
ON DUPLICATE KEY UPDATE avatar = VALUES(avatar);

INSERT INTO sys_organization_user (id, organization_id, department_id, resource_user_id, user_id, enable, employee_id, position, employee_type, supervisor_id, work_city, create_time, update_time, create_user, update_user, onboarding_date) VALUES
('demo_ou_admin', '100001', '101944022951526400', 'admin', 'admin', b'1', '管理员', '系统管理员', '正式员工', '', '上海', 1781379000000, 1781379000000, 'admin', 'admin', 1765827000000),
('demo_ou_001', '100001', 'demo_d_sales', 'demo_u_sales_mgr', 'demo_u_sales_mgr', b'1', 'E0001', '销售总监', '正式员工', '', '上海', 1781379000001, 1781379000001, 'admin', 'admin', 1765913400000),
('demo_ou_002', '100001', 'demo_d_sales', 'demo_u_sales_01', 'demo_u_sales_01', b'1', 'E0002', '高级客户经理', '正式员工', 'demo_u_sales_mgr', '上海', 1781379000002, 1781379000002, 'admin', 'admin', 1765999800000),
('demo_ou_003', '100001', 'demo_d_sales', 'demo_u_sales_02', 'demo_u_sales_02', b'1', 'E0003', '客户经理', '正式员工', 'demo_u_sales_mgr', '北京', 1781379000003, 1781379000003, 'admin', 'admin', 1766086200000),
('demo_ou_004', '100001', 'demo_d_sales', 'demo_u_sales_03', 'demo_u_sales_03', b'1', 'E0004', '客户经理', '正式员工', 'demo_u_sales_mgr', '深圳', 1781379000004, 1781379000004, 'admin', 'admin', 1766172600000),
('demo_ou_005', '100001', 'demo_d_sales', 'demo_u_sales_04', 'demo_u_sales_04', b'1', 'E0005', '行业销售顾问', '正式员工', 'demo_u_sales_mgr', '杭州', 1781379000005, 1781379000005, 'admin', 'admin', 1766259000000),
('demo_ou_006', '100001', 'demo_d_sales', 'demo_u_sales_05', 'demo_u_sales_05', b'1', 'E0006', '客户成功销售', '正式员工', 'demo_u_sales_mgr', '成都', 1781379000006, 1781379000006, 'admin', 'admin', 1766345400000),
('demo_ou_007', '100001', 'demo_d_delivery', 'demo_u_delivery_mgr', 'demo_u_delivery_mgr', b'1', 'E0007', '交付负责人', '正式员工', 'demo_u_sales_mgr', '上海', 1781379000007, 1781379000007, 'admin', 'admin', 1766431800000),
('demo_ou_008', '100001', 'demo_d_delivery', 'demo_u_delivery_01', 'demo_u_delivery_01', b'1', 'E0008', '实施顾问', '正式员工', 'demo_u_delivery_mgr', '南京', 1781379000008, 1781379000008, 'admin', 'admin', 1766518200000),
('demo_ou_009', '100001', 'demo_d_finance', 'demo_u_finance', 'demo_u_finance', b'1', 'E0009', '财务复核专员', '正式员工', 'demo_u_sales_mgr', '上海', 1781379000009, 1781379000009, 'admin', 'admin', 1766604600000),
('demo_ou_010', '100001', 'demo_d_legal', 'demo_u_legal', 'demo_u_legal', b'1', 'E0010', '法务顾问', '正式员工', 'demo_u_sales_mgr', '上海', 1781379000010, 1781379000010, 'admin', 'admin', 1766691000000),
('demo_ou_011', '100001', 'demo_d_cs', 'demo_u_cs_01', 'demo_u_cs_01', b'1', 'E0011', '客户成功经理', '正式员工', 'demo_u_delivery_mgr', '广州', 1781379000011, 1781379000011, 'admin', 'admin', 1766777400000),
('demo_ou_012', '100001', 'demo_d_cs', 'demo_u_cs_02', 'demo_u_cs_02', b'1', 'E0012', '客户成功经理', '正式员工', 'demo_u_delivery_mgr', '武汉', 1781379000012, 1781379000012, 'admin', 'admin', 1766863800000)
ON DUPLICATE KEY UPDATE department_id = VALUES(department_id), enable = VALUES(enable), employee_id = VALUES(employee_id), position = VALUES(position), employee_type = VALUES(employee_type), supervisor_id = VALUES(supervisor_id), work_city = VALUES(work_city), update_time = VALUES(update_time), update_user = VALUES(update_user);

INSERT INTO sys_user_role (id, role_id, user_id, create_time, update_time, create_user, update_user) VALUES
('demo_ur_admin', 'org_admin', 'admin', 1781379000000, 1781379000000, 'admin', 'admin'),
('demo_ur_001', 'sales_manager', 'demo_u_sales_mgr', 1781379000001, 1781379000001, 'admin', 'admin'),
('demo_ur_002', 'sales_staff', 'demo_u_sales_01', 1781379000002, 1781379000002, 'admin', 'admin'),
('demo_ur_003', 'sales_staff', 'demo_u_sales_02', 1781379000003, 1781379000003, 'admin', 'admin'),
('demo_ur_004', 'sales_staff', 'demo_u_sales_03', 1781379000004, 1781379000004, 'admin', 'admin'),
('demo_ur_005', 'sales_staff', 'demo_u_sales_04', 1781379000005, 1781379000005, 'admin', 'admin'),
('demo_ur_006', 'sales_staff', 'demo_u_sales_05', 1781379000006, 1781379000006, 'admin', 'admin'),
('demo_ur_007', 'sales_manager', 'demo_u_delivery_mgr', 1781379000007, 1781379000007, 'admin', 'admin'),
('demo_ur_008', 'sales_staff', 'demo_u_delivery_01', 1781379000008, 1781379000008, 'admin', 'admin'),
('demo_ur_009', 'sales_staff', 'demo_u_finance', 1781379000009, 1781379000009, 'admin', 'admin'),
('demo_ur_010', 'sales_staff', 'demo_u_legal', 1781379000010, 1781379000010, 'admin', 'admin'),
('demo_ur_011', 'sales_staff', 'demo_u_cs_01', 1781379000011, 1781379000011, 'admin', 'admin'),
('demo_ur_012', 'sales_staff', 'demo_u_cs_02', 1781379000012, 1781379000012, 'admin', 'admin')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), user_id = VALUES(user_id), update_time = VALUES(update_time), update_user = VALUES(update_user);

UPDATE customer SET owner = CASE MOD(CAST(COALESCE(NULLIF(REGEXP_REPLACE(id, '[^0-9]', ''), ''), '0') AS UNSIGNED), 6)
  WHEN 0 THEN 'demo_u_sales_mgr'
  WHEN 1 THEN 'demo_u_sales_01'
  WHEN 2 THEN 'demo_u_sales_02'
  WHEN 3 THEN 'demo_u_sales_03'
  WHEN 4 THEN 'demo_u_sales_04'
  WHEN 5 THEN 'demo_u_sales_05'
END, follower = owner, update_user = owner WHERE id LIKE 'demo_c_%';

UPDATE customer SET owner = 'demo_u_sales_01', follower = 'demo_u_sales_01', update_user = 'demo_u_sales_01' WHERE id = '392650858002653187';
UPDATE customer SET owner = 'demo_u_sales_02', follower = 'demo_u_sales_02', update_user = 'demo_u_sales_02' WHERE id = '392653993328779268';
UPDATE customer SET owner = 'demo_u_sales_03', follower = 'demo_u_sales_03', update_user = 'demo_u_sales_03' WHERE id = '392653993328779277';

UPDATE customer_contact ct JOIN customer c ON ct.customer_id = c.id SET ct.owner = c.owner, ct.update_user = c.owner WHERE c.id LIKE 'demo_c_%' OR c.id IN ('392650858002653187','392653993328779268','392653993328779277');

UPDATE opportunity o JOIN customer c ON o.customer_id = c.id SET o.owner = c.owner, o.follower = c.owner, o.update_user = c.owner WHERE c.id LIKE 'demo_c_%' OR c.id IN ('392650858002653187','392653993328779268','392653993328779277');

UPDATE follow_up_record r JOIN opportunity o ON r.opportunity_id = o.id SET r.owner = CASE MOD(CAST(COALESCE(NULLIF(REGEXP_REPLACE(r.id, '[^0-9]', ''), ''), '0') AS UNSIGNED), 9)
  WHEN 0 THEN 'demo_u_sales_mgr'
  WHEN 1 THEN 'demo_u_sales_01'
  WHEN 2 THEN 'demo_u_sales_02'
  WHEN 3 THEN 'demo_u_sales_03'
  WHEN 4 THEN 'demo_u_sales_04'
  WHEN 5 THEN 'demo_u_sales_05'
  WHEN 6 THEN 'demo_u_delivery_01'
  WHEN 7 THEN 'demo_u_cs_01'
  WHEN 8 THEN 'demo_u_cs_02'
END, r.update_user = r.owner WHERE o.id LIKE 'demo_o_%' OR o.id IN ('392650858002653191','392653993328779272','392653993328779281','392653993328779286','392653993328779290');

UPDATE follow_up_plan p JOIN opportunity o ON p.opportunity_id = o.id SET p.owner = o.owner, p.update_user = o.owner WHERE o.id LIKE 'demo_o_%' OR o.id IN ('392650858002653191','392653993328779272','392653993328779281','392653993328779286','392653993328779290');

SELECT 'sys_user' AS tbl, COUNT(*) AS cnt FROM sys_user UNION ALL SELECT 'sys_organization_user', COUNT(*) FROM sys_organization_user UNION ALL SELECT 'sys_user_role', COUNT(*) FROM sys_user_role UNION ALL SELECT 'customer_owner_count', COUNT(DISTINCT owner) FROM customer UNION ALL SELECT 'opportunity_owner_count', COUNT(DISTINCT owner) FROM opportunity;
