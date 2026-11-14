<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { api, errorMessage } from "../api";
import type { WeightRecord } from "../types";
import { weightText, weightTime } from "../weight";
import StateNotice from "./StateNotice.vue";

const props = defineProps<{ recordId: number; refreshKey: number }>();
defineEmits<{ close: [] }>();
const record = ref<WeightRecord | null>(null);
const loading = ref(false),
  error = ref("");
let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  error.value = "";
  record.value = null;
  try {
    const result = await api<WeightRecord>(`/weight/records/${props.recordId}`);
    if (currentRequest === requestId) record.value = result;
  } catch (cause) {
    if (currentRequest === requestId) error.value = errorMessage(cause);
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}
watch(() => [props.recordId, props.refreshKey], load);
onMounted(load);
</script>
<template>
  <section class="panel" aria-labelledby="weight-detail-title">
    <div class="section-heading">
      <div>
        <h2 id="weight-detail-title">原始测量 #{{ recordId }}</h2>
        <p>核对这条测量的数值、时间和来源。</p>
      </div>
      <button class="secondary" @click="$emit('close')">关闭详情</button>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <dl v-if="!loading && !error && record" class="meta-list">
      <div>
        <dt>体重</dt>
        <dd>{{ weightText(record.weightKg) }}</dd>
      </div>
      <div>
        <dt>实际测量时间</dt>
        <dd>{{ weightTime(record.measuredAt) }}（档案时区）</dd>
      </div>
      <div>
        <dt>来源</dt>
        <dd>
          {{ record.sourceName }}
          <span v-if="record.corrected" class="tag">已更正</span>
        </dd>
      </div>
      <div>
        <dt>创建时间</dt>
        <dd>{{ weightTime(record.createdAt) }}</dd>
      </div>
      <div>
        <dt>更新时间</dt>
        <dd>{{ weightTime(record.updatedAt) }}</dd>
      </div>
      <div>
        <dt>备注</dt>
        <dd class="prose">{{ record.note || "未填写" }}</dd>
      </div>
    </dl>
  </section>
</template>
