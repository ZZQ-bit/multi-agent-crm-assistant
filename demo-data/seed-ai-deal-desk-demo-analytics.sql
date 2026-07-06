SET NAMES utf8mb4;

-- 多Agent智能助手 demo analytics seed.
-- Purpose:
-- 1. Remove writeback smoke records that pollute the latest CRM context.
-- 2. Add realistic follow-up records and plans for chart/context demos.
-- 3. Add a small won-deal sample so funnel and L2C charts have non-zero results.
-- 4. Add payment records and invoices so contract/revenue charts have demo data.

START TRANSACTION;

-- Make the existing stats endpoint recognize SUCCESS as a won stage.
-- The current backend checks for stage names containing 赢单/成交/签约.
UPDATE opportunity_stage_config
SET name = '签约成功', update_time = UNIX_TIMESTAMP('2026-07-01 09:00:00') * 1000, update_user = 'admin'
WHERE id = 'SUCCESS' AND organization_id = '100001';

-- Clean scoped analytics demo rows first, so the script can be run repeatedly.
DELETE FROM contract_invoice WHERE id LIKE 'demo_ana_%';
DELETE FROM contract_payment_record WHERE id LIKE 'demo_ana_%';
DELETE FROM contract_payment_plan WHERE id LIKE 'demo_ana_%';
DELETE FROM contract WHERE id LIKE 'demo_ana_%';
DELETE FROM follow_up_plan WHERE id LIKE 'demo_ana_%';
DELETE FROM follow_up_record WHERE id LIKE 'demo_ana_%';

-- Remove smoke/writeback test rows that otherwise become the latest context.
DELETE FROM follow_up_plan
WHERE content LIKE '%多Agent智能助手%'
   OR content LIKE '%smoke%'
   OR content LIKE '%ngrok%';

DELETE FROM follow_up_record
WHERE content LIKE '%多Agent智能助手%'
   OR content LIKE '%smoke%'
   OR content LIKE '%ngrok%';

-- Mark a few mature opportunities as won for L2C/conversion demo charts.
UPDATE opportunity
SET last_stage = stage,
    stage = 'SUCCESS',
    possible = 100,
    actual_end_time = UNIX_TIMESTAMP('2026-06-20 17:30:00') * 1000,
    update_time = UNIX_TIMESTAMP('2026-06-20 17:30:00') * 1000,
    update_user = 'admin'
WHERE id IN ('392653993328779272', 'demo_o_003', 'demo_o_007', 'demo_o_015', 'demo_o_037', 'demo_o_052');

-- Keep the main Huadong deal in procurement/risk-review state for the core 多Agent智能助手 demo.
UPDATE opportunity
SET stage = 'BUSINESS_PROCUREMENT',
    possible = 68,
    actual_end_time = NULL,
    update_time = UNIX_TIMESTAMP('2026-06-29 18:00:00') * 1000,
    update_user = 'admin'
WHERE id = '392650858002653191';

