import assert from 'node:assert/strict';

import { normalizeDealDeskMarkdownSource, renderDealDeskMarkdown } from './dealDeskMarkdown.mjs';

const standardAnswer = `{
  "answer": "# 多Agent智能助手 业务判断\\n\\n## 1. 结论\\n\\n当前商机处于**商务采购阶段**，金额 88 万元。\\n\\n## 2. CRM 已确认事实\\n\\n- **客户名称**：华东智造集团\\n- **客户标签**：战略客户、高风险大单、AI客服、交付紧张\\n\\n## 3. 核心风险判断\\n\\n### 3.1 验收标准缺失，回款节点不可控（财务/交付/合同共同关注）\\n\\n依据规则 **DEL-026**，在验收标准不清的情况下，不得承诺固定上线或回款节点，规则 **LEG-006** 已对此发出高等级警示。\\n\\n### 3.2 付款条件未固化，存在主观付款陷阱（财务/合同共同关注）\\n\\n跟进计划显示需确认折扣和付款节奏。",
  "files": []
}`;

const standardMarkdown = normalizeDealDeskMarkdownSource(standardAnswer);
const standardHtml = renderDealDeskMarkdown(standardAnswer);

assert.match(standardMarkdown, /^# 多Agent智能助手 业务判断/);
assert.match(standardHtml, /<h1[^>]*>多Agent智能助手 业务判断<\/h1>/);
assert.match(standardHtml, /<h2[^>]*>1\. 结论<\/h2>/);
assert.match(standardHtml, /<li><strong>客户名称<\/strong>：华东智造集团<\/li>/);
assert.match(standardHtml, /<h3[^>]*>3\.1 验收标准缺失，回款节点不可控（财务\/交付\/合同共同关注）<\/h3>/);
assert.match(standardHtml, /<strong>DEL-026<\/strong>/);
assert.match(standardHtml, /<strong>LEG-006<\/strong>/);
assert.doesNotMatch(standardHtml, /<h1[^>]*>##3\./);
assert.doesNotMatch(standardHtml, /<li>026/);

const remendWrappedHeadingHtml = renderDealDeskMarkdown('# ##3.1验收标准缺失，回款节点不可控');
assert.doesNotMatch(remendWrappedHeadingHtml, /<h1[^>]*>##3\.1/);
assert.match(remendWrappedHeadingHtml, /<h2[^>]*>3\.1 验收标准缺失，回款节点不可控<\/h2>/);

const gluedDealDeskSectionHtml = renderDealDeskMarkdown(
  '## 1. 结论当前商机整体判断为高风险。\\n## 2. CRM已确认事实- **客户名称**：华东智造集团- **客户标签**：战略客户'
);
assert.match(gluedDealDeskSectionHtml, /<h2[^>]*>1\. 结论<\/h2>\s*<p>当前商机整体判断为高风险。<\/p>/);
assert.match(gluedDealDeskSectionHtml, /<h2[^>]*>2\. CRM已确认事实<\/h2>/);
assert.match(gluedDealDeskSectionHtml, /<li><strong>客户名称<\/strong>：华东智造集团<\/li>/);
assert.match(gluedDealDeskSectionHtml, /<li><strong>客户标签<\/strong>：战略客户<\/li>/);

console.log('dealDeskMarkdown real output smoke test passed');
