<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { api, errorMessage, json } from "../api";
import StateNotice from "./StateNotice.vue";

const fields = {
  GOAL: { label: "长期目标", example: "例如保持规律作息" },
  EXERCISE: { label: "运动偏好", example: "例如喜欢散步，避免跳跃动作" },
  ROUTINE: { label: "作息", example: "例如工作日 23:00 前休息" },
  REMINDER: { label: "提醒时段", example: "例如工作日晚饭后" },
} as const;
type Kind = keyof typeof fields;
type Preference = { kind: Kind; content: string };

const drafts = reactive<Record<Kind, string>>({ GOAL: "", EXERCISE: "", ROUTINE: "", REMINDER: "" });
const saved = reactive<Partial<Record<Kind, string>>>({});
const loading = ref(true);
const loadError = ref("");
const busy = ref<Kind | null>(null);
const feedback = ref<{ kind: Kind; message: string; error: boolean } | null>(null);

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    for (const item of await api<Preference[]>("/profile/preferences")) {
      saved[item.kind] = item.content;
      drafts[item.kind] = item.content;
    }
  } catch (cause) {
    loadError.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function update(kind: Kind, remove = false) {
  busy.value = kind;
  feedback.value = null;
  const path = `/profile/preferences/${kind}`;
  try {
    if (remove) {
      await api(path, json("DELETE"));
      delete saved[kind];
      drafts[kind] = "";
    } else {
      const item = await api<Preference>(path, json("PUT", { content: drafts[kind] }));
      saved[kind] = item.content;
      drafts[kind] = item.content;
    }
    feedback.value = { kind, message: remove ? "已删除" : "已保存", error: false };
  } catch (cause) {
    feedback.value = { kind, message: errorMessage(cause), error: true };
  } finally {
    busy.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="panel preference-panel" aria-labelledby="preferences-heading">
    <h2 id="preferences-heading">长期偏好记忆</h2>
    <p class="muted">逐项保存希望健康助手记住的偏好。开启 AI 分析授权后，生成计划草稿时会读取已保存的内容。</p>
    <StateNotice :loading="loading" :error="loadError" @retry="load" />
    <div v-if="!loading && !loadError" class="form-grid">
      <form v-for="(field, kind) in fields" :key="kind" @submit.prevent="update(kind)">
        <label>
          {{ field.label }}
          <textarea v-model="drafts[kind]" rows="2" required maxlength="500"
            :placeholder="field.example" :disabled="busy === kind" />
        </label>
        <div class="actions">
          <button :disabled="busy !== null || !drafts[kind].trim() || drafts[kind].trim() === saved[kind]">
            保存
          </button>
          <button v-if="saved[kind]" type="button" class="secondary" :disabled="busy !== null"
            @click="update(kind, true)">删除</button>
          <span v-if="feedback?.kind === kind" :role="feedback.error ? 'alert' : 'status'" class="small">
            {{ feedback.message }}
          </span>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.preference-panel {
  grid-column: 1 / -1;
}
.preference-panel .actions {
  margin-top: 0.65rem;
}
</style>
