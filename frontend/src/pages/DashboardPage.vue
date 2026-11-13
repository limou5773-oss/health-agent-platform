<script setup lang="ts">
import { reactive, ref } from "vue";
import { dateAfter, recentRange } from "../api";
import DashboardMetric from "../components/DashboardMetric.vue";

const applied = ref(recentRange());
const range = reactive({ ...applied.value });
const metrics = ["sleep", "steps", "workouts", "meals", "water", "weight", "heartRate"] as const;
function apply() {
  applied.value = { ...range };
}
</script>

<template>
  <section class="panel" aria-label="看板日期范围">
    <form class="filter-bar" @submit.prevent="apply">
      <label>开始日期<input v-model="range.startDate" type="date" required :max="range.endDate" /></label>
      <label>结束日期<input v-model="range.endDate" type="date" required
        :min="range.startDate" :max="dateAfter(range.startDate, 365)" /></label>
      <button>应用日期范围</button>
    </form>
  </section>
  <div class="dashboard-grid">
    <DashboardMetric v-for="metric in metrics" :key="metric" :metric="metric"
      :start-date="applied.startDate" :end-date="applied.endDate" />
  </div>
</template>

<style scoped>
.dashboard-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.dashboard-grid > section { margin: 0; }
@media (max-width: 850px) {
  .dashboard-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
