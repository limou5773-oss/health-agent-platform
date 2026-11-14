<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowUpRight, Sparkles } from "@lucide/vue";
import {
  api,
  dateAfter,
  dateString,
  errorMessage,
  json,
  kindText,
  recentRange,
  statusText,
} from "../api";
import type { CombinedTaskSources, Profile, TaskDetail as Task } from "../types";
import StateNotice from "../components/StateNotice.vue";
import TaskDetail from "../components/TaskDetail.vue";
import UsePlanTemplate from "../components/UsePlanTemplate.vue";
const route = useRoute(),
  router = useRouter();
const planStart = new Date();
planStart.setDate(planStart.getDate() + 1);
const planEnd = new Date(planStart);
planEnd.setDate(planEnd.getDate() + 6);
const form = reactive({
  goal: "",
  kind: "ANALYZE",
  ...recentRange(),
  sourceName: "",
  includeDiary: false,
  planStartDate: dateString(planStart),
  planEndDate: dateString(planEnd),
});
const sourceFields = [
  { field: "stepSource", label: "步数来源", permission: "aiActivity", path: "/activity/steps/sources" },
  { field: "workoutSource", label: "运动来源", permission: "aiActivity", path: "/activity/workouts/sources" },
  { field: "weightSource", label: "体重来源", permission: "aiWeight", path: "/weight/sources" },
  { field: "heartRateSource", label: "心率来源", permission: "aiHeartRate", path: "/heart-rate/sources" },
] as const;
const combinedSources = reactive<CombinedTaskSources>({
  stepSource: null, workoutSource: null, weightSource: null, heartRateSource: null,
});
const sourceOptions = reactive<Partial<Record<keyof CombinedTaskSources, string[]>>>({});
const sourceError = ref("");
const loadingSources = ref(false);
const profile = ref<Profile | null>(null),
  tasks = ref<Task[]>([]),
  sources = ref<string[]>([]),
  loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  busy = ref(false);
const selected = computed(() => {
  const id = route.query.task;
  return typeof id === "string" && id ? id : null;
});
const availableSourceFields = computed(() => form.kind === "COMBINED"
  ? sourceFields.filter(({ permission }) => profile.value?.[permission])
  : []);
