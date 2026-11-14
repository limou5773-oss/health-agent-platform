<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { api, errorMessage } from "../api";
import { reportMetrics, type ReportMetric } from "../report-metrics";
import type { Plan } from "../types";
import StateNotice from "./StateNotice.vue";

type Period = {
  startDate: string;
  endDate: string;
  periodDays: number;
  validDays: number;
  requiredDays: number;
  average: number | null;
  recordCount: number | null;
  missingDates: string[];
};
type Outcome = {
  planVersion: number;
  planStart: string;
  planEnd: string;
  scheduled: number;
  completed: number;
  completionRate: number | null;
  comparison: {
    metric: ReportMetric;
    sourceName: string | null;
    historical: Period;
    recent: Period;
    difference: number | null;
  };
};

const props = defineProps<{ plan: Plan }>();
const form = reactive({ metric: "SLEEP" as ReportMetric, sourceName: "" });
const result = ref<Outcome>();
const loading = ref(false);
const error = ref("");
const periods = computed(() => result.value ? [
  { label: "计划前", ...result.value.comparison.historical },
  { label: "计划后", ...result.value.comparison.recent },
] : []);
const number = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });

async function load() {
  loading.value = true;
  result.value = undefined;
  error.value = "";
  try {
    result.value = await api<Outcome>(`/plans/${props.plan.id}/outcome?${new URLSearchParams(form)}`);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

watch(() => props.plan, () => { result.value = undefined; });
</script>

<template>
  <details class="plan-outcome">
    <summary>计划前后对比</summary>
    <p class="muted">比较计划前后两个等长周期。指标差值与计划打卡完成度并列展示，仅供观察。</p>
    <form class="filter-bar" @submit.prevent="load">
      <label>指标
        <select v-model="form.metric" :disabled="loading" @change="form.sourceName = ''">
          <option v-for="(labels, key) in reportMetrics" :key="key" :value="key">{{ labels[0] }}</option>
        </select>
      </label>
      <label>来源全名<input v-model.trim="form.sourceName" maxlength="100" :disabled="loading" placeholder="全部来源" /></label>
      <button :disabled="loading">查询对比</button>
    </form>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <p v-if="result && result.planVersion !== plan.version" class="feedback">
      计划版本已变化，刷新计划后再查询对比。
    </p>
    <template v-if="result && result.planVersion === plan.version">
      <h3>计划完成度</h3>
      <p>第 {{ result.planVersion }} 版 · {{ result.planStart }} 至 {{ result.planEnd }}</p>
      <p v-if="result.completionRate === null">计划周期内无任务。</p>
      <p v-else>已完成 {{ result.completed }} / {{ result.scheduled }} 项（{{ result.completionRate }}%）</p>
      <h3>{{ reportMetrics[result.comparison.metric][0] }}对比</h3>
      <p class="small muted">来源 {{ result.comparison.sourceName ?? "全部来源" }}</p>
      <div class="table-wrap">
        <table>
          <thead><tr>
            <th scope="col">周期</th><th scope="col">日期</th><th scope="col">覆盖</th>
            <th scope="col">{{ result.comparison.metric === "HEART_RATE" ? "采样均值" : "日均" }}</th>
            <th scope="col">样本</th>
          </tr></thead>
          <tbody>
            <tr v-for="period in periods" :key="period.label">
              <th scope="row">{{ period.label }}</th>
              <td>{{ period.startDate }} 至 {{ period.endDate }}</td>
              <td>{{ period.validDays }} / {{ period.periodDays }} 天
                <span v-if="period.recordCount !== null"> · {{ period.recordCount }} 条采样</span>
              </td>
              <td>{{ period.average === null ? "无有效记录" : `${number.format(period.average)} ${reportMetrics[result.comparison.metric][1]}` }}</td>
              <td>
                <RouterLink :to="{ path: '/timeline', query: {
                  startDate: period.startDate, endDate: period.endDate, type: result.comparison.metric,
                  sourceName: result.comparison.sourceName ?? undefined,
                } }">{{ result.comparison.metric === "WORKOUT" ? "时段内开始的运动" : "查看原始记录" }}</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="result.comparison.difference !== null">
        均值差（计划后减计划前） {{ number.format(result.comparison.difference) }} {{ reportMetrics[result.comparison.metric][1] }}
      </p>
      <p v-else>至少一个周期的覆盖不足 70%，请查看缺失日期。</p>
      <details v-for="period in periods.filter(item => item.missingDates.length)" :key="period.label">
        <summary>{{ period.label }}缺失日期（{{ period.missingDates.length }} 天）</summary>
        <p>{{ period.missingDates.join("、") }}</p>
      </details>
    </template>
  </details>
</template>

<style scoped>
.plan-outcome { margin-block: 1rem; }
.plan-outcome summary { cursor: pointer; }
</style>
