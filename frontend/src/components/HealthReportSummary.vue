<script setup lang="ts">
import type { HealthSnapshot } from "../types";
import ReportCoverageBar from "./ReportCoverageBar.vue";
withDefaults(defineProps<{ snapshot: HealthSnapshot; startDate: string; endDate: string; linkRecords?: boolean }>(), { linkRecords: true });

const metrics = {
  SLEEP: ["日均睡眠时长", "小时"],
  STEPS: ["日均步数", "步"],
  WORKOUT: ["累计运动时长", "分钟"],
  MEAL: ["已知饮食热量", "千卡"],
  WATER: ["日均饮水量", "毫升"],
  WEIGHT: ["日均体重", "千克"],
  HEART_RATE: ["心率采样均值", "次/分"],
} as const;
const number = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });
</script>

<template>
  <section>
    <h3>指标、来源与覆盖</h3>
    <ul class="selection-list health-report-metrics">
      <li v-for="metric in snapshot.metrics" :key="metric.key">
        <strong>{{ metrics[metric.key][0] }}</strong>
        <span v-if="metric.value !== null">
          {{ number.format(metric.value) }} {{ metrics[metric.key][1] }}
        </span>
        <span v-else>无可统计数值</span>
        <span class="small muted">有记录 {{ metric.validDays }} / {{ snapshot.periodDays }} 天</span>
        <ReportCoverageBar :valid-days="metric.validDays" :period-days="snapshot.periodDays" />
        <span v-if="metric.sources.length" class="small">来源 {{ metric.sources.join("、") }}</span>
        <span v-if="metric.key === 'HEART_RATE'" class="small">采样 {{ metric.recordCount }} 条</span>
        <RouterLink v-for="source in (linkRecords ? metric.sources : [])" :key="source" class="text-link small report-screen-only"
          :to="{ path: '/timeline', query: { startDate, endDate, type: metric.key, sourceName: source } }">
          {{ metric.key === "WORKOUT" ? "查看周期内开始的运动" : "查看原始记录" }} · {{ source }}
        </RouterLink>
        <span v-if="metric.key === 'MEAL'" class="small">
          手动 {{ metric.manualCaloriesCount }} 条 · 估算 {{ metric.estimatedCaloriesCount }} 条 · 未知 {{ metric.unknownCaloriesCount }} 条
        </span>
        <p class="small muted">{{ metric.basis }}</p>
      </li>
    </ul>
  </section>
  <section>
    <h3>计划完成情况</h3>
    <p v-if="snapshot.completionRate === null">无任务</p>
    <p v-else>
      已完成 {{ snapshot.completion.completed }} / {{ snapshot.completion.scheduled }} 项
      （{{ snapshot.completionRate }}%）
    </p>
    <p class="small muted">按周期内已保存任务的安排日期和生成时的打卡状态统计。</p>
  </section>
</template>

<style scoped>
.health-report-metrics li {
  display: grid;
  gap: 0.25rem;
  padding: 0.65rem 0;
}
.health-report-metrics p {
  margin: 0;
}
</style>
