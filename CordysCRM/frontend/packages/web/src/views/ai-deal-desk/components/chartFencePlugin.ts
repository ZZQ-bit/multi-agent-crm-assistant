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

function toFiniteNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value !== 'string') return null;

  const normalized = value.replace(/,/g, '').trim();
  if (!normalized) return null;

  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizePoint(item: unknown): { name: string; value: number } | null {
  if (!item || typeof item !== 'object') return null;
  const candidate = item as Record<string, unknown>;
  const name = candidate.name;
  const value = toFiniteNumber(candidate.value);

  if (typeof name !== 'string' || !name.trim() || value === null) return null;
  return { name: name.trim(), value };
}

function normalizePointList(data: unknown): {
  data: Array<{ name: string; value: number }>;
  xData?: string[];
} | null {
  if (!Array.isArray(data) || data.length === 0) return null;

  const points = data.map(normalizePoint);
  if (points.some((item) => !item)) return null;
  const normalizedPoints = points as Array<{ name: string; value: number }>;
  return {
    data: normalizedPoints,
    xData: normalizedPoints.map((item) => item.name),
  };
}

function normalizeAxisSeriesChart(parsed: Record<string, unknown>): {
  data: Array<{ name: string; value: number }>;
  xData?: string[];
} | null {
  const rawXAxis = parsed.xAxis;
  const xAxis = Array.isArray(rawXAxis)
    ? rawXAxis
    : rawXAxis && typeof rawXAxis === 'object'
    ? (rawXAxis as { data?: unknown }).data
    : null;
  const labels = Array.isArray(xAxis) ? xAxis.map((item) => String(item)) : null;
  const series = Array.isArray(parsed.series) ? parsed.series[0] : null;
  const values = series && typeof series === 'object' ? (series as { data?: unknown }).data : null;

  if (!labels || !Array.isArray(values) || labels.length === 0 || labels.length !== values.length) return null;

  const points = labels.map((name, index) => {
    const value = toFiniteNumber(values[index]);
    return name.trim() && value !== null ? { name: name.trim(), value } : null;
  });

  if (points.some((item) => !item)) return null;
  return { data: points as Array<{ name: string; value: number }>, xData: labels };
}

function normalizeChartJsChart(parsed: Record<string, unknown>): {
  data: Array<{ name: string; value: number }>;
  xData?: string[];
} | null {
  const chartData = parsed.data;
  if (!chartData || typeof chartData !== 'object' || Array.isArray(chartData)) return null;

  const labels = (chartData as { labels?: unknown }).labels;
  const datasets = (chartData as { datasets?: unknown }).datasets;
  const firstDataset = Array.isArray(datasets) ? datasets[0] : null;
  const values = firstDataset && typeof firstDataset === 'object' ? (firstDataset as { data?: unknown }).data : null;

  if (!Array.isArray(labels) || !Array.isArray(values) || labels.length === 0 || labels.length !== values.length)
    return null;

  const xData = labels.map((item) => String(item));
  const points = xData.map((name, index) => {
    const value = toFiniteNumber(values[index]);
    return name.trim() && value !== null ? { name: name.trim(), value } : null;
  });

  if (points.some((item) => !item)) return null;
  return { data: points as Array<{ name: string; value: number }>, xData };
}

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

    const normalized =
      normalizePointList(parsed.data) ??
      normalizeAxisSeriesChart(parsed as Record<string, unknown>) ??
      normalizeChartJsChart(parsed as Record<string, unknown>);
    if (!normalized) return null;

    return {
      type: chartType,
      chartType,
      title: parsed.title || undefined,
      data: normalized.data,
      xData:
        normalized.xData ??
        (Array.isArray(parsed.xData) ? parsed.xData.map((item: unknown) => String(item)) : undefined),
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
