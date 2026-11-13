<script setup lang="ts">
import { computed } from "vue";
import { reportMetrics } from "../report-metrics";
import type { FocusedSnapshot } from "../types";

const props = defineProps<{ snapshot: FocusedSnapshot }>();
const periods = computed(() => [
  { label: "报告周期", ...props.snapshot.current },
  ...(props.snapshot.comparison ? [{ label: "对照周期", ...props.snapshot.comparison }] : []),
]);
</script>

<template>
  <section>
    <p class="muted">来源筛选 {{ snapshot.sourceName ?? "全部来源" }}</p>
    <p v-if="snapshot.metric === 'MEAL'" class="small muted">含未知热量条目的日期按缺失处理。</p>
    <section v-for="period in periods" :key="period.label">
      <h3>{{ period.label }}</h3>
      <p>{{ period.statistics.startDate }} 至 {{ period.statistics.endDate }}</p>
      <p>覆盖 {{ period.statistics.validDays }} / {{ period.statistics.periodDays }} 天 ·
        <strong v-if="period.statistics.average !== null">{{ snapshot.metric === "HEART_RATE" ? "采样均值" : "日均" }} {{ period.statistics.average }} {{ reportMetrics[snapshot.metric][1] }}</strong>
        <span v-else>无有效均值</span>
      </p>
      <p v-if="snapshot.metric === 'HEART_RATE'" class="small muted">采样 {{ period.statistics.recordCount }} 条</p>
      <p v-if="period.sources.length" class="small muted">记录来源 {{ period.sources.join("、") }}</p>
      <RouterLink v-if="period.sources.length" class="text-link small" :to="{ path: '/timeline', query: {
        startDate: period.statistics.startDate, endDate: period.statistics.endDate,
        type: snapshot.metric, sourceName: snapshot.sourceName ?? undefined,
      } }">{{ snapshot.metric === "WORKOUT" ? "查看周期内开始的运动" : "查看周期原始记录" }}</RouterLink>
      <p v-if="period.completionRate === null">该周期无计划任务。</p>
      <p v-else>计划完成 {{ period.completion.completed }} / {{ period.completion.scheduled }}（{{ period.completionRate }}%）</p>
      <details v-if="period.statistics.missingDates.length">
        <summary>缺失日期（{{ period.statistics.missingDates.length }} 天）</summary>
        <p>{{ period.statistics.missingDates.join("、") }}</p>
      </details>
    </section>
    <template v-if="snapshot.comparison">
      <p v-if="snapshot.difference !== null">
        {{ snapshot.metric === "HEART_RATE" ? "采样均值差" : "日均差值" }}（报告周期减对照周期）
        <strong>{{ snapshot.difference }} {{ reportMetrics[snapshot.metric][1] }}</strong>
      </p>
      <p v-else>至少一期有效覆盖不足 70%，仅展示各期统计。</p>
    </template>
  </section>
</template>
