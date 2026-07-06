SET NAMES utf8mb4;

-- 1. 清理已有的 Demo 额外数据，防止冲突（使用统一的 demo_ 前缀）
DELETE FROM clue_pool_recycle_rule WHERE id LIKE 'demo_%';
DELETE FROM clue_pool WHERE id LIKE 'demo_%';
DELETE FROM clue_field WHERE resource_id LIKE 'demo_%';
DELETE FROM clue WHERE id LIKE 'demo_%';
DELETE FROM follow_up_record WHERE id LIKE 'demo_%';
DELETE FROM follow_up_plan WHERE id LIKE 'demo_%';
DELETE FROM opportunity_quotation_field WHERE resource_id LIKE 'demo_%';
DELETE FROM opportunity_quotation WHERE id LIKE 'demo_%';
DELETE FROM contract_payment_plan WHERE id LIKE 'demo_%';
DELETE FROM contract_field WHERE resource_id LIKE 'demo_%';
DELETE FROM contract WHERE id LIKE 'demo_%';
DELETE FROM approval_task WHERE id LIKE 'demo_%';
DELETE FROM approval_instance WHERE id LIKE 'demo_%';
DELETE FROM approval_flow_version WHERE id LIKE 'demo_%';
DELETE FROM approval_flow WHERE id LIKE 'demo_%';
DELETE FROM approval_node WHERE id LIKE 'demo_%';
DELETE FROM approval_node_approver WHERE id LIKE 'demo_%';

