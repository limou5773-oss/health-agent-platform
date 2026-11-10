<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import StateNotice from "./StateNotice.vue";
const emit = defineEmits<{ changed: [] }>();
const goal = ref<number | null>(null),
  input = ref<number | "">("");
const loading = ref(true),
  busy = ref(false),
  error = ref(""),
  actionError = ref(""),
  message = ref("");
async function load() {
  loading.value = true;
  error.value = "";
  try {
    goal.value = (
      await api<{ dailySteps: number | null }>("/activity/goal")
    ).dailySteps;
    input.value = goal.value ?? "";
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function save() {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    goal.value = (
      await api<{ dailySteps: number }>(
        "/activity/goal",
        json("PUT", { dailySteps: Number(input.value) }),
      )
    ).dailySteps;
    message.value = "每日步数目标已更新，统计将按新目标计算。";
    emit("changed");
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
      <div>
        <h2>每日步数目标</h2>
        <p>按自己的实际情况设置；目标用于记录回顾。</p>
      </div>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <template v-if="!loading && !error">
      <p class="muted">
        {{
          goal === null
            ? "尚未设置目标。达标天数和完成比例暂不计算。"
            : `当前目标：${goal.toLocaleString("zh-CN")} 步 / 天`
        }}
      </p>
      <form class="filter-bar" @submit.prevent="save">
        <label
          >每日目标（正整数）<input
            v-model="input"
            type="number"
            min="1"
            max="2147483647"
            step="1"
            required
            :disabled="busy" /></label
        ><button :disabled="busy">{{ busy ? "保存中…" : "保存目标" }}</button>
      </form>
    </template>
    <p v-if="actionError" class="feedback error" role="alert">
      {{ actionError }}
    </p>
    <p v-if="message" class="feedback success" role="status">{{ message }}</p>
  </section>
</template>
