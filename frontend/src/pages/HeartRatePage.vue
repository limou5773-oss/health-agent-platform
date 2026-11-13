<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { api, dateAfter, errorMessage, json, recentRange } from "../api";
import MeasurementImportPanel from "../components/MeasurementImportPanel.vue";
import StateNotice from "../components/StateNotice.vue";
import type { HeartRateSample, Page } from "../types";

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
  <MeasurementImportPanel module="heart-rate" @imported="load()" />
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
