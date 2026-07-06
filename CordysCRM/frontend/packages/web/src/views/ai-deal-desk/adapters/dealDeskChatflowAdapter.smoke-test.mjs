import assert from 'node:assert/strict';

import { closeSmokeServer, loadSmokeModule } from '../smokeViteLoader.mjs';

const { default: adaptChatflowPayloadToAssistantReply } = await loadSmokeModule(
  'src/views/ai-deal-desk/adapters/dealDeskChatflowAdapter.ts'
);

const analysisReply = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'text_analysis',
  answerText: '**Risk is high.**',
  processEvents: [
    { id: 'event-task', type: 'task_identified', status: 'completed', text: 'task done' },
    { id: 'event-context', type: 'context_loaded', status: 'completed', text: 'context done' },
    { id: 'event-rule', type: 'rule_checked', status: 'completed', text: 'rule done' },
    { id: 'event-risk', type: 'risk_found', status: 'warning', text: 'risk found' },
    { id: 'event-suggest', type: 'suggestion_generated', status: 'completed', text: 'suggestion done' },
  ],
});

assert.equal(analysisReply.kind, 'analysis');
assert.equal(analysisReply.turn.process?.events.length, 5);
assert.deepEqual(
  analysisReply.turn.process?.events.map((event) => event.type),
  ['task_identified', 'context_loaded', 'rule_checked', 'risk_found', 'suggestion_generated']
);

const deprecatedObjectSelectReply = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'object_select',
  answerText: 'Multiple opportunities matched. Please type the full opportunity name to continue.',
  objectSelect: {
    objectType: 'opportunity',
    prompt: 'Select an opportunity',
    originalQuery: 'show risks',
    candidates: [
      {
        id: 'opportunity-1',
        name: 'East Manufacturing Expansion',
        customerName: 'East Manufacturing',
        amountText: 'CNY 960,000',
        stageName: 'Commercial negotiation',
        ownerName: 'Li Ming',
        latestFollowSummary: 'Customer is comparing payment terms.',
      },
    ],
  },
});

assert.equal(deprecatedObjectSelectReply.kind, 'quick-answer');
assert.equal(deprecatedObjectSelectReply.turn.objectSelect, undefined);
assert.match(deprecatedObjectSelectReply.turn.text, /full opportunity name/);

const writebackResultReply = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'writeback_result',
  answerText: 'The follow-up record was saved.',
  writeback: {
    id: 'writeback-1',
    type: 'follow_record',
    status: 'confirmed',
    target: {
      customerName: 'East Manufacturing',
      opportunityName: 'East Manufacturing Expansion',
    },
  },
});

assert.equal(writebackResultReply.kind, 'writeback-success');
assert.equal(writebackResultReply.writeback?.status, 'confirmed');

const integrationReplyWithJsonLikeAnswerText = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'writeback_confirm',
  answerText: [
    '请确认以下内容是否按 CRM 字段写入：',
    '',
    '```json',
    '{',
    '  "writeback": {',
    '    "id": "body-writeback-id",',
    '    "status": "confirmed",',
    '    "target": {',
    '      "customerName": "正文里的客户",',
    '      "opportunityName": "正文里的商机"',
    '    },',
    '    "recordDraft": {',
    '      "content": "正文里的跟进内容"',
    '    }',
    '  },',
    '  "boundObject": {',
    '    "objectType": "opportunity",',
    '    "objectId": "body-opportunity-id",',
    '    "objectName": "正文里的商机"',
    '  }',
    '}',
    '```',
  ].join('\n'),
  processEvents: [
    { id: 'integration-task', type: 'task_identified', status: 'completed', text: '主 Agent - 路由判断' },
    { id: 'integration-context', type: 'context_loaded', status: 'completed', text: 'tool_call success' },
    { id: 'integration-confirm', type: 'confirmation_required', status: 'running', text: 'workflow variable update' },
  ],
  writeback: {
    id: 'payload-writeback-id',
    type: 'follow_record',
    status: 'awaiting_confirm',
    target: {
      customerId: 'customer-payload-1',
      customerName: 'Payload 客户',
      opportunityId: 'opportunity-payload-1',
      opportunityName: 'Payload 商机',
    },
    recordDraft: {
      followMethod: '电话',
      content: 'Payload 跟进内容',
    },
  },
  boundObject: {
    objectType: 'opportunity',
    objectId: 'opportunity-payload-1',
    objectName: 'Payload 商机',
    customerId: 'customer-payload-1',
    customerName: 'Payload 客户',
    source: 'auto_detected',
  },
  warnings: ['TECH_FALLBACK: process_events_json missing, using compatibility path'],
});

