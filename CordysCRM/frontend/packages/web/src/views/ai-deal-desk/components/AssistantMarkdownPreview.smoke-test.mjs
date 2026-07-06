import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const currentDir = dirname(fileURLToPath(import.meta.url));
const componentSource = readFileSync(join(currentDir, 'AssistantMarkdownPreview.vue'), 'utf8');

assert.ok(
  componentSource.includes('renderDealDeskMarkdown'),
  'AssistantMarkdownPreview should render through the shared markdown-it sanitizer pipeline'
);
assert.ok(
  componentSource.includes('v-html="displayHtml"'),
  'AssistantMarkdownPreview should bind sanitized rendered HTML instead of passing raw markdown to MdPreview'
);
assert.ok(
  !componentSource.includes('md-editor-v3'),
  'AssistantMarkdownPreview should not depend on md-editor-v3 for 多Agent智能助手 read-only markdown rendering'
);
assert.ok(
  !componentSource.includes('<MdPreview'),
  'AssistantMarkdownPreview should not render the MdPreview component'
);
assert.ok(
  componentSource.includes("querySelectorAll<HTMLElement>('.chart-fence-placeholder')"),
  'AssistantMarkdownPreview should mount chart fence placeholders after markdown render'
);
assert.ok(
  componentSource.includes('createApp({ render: () => h(ChartFence, { chartData }) })'),
  'AssistantMarkdownPreview should mount ChartFence components for parsed chart placeholders'
);
assert.ok(
  componentSource.includes('onMounted(() => {') && componentSource.includes('nextTick(mountCharts)'),
  'AssistantMarkdownPreview should mount chart fences on initial render so the first completed assistant turn can show charts'
);
assert.ok(
  componentSource.includes('@click="handleMarkdownClick"'),
  'AssistantMarkdownPreview should delegate clicks from sanitized markdown controls'
);
assert.ok(
  componentSource.includes("closest<HTMLButtonElement>('[data-deal-desk-copy-code]')"),
  'AssistantMarkdownPreview should handle copy buttons rendered inside markdown HTML'
);
assert.ok(
  componentSource.includes("querySelector<HTMLElement>('pre code')"),
  'AssistantMarkdownPreview should copy the code element text instead of storing code in HTML attributes'
);
assert.match(
  componentSource,
  /:deep\(\.assistant-markdown h1\)\s*\{[\s\S]*?font-size:\s*16px;/,
  'AssistantMarkdownPreview should keep top-level markdown headings compact in chat bubbles'
);
assert.match(
  componentSource,
  /:deep\(\.assistant-markdown h2\)\s*\{[\s\S]*?font-size:\s*15px;/,
  'AssistantMarkdownPreview should render second-level markdown headings close to body text size'
);
assert.match(
  componentSource,
  /:deep\(\.assistant-markdown h3\)\s*\{[\s\S]*?font-size:\s*14px;/,
  'AssistantMarkdownPreview should avoid oversized nested markdown headings'
);

console.log('AssistantMarkdownPreview smoke test passed');
