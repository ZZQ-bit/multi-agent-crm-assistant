import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

import { closeSmokeServer, loadSmokeModule } from './smokeViteLoader.mjs';

const { createAnswerDeltaScheduler } = await loadSmokeModule(
  'src/views/ai-deal-desk/answerDeltaScheduler.ts'
);

const splitDeltas = [];
const splitScheduler = createAnswerDeltaScheduler(
  (text) => splitDeltas.push(text),
  undefined,
  { chunkSize: 4, intervalMs: 0 }
);
splitScheduler.enqueue('甲乙😀丁戊己庚辛壬');
await splitScheduler.flush();
splitScheduler.dispose();

assert.deepEqual(splitDeltas, ['甲乙😀丁', '戊己庚辛', '壬']);
assert.equal(splitDeltas.join(''), '甲乙😀丁戊己庚辛壬');

const burstDeltas = [];
const burstScheduler = createAnswerDeltaScheduler(
  (text) => burstDeltas.push(text),
  undefined,
  { chunkSize: 100, intervalMs: 1 }
);
burstScheduler.enqueue('第一段');
burstScheduler.enqueue('第二段');
await burstScheduler.flush();
burstScheduler.dispose();

assert.deepEqual(burstDeltas, ['第一段', '第二段']);

const abortController = new AbortController();
const abortedDeltas = [];
const abortScheduler = createAnswerDeltaScheduler(
  (text) => abortedDeltas.push(text),
  abortController.signal,
  { chunkSize: 2, intervalMs: 20 }
);
abortScheduler.enqueue('一二三四五六');
await Promise.resolve();
abortController.abort();
await abortScheduler.flush();
abortScheduler.dispose();

assert.deepEqual(abortedDeltas, ['一二']);

const apiSource = await readFile(new URL('./api.ts', import.meta.url), 'utf8');
assert.match(apiSource, /answerDeltaScheduler\.enqueue\(frame\.text/);
assert.match(
  apiSource,
  /if \(!finalPayload\)[\s\S]*await answerDeltaScheduler\.flush\(\);[\s\S]*return finalPayload;/
);
assert.match(apiSource, /finally \{\s*answerDeltaScheduler\.dispose\(\);\s*\}/);

await closeSmokeServer();

console.log('deal desk streaming API smoke test passed');
