<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ArrowRight, Plus } from "@lucide/vue";
import { api, dateAfter, errorMessage, recentRange } from "../api";
import type { SleepStatistics } from "../types";
import StateNotice from "../components/StateNotice.vue";
import SleepSummary from "../components/SleepSummary.vue";
const range = reactive({ ...recentRange(), sourceName: "" });
const sources = ref<string[]>([]),
  statistics = ref<SleepStatistics | null>(null),
  loading = ref(true),
  error = ref("");
async function load() {
  loading.value = true;
  error.value = "";
  statistics.value = null;
  try {
    sources.value = await api<string[]>("/sleep/sources");
    const { sourceName, ...dates } = range;
    statistics.value = await api<SleepStatistics>(
      `/sleep/statistics?${new URLSearchParams({ ...dates, ...(sourceName ? { sourceName } : {}) })}`,
    );
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="intro-strip">
    <div>
      <span class="eyebrow">认识你的睡眠节律</span>
      <p>从真实记录开始，给改变一点时间。</p>
    </div>
    <RouterLink class="button secondary" to="/records"
      ><Plus :size="16" />添加睡眠记录</RouterLink
    >
  </section>
  <form class="filter-bar" @submit.prevent="load">
    <label
      >开始日期<input
        v-model="range.startDate"
        type="date"
        required
        :max="range.endDate" /></label
    ><label
      >结束日期<input
        v-model="range.endDate"
        type="date"
        required
        :min="range.startDate"
        :max="dateAfter(range.startDate, 365)" /></label
    ><label
      >数据来源<select v-model="range.sourceName">
        <option value="">全部来源</option>
        <option v-for="source in sources" :key="source" :value="source">
          {{ source }}
        </option>
      </select></label
    ><button :disabled="loading">查看统计</button>
  </form>
  <p class="footnote">
    每次最多查看 366 天；同日不同来源的时段重叠时，请选择一个来源后查看统计。
  </p>
  <StateNotice :loading="loading" :error="error" @retry="load" /><SleepSummary
    v-if="statistics && !loading && !error"
    :statistics="statistics"
  />
  <section class="next-step">
    <div>
      <h2>把观察，变成下一步行动</h2>
      <p>健康助手可在授权后读取指定日期的数据，生成分析、计划草稿或周报。</p>
    </div>
    <RouterLink class="text-link" to="/agent"
      >前往健康助手<ArrowRight :size="17"
    /></RouterLink>
  </section>
</template>
