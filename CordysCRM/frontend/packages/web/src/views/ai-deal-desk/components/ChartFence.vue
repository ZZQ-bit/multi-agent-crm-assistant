<template>
  <div class="chart-fence-wrapper">
    <div v-if="chartData.title" class="chart-fence-title">{{ chartData.title }}</div>
    <div
      v-if="hasData"
      ref="chartContainerRef"
      class="chart-fence-container"
      :class="{ 'chart-fence-container--funnel': isDealDeskFunnel }"
    >
      <div v-if="isDealDeskFunnel" class="deal-desk-funnel">
        <svg
          class="deal-desk-funnel-svg"
          :viewBox="`0 0 ${funnelViewBoxWidth} ${funnelViewBoxHeight}`"
          preserveAspectRatio="xMidYMin meet"
          :style="{ height: `${funnelPixelHeight}px` }"
          role="img"
          :aria-label="chartData.title || '销售漏斗'"
        >
          <g v-for="segment in funnelSegments" :key="segment.name">
            <polygon class="deal-desk-funnel-svg__segment" :points="segment.polygonPoints" :fill="segment.background" />
            <text class="deal-desk-funnel-svg__label" :x="segment.nameX" :y="segment.labelY" text-anchor="start">
              {{ segment.name }}
            </text>
            <text class="deal-desk-funnel-svg__value" :x="segment.valueX" :y="segment.labelY" text-anchor="end">
              {{ segment.valueLabel }}
            </text>
          </g>
        </svg>
      </div>
      <component
        :is="chartComponent"
        v-else
        :group-name="chartData.title || ''"
        :data-indicator-name="chartData.unit || ''"
        :aggregation-method-name="chartData.unit || ''"
        :data="chartData.data"
        :x-data="chartData.xData"
        :is-full-screen="false"
        :container-ref="chartContainerEl"
      />
    </div>
    <div v-else class="chart-fence-empty">暂无可展示数据</div>
    <div v-if="chartData.unit" class="chart-fence-unit">{{ chartData.unit }}</div>
  </div>
</template>

