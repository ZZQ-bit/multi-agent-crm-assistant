import assert from 'node:assert/strict';

import {
  buildProgressiveFrames,
  normalizeDealDeskMarkdownSource,
  repairStreamingMarkdown,
  renderDealDeskMarkdown,
} from './dealDeskMarkdown.mjs';

const standardMarkdown = `# Title

This is **bold**, *italic*, and [link](https://example.com)

- **客户名称**：华东智造集团
- **付款条件**：待确认

| Item | Result |
| --- | --- |
| Amount | 120 |

\`\`\`js
console.log('##3.1 should stay code');
\`\`\`
`;

const html = renderDealDeskMarkdown(standardMarkdown);
assert.match(html, /<h1[^>]*>Title<\/h1>/);
assert.match(html, /<strong>bold<\/strong>/);
assert.match(html, /<em>italic<\/em>/);
assert.match(html, /<a[^>]*href="https:\/\/example.com"[^>]*>link<\/a>/);
assert.match(html, /<a[^>]*target="_blank"[^>]*>/);
assert.match(html, /<a[^>]*rel="noreferrer noopener"[^>]*>/);
assert.match(html, /<li><strong>客户名称<\/strong>：华东智造集团<\/li>/);
assert.match(html, /<table[\s>]/);
assert.match(html, /<pre[\s>]/);
assert.match(html, /deal-desk-code-block/);
assert.match(html, /deal-desk-code-block__language[^>]*>js<\/span>/);
assert.match(html, /data-deal-desk-copy-code/);

const unsafeLinkHtml = renderDealDeskMarkdown('[bad](javascript:alert(1)) [ok](https://example.com)');
assert.doesNotMatch(unsafeLinkHtml, /href="javascript:/);
assert.match(unsafeLinkHtml, /href="https:\/\/example.com"/);

const jsonWrapped = normalizeDealDeskMarkdownSource('{"answer":"# 多Agent智能助手 业务判断\\n\\n## 1. 结论\\n\\n正常输出","files":[]}');
assert.equal(jsonWrapped, '# 多Agent智能助手 业务判断\n\n## 1. 结论\n\n正常输出');

const compactHeadingHtml = renderDealDeskMarkdown('结论如下##3.1验收标准缺失##3.2付款条件缺失');
assert.match(compactHeadingHtml, /<p>结论如下<\/p>/);
assert.match(compactHeadingHtml, /<h2[^>]*>3\.1 验收标准缺失<\/h2>/);
assert.match(compactHeadingHtml, /<h2[^>]*>3\.2 付款条件缺失<\/h2>/);

const remendWrappedHeadingHtml = renderDealDeskMarkdown('# ##3.1验收标准缺失，回款节点不可控');
assert.match(remendWrappedHeadingHtml, /<h2[^>]*>3\.1 验收标准缺失，回款节点不可控<\/h2>/);
assert.doesNotMatch(remendWrappedHeadingHtml, /<h1[^>]*>##3\.1/);

const remendWrappedTextHeadingHtml = renderDealDeskMarkdown('# ##销售视角\n\n- **商机存在性未验证**：CRM中无匹配记录');
assert.match(remendWrappedTextHeadingHtml, /<h2[^>]*>销售视角<\/h2>/);
assert.match(remendWrappedTextHeadingHtml, /<li><strong>商机存在性未验证<\/strong>：CRM中无匹配记录<\/li>/);
assert.doesNotMatch(remendWrappedTextHeadingHtml, /<h1[^>]*>##销售视角/);

const remendWrappedActionHeadingHtml = renderDealDeskMarkdown('# ##动作一：在 CRM中补录商机');
assert.match(remendWrappedActionHeadingHtml, /<h2[^>]*>动作一：在 CRM中补录商机<\/h2>/);
assert.doesNotMatch(remendWrappedActionHeadingHtml, /<h1[^>]*>##动作一/);

const ruleCodeHtml = renderDealDeskMarkdown('依据规则 **DEL-026** 和 **LEG-006** 判断风险。');
assert.match(ruleCodeHtml, /<strong>DEL-026<\/strong>/);
assert.match(ruleCodeHtml, /<strong>LEG-006<\/strong>/);
assert.doesNotMatch(ruleCodeHtml, /<li>026/);
assert.doesNotMatch(ruleCodeHtml, /<li>006/);

const repairedLink = repairStreamingMarkdown('See [customer profile](https://exampl');
assert.match(repairedLink, /\[customer profile\]\(streamdown:incomplete-link\)/);

const fencedCodeMarkdown = normalizeDealDeskMarkdownSource('```\n##3.1 not a heading fix\n```');
assert.equal(fencedCodeMarkdown, '```\n##3.1 not a heading fix\n```');

const chartFenceHtml = renderDealDeskMarkdown(`\`\`\`chart
{"type":"funnel","title":"销售漏斗","unit":"条","data":[{"name":"商务采购","value":19}]}
\`\`\``);
assert.match(chartFenceHtml, /chart-fence-placeholder/);
assert.match(chartFenceHtml, /data-chart=/);

const invalidChartFenceHtml = renderDealDeskMarkdown(`\`\`\`chart
{"type":"invalid","data":[]}
\`\`\``);
assert.match(invalidChartFenceHtml, /<pre[\s>]/);

const frames = buildProgressiveFrames('Sentence one. Sentence two. Sentence three.', 8);
assert.ok(frames.length > 1);
assert.equal(frames.at(-1), 'Sentence one. Sentence two. Sentence three.');

console.log('dealDeskMarkdown smoke test passed');
