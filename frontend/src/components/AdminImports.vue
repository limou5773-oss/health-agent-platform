<script setup lang="ts">
import { ref, watch } from "vue";
import { api, errorMessage, json } from "../api";
import { downloadImportTemplate, getImportTemplate } from "../import-template";
import type { ImportKind, ImportTemplate } from "../import-template";
import type { Page } from "../types";
import StateNotice from "./StateNotice.vue";

type Batch = {
  kind: ImportKind; id: number; userId: number; format: string; status: "PREVIEW" | "CONFIRMED" | "REJECTED";
  totalRows: number; validRows: number; invalidRows: number; duplicateRows: number;
  unsupportedRows: number; insertedRows: number; createdAt: string;
};
type Issue = { reason: string; issueRows: number };
const reasonText: Record<string, string> = {
  INVALID_CSV: "CSV 文件无法读取或大小不符",
  INVALID_CSV_HEADER: "CSV 表头不符",
  TOO_MANY_ROWS: "CSV 行数超限",
  INVALID_TIMEZONE: "时区无效",
  INVALID_APPLE_FILE: "苹果健康文件无效",
  INVALID_APPLE_XML: "苹果健康 XML 结构无效",
  APPLE_ZIP_ENTRY_LIMIT: "ZIP 条目数超限",
  DUPLICATE_APPLE_XML: "ZIP 包含多个导出文件",
  APPLE_XML_MISSING: "ZIP 缺少导出 XML",
  APPLE_SLEEP_RECORD_LIMIT: "睡眠记录数超限",
  APPLE_ZIP_SIZE_LIMIT: "ZIP 解压大小超限",
  UNSUPPORTED_TIME_OFFSET: "跨夏令时或偏移切换",
  UNSUPPORTED_STAGE: "暂不支持的睡眠阶段",
  INVALID_FIELDS: "CSV 列数不符",
  OVERLAPPING_INTERVAL: "步数区间重叠",
  INVALID_ROW: "时间、字段或记录校验失败",
};
const status = { PREVIEW: "待确认", CONFIRMED: "已确认", REJECTED: "预览失败" };
const kind = ref<ImportKind>("SLEEP");
const issuesOnly = ref(false);
const page = ref(1);
const list = ref<Page<Batch> | null>(null);
const template = ref<ImportTemplate | null>(null);
const selected = ref<Batch | null>(null);
const issues = ref<Issue[]>([]);
const loading = ref(false), templateLoading = ref(false), issueLoading = ref(false), saving = ref(false);
const error = ref(""), templateError = ref(""), issueError = ref(""), feedback = ref("");
let listRequest = 0;
let detailRequest = 0;

async function load() {
  const request = ++listRequest;
  selected.value = null;
  detailRequest++;
  loading.value = true;
  error.value = "";
  try {
    const data = await api<Page<Batch>>(`/admin/imports?kind=${kind.value}&issuesOnly=${issuesOnly.value}&page=${page.value}`);
    if (request === listRequest) list.value = data;
  } catch (cause) {
    if (request === listRequest) error.value = errorMessage(cause);
  } finally {
    if (request === listRequest) loading.value = false;
  }
}

async function loadTemplate() {
  const current = kind.value;
  template.value = null;
  templateLoading.value = true;
  templateError.value = "";
  feedback.value = "";
  try {
    const data = await getImportTemplate(current);
    if (kind.value === current) template.value = data;
  } catch (cause) {
    if (kind.value === current) templateError.value = errorMessage(cause);
  } finally {
    if (kind.value === current) templateLoading.value = false;
  }
}

async function save() {
  if (!template.value) return;
  saving.value = true;
  feedback.value = "";
  templateError.value = "";
  try {
    template.value = await api<ImportTemplate>(`/admin/import-templates/${kind.value}`,
      json("PUT", { instructions: template.value.instructions }));
    feedback.value = "模板说明已保存。";
  } catch (cause) {
    templateError.value = errorMessage(cause);
  } finally {
    saving.value = false;
  }
}

