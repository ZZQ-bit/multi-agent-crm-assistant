import createDOMPurify from 'dompurify';
import MarkdownIt from 'markdown-it';
import remend from 'remend';

import { chartFencePlugin } from './chartFencePlugin.ts';

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: true,
});

const purifier = typeof window !== 'undefined' ? createDOMPurify(window) : null;

interface MarkdownRenderToken {
  info?: string;
  content: string;
  attrGet(name: string): string | null;
  attrSet(name: string, value: string): void;
}

type MarkdownRendererRule = (
  tokens: MarkdownRenderToken[],
  idx: number,
  options: unknown,
  env: unknown,
  self: {
    renderToken(tokens: MarkdownRenderToken[], idx: number, options: unknown): string;
  }
) => string;

const DEAL_DESK_SECTION_TITLES = [
  '结论',
  'CRM 已确认事实',
  'CRM已确认事实',
  '核心风险判断',
  '缺失信息与不能断言的部分',
  '下一步销售动作',
  '需要复核的角色',
];

function escapeRegExp(source: string) {
  return source.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function normalizeFenceLanguage(info: string) {
  return (info || '').trim().split(/\s+/)[0]?.replace(/[^\w+#.-]/g, '').slice(0, 32).toLowerCase() || '';
}

function isSafeLinkHref(href: string) {
  const normalized = href.trim().replace(/[\u0000-\u001F\u007F\s]+/g, '').toLowerCase();
  return !/^(javascript|vbscript|data):/.test(normalized);
}

function installMarkdownRenderers(md: typeof markdown) {
  const defaultFence = md.renderer.rules.fence;
  const defaultLinkOpen = md.renderer.rules.link_open;

  const enhancedFence: MarkdownRendererRule = (tokens, idx) => {
    const token = tokens[idx];
    const language = normalizeFenceLanguage(token.info || '');
    const label = language || '文本';
    const languageClass = language ? ` class="language-${md.utils.escapeHtml(language)}"` : '';

    return [
      '<div class="deal-desk-code-block">',
      '<div class="deal-desk-code-block__header">',
      `<span class="deal-desk-code-block__language">${md.utils.escapeHtml(label)}</span>`,
      '<button type="button" class="deal-desk-code-block__copy" data-deal-desk-copy-code aria-label="复制代码">复制</button>',
      '</div>',
      `<pre><code${languageClass}>${md.utils.escapeHtml(token.content)}</code></pre>`,
      '</div>',
    ].join('');
  };

  const enhancedLinkOpen: MarkdownRendererRule = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    const href = token.attrGet('href');

    if (href && !isSafeLinkHref(href)) {
      token.attrSet('href', '#');
    }
    token.attrSet('target', '_blank');
    token.attrSet('rel', 'noreferrer noopener');

    if (defaultLinkOpen) {
      return defaultLinkOpen(tokens, idx, options, env, self);
    }

    return self.renderToken(tokens, idx, options);
  };

  md.renderer.rules.fence = enhancedFence;
  md.renderer.rules.link_open = enhancedLinkOpen;
}

installMarkdownRenderers(markdown);
markdown.use(chartFencePlugin);

function unwrapAnswerJson(source: string) {
  const trimmed = source.trim();
  if (!trimmed.startsWith('{')) {
    return source;
  }

  try {
    const payload = JSON.parse(trimmed) as { answer?: unknown; answerText?: unknown; answer_text?: unknown };
    if (typeof payload.answerText === 'string') {
      return payload.answerText;
    }
    if (typeof payload.answer_text === 'string') {
      return payload.answer_text;
    }
    return typeof payload.answer === 'string' ? payload.answer : source;
  } catch {
    return source;
  }
}

function normalizeLineEndings(source: string) {
  return source.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\\r\\n|\\n|\\r/g, '\n');
}

function splitGluedDealDeskSections(source: string) {
  return DEAL_DESK_SECTION_TITLES.reduce((current, title) => {
    const pattern = new RegExp(`(^|\\n)(#{1,6}\\s+\\d+\\.\\s*)(${escapeRegExp(title)})(?=\\S)`, 'g');
    return current.replace(pattern, '$1$2$3\n\n');
  }, source);
}

function normalizeCompactHeadings(source: string) {
  return source
    .replace(/(^|\n)#{1,6}\s+(#{2,6})(?=\S)/g, '$1$2')
    .replace(/([^\n])(?=#{2,6}(?!#)\S)/g, '$1\n\n')
    .replace(/(^|\n)(#{1,6})(?!#)\s*(?=\S)/g, '$1$2 ')
    .replace(/(^|\n)(#{1,6}\s+)(\d+(?:\.\d+)*(?:\.(?=[^\s\d])|(?=[^\s.\d])))(?=\S)/g, '$1$2$3 ');
}

function normalizeKnownGluedLists(source: string) {
  return source
    .replace(/([^\n])-\s+(?=\*\*)/g, '$1\n- ')
    .replace(
      /([^\n])-\s+(?=(客户|商机|负责人|主要联系人|产品|最新|未完成|交易条件|决策链|风险信号|付款条件|折扣比例|验收标准|交付时间表|数据安全|销售主管|财务|交付负责人|商务|法务|动作|协同|目标|建议话术))/g,
      '$1\n- '
    )
    .replace(/(^|\n)-(?=\S)/g, '$1- ');
}

export function normalizeDealDeskMarkdownSource(source: string) {
  const unwrapped = unwrapAnswerJson(source || '');
  const normalized = normalizeLineEndings(unwrapped);
  const lines = normalized.split('\n');
  const result: string[] = [];
  let pendingPlainText: string[] = [];
  let inFencedCode = false;

  function flushPlainText() {
    if (!pendingPlainText.length) {
      return;
    }

    const plainText = pendingPlainText.join('\n');
    const withoutBrokenHeadings = normalizeCompactHeadings(plainText);
    const withSplitSections = splitGluedDealDeskSections(withoutBrokenHeadings);
    result.push(normalizeKnownGluedLists(withSplitSections));
    pendingPlainText = [];
  }

  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) {
      flushPlainText();
      result.push(line);
      inFencedCode = !inFencedCode;
      continue;
    }

    if (inFencedCode) {
      result.push(line);
      continue;
    }

    pendingPlainText.push(line);
  }

  flushPlainText();

  return result.join('\n').trim();
}

export function repairStreamingMarkdown(source: string) {
  return remend(normalizeDealDeskMarkdownSource(source), {
    inlineKatex: false,
    linkMode: 'protocol',
  });
}

export function sanitizeRenderedMarkdown(html: string) {
  if (!purifier) {
    return html;
  }

  return purifier.sanitize(html, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel', 'class', 'type', 'aria-label', 'data-deal-desk-copy-code', 'data-copy-state'],
  });
}

export function renderDealDeskMarkdown(source: string) {
  const normalized = normalizeDealDeskMarkdownSource(source);
  if (!normalized) {
    return '';
  }

  return sanitizeRenderedMarkdown(markdown.render(normalized));
}

export function buildProgressiveFrames(source: string, chunkSize = 24) {
  const normalized = source || '';
  if (!normalized) {
    return [''];
  }

  const safeChunkSize = Math.max(1, chunkSize);
  const frames: string[] = [];

  for (let index = safeChunkSize; index < normalized.length; index += safeChunkSize) {
    frames.push(normalized.slice(0, index));
  }

  frames.push(normalized);

  return frames.filter((frame, index) => frame || index === frames.length - 1);
}
