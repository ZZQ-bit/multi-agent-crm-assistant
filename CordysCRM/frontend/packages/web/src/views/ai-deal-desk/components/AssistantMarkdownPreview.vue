<template>
  <div class="assistant-markdown-preview">
    <div
      ref="containerRef"
      class="assistant-markdown markdown-body"
      @click="handleMarkdownClick"
      v-html="displayHtml"
    ></div>
  </div>
</template>

<script setup lang="ts">
  import { createApp, h } from 'vue';

  import ChartFence from './ChartFence.vue';

  import { normalizeDealDeskMarkdownSource, renderDealDeskMarkdown } from './dealDeskMarkdown';

  const props = withDefaults(
    defineProps<{
      source: string;
      turnId: string;
      animate?: boolean;
    }>(),
    {
      animate: true,
    }
  );

  const containerRef = ref<HTMLElement | null>(null);
  const displayMarkdown = ref('');
  const displayHtml = computed(() => renderDealDeskMarkdown(displayMarkdown.value));

  const chartApps: Array<{ unmount: () => void }> = [];
  const copyResetTimers = new Map<HTMLButtonElement, number>();

  function clearCopyFeedbackTimers() {
    copyResetTimers.forEach((timer) => window.clearTimeout(timer));
    copyResetTimers.clear();
  }

  function setCopyFeedback(button: HTMLButtonElement, text: string, state: 'idle' | 'copied' | 'failed') {
    button.textContent = text;
    button.dataset.copyState = state;

    const previousTimer = copyResetTimers.get(button);
    if (previousTimer) {
      window.clearTimeout(previousTimer);
    }

    if (state !== 'idle') {
      const timer = window.setTimeout(() => {
        button.textContent = '复制';
        button.dataset.copyState = 'idle';
        copyResetTimers.delete(button);
      }, 1400);
      copyResetTimers.set(button, timer);
    }
  }

  async function copyTextToClipboard(text: string) {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return;
    }

    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();

    try {
      const copied = document.execCommand('copy');
      if (!copied) {
        throw new Error('copy command failed');
      }
    } finally {
      document.body.removeChild(textarea);
    }
  }

  async function handleMarkdownClick(event: MouseEvent) {
    const target = event.target as HTMLElement | null;
    const button = target?.closest<HTMLButtonElement>('[data-deal-desk-copy-code]');

    if (!button || !containerRef.value?.contains(button)) {
      return;
    }

    const codeEl = button.closest('.deal-desk-code-block')?.querySelector<HTMLElement>('pre code');
    const codeText = codeEl?.textContent || '';

    if (!codeText) {
      return;
    }

    button.disabled = true;
    try {
      await copyTextToClipboard(codeText);
      setCopyFeedback(button, '已复制', 'copied');
    } catch {
      setCopyFeedback(button, '复制失败', 'failed');
    } finally {
      button.disabled = false;
    }
  }

  function mountCharts() {
    // Unmount previous chart instances
    chartApps.forEach((app) => app.unmount());
    chartApps.length = 0;

    if (!containerRef.value) return;

    const placeholders = containerRef.value.querySelectorAll<HTMLElement>('.chart-fence-placeholder');
    placeholders.forEach((el) => {
      const raw = el.dataset.chart;
      if (!raw) return;

      try {
        const chartData = JSON.parse(raw);
        const app = createApp({ render: () => h(ChartFence, { chartData }) });
        app.mount(el);
        chartApps.push(app);
      } catch {
        // Invalid chart config, leave placeholder as-is
      }
    });
  }

  watch(
    () => props.source,
    (source) => {
      displayMarkdown.value = normalizeDealDeskMarkdownSource(source || '');
    },
    { immediate: true }
  );

  watch(displayHtml, () => {
    clearCopyFeedbackTimers();
    nextTick(mountCharts);
  });

  onMounted(() => {
    nextTick(mountCharts);
  });

  onBeforeUnmount(() => {
    chartApps.forEach((app) => app.unmount());
    chartApps.length = 0;
    clearCopyFeedbackTimers();
  });
</script>

