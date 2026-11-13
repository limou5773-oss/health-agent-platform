<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { api, dateAfter, dateString, errorMessage, json } from "../api";
import type { Plan } from "../types";
import { reportMetrics as metrics } from "../report-metrics";
import StateNotice from "./StateNotice.vue";

const props = defineProps<{ plan: Plan }>();
const emit = defineEmits<{ propose: [itemId: number, date: string, reason: string, version: number] }>();
type Metric = keyof typeof metrics;
type Period = {
  startDate: string; endDate: string; periodDays: number; validDays: number; average: number | null;
  recordCount: number | null;
};
type Missed = { id: number; date: string; time: string; title: string; missedReason: string };
type Review = {
  planVersion: number; today: string; scheduled: number; completed: number;
  completionRate: number | null; missed: Missed[];
  comparison: { metric: Metric; sourceName: string | null; recent: Period; historical: Period; difference: number | null };
};
const yesterday = dateAfter(dateString(new Date()), -1);
const end = props.plan.endDate < yesterday ? props.plan.endDate : yesterday;
const initialStart = dateAfter(end, -6);
const form = reactive({
  startDate: props.plan.startDate > initialStart && props.plan.startDate <= end ? props.plan.startDate : initialStart,
  endDate: end,
  metric: "SLEEP" as Metric,
  sourceName: "",
});
const review = ref<Review | null>(null);
const loading = ref(false);
const error = ref("");
const reason = reactive<Record<number, string>>({});
const saving = ref<number | null>(null);
const saveError = ref("");
const saved = ref<number | null>(null);
const moveItem = ref<number | null>(null);
const moveDate = ref("");
const periods = computed(() => review.value ? [
  { label: "复盘周期", ...review.value.comparison.recent },
  { label: "对照周期", ...review.value.comparison.historical },
] : []);
const number = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });

async function load() {
  loading.value = true;
  error.value = "";
  review.value = null;
  moveItem.value = null;
  saved.value = null;
  saveError.value = "";
  try {
    const result = await api<Review>(`/plans/${props.plan.id}/review?${new URLSearchParams(form)}`);
    for (const item of result.missed) reason[item.id] = item.missedReason;
    review.value = result;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function saveReason(item: Missed) {
  saveError.value = "";
  saved.value = null;
  saving.value = item.id;
  try {
    await api(`/plan-items/${item.id}/missed-reason`, json("PUT", { reason: reason[item.id] }));
    item.missedReason = reason[item.id].trim();
    saved.value = item.id;
  } catch (cause) {
    saveError.value = errorMessage(cause);
  } finally {
    saving.value = null;
  }
}

function selectMove(item: Missed, today: string) {
  moveItem.value = item.id;
  moveDate.value = today;
}

watch(() => props.plan, () => { if (review.value) void load(); });
</script>

<template>
  <details class="plan-review">
    <summary>周期复盘</summary>
    <form class="filter-bar" @submit.prevent="load">
      <label>开始日期<input v-model="form.startDate" type="date" :max="form.endDate" required /></label>
      <label>结束日期<input v-model="form.endDate" type="date" :min="form.startDate"
        :max="dateAfter(form.startDate, 30)" required /></label>
      <label>对比指标
        <select v-model="form.metric">
          <option v-for="(metric, key) in metrics" :key="key" :value="key">{{ metric[0] }}</option>
        </select>
      </label>
      <label>来源全名<input v-model="form.sourceName" maxlength="100" placeholder="全部来源" /></label>
      <button :disabled="loading || saving !== null">查看复盘</button>
    </form>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <template v-if="review">
      <h3>计划完成情况</h3>
      <p v-if="review.completionRate === null">所选周期无任务。</p>
      <p v-else>已完成 {{ review.completed }} / {{ review.scheduled }} 项（{{ review.completionRate }}%）</p>
      <h3>{{ metrics[review.comparison.metric][0] }}对比</h3>
      <p class="small muted">来源 {{ review.comparison.sourceName ?? "全部来源" }}</p>
      <ul>
        <li v-for="period in periods" :key="period.label">
          {{ period.label }} {{ period.startDate }} 至 {{ period.endDate }} · 覆盖 {{ period.validDays }} / {{ period.periodDays }} 天 ·
          {{ period.average === null ? "无有效均值" : `${review.comparison.metric === "HEART_RATE" ? "采样均值" : "日均"} ${number.format(period.average)} ${metrics[review.comparison.metric][1]}` }}
          <span v-if="review.comparison.metric === 'HEART_RATE'"> · {{ period.recordCount }} 条采样</span>
        </li>
      </ul>
      <p v-if="review.comparison.difference !== null">
        {{ review.comparison.metric === "HEART_RATE" ? "采样均值差" : "日均差值" }}（复盘减对照）
        {{ number.format(review.comparison.difference) }} {{ metrics[review.comparison.metric][1] }}
      </p>
      <p v-else>至少一期覆盖未达到 70%，仅显示已有记录的统计。</p>

      <h3>未打卡任务</h3>
      <p v-if="!review.missed.length">该周期没有未打卡任务。</p>
      <p v-if="saveError" class="feedback error" role="alert">{{ saveError }}</p>
      <div v-for="item in review.missed" :key="item.id" class="review-missed">
        <strong>{{ item.date }} {{ item.time.slice(0, 5) }} · {{ item.title }}</strong>
        <form class="filter-bar" @submit.prevent="saveReason(item)">
          <label>未完成原因<input v-model="reason[item.id]" :disabled="saving === item.id" maxlength="500" placeholder="留空表示未填写" /></label>
          <button class="secondary" :disabled="saving !== null">保存原因</button>
          <span v-if="saved === item.id" role="status">已保存</span>
        </form>
        <button v-if="moveItem !== item.id" class="secondary" @click="selectMove(item, review.today)">建议改期</button>
        <form v-else class="filter-bar"
          @submit.prevent="emit('propose', item.id, moveDate, item.missedReason, review.planVersion)">
          <label>调整到<input v-model="moveDate" type="date" :min="review.today" required /></label>
          <button :disabled="saving !== null">带入修订编辑器</button>
        </form>
      </div>
    </template>
  </details>
</template>

<style scoped>
.plan-review { margin-block: 1.5rem; }
.plan-review summary { cursor: pointer; font-weight: 600; margin-bottom: 0.75rem; }
.review-missed { border-top: 1px solid var(--line); padding-block: 0.75rem; }
</style>
