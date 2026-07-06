/**
 * markdown-it plugin: parse ```chart fences into placeholder divs.
 *
 * LLM outputs:
 *   ```chart
 *   {"type":"funnel","title":"本月销售管道","data":[{"name":"线索","value":45}],"unit":"条"}
 *   ```
 *
 * This plugin renders them as:
 *   <div class="chart-fence-placeholder" data-chart='{"type":"funnel",...}'></div>
 *
 * The companion ChartFence.vue component mounts on these placeholders.
 */
export interface ChartFenceData {
  type: 'bar' | 'line' | 'pie' | 'donut' | 'funnel';
  chartType?: 'bar' | 'line' | 'pie' | 'donut' | 'funnel';
  title?: string;
  data: Array<{ name: string; value: number }>;
  xData?: string[];
  unit?: string;
}

const VALID_CHART_TYPES = new Set(['bar', 'line', 'pie', 'donut', 'funnel']);

interface MarkdownToken {
  info?: string;
  content: string;
}

type FenceRenderer = (
  tokens: MarkdownToken[],
  idx: number,
  options: unknown,
  env: unknown,
  self: {
    renderToken(tokens: MarkdownToken[], idx: number, options: unknown): string;
  }
) => string;

interface MarkdownItInstance {
  renderer: {
    rules: {
      fence?: FenceRenderer;
    };
  };
}

function tryParseChartJson(raw: string): ChartFenceData | null {
  try {
    const parsed = JSON.parse(raw.trim());

    if (!parsed || typeof parsed !== 'object') return null;
    const chartType = parsed.type || parsed.chartType;
    if (!VALID_CHART_TYPES.has(chartType)) return null;
    if (!Array.isArray(parsed.data) || parsed.data.length === 0) return null;

    // Validate data items have name + value
    const valid = parsed.data.every((item: unknown) => {
      if (!item || typeof item !== 'object') return false;
      const candidate = item as Record<string, unknown>;
      return typeof candidate.name === 'string' && typeof candidate.value === 'number';
    });
    if (!valid) return null;

    return {
      type: chartType,
      chartType,
      title: parsed.title || undefined,
      data: parsed.data,
      xData: Array.isArray(parsed.xData) ? parsed.xData : undefined,
      unit: typeof parsed.unit === 'string' ? parsed.unit : undefined,
    };
  } catch {
    return null;
  }
}

function escapeHtmlAttr(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

export function chartFencePlugin(md: MarkdownItInstance): void {
  const defaultFence = md.renderer.rules.fence;

  md.renderer.rules.fence = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    const info = (token.info || '').trim().toLowerCase();

    if (info === 'chart') {
      const chartData = tryParseChartJson(token.content);

      if (chartData) {
        const json = escapeHtmlAttr(JSON.stringify(chartData));
        return `<div class="chart-fence-placeholder" data-chart="${json}"></div>`;
      }

      // Invalid chart JSON — fall through to render as a normal code block
    }

    // Delegate to default fence renderer
    if (defaultFence) {
      return defaultFence(tokens, idx, options, env, self);
    }

    return self.renderToken(tokens, idx, options);
  };
}

export default chartFencePlugin;
