<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { api, errorMessage } from "../api";
import StateNotice from "./StateNotice.vue";
import type { HeartRateStatistics, MealStatistics, SleepStatistics, StepStatistics, WaterStatistics, WeightStatistics, WorkoutStatistics } from "../types";

type Point = { date: string; value: number | null };
type Summary = {
  points: Point[];
  value: number | null;
  label: string;
  cutoff?: string | null;
  note?: string;
};
type Coverage = { validDays: number; periodDays: number };
const metrics = {
  sleep: { label: "睡眠", unit: "小时", path: "/sleep", to: "/records", map: (data: SleepStatistics): Summary => ({
    points: data.daily.map(day => ({ date: day.date, value: day.hours })), value: data.averageHours,
    label: "日均睡眠", cutoff: data.dataCutoff,
    note: data.validDays ? `累计 ${data.totalHours} 小时，共 ${data.recordCount} 条记录，按醒来日期归属。` : undefined,
  }) },
  steps: { label: "步数", unit: "步", path: "/activity/steps", to: "/activity", map: (data: StepStatistics): Summary => ({
    points: data.daily.map(day => ({ date: day.date, value: day.steps })), value: data.averageSteps, label: "日均步数",
    note: data.dailyGoal === null ? undefined : `每日目标 ${data.dailyGoal} 步，${data.goalDays} / ${data.validDays} 个有效日达标。`,
  }) },
  workouts: { label: "运动", unit: "分钟", path: "/activity/workouts", to: "/activity", map: (data: WorkoutStatistics): Summary => ({
    points: data.daily.map(day => ({ date: day.date, value: day.minutes })), value: data.validDays ? data.totalMinutes : null,
    label: "累计运动",
  }) },
  meals: { label: "饮食", unit: "千卡", path: "/nutrition/meals", to: "/nutrition", map: (data: MealStatistics): Summary => ({
    points: data.daily.map(day => ({ date: day.date, value: day.knownCaloriesKcal })), value: data.knownCaloriesKcal,
    label: "已记录热量合计", cutoff: data.dataCutoff,
    note: `手动 ${data.manualCaloriesCount} 条，估算 ${data.estimatedCaloriesCount} 条，热量未知 ${data.unknownCaloriesCount} 条。`,
  }) },
  water: { label: "饮水", unit: "毫升", path: "/nutrition/water", to: "/nutrition", map: (data: WaterStatistics): Summary => ({
    points: data.daily.map(day => ({ date: day.date, value: day.amountMl })), value: data.averageMl,
    label: "日均饮水", cutoff: data.dataCutoff,
  }) },
  weight: { label: "体重", unit: "千克", path: "/weight", to: "/weight", map: (data: WeightStatistics): Summary => ({
    points: data.daily.map(day => ({ date: day.date, value: day.representative?.weightKg ?? null })),
    value: data.last?.weightKg ?? null, label: "周期内最新体重", cutoff: data.dataCutoff,
  }) },
  heartRate: { label: "心率", unit: "次/分", path: "/heart-rate", to: "/heart-rate", map: (data: HeartRateStatistics): Summary => ({
    points: data.daily.map(day => ({ date: day.date, value: day.averageBpm })),
    value: data.averageBpm, label: "采样均值", cutoff: data.dataCutoff,
    note: `共 ${data.recordCount} 条心率采样。`,
  }) },
} as const;

type Metric = keyof typeof metrics;
const props = defineProps<{ metric: Metric; startDate: string; endDate: string }>();
const metric = computed(() => metrics[props.metric]);
const source = ref("");
const sources = ref<string[]>([]);
const sourceError = ref("");
const error = ref("");
const loading = ref(true);
const sourceLoading = ref(true);
const revision = ref(0);
const sourceRevision = ref(0);
const summary = ref<(Summary & Coverage) | null>(null);

watch(sourceRevision, async () => {
  sourceLoading.value = true;
  sourceError.value = "";
  try {
    sources.value = await api<string[]>(`${metric.value.path}/sources`);
  } catch (cause) {
    sourceError.value = errorMessage(cause);
  } finally {
    sourceLoading.value = false;
  }
}, { immediate: true });

async function read<T extends Coverage>(path: string, map: (value: T) => Summary) {
  const response = await api<T>(path);
  return { ...map(response), validDays: response.validDays, periodDays: response.periodDays };
}

