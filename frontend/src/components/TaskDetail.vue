<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { RefreshCw, Trash2 } from "@lucide/vue";
import { api, errorMessage, json, kindText, statusText } from "../api";
import type { TaskDetail } from "../types";
import StateNotice from "./StateNotice.vue";
import DraftEditor from "./DraftEditor.vue";
import SleepSummary from "./SleepSummary.vue";
const props = defineProps<{ id: string; sources: string[] }>();
const emit = defineEmits<{ changed: []; deleted: [] }>();
const task = ref<TaskDetail | null>(null),
  loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  busy = ref(false),
  input = ref(""),
  sourceName = ref("");
let timer: number | undefined;
let active = true;
async function load(initial = false) {
  window.clearTimeout(timer);
  error.value = "";
  if (initial) loading.value = true;
  try {
    const next = await api<TaskDetail>(
      `/agent/tasks/${encodeURIComponent(props.id)}`,
    );
    if (!active) return;
    const previousStatus = task.value?.status;
    task.value = next;
    if (previousStatus && previousStatus !== next.status) emit("changed");
    if (["QUEUED", "RUNNING"].includes(next.status))
      timer = window.setTimeout(() => {
        void load();
      }, 2500);
  } catch (cause) {
    if (active) error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function action(name: "input" | "retry" | "cancel") {
  busy.value = true;
  actionError.value = "";
  window.clearTimeout(timer);
  try {
    task.value = await api<TaskDetail>(
      `/agent/tasks/${encodeURIComponent(props.id)}/${name}`,
      json(
        "POST",
        name === "input"
          ? {
              input: input.value,
              ...(sourceName.value ? { sourceName: sourceName.value } : {}),
            }
          : undefined,
      ),
    );
    input.value = "";
    emit("changed");
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function remove() {
  if (!window.confirm("删除此任务及其会话记录？")) return;
  busy.value = true;
  actionError.value = "";
  window.clearTimeout(timer);
  try {
    await api(`/agent/tasks/${encodeURIComponent(props.id)}`, json("DELETE"));
    emit("deleted");
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function update(next: TaskDetail) {
  task.value = next;
  emit("changed");
}
function confirmed() {
  emit("changed");
  void load();
}
onMounted(() => {
  void load(true);
});
onUnmounted(() => {
  active = false;
  window.clearTimeout(timer);
});
</script>
<template>
  <section class="panel task-detail">
    <div class="section-heading">
      <h2>任务 #{{ id }}</h2>
      <div class="actions">
        <button
          class="icon-button"
          :disabled="loading || busy"
          aria-label="刷新任务"
          @click="load(true)"
        >
          <RefreshCw :size="17" /></button
        ><button
          class="icon-button danger-text"
          :disabled="busy"
          aria-label="删除任务"
          @click="remove"
        >
          <Trash2 :size="17" />
        </button>
      </div>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load(true)" />
    <p v-if="actionError" class="feedback error" role="alert">
      {{ actionError }}
    </p>
    <template v-if="task && !loading && !error"
      ><div class="task-topline">
        <span
          class="tag"
          :class="{
            danger: task.status === 'FAILED',
            warm: task.status.startsWith('WAITING'),
          }"
          >{{ statusText[task.status] }}</span
        ><span class="small muted"
          >{{ kindText[task.kind] }} ·
          {{ task.createdAt.replace("T", " ") }}</span
        >
      </div>
      <h3 class="task-goal">{{ task.goal }}</h3>
      <dl class="meta-list">
        <div>
          <dt>分析数据</dt>
          <dd>{{ task.startDate }} 至 {{ task.endDate }}</dd>
        </div>
        <div v-if="task.kind === 'PLAN'">
          <dt>计划周期</dt>
          <dd>{{ task.planStartDate }} 至 {{ task.planEndDate }}</dd>
        </div>
        <div>
          <dt>数据来源</dt>
          <dd>{{ task.sourceName || "未指定；多来源冲突时需要补充" }}</dd>
        </div>
      </dl>
      <p
        v-if="['QUEUED', 'RUNNING'].includes(task.status)"
        class="feedback"
        role="status"
      >
        <span class="loading-dot"></span>任务正在处理，状态每 2.5 秒更新。
      </p>
      <div v-if="task.status === 'FAILED'" class="feedback error" role="alert">
        <strong>任务未完成</strong>
        <p>{{ task.error }}</p>
        <button
          v-if="task.retryable"
          class="secondary"
          :disabled="busy"
          @click="action('retry')"
        >
          手动重试此任务
        </button>
        <p v-else class="small">
          此错误不可重试，请按错误信息修正条件后再创建任务。
        </p>
      </div>
      <form
        v-if="task.status === 'WAITING_INPUT'"
        class="supplement-form"
        @submit.prevent="action('input')"
      >
        <h3>需要你的补充</h3>
        <p class="prose">{{ task.question }}</p>
        <label
          >补充说明<textarea
            v-model="input"
            required
            rows="3"
            maxlength="4000"
            placeholder="填写需要补充的信息"
          /></label
        ><label
          >指定数据来源（需要时选择）<select v-model="sourceName">
            <option value="">保持当前来源</option>
            <option v-for="source in sources" :key="source">
              {{ source }}
            </option>
          </select></label
        ><button :disabled="busy">补充并继续</button>
      </form>
      <section v-if="task.answer" class="answer-section">
        <h3>分析结果</h3>
        <div class="prose">{{ task.answer }}</div>
      </section>
      <SleepSummary
        v-if="task.statistics"
        :statistics="task.statistics"
      /><DraftEditor
        v-if="task.status === 'WAITING_CONFIRMATION' && task.draft"
        :key="task.draft.version"
        :task-id="task.id"
        :draft="task.draft"
        @updated="update"
        @confirmed="confirmed"
      />
      <div v-if="task.planId || task.reportId" class="result-links">
        <RouterLink
          v-if="task.planId"
          class="button"
          :to="{ path: '/plans', query: { id: task.planId } }"
          >查看已确认计划</RouterLink
        ><RouterLink
          v-if="task.reportId"
          class="button"
          :to="{ path: '/reports', query: { id: task.reportId } }"
          >查看睡眠报告</RouterLink
        >
      </div>
      <section class="task-steps">
        <h3>执行过程</h3>
        <p v-if="!task.steps.length" class="muted small">
          暂时还没有步骤记录。
        </p>
        <ol v-else class="timeline">
          <li v-for="step in task.steps" :key="step.id">
            <div class="step-heading">
              <strong>{{ step.name }}</strong
              ><span
                class="tag"
                :class="{ danger: step.status === 'FAILED' }"
                >{{ statusText[step.status] || step.status }}</span
              >
            </div>
            <p v-if="step.inputSummary" class="small">
              输入：{{ step.inputSummary }}
            </p>
            <p v-if="step.outputSummary" class="small prose">
              输出：{{ step.outputSummary }}
            </p>
            <time class="small muted">{{
              step.createdAt.replace("T", " ")
            }}</time>
          </li>
        </ol>
      </section>
      <div
        v-if="
          [
            'QUEUED',
            'RUNNING',
            'WAITING_INPUT',
            'WAITING_CONFIRMATION',
          ].includes(task.status)
        "
        class="task-footer"
      >
        <button class="secondary" :disabled="busy" @click="action('cancel')">
          取消此任务</button
        ><span class="small muted">取消后不会继续读取数据或执行工具。</span>
      </div></template
    >
  </section>
</template>
