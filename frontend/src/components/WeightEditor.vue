<script setup lang="ts">
import { reactive, ref } from "vue";
import { api, errorMessage, json } from "../api";
import type { WeightRecord } from "../types";

const props = defineProps<{ record: WeightRecord | null; sources: string[] }>();
const emit = defineEmits<{ saved: []; close: []; busy: [value: boolean] }>();
const form = reactive({
  measuredAt: props.record?.measuredAt ?? "",
  weightKg: props.record ? String(props.record.weightKg) : "",
  sourceName: props.record?.sourceName ?? "",
  note: props.record?.note ?? "",
});
const busy = ref(false),
  error = ref("");
async function save() {
  busy.value = true;
  emit("busy", true);
  error.value = "";
  try {
    await api<WeightRecord>(
      props.record ? `/weight/records/${props.record.id}` : "/weight/records",
      json(props.record ? "PUT" : "POST", {
        ...form,
        measuredAt:
          form.measuredAt.length === 16
            ? `${form.measuredAt}:00`
            : form.measuredAt,
        weightKg: Number(form.weightKg),
      }),
    );
    emit("saved");
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
    aria-labelledby="weight-editor-title"
    @submit.prevent="save"
  >
    <h3 id="weight-editor-title">
      {{ record ? `编辑测量 #${record.id}` : "录入实际测量" }}
    </h3>
    <div class="form-grid">
      <label
        >实际测量时间（档案时区）
        <input
          v-model="form.measuredAt"
          type="datetime-local"
          step="1"
          required
          :disabled="busy"
        />
      </label>
      <label
        >体重（kg）
        <input
          v-model="form.weightKg"
          type="number"
          min="0.001"
          max="9999.999"
          step="0.001"
          required
          :disabled="busy"
        />
        <span class="field-hint">请填写实际测量的千克数，最多 3 位小数。</span>
      </label>
      <label class="span-all"
        >记录来源
        <input
          v-model="form.sourceName"
          list="weight-record-sources"
          maxlength="100"
          required
          :disabled="busy"
        />
        <datalist id="weight-record-sources">
          <option v-for="source in sources" :key="source" :value="source" />
        </datalist>
        <span class="field-hint"
          >选择已有来源或填写实际来源；来源名称按精确字符区分。</span
        >
      </label>
      <label class="span-all"
        >备注
        <textarea
          v-model="form.note"
          rows="3"
          maxlength="1000"
          :disabled="busy"
        ></textarea>
      </label>
    </div>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <div class="actions">
      <button :disabled="busy">{{ busy ? "保存中…" : "保存测量" }}</button>
      <button
        type="button"
        class="secondary"
        :disabled="busy"
        @click="emit('close')"
      >
        取消
      </button>
    </div>
    <p class="footnote">
      同一来源同一秒的测量只能保存一条；更正数值请编辑原记录。同日其他测量不会被覆盖。
    </p>
  </form>
</template>