const loaders = {
  sleep: (path: string) => read(path, metrics.sleep.map),
  steps: (path: string) => read(path, metrics.steps.map),
  workouts: (path: string) => read(path, metrics.workouts.map),
  meals: (path: string) => read(path, metrics.meals.map),
  water: (path: string) => read(path, metrics.water.map),
  weight: (path: string) => read(path, metrics.weight.map),
  heartRate: (path: string) => read(path, metrics.heartRate.map),
};

watch([() => props.startDate, () => props.endDate, source, revision], async ([startDate, endDate, sourceName], _, onCleanup) => {
  let active = true;
  onCleanup(() => { active = false; });
  loading.value = true;
  error.value = "";
  summary.value = null;
  const params = new URLSearchParams({ startDate, endDate, ...(sourceName ? { sourceName } : {}) });
  const path = `${metric.value.path}/statistics?${params}`;
  try {
    const result = await loaders[props.metric](path);
    if (active) summary.value = result;
  } catch (cause) {
    if (active) error.value = errorMessage(cause);
  } finally {
    if (active) loading.value = false;
  }
}, { immediate: true });

const max = computed(() => Math.max(1, ...(summary.value?.points.map(point => point.value ?? 0) ?? [])));
const width = computed(() => 400 / (summary.value?.points.length || 1));
const number = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });
</script>

<template>
  <section class="panel dashboard-metric">
    <div class="section-heading">
      <h2>{{ metric.label }}</h2>
      <RouterLink class="text-link" :to="metric.to">查看记录</RouterLink>
    </div>
    <label>数据来源
      <select v-model="source" :disabled="sourceLoading || !!sourceError">
        <option value="">全部来源</option>
        <option v-for="name in sources" :key="name" :value="name">{{ name }}</option>
      </select>
    </label>
    <StateNotice v-if="sourceError" :error="sourceError" @retry="sourceRevision++" />
    <StateNotice :loading="loading" :error="error" @retry="revision++" />
    <template v-if="summary && !loading && !error">
      <p>{{ summary.label }} <strong>{{ summary.value === null ? "—" : `${number.format(summary.value)} ${metric.unit}` }}</strong></p>
      <p class="small muted">{{ startDate }} 至 {{ endDate }} · 有记录 {{ summary.validDays }} / {{ summary.periodDays }} 天</p>
      <p v-if="summary.cutoff" class="small muted">数据截至 {{ summary.cutoff.replace("T", " ") }}</p>
      <p v-if="summary.note" class="small muted">{{ summary.note }}</p>
      <p v-if="!summary.validDays" class="muted">这段时间没有记录，可前往记录页补录或导入。</p>
      <p v-else-if="summary.points.every(point => point.value === null)" class="muted">有记录，暂无可绘制的数值。</p>
      <template v-else>
        <svg viewBox="0 0 400 90" role="img" :aria-label="`${metric.label}每日趋势，单位${metric.unit}。逐日数值见下方。`">
          <line x1="0" x2="400" y1="88" y2="88" class="chart-grid" />
          <template v-for="(point, index) in summary.points" :key="point.date">
            <rect v-if="point.value !== null"
              :x="index * width + width * 0.15" :y="86 - point.value / max * 80"
              :width="width * 0.7" :height="point.value === 0 ? 2 : point.value / max * 80"
              class="chart-bar">
              <title>{{ point.date }} · {{ point.value }} {{ metric.unit }}</title>
            </rect>
          </template>
        </svg>
        <details>
          <summary>查看逐日数值（{{ metric.unit }}）</summary>
          <div class="table-wrap">
            <table>
              <thead><tr><th scope="col">日期</th><th scope="col">数值</th></tr></thead>
              <tbody><tr v-for="point in summary.points" :key="point.date">
                <td>{{ point.date }}</td><td>{{ point.value === null ? "缺失" : number.format(point.value) }}</td>
              </tr></tbody>
            </table>
          </div>
        </details>
      </template>
    </template>
  </section>
</template>

<style scoped>
.dashboard-metric { min-width: 0; }
.dashboard-metric > svg { width: 100%; display: block; margin-block: 0.5rem; }
.dashboard-metric summary { cursor: pointer; }
</style>
