<script setup lang="ts">
import { computed, ref } from "vue";
import { api, dateString, errorMessage, json } from "../api";
import { templateCategories } from "../plan-templates";
import type { PlanTemplate } from "../plan-templates";
import type { TaskDetail } from "../types";
import StateNotice from "./StateNotice.vue";

const emit = defineEmits<{ created: [taskId: string] }>();
const templates = ref<PlanTemplate[]>([]);
const selected = ref<number | null>(null);
const date = ref(dateString(new Date()));
const open = ref(false), loading = ref(false), busy = ref(false), error = ref(""), actionError = ref("");
const template = computed(() => templates.value.find(entry => entry.id === selected.value));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    templates.value = await api<PlanTemplate[]>("/plan-templates");
    if (!template.value) selected.value = templates.value[0]?.id ?? null;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function create() {
  if (!template.value) return;
  busy.value = true;
  actionError.value = "";
  try {
    const task = await api<TaskDetail>(`/plan-templates/${template.value.id}/draft`,
      json("POST", { version: template.value.version, startDate: date.value }));
    open.value = false;
    emit("created", task.id);
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <section class="panel">
    <button v-if="!open" class="secondary" @click="open = true; load()">从计划模板创建草稿</button>
    <template v-else>
      <div class="section-heading">
        <h2>从计划模板创建草稿</h2>
        <button class="secondary" :disabled="busy" @click="open = false">关闭</button>
      </div>
      <StateNotice :loading="loading" :error="error" :empty="!templates.length" title="暂无启用的模板" @retry="load" />
      <form v-if="template && !loading && !error" @submit.prevent="create">
        <div class="filter-bar">
          <label>模板<select v-model="selected" :disabled="busy">
            <option v-for="entry in templates" :key="entry.id" :value="entry.id">
              {{ templateCategories[entry.category] }} · {{ entry.title }}
            </option>
          </select></label>
          <label>开始日期<input v-model="date" type="date" required :disabled="busy" /></label>
          <button :disabled="busy">生成待确认草稿</button>
        </div>
        <p class="muted">第 {{ template.version }} 版 · {{ template.days }} 天 · {{ template.items.length }} 项任务</p>
        <p class="prose">{{ template.instructions }}</p>
        <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
        <button v-if="actionError" type="button" class="secondary" :disabled="busy" @click="load">刷新模板</button>
      </form>
    </template>
  </section>
</template>
