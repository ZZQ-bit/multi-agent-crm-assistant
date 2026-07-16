import assert from 'node:assert/strict';

import { closeSmokeServer, loadSmokeModule } from '../smokeViteLoader.mjs';

const { buildProcessSummaryText, normalizeProcessEvents } = await loadSmokeModule(
  'src/views/ai-deal-desk/adapters/dealDeskProcessFallback.ts'
);

const oneRunning = normalizeProcessEvents([
  { id: 'task_planner', type: 'task_understanding', status: 'running', text: '正在理解本轮任务' },
]);
assert.equal(buildProcessSummaryText(oneRunning), '正在处理：理解任务');

const parallelRunning = normalizeProcessEvents([
  { id: 'sales_agent', type: 'sales_analysis', status: 'running', text: '正在进行销售分析' },
  { id: 'finance_agent', type: 'finance_analysis', status: 'running', text: '正在进行财务分析' },
  { id: 'delivery_agent', type: 'delivery_analysis', status: 'running', text: '正在进行交付分析' },
  { id: 'legal_agent', type: 'legal_analysis', status: 'running', text: '正在进行合同分析' },
]);
assert.equal(parallelRunning.length, 4);
assert.equal(buildProcessSummaryText(parallelRunning), '正在并行处理 4 项：销售分析、财务分析、交付分析等');

const partiallyCompleted = normalizeProcessEvents([
  { id: 'task_planner', type: 'task_understanding', status: 'completed', text: '已理解本轮任务' },
  { id: 'crm_read_request', type: 'crm_retrieval', status: 'completed', text: '已读取 CRM 资料' },
  { id: 'sales_agent', type: 'sales_analysis', status: 'running', text: '正在进行销售分析' },
  { id: 'finance_agent', type: 'finance_analysis', status: 'running', text: '正在进行财务分析' },
]);
assert.equal(buildProcessSummaryText(partiallyCompleted), '已完成 2 项，正在处理 2 项');

const sameTypeDifferentNodes = normalizeProcessEvents([
  { id: 'answer-a', type: 'answer_generation', status: 'running', text: '正在整理回答' },
  { id: 'answer-b', type: 'answer_generation', status: 'running', text: '正在整理回答' },
]);
assert.equal(sameTypeDifferentNodes.length, 2);

const updatedById = normalizeProcessEvents([
  { id: 'sales_agent', type: 'sales_analysis', status: 'running', text: '正在进行销售分析' },
  { id: 'sales_agent', type: 'sales_analysis', status: 'completed', text: '已完成销售分析' },
]);
assert.equal(updatedById.length, 1);
assert.equal(updatedById[0].status, 'completed');
assert.equal(buildProcessSummaryText(updatedById), '已完成 1 项处理');

const failed = normalizeProcessEvents([
  { id: 'task_planner', type: 'task_understanding', status: 'completed', text: '已理解本轮任务' },
  {
    id: 'deal_rules_knowledge',
    type: 'knowledge_retrieval',
    status: 'failed',
    text: '业务规则检索失败',
  },
]);
assert.equal(failed[1].text, '业务规则检索失败');
assert.equal(buildProcessSummaryText(failed), '已完成 1 项，1 项异常');

const businessAnswer = normalizeProcessEvents([
  { id: 'business_answer_agent', type: 'answer_generation', status: 'running', text: '正在汇总结论' },
  { id: 'business_answer_agent', type: 'answer_generation', status: 'completed', text: '已生成结论' },
]);
assert.equal(businessAnswer[0].text, '已生成结论');

await closeSmokeServer();

console.log('dealDeskProcessFallback smoke test passed');