assert.equal(integrationReplyWithJsonLikeAnswerText.kind, 'writeback-confirm');
assert.match(integrationReplyWithJsonLikeAnswerText.turn.text, /body-writeback-id/);
assert.match(integrationReplyWithJsonLikeAnswerText.turn.text, /正文里的跟进内容/);
assert.equal(integrationReplyWithJsonLikeAnswerText.writeback?.id, 'payload-writeback-id');
assert.equal(integrationReplyWithJsonLikeAnswerText.writeback?.status, 'awaiting_confirm');
assert.equal(integrationReplyWithJsonLikeAnswerText.writeback?.target.customerName, 'Payload 客户');
assert.equal(integrationReplyWithJsonLikeAnswerText.writeback?.recordDraft?.content, 'Payload 跟进内容');
assert.notEqual(integrationReplyWithJsonLikeAnswerText.writeback?.id, 'body-writeback-id');
assert.equal(integrationReplyWithJsonLikeAnswerText.boundObject?.objectId, 'opportunity-payload-1');
assert.equal(integrationReplyWithJsonLikeAnswerText.boundObject?.objectName, 'Payload 商机');
assert.notEqual(integrationReplyWithJsonLikeAnswerText.boundObject?.objectId, 'body-opportunity-id');
assert.equal(integrationReplyWithJsonLikeAnswerText.turn.process?.summary.includes('TECH_FALLBACK'), false);
assert.equal(integrationReplyWithJsonLikeAnswerText.turn.text.includes('TECH_FALLBACK'), false);
assert.deepEqual(
  integrationReplyWithJsonLikeAnswerText.turn.process?.events.map((event) => event.text),
  ['已识别本轮任务', '已读取相关业务资料', '等待你确认下一步操作']
);
assert.equal(
  integrationReplyWithJsonLikeAnswerText.turn.process?.events.some((event) => event.text.includes('TECH_FALLBACK')),
  false
);

const quickAnswerWithEmptyProtocolObjects = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'quick_answer',
  answerText: 'hello',
  processEvents: [],
  objectSelect: {},
  writeback: {},
  boundObject: {},
});

assert.equal(quickAnswerWithEmptyProtocolObjects.kind, 'quick-answer');
assert.equal(quickAnswerWithEmptyProtocolObjects.turn.objectSelect, undefined);
assert.equal(quickAnswerWithEmptyProtocolObjects.writeback, undefined);
assert.equal(quickAnswerWithEmptyProtocolObjects.boundObject, undefined);
assert.equal(quickAnswerWithEmptyProtocolObjects.turn.process, undefined);

const legacyObjectSelectWithCandidatesReply = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'object_select',
  answerText: [
    '匹配到多个商机，请直接回复完整商机名称：',
    '',
    '- 华东智造集团AI客服升级项目',
    '- 华东智造集团售后知识库项目',
  ].join('\n'),
  objectSelect: {
    objectType: 'opportunity',
    prompt: 'Select an opportunity',
    originalQuery: '华东智造',
    candidates: [
      {
        id: 'legacy-opportunity-1',
        name: '华东智造集团AI客服升级项目',
        customerName: '华东智造集团',
      },
      {
        id: 'legacy-opportunity-2',
        name: '华东智造集团售后知识库项目',
        customerName: '华东智造集团',
      },
    ],
  },
});

assert.equal(legacyObjectSelectWithCandidatesReply.kind, 'quick-answer');
assert.equal(legacyObjectSelectWithCandidatesReply.turn.objectSelect, undefined);
assert.match(legacyObjectSelectWithCandidatesReply.turn.text, /华东智造集团AI客服升级项目/);
assert.match(legacyObjectSelectWithCandidatesReply.turn.text, /华东智造集团售后知识库项目/);

