import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const currentDir = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(currentDir, 'login-form.vue'), 'utf8');

assert.match(source, /import\s*\{[^}]*hasToken[^}]*\}\s*from\s*['"]@lib\/shared\/method\/auth['"]/);
assert.match(
  source,
  /if\s*\(\s*!hasToken\(\)\s*\)\s*\{[\s\S]*?preheat\.value\s*=\s*false/,
  'login page should not call isLogin when no local token exists'
);
assert.match(
  source,
  /preheat\.value\s*=\s*await\s+userStore\.isLogin\(true\)/,
  'login page should still silently recover valid existing sessions'
);
assert.match(source, /if\s*\(\s*!preheat\.value\s*\)\s*\{[\s\S]*?clearToken\(\)/);
assert.match(
  source,
  /loadPublicKey:\s*async\s*\(\)\s*=>\s*await\s+appStore\.initPublicKey\(\)/,
  'login submit should always fetch the latest public key from the backend'
);

console.log('loginFormInitialization smoke test passed');
