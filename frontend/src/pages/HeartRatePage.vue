<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { api, dateAfter, errorMessage, json, recentRange, saveBlob } from "../api";
import StateNotice from "../components/StateNotice.vue";
import type { HeartRateSample, Page } from "../types";

interface ImportResult {
  newRecords: number;
  duplicates: number;
  firstMeasurement: string;
  lastMeasurement: string;
  sources: string[];
}

const filters = reactive({ ...recentRange(), sourceName: "" });
let applied = { ...filters };
const records = ref<Page<HeartRateSample>>();
const sources = ref<string[]>([]);
const loading = ref(false);
const loadError = ref("");
const busy = ref(false);
const actionError = ref("");
const message = ref("");
const editingId = ref<number>();
const form = reactive({ measuredAt: "", bpm: "", sourceName: "手动", note: "" });
const editor = ref<HTMLElement>();
const file = ref<File>();
const preview = ref<ImportResult>();
const fileInput = ref<HTMLInputElement>();

async function load(page = 1) {
  loading.value = true;
  loadError.value = "";
  try {
    const query = new URLSearchParams({ ...applied, page: String(page), pageSize: "20" });
    [records.value, sources.value] = await Promise.all([
      api<Page<HeartRateSample>>(`/heart-rate/records?${query}`),
      api<string[]>("/heart-rate/sources"),
    ]);
  } catch (cause) {
    loadError.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

function apply() {
  applied = { ...filters };
  void load();
}

function edit(record?: HeartRateSample) {
  editingId.value = record?.id;
  form.measuredAt = record?.measuredAt ?? "";
  form.bpm = record ? String(record.bpm) : "";
  form.sourceName = record?.sourceName ?? "手动";
  form.note = record?.note ?? "";
  if (record) editor.value?.focus();
}

async function save() {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    const id = editingId.value;
    await api(id === undefined ? "/heart-rate/records" : `/heart-rate/records/${id}`,
      json(id === undefined ? "POST" : "PUT", { ...form, bpm: Number(form.bpm) }));
    message.value = id === undefined ? "心率采样已保存。" : "心率采样已更正。";
    edit();
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function remove(record: HeartRateSample) {
  if (!window.confirm(`删除 ${displayTime(record.measuredAt)} 来自「${record.sourceName}」的心率采样？`)) return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(`/heart-rate/records/${record.id}`, json("DELETE"));
    if (editingId.value === record.id) edit();
    message.value = "心率采样已删除。";
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
  preview.value = undefined;
}

async function upload(selected: File, confirm: boolean) {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    const body = new FormData();
    body.append("file", selected);
    const path = confirm ? "/heart-rate/imports" : "/heart-rate/imports/preview";
    const result = await api<ImportResult>(path, { method: "POST", body });
    if (confirm) {
      message.value = `已导入 ${result.newRecords} 条采样，跳过 ${result.duplicates} 条重复记录。`;
      preview.value = undefined;
      file.value = undefined;
      fileInput.value!.value = "";
      await load();
    } else {
      preview.value = result;
    }
  } catch (cause) {
    preview.value = undefined;
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

function downloadTemplate() {
  const csv = "\uFEFFmeasuredAt,bpm,sourceName,note\r\n2026-01-01T08:00:00,72,手环,示例请替换\r\n";
  saveBlob(new Blob([csv], { type: "text/csv;charset=utf-8" }), "heart-rate-template.csv");
}

const displayTime = (value: string) => value.replace("T", " ");
onMounted(() => load());
</script>

<template>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <p v-if="actionError" class="notice" role="alert">{{ actionError }}</p>
  <section class="panel">
    <h2 ref="editor" tabindex="-1">{{ editingId === undefined ? "新增心率采样" : "更正心率采样" }}</h2>
    <form class="form-grid" @submit.prevent="save">
      <label>测量时间（档案时区）
        <input v-model="form.measuredAt" type="datetime-local" step="1" required />
      </label>
      <label>心率（次/分）
        <input v-model="form.bpm" type="number" min="0.001" max="9999.999" step="0.001" required />
      </label>
      <label>来源
        <input v-model.trim="form.sourceName" :readonly="editingId !== undefined" maxlength="100" required />
      </label>
      <label>备注<input v-model="form.note" maxlength="1000" /></label>
      <div class="actions">
        <button :disabled="busy">保存记录</button>
        <button v-if="editingId !== undefined" type="button" class="secondary" :disabled="busy" @click="edit()">
          取消更正
        </button>
      </div>
    </form>
  </section>
  <section class="panel">
    <div class="section-heading">
      <h2>导入心率 CSV</h2>
      <button type="button" class="secondary" @click="downloadTemplate">下载模板</button>
    </div>
    <p class="muted">使用 UTF-8，测量时间按档案时区填写，如 2026-01-01T08:00:00。心率单位为次/分。</p>
    <form class="filter-bar" @submit.prevent="file && upload(file, false)">
      <label>选择 CSV
        <input ref="fileInput" type="file" accept=".csv,text/csv" :disabled="busy" required @change="selectFile" />
      </label>
      <button :disabled="busy || !file">预览</button>
    </form>
    <div v-if="preview">
      <p>测量时间 {{ displayTime(preview.firstMeasurement) }} 至 {{ displayTime(preview.lastMeasurement) }}</p>
      <p>来源 {{ preview.sources.join("、") }} · 可导入 {{ preview.newRecords }} 条 · 重复 {{ preview.duplicates }} 条</p>
      <button type="button" :disabled="busy || !preview.newRecords" @click="file && upload(file, true)">确认导入</button>
    </div>
  </section>
  <section class="panel">
    <div class="section-heading">
      <h2>采样明细</h2>
      <RouterLink class="text-link" to="/">查看心率趋势</RouterLink>
    </div>
    <form class="filter-bar" @submit.prevent="apply">
      <label>开始日期<input v-model="filters.startDate" type="date" required :max="filters.endDate" /></label>
      <label>结束日期
        <input v-model="filters.endDate" type="date" required :min="filters.startDate" :max="dateAfter(filters.startDate, 365)" />
      </label>
      <label>来源
        <select v-model="filters.sourceName">
          <option value="">全部来源</option>
          <option v-for="source in sources" :key="source">{{ source }}</option>
        </select>
      </label>
      <button :disabled="busy || loading">查询</button>
    </form>
    <StateNotice :loading="loading" :error="loadError" @retry="load()" />
    <template v-if="records && !loading && !loadError">
      <p v-if="!records.total" class="muted">该范围内没有心率采样。</p>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>测量时间</th><th>心率（次/分）</th><th>来源</th><th>备注</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="record in records.items" :key="record.id">
              <td>{{ displayTime(record.measuredAt) }}</td>
              <td>{{ record.bpm }} <span v-if="record.corrected" class="muted">已更正</span></td>
              <td>{{ record.sourceName }}</td>
              <td>{{ record.note || "—" }}</td>
              <td>
                <button type="button" class="secondary" :disabled="busy" @click="edit(record)">更正</button>
                <button type="button" class="secondary" :disabled="busy" @click="remove(record)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="records.total > records.pageSize" class="actions">
        <button class="secondary" :disabled="busy || records.page === 1" @click="load(records.page - 1)">上一页</button>
        <span>第 {{ records.page }} 页 · 共 {{ records.total }} 条</span>
        <button class="secondary" :disabled="busy || records.page * records.pageSize >= records.total"
          @click="load(records.page + 1)">下一页</button>
      </div>
    </template>
  </section>
</template>
