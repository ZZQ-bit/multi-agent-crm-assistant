import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const source = readFileSync(join(process.cwd(), 'src/views/ai-deal-desk/hooks/useAiDealDeskChat.ts'), 'utf8');

assert.match(source, /sessionId:\s*string;/, 'each generation must be tied to the session that started it');
assert.match(
  source,
  /const activeSessionGeneration = computed\(\(\) =>\s*activeGenerations\.value\.find\(\(generation\) => generation\.sessionId === activeSessionId\.value\)\s*\)/,
  'the hook should derive the visible generation from the active session id'
);
assert.match(
  source,
  /const isResponding = computed\(\(\) => Boolean\(activeSessionGeneration\.value\)\)/,
  'the visible composer should only show stop state for the active session generation'
);
assert.match(
  source,
  /function updateSessionById\(sessionId: string, mutator: \(session: DealDeskSession\) => void\)/,
  'stream callbacks must update the originating session by id instead of the currently active session'
);
assert.equal(
  source.includes('onAnswerDelta(delta, meta) {\n          updateSession((currentSession) => {'),
  false,
  'answer deltas from an old request must not be appended to whichever session is currently active'
);

console.log('useAiDealDeskChat session isolation smoke test passed');
