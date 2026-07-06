import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const currentDir = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(currentDir, 'App.vue'), 'utf8');

assert.match(source, /const\s+isWhiteListRoute\s*=\s*\(\)\s*=>/);
assert.match(
  source,
  /onBeforeMount\(async\s*\(\)\s*=>\s*\{[\s\S]*?if\s*\(\s*isWhiteListRoute\(\)\s*\)\s*\{\s*return;\s*\}/,
  'App initialization should skip protected-resource bootstrap on white-list routes like /login'
);
assert.match(source, /appStore\.initThirdPartyResource\(\)/);
assert.match(source, /await\s+licenseStore\.getValidateLicense\(\)/);

console.log('appInitialization smoke test passed');