async function loadCombinedSources() {
  if (loadingSources.value) return;
  const pending = availableSourceFields.value.filter(({ field }) => !sourceOptions[field]);
  if (!pending.length) return;
  sourceError.value = "";
  loadingSources.value = true;
  try {
    const loaded = await Promise.all(pending.map(async ({ field, path }) =>
      [field, await api<string[]>(path)] as const));
    for (const [field, options] of loaded) sourceOptions[field] = options;
  } catch (cause) {
    sourceError.value = errorMessage(cause);
  } finally {
    loadingSources.value = false;
  }
}
watch(availableSourceFields, loadCombinedSources);
const canCreate = computed(() => {
  const allowed = profile.value;
  if (!allowed?.aiConsent) return false;
  return form.kind === "COMBINED"
    ? allowed.aiSleep || allowed.aiActivity || allowed.aiWeight || allowed.aiHeartRate
    : allowed.aiSleep;
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [p, t, s] = await Promise.all([
      api<Profile>("/profile"),
      api<Task[]>("/agent/tasks"),
      api<string[]>("/sleep/sources"),
    ]);
    profile.value = p;
    tasks.value = t;
    sources.value = s;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function refreshList() {
  try {
    tasks.value = await api<Task[]>("/agent/tasks");
  } catch (cause) {
    actionError.value = errorMessage(cause);
  }
}
async function create() {
  busy.value = true;
  actionError.value = "";
  const { planStartDate, planEndDate, sourceName, ...rest } = form;
  try {
    const task = await api<Task>(
      "/agent/tasks",
      json("POST", {
        ...rest,
        includeDiary: rest.includeDiary && !!profile.value?.aiDiary,
        ...(sourceName && profile.value?.aiSleep ? { sourceName } : {}),
        ...(form.kind === "PLAN" ? { planStartDate, planEndDate } : {}),
        ...Object.fromEntries(availableSourceFields.value.map(({ field }) => [field, combinedSources[field]])),
      }),
    );
    form.includeDiary = false;
    await openTask(task.id);
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function deleted() {
  await router.replace({ query: {} });
  await refreshList();
}
async function openTask(id: string) {
  await router.replace({ query: { task: id } });
  await refreshList();
}
onMounted(load);
</script>
<template>
  <div class="page-actions">
    <RouterLink class="button secondary" to="/agent/triggers">主动任务设置与记录</RouterLink>
  </div>
  <UsePlanTemplate @created="openTask" />
  <StateNotice :loading="loading" :error="error" @retry="load" /><template
    v-if="!loading && !error"
    ><div v-if="!profile?.aiConsent" class="consent-banner">
      <div>
        <strong>先决定是否授权 AI 分析</strong>
        <p>
          外部模型可按任务读取所选日期的统计与档案信息。你可以随时在档案中关闭授权。
          如果主动启用日记，还可读取相同日期内逐条授权的日记。
        </p>
      </div>
      <RouterLink class="button secondary" to="/profile"
        >查看授权设置<ArrowUpRight :size="16"
      /></RouterLink>
    </div>
    <section class="panel task-create">
      <div class="section-heading">
        <div>
          <h2><Sparkles :size="20" />发起一个健康任务</h2>
          <p>数据读取范围由你设定；计划需要核对草稿后确认。</p>
        </div>
      </div>
      <form @submit.prevent="create">
        <label
          >这次想了解或改善什么？<textarea
            v-model="form.goal"
            rows="3"
            required
            maxlength="2000"
            placeholder="描述你的问题、目标，以及需要注意的偏好"
          />
        </label>
        <div class="form-grid three">
          <label
            >任务类型<select v-model="form.kind">
              <option value="ANALYZE">睡眠分析</option>
              <option value="COMBINED">综合分析</option>
              <option value="PLAN">制定计划</option>
              <option value="REPORT">生成睡眠周报</option>
            </select></label
          ><label
            >分析开始日期<input
              v-model="form.startDate"
              type="date"
              required
              :max="form.endDate" /></label
          ><label
            >分析结束日期<input
              v-model="form.endDate"
              type="date"
              required
              :min="form.startDate"
              :max="dateAfter(form.startDate, 365)" /></label
          ><label v-if="profile?.aiSleep"
            >睡眠数据来源<select v-model="form.sourceName">
              <option value="">全部来源（冲突时补充选择）</option>
              <option v-for="source in sources" :key="source">
                {{ source }}
              </option>
            </select></label
          ><template v-if="form.kind === 'PLAN'"
            ><label
              >计划执行开始日期<input
                v-model="form.planStartDate"
                type="date"
                required
                :max="form.planEndDate" /></label
            ><label
              >计划执行结束日期<input
                v-model="form.planEndDate"
                type="date"
                required
                :min="form.planStartDate"
                :max="dateAfter(form.planStartDate, 30)" /></label
          ></template>
        </div>
        <p v-if="form.kind === 'COMBINED'" class="footnote">
          外部模型可使用所选日期内已授权的指标汇总。未指定来源的指标遇到冲突时会在结果里标出。
        </p>
        <div v-if="availableSourceFields.length" class="form-grid">
          <label v-for="{ field, label } in availableSourceFields" :key="field">
            {{ label }}
            <select v-model="combinedSources[field]" :disabled="busy || loadingSources || !!sourceError">
              <option :value="null">全部来源</option>
              <option v-for="source in sourceOptions[field]" :key="source" :value="source">{{ source }}</option>
            </select>
          </label>
        </div>
        <StateNotice v-if="availableSourceFields.length && sourceError"
          :error="sourceError" @retry="loadCombinedSources" />
        <label class="checkbox-label">
          <input
            v-model="form.includeDiary"
            type="checkbox"
            :disabled="busy || !profile?.aiConsent || !profile.aiDiary"
            aria-describedby="task-diary-hint"
          />
          本次任务允许读取已授权日记（可选）
        </label>
        <p id="task-diary-hint" class="footnote">
          需在档案开启日记分析授权、逐条允许日记读取，并勾选本项。仅限分析日期内已授权日记，日记来源不受睡眠来源筛选限制。最多读取最近
          20 条，作为个人自述背景，不作诊断。
        </p>
        <p class="footnote">
          分析窗口最多 366 天；计划执行周期最多 31 天。健康建议供日常管理参考。
        </p>
        <p v-if="actionError" class="feedback error" role="alert">
          {{ actionError }}
        </p>
        <p v-if="profile?.aiConsent && !canCreate" class="feedback">
          请在个人档案中授权当前任务所需的指标。
        </p>
        <button :disabled="busy || !canCreate">
          {{ busy ? "正在创建…" : "创建任务" }}<ArrowUpRight :size="16" />
        </button>
      </form>
    </section>
    <div class="workspace-grid">
      <section class="panel task-history">
        <div class="section-heading">
          <h2>最近任务</h2>
          <span class="small muted">最近 50 条</span>
        </div>
        <p v-if="!tasks.length" class="notice empty">
          还没有任务。先设定一个你关心的目标。
        </p>
        <ul v-else class="selection-list">
          <li v-for="task in tasks" :key="task.id">
            <RouterLink
              :to="{ path: '/agent', query: { task: task.id } }"
              :class="{ selected: selected === task.id }"
              ><span class="small muted"
                >#{{ task.id }} · {{ kindText[task.kind] }}</span
              ><strong>{{ task.goal }}</strong>
              <div>
                <span
                  class="tag"
                  :class="{ danger: task.status === 'FAILED' }"
                  >{{ statusText[task.status] }}</span
                ><span class="tag" :class="{ warm: !task.includeDiary }">{{
                  task.includeDiary ? "已启用授权日记" : "未启用日记"
                }}</span
                ><span class="small muted">{{
                  task.createdAt.slice(0, 10)
                }}</span>
              </div></RouterLink
            >
          </li>
        </ul>
      </section>
      <TaskDetail
        v-if="selected"
        :key="selected"
        :id="selected"
        :sources="sources"
        @changed="refreshList"
        @deleted="deleted"
        @followed-up="openTask"
      />
      <section v-else class="panel workspace-empty">
        <Sparkles :size="28" />
        <h2>每一步，都能看见</h2>
        <p>选择一个任务，查看执行过程、补充信息或确认计划草稿。</p>
      </section>
    </div></template
  >
</template>
