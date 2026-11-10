<script setup lang="ts">
import { reactive, ref } from "vue";
import { api, errorMessage, json } from "../api";
import type { DiaryRecord } from "../types";

const props = defineProps<{ record: DiaryRecord | null; sources: string[] }>();
const emit = defineEmits<{ saved: []; close: []; busy: [value: boolean] }>();
const form = reactive({
  recordedAt: props.record?.recordedAt ?? "",
  mood: props.record?.mood ?? "",
  stressLevel:
    props.record?.stressLevel == null ? "" : String(props.record.stressLevel),
  bodyFeeling: props.record?.bodyFeeling ?? "",
  events: props.record?.events ?? "",
  tags: props.record?.tags ?? "",
  note: props.record?.note ?? "",
  sourceName: props.record?.sourceName ?? "",
  agentAllowed: props.record?.agentAllowed ?? false,
});
const busy = ref(false),
  error = ref("");
async function save() {
  error.value = "";
  busy.value = true;
  emit("busy", true);
  try {
    await api<DiaryRecord>(
      props.record ? `/diary/entries/${props.record.id}` : "/diary/entries",
      json(props.record ? "PUT" : "POST", {
        ...form,
        recordedAt:
          form.recordedAt.length === 16
            ? `${form.recordedAt}:00`
            : form.recordedAt,
        stressLevel: form.stressLevel === "" ? null : Number(form.stressLevel),
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
    aria-labelledby="diary-editor-title"
    :aria-busy="busy"
    @submit.prevent="save"
  >
    <h3 id="diary-editor-title">
      {{ record ? `编辑历史日记 #${record.id}` : "写一条日记" }}
    </h3>
    <p class="footnote">
      用自己的文字记录，不需要选择预设情绪。至少填写一项自述文字或压力值。
    </p>
    <div class="form-grid">
      <label
        >实际记录时间（档案时区）
        <input
          v-model="form.recordedAt"
          type="datetime-local"
          step="1"
          required
          :disabled="busy"
        />
        <span class="field-hint"
          >可补写过去的感受；与系统录入、更新时间分别保存。</span
        >
      </label>
      <label
        >记录来源
        <input
          v-model="form.sourceName"
          list="diary-entry-sources"
          maxlength="100"
          required
          :disabled="busy"
        />
        <datalist id="diary-entry-sources">
          <option v-for="source in sources" :key="source" :value="source" />
        </datalist>
        <span class="field-hint"
          >选择已有来源或填写实际来源，名称按原文保存。</span
        >
      </label>
      <label
        >情绪自述
        <input v-model="form.mood" maxlength="40" :disabled="busy" />
        <span class="field-hint">可不填，最多 40 字。</span>
      </label>
      <label
        >主观压力（可不填）
        <input
          v-model="form.stressLevel"
          type="number"
          min="0"
          max="10"
          step="1"
          :disabled="busy"
          aria-describedby="diary-stress-hint"
        />
        <span id="diary-stress-hint" class="field-hint"
          >0–10 的整数：0 为未感到压力，10
          为自己感受到的最大压力。这是个人感受，不是诊断评分。</span
        >
      </label>
      <label class="span-all"
        >身体感受
        <textarea
          v-model="form.bodyFeeling"
          rows="3"
          maxlength="500"
          :disabled="busy"
        ></textarea>
        <span class="field-hint">最多 500 字。</span>
      </label>
      <label class="span-all"
        >相关事件
        <textarea
          v-model="form.events"
          rows="3"
          maxlength="1000"
          :disabled="busy"
        ></textarea>
        <span class="field-hint">最多 1000 字。</span>
      </label>
      <label class="span-all"
        >自述标签
        <input v-model="form.tags" maxlength="200" :disabled="busy" />
        <span class="field-hint"
          >最多 200 字，按你填写的文字保留，不自动分类。</span
        >
      </label>
      <label class="span-all"
        >备注
        <textarea
          v-model="form.note"
          rows="4"
          maxlength="2000"
          :disabled="busy"
        ></textarea>
        <span class="field-hint">最多 2000 字。</span>
      </label>
    </div>
    <label class="checkbox-label"
      ><input
        v-model="form.agentAllowed"
        type="checkbox"
        :disabled="busy"
      />允许健康助手在我主动启用日记的任务中读取这条记录</label
    >
    <p
      v-if="record?.agentAllowed || form.agentAllowed"
      class="feedback warm"
      role="note"
    >
      保存此变更可能取消日期相交且启用日记的任务，并使相关报告失效；包括修改内容、实际记录时间或撤销授权。已确认的正式计划会保留。已发给外部模型的信息无法追回。
    </p>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <div class="actions">
      <button :disabled="busy">{{ busy ? "保存中…" : "保存日记" }}</button>
      <button
        type="button"
        class="secondary"
        :disabled="busy"
        @click="emit('close')"
      >
        取消
      </button>
    </div>
  </form>
</template>