const processWithHiddenMemoryEvent = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'text_analysis',
  answerText: 'hello',
  processEvents: [
    { id: 'event-memory', type: 'memory_used', status: 'completed', text: 'memory used' },
    { id: 'event-task', type: 'task_identified', status: 'completed', text: 'task done' },
  ],
});

assert.equal(processWithHiddenMemoryEvent.turn.process?.events.length, 1);
assert.equal(processWithHiddenMemoryEvent.turn.process?.events[0]?.type, 'task_identified');
assert.equal(processWithHiddenMemoryEvent.turn.process?.summary.includes('会话记忆'), false);

const quickAnswerWithBusinessSummaryLabels = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'text_analysis',
  answerText: 'hello',
  processEvents: [
    { id: 'quick-task', type: 'task_identified', status: 'completed', text: '主 Agent - 路由判断' },
    { id: 'quick-context', type: 'context_loaded', status: 'completed', text: 'tool_call success' },
    { id: 'quick-suggest', type: 'suggestion_generated', status: 'completed', text: 'LLM node completed' },
  ],
});

assert.equal(
  quickAnswerWithBusinessSummaryLabels.turn.process?.summary,
  '已完成 3 项检查：任务识别、资料读取、建议生成'
);
assert.deepEqual(
  quickAnswerWithBusinessSummaryLabels.turn.process?.events.map((event) => event.text),
  ['已识别本轮任务', '已读取相关业务资料', '已生成结论和下一步建议']
);

const quickAnswerWithWorkflowProcessEvents = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'quick_answer',
  answerText: 'hello',
  processEvents: [
    { id: 'quick-task-2', type: 'task_identified', status: 'completed', text: 'workflow route' },
    { id: 'quick-context-2', type: 'context_loaded', status: 'running', text: 'tool planning' },
    { id: 'quick-suggest-2', type: 'suggestion_generated', status: 'completed', text: 'light answer' },
  ],
});

assert.equal(quickAnswerWithWorkflowProcessEvents.turn.process, undefined);

const failedReply = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'failed',
  answerText: '多Agent智能助手 未返回有效最终回复。',
  processEvents: [
    { id: 'failed-event', type: 'failed', status: 'failed', text: 'workflow failed' },
  ],
});

assert.equal(failedReply.turn.status, 'failed');
assert.equal(failedReply.turn.text, '多Agent智能助手 未返回有效最终回复。');

const processWithDuplicateStatuses = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'text_analysis',
  answerText: 'hello',
  processEvents: [
    { id: 'rule-running', type: 'rule_checked', status: 'running', text: 'running' },
    { id: 'rule-completed', type: 'rule_checked', status: 'completed', text: 'completed' },
    { id: 'rule-warning', type: 'rule_checked', status: 'warning', text: 'warning' },
    { id: 'context-failed', type: 'context_loaded', status: 'failed', text: 'failed' },
    { id: 'context-completed', type: 'context_loaded', status: 'completed', text: 'completed' },
  ],
});

assert.deepEqual(
  processWithDuplicateStatuses.turn.process?.events.map((event) => [event.type, event.status]),
  [
    ['rule_checked', 'warning'],
    ['context_loaded', 'failed'],
  ]
);

const processWithUnknownTechnicalEvent = adaptChatflowPayloadToAssistantReply({
  protocolVersion: '1.0',
  turnType: 'text_analysis',
  answerText: 'hello',
  processEvents: [
    { id: 'unknown-tool-call', type: 'tool_call', status: 'completed', text: 'tool_call success' },
    { id: 'known-task', type: 'task_identified', status: 'completed', text: 'workflow route' },
  ],
});

assert.equal(processWithUnknownTechnicalEvent.turn.process?.events.length, 1);
assert.equal(processWithUnknownTechnicalEvent.turn.process?.events[0]?.type, 'task_identified');
assert.equal(processWithUnknownTechnicalEvent.turn.process?.events[0]?.text, '已识别本轮任务');

await closeSmokeServer();

console.log('dealDeskChatflowAdapter smoke test passed');
