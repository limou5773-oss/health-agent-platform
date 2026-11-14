<script setup lang="ts">
import { computed } from "vue";
import { dateAfter } from "../api";
import { reportMetrics } from "../report-metrics";
import type { CombinedStatistics } from "../types";

const props = defineProps<{ statistics: CombinedStatistics; canResolve: boolean }>();
const number = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });
const dates = computed(() => Array.from({ length: props.statistics.periodDays },
  (_, index) => dateAfter(props.statistics.startDate, index)));
const series = computed(() => props.statistics.metrics.map(metric => ({
  ...metric, values: new Map(metric.daily.map(day => [day.date, day.value])),
})));
</script>

<template>
  <section class="answer-section">
    <h3>同期指标与原始记录</h3>
    <p>{{ statistics.startDate }} 至 {{ statistics.endDate }}</p>
    <ul class="selection-list">
      <li v-for="metric in series" :key="metric.key">
        <strong>{{ reportMetrics[metric.key][0] }}（{{ metric.unit }}）</strong>
        <p v-if="metric.issue" class="small">
          {{ metric.issue }}。{{ canResolve ? "可在上方选择来源并继续。" : "可选定来源后新建综合分析。" }}
        </p>
        <p v-else class="small">
          覆盖 {{ metric.validDays }} / {{ statistics.periodDays }} 天
          <span v-if="metric.recordCount !== null"> · {{ metric.recordCount }} 条记录</span>
          · {{ metric.basis }}
        </p>
        <div class="actions">
          <RouterLink v-for="source in metric.sources" :key="source" class="text-link small"
            :to="{ path: '/timeline', query: { type: metric.key, sourceName: source,
              startDate: statistics.startDate, endDate: statistics.endDate } }">
            {{ source }}{{ metric.key === "WORKOUT" ? " · 周期内开始的运动" : "" }}
          </RouterLink>
        </div>
      </li>
    </ul>
    <details>
      <summary>查看逐日对齐数据</summary>
      <div class="table-wrap">
        <table>
          <thead><tr><th scope="col">日期</th><th v-for="metric in series" :key="metric.key" scope="col">
            {{ reportMetrics[metric.key][0] }}（{{ metric.unit }}）
          </th></tr></thead>
          <tbody><tr v-for="date in dates" :key="date">
            <th scope="row">{{ date }}</th>
            <td v-for="metric in series" :key="metric.key">
              {{ metric.issue ? "来源冲突" : metric.values.get(date) == null ? "缺失" : number.format(metric.values.get(date)!) }}
            </td>
          </tr></tbody>
        </table>
      </div>
    </details>
    <details v-if="statistics.diaryIds.length">
      <summary>本次工具读取的授权日记（{{ statistics.diaryIds.length }} 条）</summary>
      <ul>
        <li v-for="id in statistics.diaryIds" :key="id">
          <RouterLink class="text-link" :to="{ path: '/timeline', query: {
            startDate: statistics.startDate, endDate: statistics.endDate, type: 'DIARY',
            eventType: 'DIARY', eventId: String(id),
          } }">查看日记 #{{ id }}</RouterLink>
        </li>
      </ul>
    </details>
  </section>
</template>
