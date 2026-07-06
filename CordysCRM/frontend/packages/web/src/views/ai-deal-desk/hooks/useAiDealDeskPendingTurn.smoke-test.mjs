import assert from 'node:assert/strict';

import { closeSmokeServer, loadSmokeModule } from '../smokeViteLoader.mjs';

const { buildResolvedAssistantTurn, createPendingAssistantTurn } = await loadSmokeModule(
  'src/views/ai-deal-desk/hooks/useAiDealDeskPendingTurn.ts'
);

const pendingTurn = createPendingAssistantTurn('assistant-pending-1', null);

assert.equal(pendingTurn.status, 'generating');
assert.equal(pendingTurn.process, undefined);
assert.match(pendingTurn.time, /^\d{2}:\d{2}$/);

const resolvedTurn = buildResolvedAssistantTurn(pendingTurn, {
  time: 'just now',
  text: 'final reply',
  process: {
    summary: 'formal process',
    expanded: false,
    events: [
      { id: 'resolved-1', type: 'task_identified', status: 'completed', text: 'task done' },
      { id: 'resolved-2', type: 'context_loaded', status: 'completed', text: 'context done' },
      { id: 'resolved-3', type: 'suggestion_generated', status: 'completed', text: 'suggestion done' },
    ],
  },
});

assert.equal(resolvedTurn.id, 'assistant-pending-1');
assert.equal(resolvedTurn.role, 'assistant');
assert.equal(resolvedTurn.status, 'default');
assert.equal(resolvedTurn.text, 'final reply');
assert.equal(resolvedTurn.process?.summary, 'formal process');

const pendingTurnWithLiveProcess = createPendingAssistantTurn('assistant-pending-2', null);
pendingTurnWithLiveProcess.process = {
  summary: 'streamed process',
  expanded: true,
  events: [
    { id: 'stream-task', type: 'task_identified', status: 'completed', text: 'task done' },
    { id: 'stream-suggest', type: 'suggestion_generated', status: 'running', text: 'suggestion running' },
    { id: 'stream-context', type: 'context_loaded', status: 'completed', text: 'context done' },
    { id: 'stream-rule', type: 'rule_checked', status: 'completed', text: 'rule done' },
  ],
};

const resolvedQuickAnswerWithOnlyStreamProcess = buildResolvedAssistantTurn(pendingTurnWithLiveProcess, {
  time: 'just now',
  text: 'final reply',
});

assert.equal(resolvedQuickAnswerWithOnlyStreamProcess.process, undefined);

const pendingTurnWithFinalReplyProcess = createPendingAssistantTurn('assistant-pending-3', null);
const resolvedTurnWithFinalRunningProcess = buildResolvedAssistantTurn(pendingTurnWithFinalReplyProcess, {
  time: 'just now',
  text: 'final reply',
  process: {
    summary: 'formal process',
    expanded: true,
    events: [
      { id: 'reply-task', type: 'task_identified', status: 'completed', text: 'task done' },
      { id: 'reply-context', type: 'context_loaded', status: 'running', text: 'context running' },
      { id: 'reply-suggest', type: 'suggestion_generated', status: 'completed', text: 'suggestion done' },
    ],
  },
});

assert.equal(resolvedTurnWithFinalRunningProcess.process?.expanded, false);
assert.deepEqual(
  resolvedTurnWithFinalRunningProcess.process?.events.map((event) => event.status),
  ['completed', 'completed', 'completed']
);
assert.equal(resolvedTurnWithFinalRunningProcess.process?.summary.includes('正在'), false);

await closeSmokeServer();

console.log('useAiDealDeskPendingTurn smoke test passed');
