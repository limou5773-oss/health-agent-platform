<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { api, dateAfter, errorMessage, recentRange } from "../api";
import StateNotice from "../components/StateNotice.vue";

const metrics = {
  SLEEP: { label: "睡眠时长", unit: "小时", sources: "/sleep/sources" },
  STEPS: { label: "步数", unit: "步", sources: "/activity/steps/sources" },
  WORKOUT: { label: "运动时长", unit: "分钟", sources: "/activity/workouts/sources" },
  MEAL: { label: "饮食热量", unit: "千卡", sources: "/nutrition/meals/sources" },
  WATER: { label: "饮水量", unit: "毫升", sources: "/nutrition/water/sources" },
  WEIGHT: { label: "体重", unit: "千克", sources: "/weight/sources" },
  HEART_RATE: { label: "心率", unit: "次/分", sources: "/heart-rate/sources" },
} as const;

type Metric = keyof typeof metrics;
type Period = {
  startDate: string;
  endDate: string;
  periodDays: number;
  validDays: number;
  requiredDays: number;
  coverageRate: number;
  sufficient: boolean;
  average: number | null;
  missingDates: string[];
  recordCount: number | null;
};
type Comparison = {
  metric: Metric;
  sourceName: string | null;
  recent: Period;
  historical: Period;
  difference: number | null;
};

const defaults = recentRange();
const form = reactive({
  metric: "SLEEP" as Metric,
  sourceName: "",
  custom: false,
  recentStart: defaults.startDate,
  recentEnd: defaults.endDate,
  historicalStart: dateAfter(defaults.startDate, -28),
  historicalEnd: dateAfter(defaults.startDate, -1),
});
const sources = ref<string[]>([]);
const sourceError = ref("");
const sourceLoading = ref(false);
const sourceRevision = ref(0);
watch(
  [() => form.metric, sourceRevision],
  async ([metric], _previous, onCleanup) => {
    let active = true;
    onCleanup(() => { active = false; });
    form.sourceName = "";
    sources.value = [];
    sourceError.value = "";
    sourceLoading.value = true;
    try {
      const list = await api<string[]>(metrics[metric].sources);
      if (active) sources.value = list;
    } catch (cause) {
      if (active) sourceError.value = errorMessage(cause);
    } finally {
      if (active) sourceLoading.value = false;
    }
  },
  { immediate: true },
);

const result = ref<Comparison | null>(null);
const loading = ref(false);
const error = ref("");
let lastQuery = new URLSearchParams({ metric: form.metric });
const periods = computed(() => {
  const comparison = result.value;
  return comparison
    ? [
        { name: "近期", ...comparison.recent },
        { name: "历史基线", ...comparison.historical },
      ]
    : [];
});
const number = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });

async function compare(query: URLSearchParams) {
  lastQuery = query;
  loading.value = true;
  error.value = "";
  result.value = null;
  try {
    result.value = await api<Comparison>(`/baseline?${query}`);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

function apply() {
  const query = new URLSearchParams({ metric: form.metric });
  if (form.sourceName) query.set("sourceName", form.sourceName);
  if (form.custom) {
    query.set("recentStart", form.recentStart);
    query.set("recentEnd", form.recentEnd);
    query.set("historicalStart", form.historicalStart);
    query.set("historicalEnd", form.historicalEnd);
  }
  void compare(query);
}

apply();
</script>

<template>
  <section class="panel" aria-label="基线比较条件">
    <form @submit.prevent="apply">
      <div class="filter-bar">
        <label>
          指标
          <select v-model="form.metric" :disabled="loading">
            <option v-for="(metric, key) in metrics" :key="key" :value="key">{{ metric.label }}</option>
          </select>
        </label>
        <label>
          数据来源
          <select v-model="form.sourceName" :disabled="loading || sourceLoading">
            <option value="">{{ sourceLoading ? "正在读取来源…" : "全部来源" }}</option>
            <option v-for="source in sources" :key="source" :value="source">{{ source }}</option>
          </select>
        </label>
        <label>
          比较周期
          <select v-model="form.custom">
            <option :value="false">近 7 天与此前 28 天</option>
            <option :value="true">自选日期</option>
          </select>
        </label>
        <button type="submit" :disabled="loading">比较</button>
      </div>
      <template v-if="form.custom">
        <div class="filter-bar">
          <label>
            近期开始
            <input v-model="form.recentStart" type="date" :max="form.recentEnd" required />
          </label>
          <label>
            近期结束
            <input v-model="form.recentEnd" type="date" :min="form.recentStart" :max="dateAfter(form.recentStart, 365)" required />
          </label>
          <label>
            历史开始
            <input v-model="form.historicalStart" type="date" :max="form.historicalEnd" required />
          </label>
          <label>
            历史结束
            <input
              v-model="form.historicalEnd"
              type="date"
              :min="form.historicalStart"
              :max="dateAfter(form.recentStart, -1)"
              required
            />
          </label>
        </div>
        <p class="muted">每期最多 366 天，历史周期须在近期周期之前。每期至少需要 70% 的日期有有效记录。</p>
      </template>
    </form>
    <StateNotice :error="sourceError" @retry="sourceRevision++" />
  </section>

  <StateNotice :loading="loading" :error="error" @retry="compare(lastQuery)" />
  <section v-if="result" class="panel" aria-labelledby="baseline-result">
    <h2 id="baseline-result">{{ metrics[result.metric].label }}比较</h2>
    <p class="muted">来源 {{ result.sourceName ?? "全部来源" }}</p>
    <p v-if="result.metric === 'MEAL'" class="muted">
      含热量未知条目的日期不计入覆盖。日均值基于当天已记录的手动及估算热量。
    </p>
    <div class="table-wrap">
      <table>
        <thead>
          <tr><th scope="col">周期</th><th scope="col">日期范围</th>
            <th scope="col">{{ result.metric === "HEART_RATE" ? "采样均值" : "日均值" }}</th><th scope="col">有效覆盖</th></tr>
        </thead>
        <tbody>
          <tr v-for="period in periods" :key="period.name">
            <th scope="row">{{ period.name }}</th>
            <td>{{ period.startDate }} 至 {{ period.endDate }}</td>
            <td>{{ period.average === null ? "无有效记录" : `${number.format(period.average)} ${metrics[result.metric].unit}` }}</td>
            <td>
              {{ period.validDays }} / {{ period.periodDays }} 天（{{ period.coverageRate }}%）
              <span v-if="result.metric === 'HEART_RATE'"> · {{ period.recordCount }} 条采样</span>
              <br />{{ period.sufficient ? "达标" : "覆盖不足" }}，至少需 {{ period.requiredDays }} 天
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="result.difference !== null">
      {{ result.metric === "HEART_RATE" ? "采样均值差" : "日均差值" }}（近期减历史）
      <strong>{{ result.difference > 0 ? "+" : "" }}{{ number.format(result.difference) }} {{ metrics[result.metric].unit }}</strong>
    </p>
    <p v-else>覆盖不足，仅展示各期已有记录的均值。</p>
    <details v-for="period in periods" :key="period.name" class="baseline-missing">
      <summary>{{ period.name }}缺失日期（{{ period.missingDates.length }} 天）</summary>
      <p>{{ period.missingDates.length ? period.missingDates.join("、") : "日期记录齐全" }}</p>
    </details>
  </section>
</template>

<style scoped>
.baseline-missing {
  margin-top: 0.75rem;
}
.baseline-missing summary {
  cursor: pointer;
}
</style>