INSERT INTO follow_up_record
(`id`, `customer_id`, `opportunity_id`, `type`, `clue_id`, `content`, `organization_id`, `follow_time`, `follow_method`, `owner`, `contact_id`, `create_time`, `update_time`, `create_user`, `update_user`)
VALUES
('demo_ana_fr_001', '392650858002653187', '392650858002653191', 'CUSTOMER', NULL, '客户确认本期重点是客服知识库升级和售后工单自动分流，采购希望在 7 月 10 日前完成商务定稿。当前卡点是 75 折报价、首付比例和上线验收口径。', '100001', UNIX_TIMESTAMP('2026-06-29 10:00:00') * 1000, '1', 'demo_u_sales_01', '392650858002653189', UNIX_TIMESTAMP('2026-06-29 10:00:00') * 1000, UNIX_TIMESTAMP('2026-06-29 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_002', '392650858002653187', '392650858002653191', 'CUSTOMER', NULL, '财务侧初步反馈：若按 75 折成交，需要客户接受 50% 首付、40% 上线验收、10% 质保尾款；如坚持 30% 首付，需要减少免费定制范围。', '100001', UNIX_TIMESTAMP('2026-06-29 15:30:00') * 1000, '2', 'demo_u_sales_01', '392650858002653189', UNIX_TIMESTAMP('2026-06-29 15:30:00') * 1000, UNIX_TIMESTAMP('2026-06-29 15:30:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_003', '392650858002653187', '392650858002653191', 'CUSTOMER', NULL, '交付评估认为标准知识库、问答命中率报表和工单分流可以 4 周上线；历史数据清洗、外部系统接口和定制审批流建议拆到二期。', '100001', UNIX_TIMESTAMP('2026-06-30 09:20:00') * 1000, '1', 'demo_u_delivery', '392650858002653189', UNIX_TIMESTAMP('2026-06-30 09:20:00') * 1000, UNIX_TIMESTAMP('2026-06-30 09:20:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_004', '392650858002653187', '392650858002653191', 'CUSTOMER', NULL, '法务提醒客户提出延期赔付和未达命中率退款条款，建议合同中明确验收样本、试运行周期和赔付上限，避免把模型效果承诺写成无条件结果。', '100001', UNIX_TIMESTAMP('2026-06-30 16:10:00') * 1000, '2', 'demo_u_legal', '392650858002653189', UNIX_TIMESTAMP('2026-06-30 16:10:00') * 1000, UNIX_TIMESTAMP('2026-06-30 16:10:00') * 1000, 'admin', 'admin'),

('demo_ana_fr_005', 'demo_c_001', 'demo_o_001', 'CUSTOMER', NULL, '客户采购要求 8 折、首付 30%、分四期付款，并希望合同加入延期赔付条款。销售判断项目预算真实，但需要 多Agent智能助手 统一折扣、账期、合同和交付边界。', '100001', UNIX_TIMESTAMP('2026-06-24 11:00:00') * 1000, '1', 'demo_u_sales_01', 'demo_ct_001', UNIX_TIMESTAMP('2026-06-24 11:00:00') * 1000, UNIX_TIMESTAMP('2026-06-24 11:00:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_006', 'demo_c_001', 'demo_o_001', 'CUSTOMER', NULL, '采购流程已推进到商务阶段，但付款节点和验收标准仍未明确。客户内部 CFO 关注回款节奏，信息化负责人更关注上线周期。', '100001', UNIX_TIMESTAMP('2026-06-25 14:30:00') * 1000, '2', 'demo_u_sales_01', 'demo_ct_001', UNIX_TIMESTAMP('2026-06-25 14:30:00') * 1000, UNIX_TIMESTAMP('2026-06-25 14:30:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_007', 'demo_c_001', 'demo_o_003', 'CUSTOMER', NULL, '标准推进单已完成方案确认，客户接受标准合同和 60% 首付，预计本周内完成签约。', '100001', UNIX_TIMESTAMP('2026-06-18 10:00:00') * 1000, '1', 'demo_u_sales_01', 'demo_ct_001', UNIX_TIMESTAMP('2026-06-18 10:00:00') * 1000, UNIX_TIMESTAMP('2026-06-18 10:00:00') * 1000, 'admin', 'admin'),

('demo_ana_fr_008', 'demo_c_003', 'demo_o_007', 'CUSTOMER', NULL, '西南医药流通标准推进单已完成产品演示和报价确认，客户法务未提出特殊条款，转入签约成功样本。', '100001', UNIX_TIMESTAMP('2026-06-19 10:30:00') * 1000, '1', 'demo_u_sales_03', 'demo_ct_005', UNIX_TIMESTAMP('2026-06-19 10:30:00') * 1000, UNIX_TIMESTAMP('2026-06-19 10:30:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_009', 'demo_c_005', 'demo_o_015', 'CUSTOMER', NULL, '岭南连锁餐饮预测项目一期已签约，客户选择先跑 20 家门店试点，后续按季度扩容。', '100001', UNIX_TIMESTAMP('2026-06-20 15:00:00') * 1000, '2', 'demo_u_sales_05', 'demo_ct_009', UNIX_TIMESTAMP('2026-06-20 15:00:00') * 1000, UNIX_TIMESTAMP('2026-06-20 15:00:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_010', 'demo_c_013', 'demo_o_037', 'CUSTOMER', NULL, '成渝金融外包中心确认采购预测模块，合同采用标准验收条款，首付款 50%，上线验收 40%，质保尾款 10%。', '100001', UNIX_TIMESTAMP('2026-06-21 11:40:00') * 1000, '1', 'demo_u_sales_01', 'demo_ct_026', UNIX_TIMESTAMP('2026-06-21 11:40:00') * 1000, UNIX_TIMESTAMP('2026-06-21 11:40:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_011', 'demo_c_021', 'demo_o_052', 'CUSTOMER', NULL, '合肥先进材料预测重点单已完成签约，客户要求 7 月完成数据接入，8 月输出第一版销售预测看板。', '100001', UNIX_TIMESTAMP('2026-06-22 09:30:00') * 1000, '2', 'demo_u_sales_03', 'demo_ct_041', UNIX_TIMESTAMP('2026-06-22 09:30:00') * 1000, UNIX_TIMESTAMP('2026-06-22 09:30:00') * 1000, 'admin', 'admin'),

('demo_ana_fr_012', 'demo_c_008', 'demo_o_022', 'CUSTOMER', NULL, '川渝物流供应链提出大客户价格保护和延期赔付诉求，预计需要财务、交付、法务共同评审后再给最终报价。', '100001', UNIX_TIMESTAMP('2026-06-26 10:10:00') * 1000, '1', 'demo_u_sales_02', 'demo_ct_015', UNIX_TIMESTAMP('2026-06-26 10:10:00') * 1000, UNIX_TIMESTAMP('2026-06-26 10:10:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_013', 'demo_c_014', 'demo_o_039', 'CUSTOMER', NULL, '珠江 SaaS 服务商希望年度框架价再下探 12%，同时承诺引入 3 个渠道客户。需要判断渠道承诺是否能抵消毛利下降。', '100001', UNIX_TIMESTAMP('2026-06-27 13:30:00') * 1000, '2', 'demo_u_sales_02', 'demo_ct_028', UNIX_TIMESTAMP('2026-06-27 13:30:00') * 1000, UNIX_TIMESTAMP('2026-06-27 13:30:00') * 1000, 'admin', 'admin'),
('demo_ana_fr_014', 'demo_c_026', 'demo_o_062', 'CUSTOMER', NULL, '杭州客户成功咨询要求把客户健康分、续费预警和自动任务编排一次性交付。交付侧建议拆成一期标准看板和二期自动化任务。', '100001', UNIX_TIMESTAMP('2026-06-28 16:00:00') * 1000, '1', 'demo_u_sales_02', 'demo_ct_051', UNIX_TIMESTAMP('2026-06-28 16:00:00') * 1000, UNIX_TIMESTAMP('2026-06-28 16:00:00') * 1000, 'admin', 'admin');

INSERT INTO follow_up_plan
(`id`, `customer_id`, `opportunity_id`, `type`, `clue_id`, `content`, `organization_id`, `owner`, `contact_id`, `estimated_time`, `method`, `status`, `converted`, `create_time`, `update_time`, `create_user`, `update_user`)
VALUES
('demo_ana_fp_001', '392650858002653187', '392650858002653191', 'CUSTOMER', NULL, '组织 多Agent智能助手 评审会，确认 75 折报价是否需要缩减免费定制范围，并给出可接受付款方案。', '100001', 'demo_u_sales_01', '392650858002653189', UNIX_TIMESTAMP('2026-07-02 10:00:00') * 1000, '1', 'PENDING', b'0', UNIX_TIMESTAMP('2026-06-30 18:00:00') * 1000, UNIX_TIMESTAMP('2026-06-30 18:00:00') * 1000, 'admin', 'admin'),
('demo_ana_fp_002', '392650858002653187', '392650858002653191', 'CUSTOMER', NULL, '向客户同步一期/二期交付边界，确认验收样本、试运行周期和赔付上限。', '100001', 'demo_u_delivery', '392650858002653189', UNIX_TIMESTAMP('2026-07-03 14:00:00') * 1000, '2', 'PENDING', b'0', UNIX_TIMESTAMP('2026-06-30 18:05:00') * 1000, UNIX_TIMESTAMP('2026-06-30 18:05:00') * 1000, 'admin', 'admin'),
('demo_ana_fp_003', 'demo_c_001', 'demo_o_001', 'CUSTOMER', NULL, '补齐华北装备制造项目的付款节点、折扣底线和合同赔付上限，再决定是否推进终版报价。', '100001', 'demo_u_sales_01', 'demo_ct_001', UNIX_TIMESTAMP('2026-07-04 11:00:00') * 1000, '1', 'PENDING', b'0', UNIX_TIMESTAMP('2026-06-25 18:00:00') * 1000, UNIX_TIMESTAMP('2026-06-25 18:00:00') * 1000, 'admin', 'admin'),
('demo_ana_fp_004', 'demo_c_008', 'demo_o_022', 'CUSTOMER', NULL, '安排财务和法务复核川渝物流的大客户价格保护条款，输出可接受报价区间。', '100001', 'demo_u_sales_02', 'demo_ct_015', UNIX_TIMESTAMP('2026-07-04 15:00:00') * 1000, '2', 'PENDING', b'0', UNIX_TIMESTAMP('2026-06-26 18:00:00') * 1000, UNIX_TIMESTAMP('2026-06-26 18:00:00') * 1000, 'admin', 'admin'),
('demo_ana_fp_005', 'demo_c_014', 'demo_o_039', 'CUSTOMER', NULL, '要求珠江 SaaS 服务商提供渠道客户名单和引入时间表，用于评估折扣换渠道承诺是否成立。', '100001', 'demo_u_sales_02', 'demo_ct_028', UNIX_TIMESTAMP('2026-07-05 10:30:00') * 1000, '2', 'PENDING', b'0', UNIX_TIMESTAMP('2026-06-27 18:00:00') * 1000, UNIX_TIMESTAMP('2026-06-27 18:00:00') * 1000, 'admin', 'admin'),
('demo_ana_fp_006', 'demo_c_026', 'demo_o_062', 'CUSTOMER', NULL, '与杭州客户成功咨询确认一期只交付健康分和续费预警看板，自动任务编排进入二期报价。', '100001', 'demo_u_sales_02', 'demo_ct_051', UNIX_TIMESTAMP('2026-07-05 16:00:00') * 1000, '1', 'PENDING', b'0', UNIX_TIMESTAMP('2026-06-28 18:00:00') * 1000, UNIX_TIMESTAMP('2026-06-28 18:00:00') * 1000, 'admin', 'admin');

INSERT INTO contract
(`id`, `name`, `customer_id`, `owner`, `amount`, `number`, `approval_status`, `stage`, `organization_id`, `void_reason`, `create_time`, `update_time`, `create_user`, `update_user`, `start_time`, `end_time`, `pos`)
VALUES
('demo_ana_ctr_001', '北方连锁零售知识库一期系统合同', '392653993328779268', 'demo_u_sales_02', 420000.00, 'CTR-2026-ANA-001', 'APPROVED', 'IN_PROGRESS', '100001', NULL, UNIX_TIMESTAMP('2026-06-18 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-18 09:00:00') * 1000, 'admin', 'admin', UNIX_TIMESTAMP('2026-06-18 00:00:00') * 1000, UNIX_TIMESTAMP('2027-06-17 23:59:59') * 1000, 101),
('demo_ana_ctr_002', '华北装备制造标准推进单合同', 'demo_c_001', 'demo_u_sales_01', 217000.00, 'CTR-2026-ANA-002', 'APPROVED', 'IN_PROGRESS', '100001', NULL, UNIX_TIMESTAMP('2026-06-20 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-20 09:00:00') * 1000, 'admin', 'admin', UNIX_TIMESTAMP('2026-06-20 00:00:00') * 1000, UNIX_TIMESTAMP('2027-06-19 23:59:59') * 1000, 102),
('demo_ana_ctr_003', '岭南连锁餐饮销售预测试点合同', 'demo_c_005', 'demo_u_sales_05', 613000.00, 'CTR-2026-ANA-003', 'APPROVED', 'IN_PROGRESS', '100001', NULL, UNIX_TIMESTAMP('2026-06-22 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-22 09:00:00') * 1000, 'admin', 'admin', UNIX_TIMESTAMP('2026-06-22 00:00:00') * 1000, UNIX_TIMESTAMP('2027-06-21 23:59:59') * 1000, 103),
('demo_ana_ctr_004', '成渝金融外包预测模块采购合同', 'demo_c_013', 'demo_u_sales_01', 1283000.00, 'CTR-2026-ANA-004', 'APPROVED', 'IN_PROGRESS', '100001', NULL, UNIX_TIMESTAMP('2026-06-23 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-23 09:00:00') * 1000, 'admin', 'admin', UNIX_TIMESTAMP('2026-06-23 00:00:00') * 1000, UNIX_TIMESTAMP('2027-06-22 23:59:59') * 1000, 104);

INSERT INTO contract_payment_plan
(`id`, `name`, `contract_id`, `owner`, `plan_status`, `plan_amount`, `plan_end_time`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`)
VALUES
('demo_ana_pay_001_1', '知识库一期首付款', 'demo_ana_ctr_001', 'demo_u_sales_02', 'COMPLETED', 210000.00, UNIX_TIMESTAMP('2026-06-25 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-18 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-25 09:30:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_001_2', '上线验收尾款', 'demo_ana_ctr_001', 'demo_u_sales_02', 'PENDING', 210000.00, UNIX_TIMESTAMP('2026-08-15 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-18 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-18 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_002_1', '标准推进首付款', 'demo_ana_ctr_002', 'demo_u_sales_01', 'COMPLETED', 130200.00, UNIX_TIMESTAMP('2026-06-27 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-20 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-27 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_002_2', '标准推进验收款', 'demo_ana_ctr_002', 'demo_u_sales_01', 'PENDING', 86800.00, UNIX_TIMESTAMP('2026-07-30 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-20 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-20 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_003_1', '餐饮预测试点首付款', 'demo_ana_ctr_003', 'demo_u_sales_05', 'COMPLETED', 306500.00, UNIX_TIMESTAMP('2026-06-29 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-22 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-29 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_003_2', '试点上线验收款', 'demo_ana_ctr_003', 'demo_u_sales_05', 'PENDING', 245200.00, UNIX_TIMESTAMP('2026-08-20 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-22 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-22 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_003_3', '质保尾款', 'demo_ana_ctr_003', 'demo_u_sales_05', 'PENDING', 61300.00, UNIX_TIMESTAMP('2026-12-20 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-22 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-22 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_004_1', '金融预测模块首付款', 'demo_ana_ctr_004', 'demo_u_sales_01', 'COMPLETED', 641500.00, UNIX_TIMESTAMP('2026-06-30 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-23 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-30 11:30:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_004_2', '金融预测模块验收款', 'demo_ana_ctr_004', 'demo_u_sales_01', 'PENDING', 513200.00, UNIX_TIMESTAMP('2026-09-15 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-23 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-23 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_pay_004_3', '金融预测模块质保尾款', 'demo_ana_ctr_004', 'demo_u_sales_01', 'PENDING', 128300.00, UNIX_TIMESTAMP('2027-01-15 00:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-23 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-23 09:00:00') * 1000, 'admin', 'admin');

INSERT INTO contract_payment_record
(`id`, `name`, `no`, `owner`, `contract_id`, `payment_plan_id`, `record_amount`, `record_end_time`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`)
VALUES
('demo_ana_rec_001', '华东标准软件采购首付款到账', 'PR-2025-001', 'demo_u_sales_01', 'demo_ctr_001', 'demo_pay_001_1', 130000.00, UNIX_TIMESTAMP('2025-01-06 10:00:00') * 1000, '100001', UNIX_TIMESTAMP('2025-01-06 10:00:00') * 1000, UNIX_TIMESTAMP('2025-01-06 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_rec_002', '华东标准软件采购尾款到账', 'PR-2025-002', 'demo_u_sales_01', 'demo_ctr_001', 'demo_pay_001_2', 130000.00, UNIX_TIMESTAMP('2025-02-05 10:00:00') * 1000, '100001', UNIX_TIMESTAMP('2025-02-05 10:00:00') * 1000, UNIX_TIMESTAMP('2025-02-05 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_rec_003', '华东定制实施首付款到账', 'PR-2026-001', 'demo_u_sales_01', 'demo_ctr_002', 'demo_pay_002_1', 75000.00, UNIX_TIMESTAMP('2026-04-08 10:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-04-08 10:00:00') * 1000, UNIX_TIMESTAMP('2026-04-08 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_rec_004', '北方零售知识库首付款到账', 'PR-2026-ANA-001', 'demo_u_sales_02', 'demo_ana_ctr_001', 'demo_ana_pay_001_1', 210000.00, UNIX_TIMESTAMP('2026-06-25 09:30:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-25 09:30:00') * 1000, UNIX_TIMESTAMP('2026-06-25 09:30:00') * 1000, 'admin', 'admin'),
('demo_ana_rec_005', '华北装备制造标准推进首付款到账', 'PR-2026-ANA-002', 'demo_u_sales_01', 'demo_ana_ctr_002', 'demo_ana_pay_002_1', 130200.00, UNIX_TIMESTAMP('2026-06-27 10:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-27 10:00:00') * 1000, UNIX_TIMESTAMP('2026-06-27 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_rec_006', '岭南连锁餐饮试点首付款到账', 'PR-2026-ANA-003', 'demo_u_sales_05', 'demo_ana_ctr_003', 'demo_ana_pay_003_1', 306500.00, UNIX_TIMESTAMP('2026-06-29 10:00:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-29 10:00:00') * 1000, UNIX_TIMESTAMP('2026-06-29 10:00:00') * 1000, 'admin', 'admin'),
('demo_ana_rec_007', '成渝金融预测模块首付款到账', 'PR-2026-ANA-004', 'demo_u_sales_01', 'demo_ana_ctr_004', 'demo_ana_pay_004_1', 641500.00, UNIX_TIMESTAMP('2026-06-30 11:30:00') * 1000, '100001', UNIX_TIMESTAMP('2026-06-30 11:30:00') * 1000, UNIX_TIMESTAMP('2026-06-30 11:30:00') * 1000, 'admin', 'admin');

INSERT INTO contract_invoice
(`id`, `name`, `contract_id`, `owner`, `amount`, `invoice_type`, `tax_rate`, `approval_status`, `business_title_id`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`)
VALUES
('demo_ana_inv_001', '华东标准软件采购首付款发票', 'demo_ctr_001', 'demo_u_sales_01', 130000.00, 'SPECIAL', 0.06, 'APPROVED', NULL, '100001', UNIX_TIMESTAMP('2025-01-07 09:00:00') * 1000, UNIX_TIMESTAMP('2025-01-07 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_inv_002', '华东标准软件采购尾款发票', 'demo_ctr_001', 'demo_u_sales_01', 130000.00, 'SPECIAL', 0.06, 'APPROVED', NULL, '100001', UNIX_TIMESTAMP('2025-02-06 09:00:00') * 1000, UNIX_TIMESTAMP('2025-02-06 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_inv_003', '华东定制实施首付款发票', 'demo_ctr_002', 'demo_u_sales_01', 75000.00, 'SPECIAL', 0.06, 'APPROVED', NULL, '100001', UNIX_TIMESTAMP('2026-04-09 09:00:00') * 1000, UNIX_TIMESTAMP('2026-04-09 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_inv_004', '北方零售知识库首付款发票', 'demo_ana_ctr_001', 'demo_u_sales_02', 210000.00, 'SPECIAL', 0.06, 'APPROVED', NULL, '100001', UNIX_TIMESTAMP('2026-06-26 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-26 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_inv_005', '华北装备制造标准推进首付款发票', 'demo_ana_ctr_002', 'demo_u_sales_01', 130200.00, 'SPECIAL', 0.06, 'APPROVED', NULL, '100001', UNIX_TIMESTAMP('2026-06-28 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-28 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_inv_006', '岭南连锁餐饮试点首付款发票', 'demo_ana_ctr_003', 'demo_u_sales_05', 306500.00, 'SPECIAL', 0.06, 'APPROVED', NULL, '100001', UNIX_TIMESTAMP('2026-06-30 09:00:00') * 1000, UNIX_TIMESTAMP('2026-06-30 09:00:00') * 1000, 'admin', 'admin'),
('demo_ana_inv_007', '成渝金融预测模块首付款发票', 'demo_ana_ctr_004', 'demo_u_sales_01', 641500.00, 'SPECIAL', 0.06, 'APPROVED', NULL, '100001', UNIX_TIMESTAMP('2026-07-01 09:00:00') * 1000, UNIX_TIMESTAMP('2026-07-01 09:00:00') * 1000, 'admin', 'admin');

-- Refresh latest follow time for affected objects.
UPDATE opportunity o
SET follow_time = (
        SELECT MAX(r.follow_time)
        FROM follow_up_record r
        WHERE r.opportunity_id = o.id
    ),
    follower = COALESCE((
        SELECT r.owner
        FROM follow_up_record r
        WHERE r.opportunity_id = o.id
        ORDER BY r.follow_time DESC, r.create_time DESC
        LIMIT 1
    ), o.follower)
WHERE o.id IN (
    '392650858002653191', '392653993328779272',
    'demo_o_001', 'demo_o_003', 'demo_o_007', 'demo_o_015', 'demo_o_022', 'demo_o_037',
    'demo_o_039', 'demo_o_052', 'demo_o_062'
);

UPDATE customer c
SET follow_time = (
        SELECT MAX(r.follow_time)
        FROM follow_up_record r
        WHERE r.customer_id = c.id
    ),
    follower = COALESCE((
        SELECT r.owner
        FROM follow_up_record r
        WHERE r.customer_id = c.id
        ORDER BY r.follow_time DESC, r.create_time DESC
        LIMIT 1
    ), c.follower)
WHERE c.id IN (
    '392650858002653187', '392653993328779268',
    'demo_c_001', 'demo_c_003', 'demo_c_005', 'demo_c_008', 'demo_c_013',
    'demo_c_014', 'demo_c_021', 'demo_c_026'
);

COMMIT;

SELECT 'opportunity_success' AS metric, COUNT(*) AS value FROM opportunity WHERE stage = 'SUCCESS'
UNION ALL SELECT 'demo_analytics_follow_records', COUNT(*) FROM follow_up_record WHERE id LIKE 'demo_ana_fr_%'
UNION ALL SELECT 'demo_analytics_follow_plans', COUNT(*) FROM follow_up_plan WHERE id LIKE 'demo_ana_fp_%'
UNION ALL SELECT 'contract_payment_records', COUNT(*) FROM contract_payment_record
UNION ALL SELECT 'contract_invoices', COUNT(*) FROM contract_invoice
UNION ALL SELECT 'remaining_smoke_follow_records', COUNT(*) FROM follow_up_record WHERE content LIKE '%smoke%' OR content LIKE '%ngrok%' OR content LIKE '%多Agent智能助手%';
