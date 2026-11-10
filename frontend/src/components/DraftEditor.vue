<script setup lang="ts">
import { reactive, ref } from "vue";
import { Plus, Trash2 } from "@lucide/vue";
import { api, dateAfter, errorMessage, json } from "../api";
import type { PlanDraft, TaskDetail } from "../types";
const props = defineProps<{ taskId: string; draft: PlanDraft }>();
const emit = defineEmits<{
  updated: [task: TaskDetail];
  confirmed: [planId: number];
}>();
const form = reactive<PlanDraft>(JSON.parse(JSON.stringify(props.draft)));
const busy = ref(false),
  error = ref(""),
  dirty = ref(false);
const operationId = crypto.randomUUID();
function addItem() {
  form.items.push({
    date: form.startDate,
    time: "",
    title: "",
    description: "",
  });
  dirty.value = true;
}
async function save() {
  busy.value = true;
  error.value = "";
  try {
    const task = await api<TaskDetail>(
      `/agent/tasks/${encodeURIComponent(props.taskId)}/draft`,
      json("PUT", form),
    );
    dirty.value = false;
    emit("updated", task);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function confirm() {
  busy.value = true;
  error.value = "";
  try {
    const result = await api<{ planId: number }>(
      `/agent/tasks/${encodeURIComponent(props.taskId)}/confirm`,
      json("POST", { version: props.draft.version, operationId }),
    );
    emit("confirmed", result.planId);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="draft-editor">
    <div class="section-heading">
      <div>
        <h3>计划草稿</h3>
        <p>版本 {{ draft.version }} · 核对后确认，才会生成正式计划。</p>
      </div>
      <span class="tag warm">等待你确认</span>
    </div>
    <p v-if="error" role="alert" class="feedback error">{{ error }}</p>
    <form @submit.prevent="save" @input="dirty = true">
      <div class="form-grid">
        <label class="span-all"
          >计划名称<input
            v-model="form.title"
            required
            maxlength="200" /></label
        ><label
          >开始日期<input
            v-model="form.startDate"
            type="date"
            required
            :max="form.endDate" /></label
        ><label
          >结束日期<input
            v-model="form.endDate"
            type="date"
            required
            :min="form.startDate"
            :max="dateAfter(form.startDate, 30)" /></label
        ><label class="span-all"
          >制定理由<textarea
            v-model="form.reason"
            rows="3"
            required
            maxlength="4000"
          />
        </label>
      </div>
      <p class="footnote">
        计划最多 31 天、93 个行动；每项日期必须在计划周期内。
      </p>
      <fieldset
        v-for="(item, index) in form.items"
        :key="index"
        class="draft-item"
      >
        <legend>行动 {{ index + 1 }}</legend>
        <div class="form-grid">
          <label
            >执行日期<input
              v-model="item.date"
              type="date"
              required
              :min="form.startDate"
              :max="form.endDate" /></label
          ><label>时间<input v-model="item.time" type="time" required /></label
          ><label class="span-all"
            >行动名称<input
              v-model="item.title"
              required
              maxlength="200" /></label
          ><label class="span-all"
            >具体做法<textarea
              v-model="item.description"
              rows="2"
              required
              maxlength="2000"
            />
          </label>
        </div>
        <button
          type="button"
          class="text-button danger-text"
          :disabled="busy"
          @click="
            form.items.splice(index, 1);
            dirty = true;
          "
        >
          <Trash2 :size="15" />删除这项
        </button>
      </fieldset>
      <div class="actions">
        <button
          type="button"
          class="secondary"
          :disabled="busy || form.items.length >= 93"
          @click="addItem"
        >
          <Plus :size="16" />增加行动</button
        ><button :disabled="busy || !dirty">保存草稿修改</button>
      </div>
    </form>
    <div class="draft-confirm">
      <p>
        {{
          dirty
            ? "你有尚未保存的修改，请先保存，再确认版本。"
            : `确认后将按版本 ${draft.version} 创建正式计划。`
        }}
      </p>
      <button :disabled="busy || dirty || !form.items.length" @click="confirm">
        {{ busy ? "处理中…" : "确认并创建计划" }}
      </button>
    </div>
  </section>
</template>
