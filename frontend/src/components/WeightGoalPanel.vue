<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { api, errorMessage, json } from "../api";
import type { WeightGoal, WeightGoalProgress as GoalProgress } from "../types";
import { weightText, weightTime } from "../weight";
import StateNotice from "./StateNotice.vue";
import WeightGoalEditor from "./WeightGoalEditor.vue";
import WeightGoalProgress from "./WeightGoalProgress.vue";

const props = defineProps<{ sources: string[]; refreshKey: number }>();
defineEmits<{ detail: [id: number] }>();
const goal = ref<WeightGoal | null>(null),
  progress = ref<GoalProgress | null>(null);
const loading = ref(false),
  progressLoading = ref(false),
  busy = ref(false),
  editor = ref(false);
const error = ref(""),
  progressError = ref(""),
  actionError = ref(""),
  message = ref("");
let progressRequestId = 0;
async function loadGoal() {
  loading.value = true;
  error.value = "";
  try {
    goal.value = (await api<WeightGoal | undefined>("/weight/goal")) ?? null;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function loadProgress() {
  const currentRequest = ++progressRequestId;
  progressLoading.value = true;
  progressError.value = "";
  progress.value = null;
  try {
    const result = await api<GoalProgress | undefined>("/weight/goal/progress");
    if (currentRequest === progressRequestId) progress.value = result ?? null;
  } catch (cause) {
    if (currentRequest === progressRequestId)
      progressError.value = errorMessage(cause);
  } finally {
    if (currentRequest === progressRequestId) progressLoading.value = false;
  }
}
function saved(result: WeightGoal) {
  goal.value = result;
  editor.value = false;
  message.value = "体重目标已保存。";
  void loadProgress();
}
async function remove() {
  if (!window.confirm("删除当前体重目标？测量记录会继续保留。")) return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api("/weight/goal", json("DELETE"));
    goal.value = null;
    message.value = "体重目标已删除。";
    await loadProgress();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function edit() {
  editor.value = true;
  message.value = "";
  actionError.value = "";
}
watch(() => props.refreshKey, loadProgress);
onMounted(() => {
  void Promise.all([loadGoal(), loadProgress()]);
});
</script>
<template>
  <section
    id="weight-goal"
    class="panel weight-goal"
    aria-labelledby="weight-goal-title"
  >
    <div class="section-heading">
      <div>
        <h2 id="weight-goal-title">个人体重目标</h2>
        <p>自行设置周期、目标千克数和采用的测量来源。</p>
      </div>
      <div v-if="!loading && !error" class="actions">
        <button :disabled="busy || editor" @click="edit">
          {{ goal ? "修改目标" : "设置目标" }}</button
        ><button
          v-if="goal"
          class="secondary danger-text"
          :disabled="busy || editor"
          @click="remove"
        >
          删除目标
        </button>
      </div>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="loadGoal" />
    <template v-if="!loading && !error">
      <WeightGoalEditor
        v-if="editor"
        :goal="goal"
        :sources="sources"
        @saved="saved"
        @close="editor = false"
        @busy="busy = $event"
      />
      <template v-else-if="goal">
        <p>
          <strong>{{ goal.startDate }} 至 {{ goal.targetDate }}</strong> · 目标
          {{ weightText(goal.targetWeightKg) }} ·
          {{ goal.sourceName || "全部来源" }}
        </p>
        <p class="small muted">
          创建 {{ weightTime(goal.createdAt) }} · 更新
          {{ weightTime(goal.updatedAt) }}
        </p>
      </template>
      <p v-else class="notice empty">
        尚未设置体重目标。可以先记录测量，之后再设置自己的目标。
      </p>
    </template>
    <p v-if="actionError" class="feedback error" role="alert">
      {{ actionError }}
    </p>
    <p v-if="message" class="feedback success" role="status">{{ message }}</p>
    <StateNotice
      :loading="progressLoading"
      :error="progressError"
      @retry="loadProgress"
    />
    <p v-if="progressError" class="field-hint">
      目标配置可继续修改。如果提示来源冲突，请在“修改目标”中选择采用的测量来源，再保存并重新计算。
    </p>
    <WeightGoalProgress
      v-if="!progressLoading && !progressError && progress"
      :progress="progress"
      @detail="$emit('detail', $event)"
    />
  </section>
</template>
<style scoped>
.weight-goal {
  scroll-margin-top: 5rem;
}
</style>
