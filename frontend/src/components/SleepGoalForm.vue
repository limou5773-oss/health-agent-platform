<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import StateNotice from "./StateNotice.vue";

type Goal = { minutes: number | null };
const emit = defineEmits<{ saved: [] }>();
const minutes = ref<number | "">("");
const loading = ref(true);
const saving = ref(false);
const loadError = ref("");
const saveError = ref("");
const saved = ref(false);

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const goal = await api<Goal>("/sleep/goal");
    minutes.value = goal.minutes ?? "";
  } catch (cause) {
    loadError.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  saveError.value = "";
  saved.value = false;
  try {
    const goal = await api<Goal>("/sleep/goal", json("PUT", { minutes: minutes.value === "" ? null : minutes.value }));
    minutes.value = goal.minutes ?? "";
    saved.value = true;
    emit("saved");
  } catch (cause) {
    saveError.value = errorMessage(cause);
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <details>
    <summary>设置每日睡眠目标</summary>
    <StateNotice :loading="loading" :error="loadError" @retry="load" />
    <form v-if="!loading && !loadError" class="filter-bar" @submit.prevent="save">
      <label>每日睡眠时长（分钟）
        <input v-model.number="minutes" type="number" min="1" max="1440" step="1"
          placeholder="留空表示不设目标" :disabled="saving" @input="saved = false" />
      </label>
      <button class="secondary" :disabled="saving">保存目标</button>
    </form>
    <p v-if="saveError" class="feedback error" role="alert">{{ saveError }}</p>
    <p v-if="saved" class="small" role="status">睡眠目标已更新。</p>
  </details>
</template>
