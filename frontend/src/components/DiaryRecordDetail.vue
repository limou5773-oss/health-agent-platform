<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { api, errorMessage } from "../api";
import type { DiaryRecord } from "../types";
import StateNotice from "./StateNotice.vue";

const props = defineProps<{ recordId: number; refreshKey: number }>();
defineEmits<{ close: [] }>();
const record = ref<DiaryRecord | null>(null);
const loading = ref(false),
  error = ref("");
let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  error.value = "";
  record.value = null;
  try {
    const result = await api<DiaryRecord>(`/diary/entries/${props.recordId}`);
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
  <section class="panel" aria-labelledby="diary-detail-title">
    <div class="section-heading">
      <div>
        <h2 id="diary-detail-title">原始日记 #{{ recordId }}</h2>
        <p>按原文保留你的自述与来源，不作情绪分类或诊断。</p>
      </div>
      <button class="secondary" @click="$emit('close')">关闭详情</button>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <template v-if="!loading && !error && record">
      <dl class="meta-list">
        <div>
          <dt>实际记录时间</dt>
          <dd>{{ record.recordedAt.replace("T", " ") }}（档案时区）</dd>
        </div>
        <div>
          <dt>录入时间</dt>
          <dd>{{ record.createdAt.replace("T", " ") }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ record.updatedAt.replace("T", " ") }}</dd>
        </div>
        <div>
          <dt>记录来源</dt>
          <dd class="prose">{{ record.sourceName }}</dd>
        </div>
        <div>
          <dt>Agent 权限</dt>
          <dd>
            <span class="tag" :class="{ warm: !record.agentAllowed }">{{
              record.agentAllowed ? "允许后续任务读取" : "私密 · 仅自己可见"
            }}</span>
          </dd>
        </div>
      </dl>
      <dl class="diary-content">
        <div>
          <dt>情绪自述</dt>
          <dd class="prose">{{ record.mood || "未填写" }}</dd>
        </div>
        <div>
          <dt>主观压力</dt>
          <dd>
            {{
              record.stressLevel === null
                ? "未填写"
                : `${record.stressLevel} / 10`
            }}
            <p class="small muted">用户自己的主观感受，不是诊断评分。</p>
          </dd>
        </div>
        <div>
          <dt>身体感受</dt>
          <dd class="prose">{{ record.bodyFeeling || "未填写" }}</dd>
        </div>
        <div>
          <dt>相关事件</dt>
          <dd class="prose">{{ record.events || "未填写" }}</dd>
        </div>
        <div>
          <dt>自述标签</dt>
          <dd class="prose">{{ record.tags || "未填写" }}</dd>
        </div>
        <div>
          <dt>备注</dt>
          <dd class="prose">{{ record.note || "未填写" }}</dd>
        </div>
      </dl>
    </template>
  </section>
</template>
<style scoped>
.diary-content {
  margin: 0;
}
.diary-content > div {
  padding: 1rem 0;
  border-top: 1px solid var(--line);
}
.diary-content dt {
  font-weight: 600;
  color: var(--green);
  font-size: 0.85rem;
}
.diary-content dd {
  margin: 0.5rem 0 0;
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}
.diary-content dd p {
  margin-bottom: 0;
}
</style>