-- 2. 插入线索池 (Clue Pool)
INSERT INTO clue_pool (`id`, `name`, `scope_id`, `organization_id`, `owner_id`, `enable`, `auto`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
('demo_pool_001', '华东智能制造开拓线索池', '["admin", "demo_u_sales_01", "demo_u_sales_02"]', '100001', 'admin', b'1', b'1', 1781373600000, 1781373600000, 'admin', 'admin');

-- 3. 插入线索 (Clue/Lead)
-- 包含 NEW(新建), FOLLOWING(跟进中), INTERESTED(感兴趣), SUCCESS(成功/已转化), FAIL(失效/战败)
INSERT INTO clue (`id`, `name`, `owner`, `last_stage`, `stage`, `collection_time`, `contact`, `phone`, `products`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`, `transition_type`, `transition_id`, `in_shared_pool`, `follower`, `follow_time`, `pool_id`) VALUES
-- 1. 新线索 - 苏州精工自动化设备有限公司
('demo_clue_001', '苏州精工自动化设备有限公司', 'demo_u_sales_01', NULL, 'NEW', 1781373610000, '刘强', '13812345678', '["demo_p_005"]', '100001', 1781373610000, 1781373610000, 'admin', 'admin', 'NONE', NULL, b'0', NULL, NULL, NULL),
-- 2. 跟进中线索 - 无锡智能仓储装备集团
('demo_clue_002', '无锡智能仓储装备集团', 'demo_u_sales_02', 'NEW', 'FOLLOWING', 1781373620000, '李丽', '13912345678', '["demo_p_004"]', '100001', 1781373620000, 1781373800000, 'admin', 'admin', 'NONE', NULL, b'0', 'demo_u_sales_02', 1781373800000, NULL),
-- 3. 感兴趣线索 - 常州精细化工有限公司
('demo_clue_003', '常州精细化工有限公司', 'demo_u_sales_02', 'FOLLOWING', 'INTERESTED', 1781373630000, '张军', '13712345678', '["demo_p_005"]', '100001', 1781373630000, 1781374000000, 'admin', 'admin', 'NONE', NULL, b'0', 'demo_u_sales_02', 1781374000000, NULL),
-- 4. 成功转商机线索 - 徐州重工集团有限公司 (已转为 demo_c_001 和 demo_o_001)
('demo_clue_004', '徐州重工集团有限公司', 'demo_u_sales_01', 'INTERESTED', 'SUCCESS', 1781373640000, '王伟', '13612345678', '["demo_p_001"]', '100001', 1781373640000, 1781375000000, 'admin', 'admin', 'OPPORTUNITY', 'demo_o_001', b'0', 'demo_u_sales_01', 1781375000000, NULL),
-- 5. 公海线索 - 南通纺织机械股份 (处于线索公海，待认领)
('demo_clue_005', '南通纺织机械股份', NULL, NULL, 'NEW', NULL, '赵强', '13512345678', '["demo_p_005"]', '100001', 1781373650000, 1781373650000, 'admin', 'admin', 'NONE', NULL, b'1', NULL, NULL, 'demo_pool_001'),
-- 6. 战败线索 - 盐城汽车零配件制造厂 (客户预算收缩)
('demo_clue_006', '盐城汽车零配件制造厂', 'demo_u_sales_03', 'FOLLOWING', 'FAIL', 1781373660000, '何刚', '13412345678', '["demo_p_004"]', '100001', 1781373660000, 1781376000000, 'admin', 'admin', 'NONE', NULL, b'0', 'demo_u_sales_03', 1781376000000, NULL);

-- 4. 插入线索的跟进记录 (Follow-up Record)
INSERT INTO follow_up_record (`id`, `customer_id`, `opportunity_id`, `type`, `clue_id`, `content`, `organization_id`, `follow_time`, `follow_method`, `owner`, `contact_id`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
('demo_fr_clue_001', NULL, NULL, 'CLUE', 'demo_clue_001', '新接入线索，客户为苏州精工自动化设备有限公司，主要对智能客服知识库套件有初步意向。后续已建档待首次沟通。', '100001', 1781373610000, 'WEB_FORM', 'demo_u_sales_01', NULL, 1781373610000, 1781373610000, 'admin', 'admin'),
('demo_fr_clue_002', NULL, NULL, 'CLUE', 'demo_clue_002', '与无锡智能仓储装备集团的刘总电话联系，对方表示目前正在规划客户运营自动化套件的采购预算。需要我们提供标准的产品报价和实施方案，并且希望能够了解折扣优惠空间。', '100001', 1781373800000, 'PHONE', 'demo_u_sales_02', NULL, 1781373800000, 1781373800000, 'admin', 'admin'),
('demo_fr_clue_003', NULL, NULL, 'CLUE', 'demo_clue_003', '常州精细化工的张总反馈，企业知识库治理套件非常符合其当前知识沉淀的痛点。主要要求：1. 要求 2 周内必须完成部署上线；2. 要求免费提供 3 个外部系统接口定制；3. 要求账期合理，希望分期付款。已向其初步阐述交付边界和标准账期，下周安排深入演示。', '100001', 1781374000000, 'MEETING', 'demo_u_sales_02', NULL, 1781374000000, 1781374000000, 'admin', 'admin'),
('demo_fr_clue_004', NULL, NULL, 'CLUE', 'demo_clue_006', '与盐城汽车零配件制造厂的何总沟通。客户表示由于今年整体行业预算收缩，客户运营自动化项目已无限期搁置，暂时没有采购计划，因此线索作废处理。', '100001', 1781376000000, 'PHONE', 'demo_u_sales_03', NULL, 1781376000000, 1781376000000, 'admin', 'admin');

-- 5. 插入线索的跟进计划 (Follow-up Plan)
INSERT INTO follow_up_plan (`id`, `customer_id`, `opportunity_id`, `type`, `clue_id`, `content`, `organization_id`, `owner`, `contact_id`, `estimated_time`, `method`, `status`, `converted`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
('demo_fp_clue_001', NULL, NULL, 'CLUE', 'demo_clue_001', '致电客户确认详细系统集成诉求，并确认预算情况。', '100001', 'demo_u_sales_01', NULL, 1781460000000, 'PHONE', 'PENDING', b'0', 1781373610000, 1781373610000, 'admin', 'admin'),
('demo_fp_clue_002', NULL, NULL, 'CLUE', 'demo_clue_002', '发送产品画册与方案报价，并跟进折扣与付款方案反馈。', '100001', 'demo_u_sales_02', NULL, 1781460100000, 'EMAIL', 'PENDING', b'0', 1781373800000, 1781373800000, 'admin', 'admin'),
('demo_fp_clue_003', NULL, NULL, 'CLUE', 'demo_clue_003', '进行系统知识库方案的线上产品功能演示，重点核对交付周期与定制化范围。', '100001', 'demo_u_sales_02', NULL, 1781460200000, 'ONLINE_MEETING', 'PENDING', b'0', 1781374000000, 1781374000000, 'admin', 'admin');

-- 6. 插入审批工作流元数据 (Approval Flow & Version) - 满足审批待办查询的 JOIN 关系
INSERT INTO approval_flow (`id`, `current_version_id`, `number`, `name`, `form_type`, `create_execute`, `update_execute`, `submitter_can_revoke`, `allow_batch_process`, `allow_withdraw`, `allow_add_sign`, `duplicate_approver_rule`, `require_comment`, `enable`, `deleted`, `create_time`, `update_time`, `create_user`, `update_user`, `organization_id`, `status_permissions`) VALUES
('demo_flow_quote', 'demo_flow_ver_quote', 'APV-QTE-001', '报价单特批审批流', 'quotation', b'1', b'1', b'1', b'0', b'0', b'0', 'FIRST_ONLY', b'0', b'1', b'0', 1781373600000, 1781373600000, 'admin', 'admin', '100001', '[]'),
('demo_flow_contract', 'demo_flow_ver_contract', 'APV-CTR-001', '合同审查审批流', 'contract', b'1', b'1', b'1', b'0', b'0', b'0', 'FIRST_ONLY', b'0', b'1', b'0', 1781373600000, 1781373600000, 'admin', 'admin', '100001', '[]');

INSERT INTO approval_flow_version (`id`, `flow_id`, `create_time`, `create_user`, `organization_id`) VALUES
('demo_flow_ver_quote', 'demo_flow_quote', 1781373600000, 'admin', '100001'),
('demo_flow_ver_contract', 'demo_flow_contract', 1781373600000, 'admin', '100001');

-- 7. 插入商机报价 (Opportunity Quotation)
-- 包含已通过的标准报价、待审批的高折扣风险报价和已作废/撤回的报价
INSERT INTO opportunity_quotation (`id`, `name`, `opportunity_id`, `amount`, `approval_status`, `invalid`, `until_time`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
-- 1. 华东智造-AI客服升级项目-标准9折特批报价 (金额对齐商机总金额 ¥880,000)
('demo_q_001', '华东智造-AI客服升级项目-标准9折特批报价', '392650858002653191', 880000.00, 'APPROVED', 0, 1812902400000, '100001', 1781373800000, 1781373900000, 'admin', 'admin'),
-- 2. 华东智造-AI客服升级项目-75折特大折扣申请 (金额为 ¥742,500，相对于990k标准总价为 75% 折扣，触发破线特批，审批中)
('demo_q_002', '华东智造-AI客服升级项目-75折特大折扣申请', '392650858002653191', 742500.00, 'APPROVING', 0, 1809072000000, '100001', 1781460000000, 1781460000000, 'demo_u_sales_01', 'demo_u_sales_01'),
-- 3. 华东智造-AI客服升级项目-历史撤回报价 (金额为 ¥950,000，已撤回)
('demo_q_003', '华东智造-AI客服升级项目-历史撤回报价', '392650858002653191', 950000.00, 'REVOKED', 0, 1781460000000, '100001', 1781373600000, 1781373700000, 'admin', 'admin'),
-- 4. 北方零售智能客服知识库一期-标准报价 (标准金额 ¥128,000)
('demo_q_004', '北方零售智能客服知识库一期-标准报价', '392653993328779272', 128000.00, 'APPROVED', 0, 1812902400000, '100001', 1781373800000, 1781373900000, 'admin', 'admin');

-- 8. 插入商机报价单的表单自定义属性 (Quotation Field Values) - 保持前端表单及 API 查询字段完整
INSERT INTO opportunity_quotation_field (`id`, `resource_id`, `field_id`, `field_value`, `ref_sub_id`, `row_id`, `biz_id`) VALUES
-- 对应 9折报价单 demo_q_001
('demo_qf_001_opp', 'demo_q_001', '392647267409993860', '392650858002653191', NULL, NULL, NULL), -- 关联商机
('demo_qf_001_name', 'demo_q_001', '392647267409993859', '华东智造-AI客服升级项目-标准9折特批报价', NULL, NULL, NULL), -- 报价单名称
('demo_qf_001_contact', 'demo_q_001', '392647267409993861', '392650858002653189', NULL, NULL, NULL), -- 联系人张伟
('demo_qf_001_time', 'demo_q_001', '392647267409993862', '1781373800000', NULL, NULL, NULL), -- 报价日期
('demo_qf_001_until', 'demo_q_001', '392647267409993863', '1812902400000', NULL, NULL, NULL), -- 有效期
-- 对应 75折报价单 demo_q_002
('demo_qf_002_opp', 'demo_q_002', '392647267409993860', '392650858002653191', NULL, NULL, NULL),
('demo_qf_002_name', 'demo_q_002', '392647267409993859', '华东智造-AI客服升级项目-75折特大折扣申请', NULL, NULL, NULL),
('demo_qf_002_contact', 'demo_q_002', '392647267409993861', '392650858002653189', NULL, NULL, NULL),
('demo_qf_002_time', 'demo_q_002', '392647267409993862', '1781460000000', NULL, NULL, NULL),
('demo_qf_002_until', 'demo_q_002', '392647267409993863', '1809072000000', NULL, NULL, NULL);

-- 9. 插入合同 (Contract)
-- 包含已履行的历史合同 (demo_ctr_001)、履行中的带风险合同 (demo_ctr_002) 和待签署的审批中合同 (demo_ctr_003)
INSERT INTO contract (`id`, `name`, `customer_id`, `owner`, `amount`, `number`, `approval_status`, `stage`, `start_time`, `end_time`, `void_reason`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`, `pos`) VALUES
-- 1. 已结案的历史合同 - 华东智造集团标准软件采购合同 (¥260,000, 已结案)
('demo_ctr_001', '华东智造集团标准软件采购合同', '392650858002653187', 'demo_u_sales_01', 260000.00, 'CTR-2025-001', 'APPROVED', 'ARCHIVED', 1735689600000, 1767225600000, NULL, '100001', 1735689600000, 1735689600000, 'admin', 'admin', 1),
-- 2. 履行中的合同 - 华东智造集团定制实施服务合同 (¥150,000, 履行中, 且尾款在 2026-06-15 逾期未付，财务风险)
('demo_ctr_002', '华东智造集团定制实施服务合同', '392650858002653187', 'demo_u_sales_01', 150000.00, 'CTR-2026-001', 'APPROVED', 'IN_PROGRESS', 1775011200000, 1790755200000, NULL, '100001', 1775011200000, 1775011200000, 'admin', 'admin', 2),
-- 3. 待签署/审批中的合同 - 北方零售知识库一期系统集成合同 (¥320,000, 待签署, 提审中)
('demo_ctr_003', '北方零售知识库一期系统集成合同', '392653993328779268', 'demo_u_sales_02', 320000.00, 'CTR-2026-002', 'APPROVING', 'PENDING_SIGNING', 1782842400000, 1814378400000, NULL, '100001', 1781373600000, 1781373600000, 'demo_u_sales_02', 'demo_u_sales_02', 3);

-- 10. 插入合同回款计划 (Contract Payment Plan)
-- 保证每个合同的回款之和严格等于合同总金额
INSERT INTO contract_payment_plan (`id`, `name`, `contract_id`, `owner`, `plan_status`, `plan_amount`, `plan_end_time`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
-- 历史合同 demo_ctr_001 回款已全部结清 (50%首付 + 50%尾款 = 260,000)
('demo_pay_001_1', '合同首付款', 'demo_ctr_001', 'demo_u_sales_01', 'COMPLETED', 130000.00, 1736152274000, '100001', 1735689600000, 1736152274000, 'admin', 'admin'),
('demo_pay_001_2', '合同尾款', 'demo_ctr_001', 'demo_u_sales_01', 'COMPLETED', 130000.00, 1738744274000, '100001', 1735689600000, 1738744274000, 'admin', 'admin'),
-- 正在履行合同 demo_ctr_002 (50%首付已付 + 50%尾款逾期未付 = 150,000)
-- 尾款截止日 1781373600000 为 2026-06-15，相对于系统时间 2026-06-29 已逾期 14 天
('demo_pay_002_1', '实施首付款', 'demo_ctr_002', 'demo_u_sales_01', 'COMPLETED', 75000.00, 1775616000000, '100001', 1775011200000, 1775616000000, 'admin', 'admin'),
('demo_pay_002_2', '上线验收尾款', 'demo_ctr_002', 'demo_u_sales_01', 'PENDING', 75000.00, 1781373600000, '100001', 1775011200000, 1775011200000, 'admin', 'admin'),
-- 审批中合同 demo_ctr_003 待回款 (30%首付 + 70%尾款 = 320,000)
('demo_pay_003_1', '系统集成首付款', 'demo_ctr_003', 'demo_u_sales_02', 'PENDING', 96000.00, 1783447200000, '100001', 1781373600000, 1781373600000, 'demo_u_sales_02', 'demo_u_sales_02'),
('demo_pay_003_2', '最终验收尾款', 'demo_ctr_003', 'demo_u_sales_02', 'PENDING', 224000.00, 1786039200000, '100001', 1781373600000, 1781373600000, 'demo_u_sales_02', 'demo_u_sales_02');

-- 11. 插入审批待办与流转记录 (Approval Instance & Task)
-- 制造两条真实的待办：一条报价特批待办给财务韩知夏，一条合同待办给法务顾言
INSERT INTO approval_instance (`id`, `flow_version_id`, `type`, `resource_id`, `submitter_id`, `current_node_id`, `approval_status`, `submit_time`, `approval_time`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
-- 报价审批实例
('demo_app_inst_quote', 'demo_flow_ver_quote', 'quotation', 'demo_q_002', 'demo_u_sales_01', 'demo_node_quote_finance', 'APPROVING', 1781460000000, NULL, 1781460000000, 1781460000000, 'demo_u_sales_01', 'demo_u_sales_01'),
-- 合同审批实例
('demo_app_inst_contract', 'demo_flow_ver_contract', 'contract', 'demo_ctr_003', 'demo_u_sales_02', 'demo_node_contract_legal', 'APPROVING', 1781373600000, NULL, 1781373600000, 1781373600000, 'demo_u_sales_02', 'demo_u_sales_02');

INSERT INTO approval_task (`id`, `node_id`, `node_round`, `instance_id`, `approver_id`, `status`, `type`, `action`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
-- 财务韩知夏的报价特批待办
('demo_app_task_quote', 'demo_node_quote_finance', 1, 'demo_app_inst_quote', 'demo_u_finance', 'APPROVING', 'NL', NULL, 1781460000000, 1781460000000, 'demo_u_sales_01', 'demo_u_sales_01'),
-- 法务顾言的合同待签署审核待办
('demo_app_task_contract', 'demo_node_contract_legal', 1, 'demo_app_inst_contract', 'demo_u_legal', 'APPROVING', 'NL', NULL, 1781373600000, 1781373600000, 'demo_u_sales_02', 'demo_u_sales_02');

-- 12. 插入审批流节点和节点审批人配置，解决 NullPointerException 缺口
INSERT INTO approval_node (`id`, `flow_version_id`, `number`, `name`, `node_type`, `sort`) VALUES
('demo_node_quote_finance', 'demo_flow_ver_quote', 'PN001', '财务特批节点', 'DEFAULT', 10),
('demo_node_contract_legal', 'demo_flow_ver_contract', 'PN001', '法务审查节点', 'DEFAULT', 10);

INSERT INTO approval_node_approver (`id`, `flow_version_id`, `approval_type`, `multi_approver_mode`, `empty_approver_action`, `fallback_approver`, `same_submitter_action`, `approver_type`, `approver_direction`, `cc_type`, `cc_direction`, `cc_list`, `approver_list`, `pass_post_config`, `reject_post_config`, `field_permissions`) VALUES
('demo_node_quote_finance', 'demo_flow_ver_quote', 'MANUAL', 'ANY', 'AUTO_PASS', NULL, 'SKIP', 'MEMBER', 'BOTTOM_UP', NULL, 'BOTTOM_UP', NULL, '["demo_u_finance"]', NULL, NULL, NULL),
('demo_node_contract_legal', 'demo_flow_ver_contract', 'MANUAL', 'ANY', 'AUTO_PASS', NULL, 'SKIP', 'MEMBER', 'BOTTOM_UP', NULL, 'BOTTOM_UP', NULL, '["demo_u_legal"]', NULL, NULL, NULL);
