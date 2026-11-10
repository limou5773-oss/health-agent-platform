<script setup lang="ts">
import { computed } from "vue";
import type { WeightStatistics } from "../types";
import { weightNumber, weightTime } from "../weight";

const props = defineProps<{ daily: WeightStatistics["daily"] }>();
const chart = computed(() => {
  const weights = props.daily.flatMap((day) =>
    day.representative ? [day.representative.weightKg] : [],
  );
  if (!weights.length) return null;
  const min = Math.min(...weights),
    max = Math.max(...weights);
  const x = (index: number) =>
    props.daily.length === 1
      ? 420
      : 70 + (index / (props.daily.length - 1)) * 700;
  const y = (weight: number) =>
    min === max ? 110 : 190 - ((weight - min) / (max - min)) * 160;
  let connected = false;
  const segments: string[] = [];
  const points = props.daily.flatMap((day, index) => {
    if (day.representative === null) {
      connected = false;
      return [];
    }
    const point = {
      x: x(index),
      y: y(day.representative.weightKg),
      record: day.representative,
    };
    segments.push(`${connected ? "L" : "M"}${point.x},${point.y}`);
    connected = true;
    return [point];
  });
  return { min, max, path: segments.join(" "), points };
});
</script>
<template>
  <figure v-if="chart" class="weight-chart">
    <svg
      viewBox="0 0 800 240"
      role="img"
      aria-label="每日体重代表值趋势，单位 kg；缺失日期处断开，原始记录见下方逐日数据。"
    >
      <g class="chart-grid">
        <line x1="70" y1="30" x2="770" y2="30" />
        <line x1="70" y1="110" x2="770" y2="110" />
        <line x1="70" y1="190" x2="770" y2="190" />
      </g>
      <g class="chart-label">
        <text x="60" :y="chart.min === chart.max ? 114 : 194" text-anchor="end">
          {{ weightNumber(chart.min) }}
        </text>
        <text v-if="chart.min !== chart.max" x="60" y="34" text-anchor="end">
          {{ weightNumber(chart.max) }}
        </text>
        <text x="70" y="225">{{ daily[0]?.date }}</text>
        <text v-if="daily.length > 1" x="770" y="225" text-anchor="end">
          {{ daily[daily.length - 1]?.date }}
        </text>
        <text x="12" y="18">kg</text>
      </g>
      <path :d="chart.path" class="weight-line" />
      <circle
        v-for="point in chart.points"
        :key="point.record.id"
        :cx="point.x"
        :cy="point.y"
        r="3.5"
        class="weight-dot"
      >
        <title>
          #{{ point.record.id }} · {{ weightTime(point.record.measuredAt) }} ·
          {{ weightNumber(point.record.weightKg) }} kg ·
          {{ point.record.sourceName }}
        </title>
      </circle>
    </svg>
    <figcaption class="footnote">
      每个点对应真实的每日代表记录；缺失日期断开，不补零、不插值。下方逐日数据可进入原始明细。
    </figcaption>
  </figure>
</template>
<style scoped>
.weight-chart {
  margin: 1.5rem 0;
}
.weight-chart svg {
  display: block;
  width: 100%;
  min-width: 0;
}
.chart-grid {
  stroke: var(--line);
  stroke-dasharray: 4 4;
}
.chart-label {
  fill: var(--muted);
  font-size: 12px;
}
.weight-line {
  fill: none;
  stroke: var(--green);
  stroke-width: 2.5;
}
.weight-dot {
  fill: var(--green);
  stroke: var(--surface);
  stroke-width: 1;
}
@media (max-width: 600px) {
  .chart-label {
    font-size: 18px;
  }
}
</style>