<script setup lang="ts">
  import { computed, defineAsyncComponent, ref } from 'vue';

  import type { ChartFenceData } from './chartFencePlugin';

  const props = defineProps<{
    chartData: ChartFenceData;
  }>();

  const chartContainerRef = ref<HTMLDivElement>();
  const chartContainerEl = computed(() => chartContainerRef.value as Element | undefined);
  const hasData = computed(() => Array.isArray(props.chartData.data) && props.chartData.data.length > 0);
  const resolvedChartType = computed<ChartFenceData['type']>(
    () => props.chartData.type || props.chartData.chartType || 'bar'
  );
  const isDealDeskFunnel = computed(() => resolvedChartType.value === 'funnel');

  const chartComponentMap = {
    bar: defineAsyncComponent(() => import('@/components/pure/crm-chart/charts/bar.vue')),
    line: defineAsyncComponent(() => import('@/components/pure/crm-chart/charts/line.vue')),
    pie: defineAsyncComponent(() => import('@/components/pure/crm-chart/charts/pie.vue')),
    donut: defineAsyncComponent(() => import('@/components/pure/crm-chart/charts/doughnut.vue')),
    funnel: defineAsyncComponent(() => import('@/components/pure/crm-chart/charts/funnel.vue')),
  } as const;

  const chartComponent = computed(() => chartComponentMap[resolvedChartType.value] || chartComponentMap.bar);

  const funnelPalette = ['#0f766e', '#1d7f78', '#2c8a81', '#3f9a91', '#52aaa1', '#78bcb4', '#9bcfc8', '#c3e4df'];
  const funnelBaseTop = 6;
  const funnelViewBoxWidth = 200;

  const funnelSegments = computed(() => {
    const values = props.chartData.data ?? [];
    const total = values.length;
    const maxVisualWidth = 184;
    const minVisualWidth = total > 5 ? 88 : 100;
    const widthStep = total > 1 ? (maxVisualWidth - minVisualWidth) / (total - 1) : 0;
    const stageHeight = total > 6 ? 12 : 14;
    const tailWidth = Math.max(24, Math.round(minVisualWidth - Math.max(widthStep, 8)));
    const centerX = funnelViewBoxWidth / 2;

    return values.map((item, index) => {
      const value = Number(item.value) || 0;
      const topWidth = Math.max(minVisualWidth, Number((maxVisualWidth - index * widthStep).toFixed(2)));
      const bottomWidth =
        index === total - 1
          ? tailWidth
          : Math.max(minVisualWidth, Number((maxVisualWidth - (index + 1) * widthStep).toFixed(2)));
      const yTop = funnelBaseTop + index * stageHeight;
      const yBottom = yTop + stageHeight;
      const topLeft = Number((centerX - topWidth / 2).toFixed(2));
      const topRight = Number((centerX + topWidth / 2).toFixed(2));
      const bottomLeft = Number((centerX - bottomWidth / 2).toFixed(2));
      const bottomRight = Number((centerX + bottomWidth / 2).toFixed(2));
      const labelInset = Math.max(7.2, Math.min(12.4, bottomWidth * 0.1));

      return {
        name: item.name,
        value,
        valueLabel: props.chartData.unit ? `${value}${props.chartData.unit}` : `${value}`,
        background: funnelPalette[index % funnelPalette.length],
        polygonPoints: `${topLeft},${yTop} ${topRight},${yTop} ${bottomRight},${yBottom} ${bottomLeft},${yBottom}`,
        labelY: Number((yTop + stageHeight / 2).toFixed(2)),
        nameX: Number((Math.max(topLeft, bottomLeft) + labelInset).toFixed(2)),
        valueX: Number((Math.min(topRight, bottomRight) - labelInset).toFixed(2)),
      };
    });
  });

  const funnelViewBoxHeight = computed(() => {
    const total = props.chartData.data?.length ?? 0;
    const stageHeight = total > 6 ? 12 : 14;
    return funnelBaseTop * 2 + total * stageHeight;
  });

  const funnelPixelHeight = computed(() => {
    const total = props.chartData.data?.length ?? 0;
    return Math.max(220, total * 36 + 18);
  });
</script>

<style scoped lang="less">
  .chart-fence-wrapper {
    margin: 12px 0;
    padding: 12px;
    border: 1px solid #dde6ea;
    border-radius: 8px;
    background: #f7fafb;
  }

  .chart-fence-title {
    margin-bottom: 8px;
    color: var(--text-n1, #142433);
    font-size: 14px;
    font-weight: 600;
  }

  .chart-fence-container {
    width: 100%;
    height: 280px;
    min-height: 200px;
  }

  .chart-fence-container--funnel {
    height: auto;
    min-height: 0;
  }

  .deal-desk-funnel {
    padding: 8px 0 2px;
  }

  .deal-desk-funnel-svg {
    display: block;
    width: 100%;
    overflow: visible;
  }

  .deal-desk-funnel-svg__segment {
    stroke: rgba(255, 255, 255, 0.18);
    stroke-width: 0.7;
  }

  .deal-desk-funnel-svg__label,
  .deal-desk-funnel-svg__value {
    fill: #ffffff;
    font-size: 4.8px;
    font-weight: 600;
    dominant-baseline: middle;
  }

  .deal-desk-funnel-svg__value {
    opacity: 0.96;
  }

  .chart-fence-empty {
    display: flex;
    min-height: 180px;
    align-items: center;
    justify-content: center;
    padding: 16px;
    border: 1px dashed #d6dfe4;
    border-radius: 8px;
    background: #ffffff;
    color: var(--text-n2, #667785);
    font-size: 13px;
  }

  .chart-fence-unit {
    margin-top: 4px;
    color: var(--text-n2, #667785);
    font-size: 12px;
    text-align: right;
  }

  @media (max-width: 768px) {
    .chart-fence-container {
      height: 220px;
      min-height: 180px;
    }

    .chart-fence-container--funnel {
      height: auto;
      min-height: 0;
    }

    .deal-desk-funnel-svg__label,
    .deal-desk-funnel-svg__value {
      font-size: 4.4px;
    }
  }
</style>