<style scoped lang="less">
  .assistant-markdown-preview {
    min-width: 0;
  }

  :deep(.assistant-markdown) {
    background: transparent;
    font-size: 14px;
    color: var(--text-n1);
  }

  .assistant-markdown {
    color: var(--text-n1);
    font-size: 14px;
    line-height: 24px;
  }

  :deep(.assistant-markdown h1),
  :deep(.assistant-markdown h2),
  :deep(.assistant-markdown h3),
  :deep(.assistant-markdown h4),
  :deep(.assistant-markdown h5),
  :deep(.assistant-markdown h6) {
    margin: 14px 0 6px;
    padding-bottom: 0;
    border-bottom: none;
    color: var(--text-n1);
    line-height: 1.5;
    letter-spacing: 0;
  }

  :deep(.assistant-markdown h1) {
    margin-top: 0;
    font-size: 16px;
    font-weight: 700;
  }

  :deep(.assistant-markdown h2) {
    font-size: 15px;
    font-weight: 700;
  }

  :deep(.assistant-markdown h3) {
    font-size: 14px;
    font-weight: 700;
  }

  :deep(.assistant-markdown h4),
  :deep(.assistant-markdown h5),
  :deep(.assistant-markdown h6) {
    font-size: 14px;
    font-weight: 700;
  }

  :deep(.assistant-markdown strong) {
    font-weight: 650;
  }

  :deep(.assistant-markdown > *:first-child) {
    margin-top: 0;
  }

  :deep(.assistant-markdown > *:last-child) {
    margin-bottom: 0;
  }

  :deep(.assistant-markdown p),
  :deep(.assistant-markdown li),
  :deep(.assistant-markdown blockquote) {
    font-size: 14px;
    line-height: 24px;
  }

  :deep(.assistant-markdown p) {
    margin: 8px 0;
  }

  :deep(.assistant-markdown ul),
  :deep(.assistant-markdown ol) {
    margin: 8px 0;
    padding-left: 22px;
  }

  :deep(.assistant-markdown table) {
    display: block;
    width: 100%;
    max-width: 100%;
    overflow-x: auto;
    border-collapse: collapse;
    margin: 12px 0;
  }

  :deep(.assistant-markdown th),
  :deep(.assistant-markdown td) {
    border: 1px solid #d8e1e5;
    padding: 8px 12px;
    text-align: left;
    white-space: nowrap;
  }

  :deep(.assistant-markdown th) {
    background: #f5f8fa;
    font-weight: 600;
  }

  :deep(.assistant-markdown pre) {
    border-radius: 8px;
    overflow-x: auto;
  }

  :deep(.assistant-markdown .deal-desk-code-block) {
    margin: 12px 0;
    overflow: hidden;
    border: 1px solid #d8e1e5;
    border-radius: 8px;
    background: #101820;
  }

  :deep(.assistant-markdown .deal-desk-code-block__header) {
    display: flex;
    min-height: 32px;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 0 10px 0 12px;
    border-bottom: 1px solid rgba(216, 225, 229, 0.18);
    background: #f5f8fa;
  }

  :deep(.assistant-markdown .deal-desk-code-block__language) {
    overflow: hidden;
    color: #48606f;
    font-size: 12px;
    font-weight: 600;
    line-height: 32px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.assistant-markdown .deal-desk-code-block__copy) {
    flex: 0 0 auto;
    height: 24px;
    padding: 0 8px;
    border: 1px solid #cbd8df;
    border-radius: 6px;
    background: #ffffff;
    color: #48606f;
    cursor: pointer;
    font-size: 12px;
    line-height: 22px;
  }

  :deep(.assistant-markdown .deal-desk-code-block__copy:hover) {
    border-color: #89b7bf;
    color: #1f7a8c;
  }

  :deep(.assistant-markdown .deal-desk-code-block__copy:disabled) {
    cursor: default;
    opacity: 0.72;
  }

  :deep(.assistant-markdown .deal-desk-code-block__copy[data-copy-state='copied']) {
    border-color: #8ec5a4;
    color: #1e7a42;
  }

  :deep(.assistant-markdown .deal-desk-code-block__copy[data-copy-state='failed']) {
    border-color: #e7a3a3;
    color: #b42318;
  }

  :deep(.assistant-markdown .deal-desk-code-block pre) {
    margin: 0;
    padding: 12px;
    border-radius: 0;
    background: #101820;
    color: #edf4f7;
    font-size: 13px;
    line-height: 20px;
  }

  :deep(.assistant-markdown .deal-desk-code-block code) {
    background: transparent;
    color: inherit;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
    font-size: 13px;
  }

  :deep(.assistant-markdown code:not(pre code)) {
    padding: 2px 6px;
    border-radius: 6px;
    background: rgba(15, 23, 42, 0.06);
    font-size: 13px;
  }

  :deep(.assistant-markdown img) {
    display: block;
    max-width: min(100%, 520px);
    border-radius: 12px;
  }

  :deep(.assistant-markdown a) {
    color: #1f7a8c;
  }

  :deep(.assistant-markdown blockquote) {
    margin: 12px 0;
    padding: 8px 12px;
    border-left: 3px solid #9fcbd1;
    color: var(--text-n2);
    background: #f7fbfc;
  }
</style>
