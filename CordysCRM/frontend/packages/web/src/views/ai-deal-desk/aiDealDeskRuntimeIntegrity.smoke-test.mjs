import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const viewRoot = join(root, 'src/views/ai-deal-desk');

function read(relativePath) {
  return readFileSync(join(viewRoot, relativePath), 'utf8');
}

assert.equal(existsSync(join(viewRoot, 'hooks/useAiDealDeskChat.ts')), true);

const indexSource = read('index.vue');
assert.equal(indexSource.includes('useAiDealDeskMock'), false);
assert.equal(indexSource.includes('selectCandidate'), false);
assert.equal(indexSource.includes('@retry="retryTurn"'), true);

const chatHookSource = read('hooks/useAiDealDeskChat.ts');
assert.equal(chatHookSource.includes('../mock'), false);
assert.equal(chatHookSource.includes('dealDeskMockProvider'), false);
assert.equal(chatHookSource.includes('fallbackProvider'), false);
assert.equal(chatHookSource.includes('resolveTextCandidateSelection'), false);
assert.equal(chatHookSource.includes('activeGeneration'), true);
assert.equal(chatHookSource.includes('stopCurrentGeneration'), true);
assert.equal(chatHookSource.includes('retryTurn'), true);
assert.equal(chatHookSource.includes('runAssistantGeneration'), true);
assert.equal(chatHookSource.includes('isRetryableFailedTurn'), true);

const conversationTurnSource = read('components/ConversationTurn.vue');
assert.equal(conversationTurnSource.includes('ObjectSelectBlock'), false);
assert.equal(conversationTurnSource.includes('selectCandidate'), false);
assert.equal(conversationTurnSource.includes('processSignature'), true);
assert.equal(conversationTurnSource.includes('props.turn.process?.events.length'), true);
assert.equal(conversationTurnSource.includes('turn-copy-button'), true);
assert.equal(conversationTurnSource.includes('turn-actions'), true);
assert.equal(conversationTurnSource.includes('turn-actions--user'), true);
assert.equal(conversationTurnSource.includes('turn-actions--assistant'), true);
assert.equal(conversationTurnSource.includes('turn-copy-button--user'), false);
assert.equal(conversationTurnSource.includes('turn-copy-button--assistant'), false);
assert.equal(conversationTurnSource.includes('copyTurnText'), true);
assert.equal(conversationTurnSource.includes("e: 'retry'"), true);
assert.equal(conversationTurnSource.includes('canRetryTurn'), true);
assert.equal(conversationTurnSource.includes('未返回有效最终回复'), true);
assert.equal(conversationTurnSource.includes('turn-retry-button'), true);
assert.equal(conversationTurnSource.includes('iconicon_refresh'), true);
assert.equal(conversationTurnSource.includes('useLegacyCopy'), true);
assert.equal(conversationTurnSource.includes('iconicon_file_copy'), true);
assert.equal(conversationTurnSource.includes(':deep(.turn-action-button *)'), true);
assert.equal(conversationTurnSource.includes('pointer-events: none'), true);
assert.equal(conversationTurnSource.includes('z-index: 1'), true);
assert.equal(conversationTurnSource.includes('.assistant-block:hover .turn__time'), true);

const apiSource = read('api.ts');
assert.equal(apiSource.includes('stopDealDeskChat'), true);
assert.equal(apiSource.includes('AbortSignal'), true);
assert.equal(apiSource.includes('onTaskId'), true);
assert.equal(apiSource.includes("import { getToken } from '@lib/shared/method/auth'"), true);
assert.equal(apiSource.includes('function buildDealDeskApiUrl'), true);
assert.equal(apiSource.includes('import.meta.env.VITE_API_BASE_URL'), true);

const composerSource = read('components/ComposerPanel.vue');
assert.equal(composerSource.includes('isResponding'), true);
assert.equal(composerSource.includes("e: 'stop'"), true);
assert.equal(composerSource.includes('StopCircleOutline'), true);

const pendingTurnSource = read('hooks/useAiDealDeskPendingTurn.ts');
const chatflowAdapterSource = read('adapters/dealDeskChatflowAdapter.ts');
assert.equal(chatHookSource.includes("time: '刚刚'"), false);
assert.equal(pendingTurnSource.includes("time: '刚刚'"), false);
assert.equal(chatflowAdapterSource.includes("time: '刚刚'"), false);
assert.equal(chatHookSource.includes('formatDealDeskClockTime'), true);
assert.equal(chatHookSource.includes('formatDealDeskSessionTime'), true);
assert.equal(pendingTurnSource.includes('formatDealDeskClockTime'), true);
assert.equal(chatflowAdapterSource.includes('formatDealDeskClockTime'), true);

const timeSource = read('dealDeskTime.ts');
assert.equal(timeSource.includes('formatDealDeskSessionTime'), true);
assert.equal(timeSource.includes('今天 ${clockTime}'), true);
assert.equal(timeSource.includes('昨天 ${clockTime}'), true);
assert.equal(timeSource.includes('${month}-${day} ${clockTime}'), true);

const typesSource = read('types.ts');
assert.equal(typesSource.includes("'failed'"), true);

console.log('aiDealDeskRuntimeIntegrity smoke test passed');
