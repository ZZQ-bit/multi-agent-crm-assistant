import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const currentDir = dirname(fileURLToPath(import.meta.url));
const componentSource = readFileSync(join(currentDir, 'ChartFence.vue'), 'utf8');

assert.ok(
  componentSource.includes('deal-desk-funnel'),
  'ChartFence should render a dedicated 多Agent智能助手 funnel surface instead of only delegating funnel charts to the generic CRM chart component'
);

assert.ok(
  componentSource.includes('isDealDeskFunnel'),
  'ChartFence should branch funnel charts into a 多Agent智能助手-specific rendering path'
);

assert.ok(
  componentSource.includes('funnelSegments'),
  'ChartFence should compute dedicated funnel segment view models so stage order and labels stay readable'
);

assert.ok(
  componentSource.includes('deal-desk-funnel-svg'),
  'ChartFence should render funnel charts through a dedicated SVG funnel so the visual result reads as a continuous funnel instead of disconnected slanted bars'
);

assert.ok(
  componentSource.includes('polygonPoints'),
  'ChartFence should compute polygon points for each funnel segment so adjacent stages share edges and form a true funnel silhouette'
);

console.log('ChartFence smoke test passed');
