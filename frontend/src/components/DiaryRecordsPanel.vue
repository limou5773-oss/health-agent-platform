<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { Plus } from "@lucide/vue";
import { api, errorMessage, json, recentRange } from "../api";
import type { DiaryRecord, Page } from "../types";
import StateNotice from "./StateNotice.vue";
import DiaryEditor from "./DiaryEditor.vue";
import DiaryRecordList from "./DiaryRecordList.vue";

const props = defineProps<{ sources: string[]; refreshKey: number }>();
const emit = defineEmits<{
  changed: [deletedId?: number];
  detail: [id: number];
}>();
const filters = reactive({
  ...recentRange(),
  sourceName: "",
  query: "",
  agentAllowed: "",
});
const records = ref<Page<DiaryRecord> | null>(null),
  page = ref(1);
const loading = ref(false),
  busy = ref(false),
  editor = ref(false);
const editing = ref<DiaryRecord | null>(null),
  editorKey = ref(0);
const error = ref(""),
  actionError = ref(""),
  message = ref("");
let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  records.value = null;
  error.value = "";
  const { sourceName, startDate, endDate, query, agentAllowed } = filters;
  try {
    const result = await api<Page<DiaryRecord>>(
      `/diary/entries?${new URLSearchParams({
        ...(startDate ? { startDate } : {}),
        ...(endDate ? { endDate } : {}),
        ...(sourceName ? { sourceName } : {}),
        ...(query ? { query } : {}),
        ...(agentAllowed ? { agentAllowed } : {}),
        page: String(page.value),
        pageSize: "20",
      })}`,
    );
    if (currentRequest === requestId) records.value = result;
  } catch (cause) {
    if (currentRequest === requestId) error.value = errorMessage(cause);
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}
function edit(record: DiaryRecord | null = null) {
  editing.value = record;
  editorKey.value++;
  editor.value = true;
  actionError.value = "";
  message.value = "";
}
function saved() {
  editor.value = false;
  message.value = "日记已保存。";
  page.value = 1;
  emit("changed");
}
async function permission(record: DiaryRecord) {
  const agentAllowed = !record.agentAllowed;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api<DiaryRecord>(
      `/diary/entries/${record.id}/agent-permission`,
      json("PATCH", { agentAllowed }),
    );
    message.value = agentAllowed
      ? "已允许后续任务读取此日记；仍需档案授权和本次任务勾选。"
      : "已撤销此日记的后续读取权限。";
    page.value = 1;
    emit("changed");
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function remove(record: DiaryRecord) {
  if (
    !window.confirm(
      `删除 ${record.recordedAt.replace("T", " ")} 的日记 #${record.id}？${record.agentAllowed ? "\n此记录已授权：日期相交且启用日记的任务将失效，相关报告也将失效，已确认的正式计划会保留。已发给外部模型的信息无法追回。" : ""}`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(`/diary/entries/${record.id}`, json("DELETE"));
    message.value = "日记已删除。";
    page.value = 1;
    emit("changed", record.id);
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function filter() {
  page.value = 1;
  void load();
}
function changePage(next: number) {
  page.value = next;
  void load();
}
watch(() => props.refreshKey, load);
onMounted(load);
</script>
<template>
  <section class="panel" aria-labelledby="diary-records-title">
    <div class="section-heading">
      <div>
        <h2 id="diary-records-title">我的日记</h2>
        <p>同一时间的多条日记分别保存，可查看原文、更正或逐条授权。</p>
      </div>
      <button :disabled="busy || editor" @click="edit()">
        <Plus :size="16" />写日记
      </button>
    </div>
    <form class="filter-bar" @submit.prevent="filter">
      <label
        >开始日期<input
          v-model="filters.startDate"
          type="date"
          :max="filters.endDate"
          :disabled="loading || busy"
      /></label>
      <label
        >结束日期<input
          v-model="filters.endDate"
          type="date"
          :min="filters.startDate"
          :disabled="loading || busy"
      /></label>
      <label
        >记录来源<input
          v-model="filters.sourceName"
          list="diary-filter-sources"
          maxlength="100"
          placeholder="留空查看所有来源"
          :disabled="loading || busy"
        />
        <datalist id="diary-filter-sources">
          <option v-for="source in sources" :key="source" :value="source" />
        </datalist>
      </label>
      <label
        >关键词<input
          v-model="filters.query"
          type="search"
          maxlength="100"
          placeholder="搜索自述文字"
          :disabled="loading || busy"
      /></label>
      <label
        >Agent 权限<select
          v-model="filters.agentAllowed"
          :disabled="loading || busy"
        >
          <option value="">全部权限</option>
          <option value="false">私密</option>
          <option value="true">允许读取</option>
        </select></label
      >
      <button class="secondary" :disabled="loading || busy">筛选记录</button>
    </form>
    <DiaryEditor
      v-if="editor"
      :key="editorKey"
      :record="editing"
      :sources="sources"
      @busy="busy = $event"
      @close="editor = false"
      @saved="saved"
    />
    <p v-if="actionError" class="feedback error" role="alert">
      {{ actionError }}
    </p>
    <p v-if="message" class="feedback success" role="status">{{ message }}</p>
    <StateNotice
      :loading="loading"
      :error="error"
      :empty="records?.total === 0"
      title="当前范围还没有日记"
      description="可调整日期、来源、关键词或权限筛选。清空日期可查找全部历史记录。"
      @retry="load"
    />
    <DiaryRecordList
      v-if="!loading && !error && records && records.total"
      :records="records"
      :busy="busy"
      :editing="editor"
      @detail="emit('detail', $event)"
      @edit="edit"
      @permission="permission"
      @remove="remove"
      @page="changePage"
    />
    <p class="footnote">
      按实际记录时间筛选与倒序排列，不合并同秒记录。来源按名称精确匹配；关键词搜索情绪、身体感受、事件、标签和备注原文。
      允许读取、撤销授权及修改已授权内容会使日期相交的日记任务和报告失效，已确认的正式计划保留。
    </p>
  </section>
</template>
