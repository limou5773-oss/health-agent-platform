<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { api, dateAfter, errorMessage, json, recentRange } from "../api";
import { reportMetrics, type ReportMetric } from "../report-metrics";
import type { Plan } from "../types";
import StateNotice from "./StateNotice.vue";

type Goal = {
  id: number;
  title: string;
  startDate: string;
  endDate: string;
  metric: ReportMetric;
  direction: "AT_LEAST" | "AT_MOST";
  targetValue: number;
  preferences: string;
};

const props = defineProps<{ plan: Plan | null }>();
const emit = defineEmits<{ linked: [plan: Plan]; deleted: [goalId: number] }>();
const goals = ref<Goal[]>([]);
const loading = ref(true);
const error = ref("");
const busy = ref(false);
const actionError = ref("");
const editing = ref<number | null>(null);
const editorOpen = ref(false);
const selectedGoal = ref<number | null>(null);
const form = reactive<Omit<Goal, "id" | "targetValue"> & { targetValue: number | "" }>({
  ...recentRange(), title: "", metric: "SLEEP", direction: "AT_LEAST", targetValue: "", preferences: "",
});

watch([() => props.plan?.id, () => props.plan?.goalId],
  ([, goalId]) => { selectedGoal.value = goalId ?? null; }, { immediate: true });

async function load() {
  loading.value = true;
  error.value = "";
  try {
    goals.value = await api<Goal[]>("/health-goals");
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

function edit(goal?: Goal) {
  editing.value = goal?.id ?? null;
  if (goal) {
    const { id, ...fields } = goal;
    Object.assign(form, fields);
  } else {
    Object.assign(form, recentRange(), { title: "", metric: "SLEEP", direction: "AT_LEAST", targetValue: "", preferences: "" });
    form.startDate = form.endDate;
  }
  editorOpen.value = true;
  actionError.value = "";
}

async function save() {
  busy.value = true;
  actionError.value = "";
  try {
    const id = editing.value;
    await api<Goal>(id === null ? "/health-goals" : `/health-goals/${id}`,
      json(id === null ? "POST" : "PUT", form));
    editorOpen.value = false;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function remove(goal: Goal) {
  if (!window.confirm(`删除「${goal.title}」并解除所有计划关联？`)) return;
  busy.value = true;
  actionError.value = "";
  try {
    await api(`/health-goals/${goal.id}`, json("DELETE"));
    if (selectedGoal.value === goal.id) selectedGoal.value = null;
    if (editing.value === goal.id) editorOpen.value = false;
    emit("deleted", goal.id);
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function link(plan: Plan) {
  busy.value = true;
  actionError.value = "";
  try {
    emit("linked", await api<Plan>(`/plans/${plan.id}/goal`, json("PUT", { goalId: selectedGoal.value })));
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="panel">
    <div class="section-heading">
      <h2>健康目标</h2>
      <button class="secondary" :disabled="loading || busy" @click="edit()">新增目标</button>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
    <form v-if="editorOpen" @submit.prevent="save">
      <fieldset :disabled="busy">
        <legend>{{ editing === null ? "新增目标" : "编辑目标" }}</legend>
        <div class="form-grid">
          <label class="span-all">目标名称<input v-model="form.title" required maxlength="200" /></label>
          <label>开始日期<input v-model="form.startDate" type="date" :max="form.endDate" required /></label>
          <label>结束日期<input v-model="form.endDate" type="date" :min="form.startDate"
            :max="dateAfter(form.startDate, 365)" required /></label>
          <label>指标<select v-model="form.metric">
            <option v-for="([name], key) in reportMetrics" :key="key" :value="key">{{ name }}</option>
          </select></label>
          <label>方向<select v-model="form.direction">
            <option value="AT_LEAST">至少</option><option value="AT_MOST">至多</option>
          </select></label>
          <label>目标值（{{ reportMetrics[form.metric][1] }}）
            <input v-model.number="form.targetValue" type="number" min="0" max="999999999.999" step="0.001" required />
          </label>
          <label class="span-all">偏好（选填）<textarea v-model="form.preferences" rows="2" maxlength="500" /></label>
        </div>
        <div class="actions">
          <button>保存目标</button>
          <button type="button" class="secondary" @click="editorOpen = false">取消</button>
        </div>
      </fieldset>
    </form>
    <p v-if="!loading && !error && !goals.length && !editorOpen" class="muted">暂无健康目标。</p>
    <ul v-if="goals.length" class="selection-list">
      <li v-for="goal in goals" :key="goal.id">
        <strong>{{ goal.title }}</strong>
        <p class="small muted">
          {{ goal.startDate }} 至 {{ goal.endDate }} · {{ reportMetrics[goal.metric][0] }}
          {{ goal.direction === "AT_LEAST" ? "至少" : "至多" }} {{ goal.targetValue }} {{ reportMetrics[goal.metric][1] }}
        </p>
        <p v-if="goal.preferences" class="small prose">{{ goal.preferences }}</p>
        <div class="actions">
          <button class="text-button" :disabled="busy" @click="edit(goal)">编辑</button>
          <button class="text-button danger-text" :disabled="busy" @click="remove(goal)">删除</button>
        </div>
      </li>
    </ul>
    <form v-if="plan && !loading && !error && goals.length" class="filter-bar" @submit.prevent="link(plan)">
      <label>「{{ plan.title }}」关联目标<select v-model="selectedGoal" :disabled="busy">
        <option :value="null">无关联</option>
        <option v-for="goal in goals" :key="goal.id" :value="goal.id">{{ goal.title }}</option>
      </select></label>
      <button class="secondary" :disabled="busy || selectedGoal === plan.goalId">保存关联</button>
    </form>
  </section>
</template>
