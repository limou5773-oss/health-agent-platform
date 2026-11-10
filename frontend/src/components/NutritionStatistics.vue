<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ApiError, api, errorMessage, recentRange } from "../api";
import type { MealStatistics, WaterStatistics } from "../types";
import { recordTime } from "../nutrition";
import MealSummary from "./MealSummary.vue";
import WaterSummary from "./WaterSummary.vue";
import StateNotice from "./StateNotice.vue";
const props = defineProps<{
  kind: "meals" | "water";
  sources: string[];
  refreshKey: number;
}>();
const label = computed(() => (props.kind === "meals" ? "饮食" : "饮水"));
const filters = reactive({ ...recentRange(), sourceName: "" });
const meals = ref<MealStatistics | null>(null),
  water = ref<WaterStatistics | null>(null);
const summary = computed(() => meals.value ?? water.value);
const loading = ref(false),
  error = ref(""),
  selectionRequired = ref(false);
// Saving a record can refresh statistics while a previous query is still pending.
let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  error.value = "";
  selectionRequired.value = false;
  meals.value = null;
  water.value = null;
  const { sourceName, ...dates } = filters;
  try {
    const path = `/nutrition/${props.kind}/statistics?${new URLSearchParams({ ...dates, ...(sourceName ? { sourceName } : {}) })}`;
    if (props.kind === "meals") {
      const result = await api<MealStatistics>(path);
      if (currentRequest === requestId) meals.value = result;
    } else {
      const result = await api<WaterStatistics>(path);
      if (currentRequest === requestId) water.value = result;
    }
    if (currentRequest === requestId) selectionRequired.value = false;
  } catch (cause) {
    if (currentRequest === requestId) {
      error.value = errorMessage(cause);
      selectionRequired.value =
        cause instanceof ApiError && cause.code === "SOURCE_SELECTION_REQUIRED";
    }
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}
watch(() => props.refreshKey, load);
onMounted(load);
</script>
<template>
  <section class="panel" :aria-label="`${label}每日汇总`">
    <div class="section-heading">
      <div>
        <h2>{{ label }}每日汇总</h2>
        <p>日期与来源独立于记录列表和另一类统计，最多选择 366 天。</p>
      </div>
    </div>
    <form class="filter-bar" @submit.prevent="load">
      <label
        >统计开始日期<input
          v-model="filters.startDate"
          type="date"
          required
          :max="filters.endDate"
          :disabled="loading"
      /></label>
      <label
        >统计结束日期<input
          v-model="filters.endDate"
          type="date"
          required
          :min="filters.startDate"
          :disabled="loading"
      /></label>
      <label
        >统计来源<select v-model="filters.sourceName" :disabled="loading">
          <option value="">所有来源（冲突时需选择）</option>
          <option
            v-if="filters.sourceName && !sources.includes(filters.sourceName)"
            :value="filters.sourceName"
          >
            {{ filters.sourceName }}（当前筛选）
          </option>
          <option v-for="source in sources" :key="source" :value="source">
            {{ source }}
          </option>
        </select></label
      >
      <button :disabled="loading">
        {{ loading ? "统计中…" : "查看统计" }}
      </button>
    </form>
    <p v-if="selectionRequired" class="feedback" role="alert">
      所选范围存在来源冲突。请在“统计来源”中明确选择真实来源后再计算。
    </p>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <template v-if="!loading && !error && summary">
      <p class="small muted">
        {{ summary.startDate }} 至 {{ summary.endDate }} ·
        {{ summary.sourceName ?? "全部来源（无冲突的独立事件累加）" }}
      </p>
      <MealSummary v-if="meals" :statistics="meals" />
      <WaterSummary v-if="water" :statistics="water" />
      <p class="footnote">
        数据截止（范围内最新实际记录时间）：{{
          summary.dataCutoff === null
            ? "无记录"
            : recordTime(summary.dataCutoff)
        }}。每日按档案时区归属。
      </p>
    </template>
  </section>
</template>