async function show(batch: Batch) {
  const request = ++detailRequest;
  selected.value = batch;
  issues.value = [];
  issueLoading.value = true;
  issueError.value = "";
  try {
    const data = await api<Issue[]>(`/admin/imports/${batch.kind}/${batch.id}/issues`);
    if (request === detailRequest) issues.value = data;
  } catch (cause) {
    if (request === detailRequest) issueError.value = errorMessage(cause);
  } finally {
    if (request === detailRequest) issueLoading.value = false;
  }
}
watch([kind, issuesOnly, page], load, { immediate: true });
watch(kind, loadTemplate, { immediate: true });
</script>

<template>
  <section class="panel">
    <h2>导入批次</h2>
    <div class="filter-bar">
      <label>数据类型
        <select v-model="kind" :disabled="saving" @change="page = 1">
          <option value="SLEEP">睡眠</option><option value="STEPS">步数</option>
        </select>
      </label>
      <label class="checkbox-label"><input v-model="issuesOnly" type="checkbox" @change="page = 1" />只看有问题的批次</label>
      <button class="secondary" :disabled="loading" @click="load">刷新</button>
    </div>
    <StateNotice :loading="loading" :error="error" :empty="!list?.items.length"
      title="没有符合条件的导入记录" @retry="load" />
    <div v-if="list?.items.length && !loading && !error" class="table-wrap">
      <table>
        <thead><tr><th>编号</th><th>用户编号</th><th>格式</th><th>状态</th><th>总行</th><th>有效</th><th>无效</th><th>重复</th><th>不支持</th><th>入库</th><th>时间</th><th>问题分类</th></tr></thead>
        <tbody>
          <tr v-for="batch in list.items" :key="batch.id">
            <td>{{ batch.id < 0 ? `失败请求 #${-batch.id}` : `批次 #${batch.id}` }}</td><td>{{ batch.userId }}</td>
            <td>{{ batch.format }}</td><td>{{ status[batch.status] }}</td>
            <td>{{ batch.totalRows }}</td><td>{{ batch.validRows }}</td><td>{{ batch.invalidRows }}</td>
            <td>{{ batch.duplicateRows }}</td><td>{{ batch.unsupportedRows }}</td><td>{{ batch.insertedRows }}</td>
            <td>{{ batch.createdAt.replace("T", " ").slice(0, 19) }}</td>
            <td><button class="secondary" @click="show(batch)">查看</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="list && list.total > list.pageSize && !loading && !error" class="actions">
      <button class="secondary" :disabled="page === 1" @click="page--">上一页</button>
      <span>第 {{ page }} / {{ Math.ceil(list.total / list.pageSize) }} 页</span>
      <button class="secondary" :disabled="page * list.pageSize >= list.total" @click="page++">下一页</button>
    </div>
    <div v-if="selected">
      <h3>{{ selected.id < 0 ? "失败请求" : "批次" }} #{{ Math.abs(selected.id) }} 的问题分类</h3>
      <StateNotice :loading="issueLoading" :error="issueError" :empty="!issues.length"
        title="没有可查看的问题分类" :description="selected.status === 'CONFIRMED' ? '旧版本已确认的批次可能只保留计数。' : '未记录分类。'"
        @retry="show(selected)" />
      <ul v-if="issues.length">
        <li v-for="issue in issues" :key="issue.reason">{{ reasonText[issue.reason] ?? issue.reason }}
          <span v-if="selected.id > 0"> · {{ issue.issueRows }} 行</span>
        </li>
      </ul>
    </div>
  </section>
  <section class="panel">
    <h2>CSV 模板</h2>
    <StateNotice v-if="!template" :loading="templateLoading" :error="templateError" @retry="loadTemplate" />
    <form v-if="template" @submit.prevent="save">
      <p><code>{{ template.headers.join(",") }}</code></p>
      <label>填写说明
        <textarea v-model="template.instructions" rows="4" maxlength="1000" :disabled="saving" />
      </label>
      <p v-if="templateError" class="feedback error" role="alert">{{ templateError }}</p>
      <p v-if="feedback" class="feedback success" role="status">{{ feedback }}</p>
      <div class="actions">
        <button :disabled="saving">保存模板说明</button>
        <button type="button" class="secondary" @click="downloadImportTemplate(template)">下载空白模板</button>
      </div>
    </form>
  </section>
</template>
