<script setup lang="ts">
import { reactive, ref } from "vue";
import { api, dateAfter, errorMessage, json } from "../api";
import type { WeightGoal } from "../types";

const props = defineProps<{ goal: WeightGoal | null; sources: string[] }>();
const emit = defineEmits<{
  saved: [goal: WeightGoal];
  close: [];
  busy: [value: boolean];
}>();
const form = reactive({
  startDate: props.goal?.startDate ?? "",
  targetDate: props.goal?.targetDate ?? "",
  targetWeightKg: props.goal ? String(props.goal.targetWeightKg) : "",
  sourceName: props.goal?.sourceName ?? "",
});
const busy = ref(false),
  error = ref("");
async function save() {
  busy.value = true;
  emit("busy", true);
  error.value = "";
  try {
    const result = await api<WeightGoal>(
      "/weight/goal",
      json("PUT", {
        ...form,
        targetWeightKg: Number(form.targetWeightKg),
        sourceName: form.sourceName || null,
      }),
    );
    emit("saved", result);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
    emit("busy", false);
  }
}
</script>
<template>
  <form
    class="editor-panel"
    aria-labelledby="weight-goal-editor-title"
    @submit.prevent="save"
  >
    <h3 id="weight-goal-editor-title">
      {{ goal ? "修改体重目标" : "设置体重目标" }}
    </h3>
    <div class="form-grid">
      <label
        >周期开始日期<input
          v-model="form.startDate"
          type="date"
          required
          :max="form.targetDate"
          :disabled="busy"
      /></label>
      <label
        >目标日期<input
          v-model="form.targetDate"
          type="date"
          required
          :min="form.startDate"
          :max="dateAfter(form.startDate, 365)"
          :disabled="busy"
      /></label>
      <label
        >目标体重（kg）<input
          v-model="form.targetWeightKg"
          type="number"
          min="0.001"
          max="9999.999"
          step="0.001"
          required
          :disabled="busy"
      /></label>
      <label
        >目标采用的测量来源<select v-model="form.sourceName" :disabled="busy">
          <option value="">全部来源（冲突时需明确选择）</option>
          <option
            v-if="form.sourceName && !sources.includes(form.sourceName)"
            :value="form.sourceName"
          >
            {{ form.sourceName }}（已保存来源）
          </option>
          <option v-for="source in sources" :key="source" :value="source">
            {{ source }}
          </option>
        </select></label
      >
    </div>
    <p class="field-hint">
      周期为 1 至 366
      天。基线取周期内首个有效日的最后一次测量；没有记录时不计算进度。请填写自己的目标数值，平台不预设目标。
    </p>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <div class="actions">
      <button :disabled="busy">{{ busy ? "保存中…" : "保存目标" }}</button
      ><button
        type="button"
        class="secondary"
        :disabled="busy"
        @click="$emit('close')"
      >
        取消
      </button>
    </div>
  </form>
</template>
