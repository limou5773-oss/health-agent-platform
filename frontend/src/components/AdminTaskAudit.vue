<script setup lang="ts">
import { ref, watch } from "vue";
import { api, errorMessage, statusText } from "../api";
import StateNotice from "./StateNotice.vue";

type Audit = {
  task: {
    id: string; status: string; planId: number | null; reportId: number | null;
    confirmedVersion: number | null; confirmedAt: string | null;
    feedback: "HELPFUL" | "NOT_HELPFUL" | null; feedbackAt: string | null;
  };
  steps: { id: number; name: string; status: string; createdAt: string }[];
};
const props = defineProps<{ id: string }>();
const emit = defineEmits<{ close: [] }>();
const details = ref<Audit | null>(null);
const loading = ref(true);
const error = ref("");
const stepLabels: Record<string, string> = {
  get_sleep_statistics: "查询睡眠统计",
  get_profile: "读取授权档案",
  get_plans: "查询已有计划",
  search_knowledge: "检索知识资料",
  get_authorized_diary: "读取授权日记",
};
async function load() {
  loading.value = true;
  error.value = "";
  try {
    details.value = await api<Audit>(`/admin/tasks/${encodeURIComponent(props.id)}`);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
watch(() => props.id, load, { immediate: true });
</script>

<template>
  <section class="panel">
    <div class="section-heading">
      <h2>任务运行详情</h2>
      <div class="actions">
        <button class="secondary" :disabled="loading" @click="load">刷新详情</button>
        <button class="secondary" @click="emit('close')">关闭</button>
      </div>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <template v-if="details && !loading && !error">
      <p class="muted">#{{ details.task.id }} · {{ statusText[details.task.status] }}</p>
      <dl class="meta-list">
        <div><dt>产物编号</dt><dd>
          <span v-if="details.task.planId">计划 #{{ details.task.planId }}</span>
          <span v-if="details.task.reportId">报告 #{{ details.task.reportId }}</span>
          <span v-if="!details.task.planId && !details.task.reportId">暂无产物</span>
        </dd></div>
        <div><dt>计划确认</dt><dd>
          <template v-if="details.task.confirmedVersion !== null">
            草稿第 {{ details.task.confirmedVersion }} 版 · {{ details.task.confirmedAt?.replace("T", " ") ?? "无历史时间记录" }}
          </template>
          <template v-else>暂无确认记录</template>
        </dd></div>
        <div><dt>用户反馈</dt><dd>
          {{ details.task.feedback === "HELPFUL" ? "有帮助" : details.task.feedback === "NOT_HELPFUL" ? "没有帮助" : "未提交" }}
          <span v-if="details.task.feedbackAt"> · {{ details.task.feedbackAt.replace("T", " ") }}</span>
        </dd></div>
      </dl>
      <h3>执行步骤</h3>
      <p v-if="!details.steps.length" class="muted">暂无步骤记录。</p>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th scope="col">步骤</th><th scope="col">调用摘要</th><th scope="col">状态</th><th scope="col">记录时间</th></tr></thead>
          <tbody><tr v-for="step in details.steps" :key="step.id">
            <td>#{{ step.id }}</td>
            <td>{{ stepLabels[step.name] ?? step.name }}</td>
            <td>{{ statusText[step.status] }}</td>
            <td>{{ step.createdAt.replace("T", " ") }}</td>
          </tr></tbody>
        </table>
      </div>
    </template>
  </section>
</template>
