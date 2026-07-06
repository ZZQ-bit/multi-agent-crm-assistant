import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const currentDir = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(currentDir, 'index.ts'), 'utf8');

assert.match(
  source,
  /if\s*\(\s*sessionId\s*&&\s*csrfToken\s*&&\s*\(config as Recordable\)\?\.requestOptions\?\.withToken !== false\s*\)/,
  'request interceptor should only attach auth headers when both sessionId and csrfToken exist'
);

console.log('authHeaders smoke test passed');
