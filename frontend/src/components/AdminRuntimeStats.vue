<script setup lang="ts">
import { importKindText, type ImportKind } from "../import-template";
import { onMounted, reactive, ref } from "vue";
import { api, dateAfter, errorMessage, recentRange, statusText } from "../api";
import StateNotice from "./StateNotice.vue";

type Stats = {
  startDate: string;
  endDate: string;
  taskStatuses: { status: string; count: number }[];
  timedTasks: number;
  averageDurationMs: number | null;
  completionRate: number | null;
  toolFailures: { tool: string; count: number }[];
  imports: { kind: ImportKind; batches: number; confirmed: number; rejected: number; inputRows: number; insertedRows: number }[];
  scheduledItems: number;
  completedItems: number;
  planCompletionRate: number | null;
};
const form = reactive(recentRange());
const stats = ref<Stats | null>(null);
const loading = ref(true);
const error = ref("");
const taskStates = ["COMPLETED", "FAILED", "CANCELLED", "QUEUED", "RUNNING", "WAITING_INPUT", "WAITING_CONFIRMATION"];
const toolNames: Record<string, string> = {
  get_sleep_statistics: "睡眠统计",
  get_profile: "健康档案",
  get_plans: "已有计划",
  search_knowledge: "知识检索",
  get_authorized_diary: "授权日记",
};

async function load() {
  loading.value = true;
  error.value = "";
  try {
    stats.value = await api<Stats>(`/admin/runtime-stats?${new URLSearchParams(form)}`);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <section class="panel">
    <h2>系统运行统计</h2>
    <form class="filter-bar" @submit.prevent="load">
      <label>开始日期<input v-model="form.startDate" type="date" required :max="form.endDate" /></label>
      <label>结束日期<input v-model="form.endDate" type="date" required
        :min="form.startDate" :max="dateAfter(form.startDate, 365)" /></label>
      <button :disabled="loading">查询统计</button>
    </form>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <p v-if="stats && !loading && !error" class="muted">统计周期 {{ stats.startDate }} 至 {{ stats.endDate }}</p>
  </section>

  <template v-if="stats && !loading && !error">
    <section class="panel">
      <h2>任务执行</h2>
      <p>完成率 <strong>{{ stats.completionRate === null ? "无已结束任务" : `${stats.completionRate}%` }}</strong></p>
      <p>平均执行耗时 <strong>{{ stats.averageDurationMs === null ? "暂无采样" : `${(stats.averageDurationMs / 1000).toFixed(2)} 秒` }}</strong>
        <span class="muted"> · {{ stats.timedTasks }} 项已采样任务</span>
      </p>
      <p class="muted">按任务创建日期归集当前状态。完成率以完成、失败及取消数之和为分母。耗时累计实际执行轮次。</p>
      <div class="table-wrap">
        <table>
          <thead><tr><th scope="col">状态</th><th scope="col">任务数</th></tr></thead>
          <tbody>
            <tr v-for="state in taskStates" :key="state">
              <th scope="row">{{ statusText[state] }}</th>
              <td>{{ stats.taskStatuses.find(item => item.status === state)?.count ?? 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <h3>工具失败</h3>
      <p v-if="!stats.toolFailures.length" class="muted">本周期无已记录的工具失败。</p>
      <ul v-else>
        <li v-for="failure in stats.toolFailures" :key="failure.tool">
          {{ toolNames[failure.tool] }} · {{ failure.count }} 次
        </li>
      </ul>
    </section>

    <section class="panel">
      <h2>导入量</h2>
      <p class="muted">按批次创建日期统计。预览拒绝单独计数。</p>
      <div class="table-wrap">
        <table>
          <thead><tr><th scope="col">类型</th><th scope="col">批次</th><th scope="col">已确认</th>
            <th scope="col">预览拒绝</th><th scope="col">输入行数</th><th scope="col">已入库行数</th></tr></thead>
          <tbody>
            <tr v-for="row in stats.imports" :key="row.kind">
              <th scope="row">{{ importKindText[row.kind] }}</th>
              <td>{{ row.batches }}</td><td>{{ row.confirmed }}</td><td>{{ row.rejected }}</td>
              <td>{{ row.inputRows }}</td><td>{{ row.insertedRows }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="panel">
      <h2>计划完成情况</h2>
      <p v-if="stats.planCompletionRate === null">本周期无有效任务。</p>
      <p v-else>已打卡 {{ stats.completedItems }} / {{ stats.scheduledItems }} 项（{{ stats.planCompletionRate }}%）</p>
      <p class="muted">按安排日期统计当前有效任务及对应的实际打卡。</p>
    </section>
  </template>
</template>
