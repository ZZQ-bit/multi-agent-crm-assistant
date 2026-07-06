import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const currentDir = dirname(fileURLToPath(import.meta.url));
const constantsSource = readFileSync(join(currentDir, 'constants.ts'), 'utf8');

assert.match(constantsSource, /LOGIN_ROUTE\s*=\s*\{[\s\S]*name:\s*['"]login['"][\s\S]*path:\s*['"]\/login['"][\s\S]*\}/);
assert.match(
  constantsSource,
  /WHITE_LIST\s*=\s*\[[^\]]*LOGIN_ROUTE[^\]]*\]/,
  'login route should be part of WHITE_LIST so auth-only initialization is skipped on the login page'
);

console.log('auth boundary smoke test passed');
