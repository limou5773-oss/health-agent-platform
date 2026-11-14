<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { api, errorMessage, json } from "../api";
import type { DraftItem, Plan } from "../types";
import StateNotice from "./StateNotice.vue";

type Content = Pick<Plan, "title" | "reason" | "startDate" | "endDate"> & {
  items: (DraftItem & { id: number | null; reminderTime: string | null })[];
};
type Preview = {
  planVersion: number;
  latestVersion: number;
  conflicts: {
    itemId: number; title: string; scheduledAt: string; priorityPlanId: number;
    priorityItemId: number; priorityTitle: string; priorityAt: string; reason: string; suggestion: string;
  }[];
  draft: Content | null;
  note: string;
};

const props = defineProps<{ plan: Plan; plans: Plan[] }>();
const emit = defineEmits<{ propose: [content: Content, note: string, version: number, latestVersion: number] }>();
const options = computed(() => props.plans.filter(item => item.id !== props.plan.id && item.status === "ACTIVE"));
const form = reactive({ priorityPlanIds: [] as number[], availableFrom: "", availableUntil: "" });
const result = ref<Preview>();
const loading = ref(false);
const error = ref("");
watch([form, () => props.plan.version], () => { result.value = undefined; error.value = ""; });

async function preview() {
  loading.value = true;
  result.value = undefined;
  error.value = "";
  try {
    result.value = await api<Preview>(`/plans/${props.plan.id}/coordination/preview`, json("POST", form));
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <details v-if="plan.status === 'ACTIVE'">
    <summary>多目标协调</summary>
    <p v-if="!options.length" class="muted">有另一份进行中的计划后，即可检查两份计划的安排。</p>
    <template v-else>
      <p class="muted">选择优先保留的计划和每日可用时段。建议仅调整当前计划。</p>
      <form @submit.prevent="preview">
        <fieldset :disabled="loading">
          <legend>优先保留</legend>
          <label v-for="option in options" :key="option.id" class="checkbox-label">
            <input v-model="form.priorityPlanIds" type="checkbox" :value="option.id"
              :disabled="form.priorityPlanIds.length >= 20 && !form.priorityPlanIds.includes(option.id)" />
            {{ option.title }}
          </label>
          <div class="filter-bar">
            <label>可用起始<input v-model="form.availableFrom" type="time" required /></label>
            <label>可用结束<input v-model="form.availableUntil" type="time" :min="form.availableFrom" required /></label>
            <button :disabled="!form.priorityPlanIds.length">检查安排</button>
          </div>
        </fieldset>
      </form>
      <StateNotice :loading="loading" :error="error" @retry="preview" />
      <div v-if="result">
        <p v-if="!result.conflicts.length">所选计划之间没有发现安排冲突。</p>
        <ul v-else>
          <li v-for="row in result.conflicts" :key="row.itemId">
            <strong>{{ row.reason }}</strong> · {{ row.scheduledAt.replace("T", " ") }} {{ row.title }}
            <p class="small">优先计划 #{{ row.priorityPlanId }} · {{ row.priorityAt.replace("T", " ") }} {{ row.priorityTitle }}</p>
            <p>{{ row.suggestion }}</p>
          </li>
        </ul>
        <button v-if="result.draft" type="button" class="secondary"
          @click="emit('propose', result.draft, result.note, result.planVersion, result.latestVersion)">
          带入修订草稿
        </button>
      </div>
    </template>
  </details>
</template>

<style scoped>
details { margin-block: 1rem; }
summary { cursor: pointer; }
</style>
