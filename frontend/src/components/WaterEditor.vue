<script setup lang="ts">
import { reactive, ref } from "vue";
import { X } from "@lucide/vue";
import { api, errorMessage, json } from "../api";
import type { WaterRecord } from "../types";
const props = defineProps<{ record: WaterRecord | null; sources: string[] }>();
const emit = defineEmits<{ saved: []; close: []; busy: [value: boolean] }>();
const form = reactive({
  consumedAt: props.record?.consumedAt.slice(0, 19) ?? "",
  amountMl: props.record?.amountMl ?? ("" as string | number),
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
    await api(
      props.record ? `/nutrition/water/${props.record.id}` : "/nutrition/water",
      json(props.record ? "PUT" : "POST", {
        ...form,
        consumedAt:
          form.consumedAt.length === 16
            ? `${form.consumedAt}:00`
            : form.consumedAt,
        amountMl: Number(form.amountMl),
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
  <section class="editor-panel" aria-label="饮水编辑表单">
    <div class="section-heading">
      <h3>{{ record ? "编辑饮水记录" : "新增饮水记录" }}</h3>
      <button
        class="icon-button"
        :disabled="busy"
        aria-label="关闭饮水编辑"
        @click="$emit('close')"
      >
        <X :size="18" />
      </button>
    </div>
    <form class="form-grid" @submit.prevent="save">
      <label
        >饮用时间（档案时区）<input
          v-model="form.consumedAt"
          type="datetime-local"
          step="1"
          required
          :disabled="busy"
      /></label>
      <label
        >实际饮水量（毫升）<input
          v-model="form.amountMl"
          type="number"
          min="0.01"
          max="999999999.99"
          step="0.01"
          required
          :disabled="busy"
      /></label>
      <label class="span-all"
        >记录来源<input
          v-model="form.sourceName"
          list="water-sources"
          maxlength="100"
          required
          placeholder="填写实际记录来源"
          :disabled="busy"
      /></label>
      <label class="span-all"
        >备注（选填）<textarea
          v-model="form.note"
          rows="2"
          maxlength="1000"
          :disabled="busy"
        />
      </label>
      <p class="field-hint span-all">
        固定以毫升保存，仅汇总实际录入的水量。请按档案时区填写实际饮用时间。
      </p>
      <div class="actions span-all">
        <button :disabled="busy">{{ busy ? "保存中…" : "保存饮水记录" }}</button
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
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <datalist id="water-sources">
      <option v-for="source in sources" :key="source" :value="source" />
    </datalist>
  </section>
</template>
