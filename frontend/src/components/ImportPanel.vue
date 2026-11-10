<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { Download, Upload } from "@lucide/vue";
import { api, errorMessage, json, saveBlob } from "../api";
import type { ImportBatch, ImportPreview, Profile } from "../types";
import StateNotice from "./StateNotice.vue";
const emit = defineEmits<{ imported: [] }>();
const format = ref<"CSV" | "APPLE_HEALTH">("CSV");
const profileTimezone = ref("");
const timezone = ref(""),
  file = ref<File | null>(null),
  preview = ref<ImportPreview | null>(null),
  result = ref<ImportBatch | null>(null),
  batches = ref<ImportBatch[]>([]);
const loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  busy = ref(false),
  previewPage = ref(1);
const rows = computed(
  () =>
    preview.value?.rows.slice(
      (previewPage.value - 1) * 20,
      previewPage.value * 20,
    ) || [],
);
watch(format, () => {
  file.value = null;
  preview.value = null;
  result.value = null;
  actionError.value = "";
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [profile, history] = await Promise.all([
      api<Profile>("/profile"),
      api<ImportBatch[]>("/imports"),
    ]);
    timezone.value = profile.timezone;
    profileTimezone.value = profile.timezone;
    batches.value = history;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] || null;
  preview.value = null;
  result.value = null;
}
function template() {
  saveBlob(
    new Blob(["\uFEFFstartTime,endTime,sourceName,note\r\n"], {
      type: "text/csv;charset=utf-8",
    }),
    "睡眠记录模板.csv",
  );
}
async function upload() {
  if (!file.value) return;
  busy.value = true;
  actionError.value = "";
  preview.value = null;
  result.value = null;
  const form = new FormData();
  form.append("file", file.value);
  if (format.value === "CSV") form.append("timezone", timezone.value);
  try {
    const path =
      format.value === "CSV" ? "/imports/preview" : "/imports/apple/preview";
    preview.value = await api<ImportPreview>(path, {
      method: "POST",
      body: form,
    });
    previewPage.value = 1;
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function confirm() {
  if (!preview.value) return;
  busy.value = true;
  actionError.value = "";
  try {
    result.value = await api<ImportBatch>(
      `/imports/${preview.value.id}/confirm`,
      json("POST"),
    );
    preview.value = null;
    emit("imported");
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="section-heading">
      <div>
        <h2>导入健康记录</h2>
        <p>先预览并核对，再确认写入睡眠记录。</p>
      </div>
      <button
        v-if="format === 'CSV'"
        type="button"
        class="secondary"
        @click="template"
      >
        <Download :size="16" />下载空白模板
      </button>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load" /><template
      v-if="!loading && !error"
      ><form class="import-form" @submit.prevent="upload">
        <label
          >接入格式
          <select v-model="format" :disabled="busy">
            <option value="CSV">通用 CSV</option>
            <option value="APPLE_HEALTH">苹果健康 XML / ZIP（睡眠）</option>
          </select>
        </label>
        <label
          >{{ format === "CSV" ? "CSV 文件" : "苹果健康导出文件"
          }}<input
            :key="format"
            type="file"
            :accept="
              format === 'CSV'
                ? '.csv,text/csv'
                : '.xml,.zip,application/xml,application/zip'
            "
            :disabled="busy"
            required
            @change="selectFile" /></label
        ><label v-if="format === 'CSV'"
          >文件时区<input
            v-model="timezone"
            required
            placeholder="Asia/Shanghai" /></label
        ><button :disabled="busy || !file">
          <Upload :size="16" />{{ busy ? "处理中…" : "上传并预览" }}
        </button>
      </form>
      <p v-if="format === 'CSV'" class="footnote">
        最多 10 MB / 10,000 行。表头为
        startTime,endTime,sourceName,note；时间格式如
        2026-11-01T23:00:00。此处格式仅作说明，请填入自己的真实数据。
      </p>
      <p v-else class="footnote">
        上传苹果健康导出的 export.xml 或包含该文件的 ZIP，单文件最多 100 MB，
        ZIP 展开内容最多 500 MB、10,000 个文件条目；每批最多 10,000 条睡眠记录。
        未分期、核心、深睡、REM
        纳入睡眠时长；清醒、在床及其他健康指标跳过并计数。
        时间按原始偏移换算为档案时区
        {{ profileTimezone }}。此处为文件导入，不含设备自动同步。
      </p>
      <p v-if="actionError" class="feedback error" role="alert">
        {{ actionError }}
      </p>
      <p v-if="result" class="feedback success" role="status">
        导入完成：写入 {{ result.insertedRows }} 条，重复
        {{ result.duplicateRows }} 条，无效 {{ result.invalidRows }} 条。
        <template v-if="result.format === 'APPLE_HEALTH'">
          跳过 {{ result.skippedRows }} 条，不支持
          {{ result.unsupportedRows }} 条。
        </template>
      </p>
      <div v-if="preview" class="import-preview">
        <div class="section-heading">
          <div>
            <h3>导入预览 · 批次 #{{ preview.id }}</h3>
            <p>
              文件共 {{ preview.totalRows }} 条记录 · 有效
              {{ preview.validRows }} · 无效 {{ preview.invalidRows }} · 重复
              {{ preview.duplicateRows }}
              <template v-if="preview.format === 'APPLE_HEALTH'">
                · 跳过 {{ preview.skippedRows }} · 不支持
                {{ preview.unsupportedRows }}
              </template>
            </p>
          </div>
          <button :disabled="busy || preview.validRows === 0" @click="confirm">
            确认导入有效记录
          </button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>
                  {{ preview.format === "APPLE_HEALTH" ? "记录序号" : "行号" }}
                </th>
                <th>入睡 / 醒来</th>
                <th>归属日期</th>
                <th>来源</th>
                <th>校验结果</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.rowNumber">
                <td>{{ row.rowNumber }}</td>
                <td>
                  {{ row.startTime || "—" }}<br />{{ row.endTime || "—" }}
                </td>
                <td>{{ row.sleepDate || "—" }}</td>
                <td>
                  {{ row.sourceName || "—" }}
                  <p v-if="row.note" class="small">{{ row.note }}</p>
                </td>
                <td>
                  <span :class="row.valid ? 'tag' : 'tag danger'">{{
                    row.valid ? "有效" : "未纳入"
                  }}</span>
                  <p v-if="row.error" class="small">{{ row.error }}</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!preview.rows.length" class="notice empty">
          该文件没有可预览的睡眠条目。请核对导出范围和上述跳过、不支持数量。
        </p>
        <div class="pagination">
          <button
            class="secondary"
            :disabled="previewPage === 1"
            @click="previewPage--"
          >
            上一页</button
          ><span
            >{{ previewPage }} /
            {{ Math.max(1, Math.ceil(preview.rows.length / 20)) }}</span
          ><button
            class="secondary"
            :disabled="previewPage * 20 >= preview.rows.length"
            @click="previewPage++"
          >
            下一页
          </button>
        </div>
      </div>
      <details class="data-details">
        <summary>导入批次记录（{{ batches.length }}）</summary>
        <p v-if="!batches.length" class="muted">还没有导入批次。</p>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>批次</th>
                <th>格式</th>
                <th>状态</th>
                <th>总记录数</th>
                <th>有效 / 无效 / 重复</th>
                <th>跳过 / 不支持</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="batch in batches" :key="batch.id">
                <td>#{{ batch.id }}</td>
                <td>
                  {{ batch.format === "APPLE_HEALTH" ? "苹果健康" : "CSV" }}
                </td>
                <td>{{ batch.status }}</td>
                <td>{{ batch.totalRows }}</td>
                <td>
                  {{ batch.validRows }} / {{ batch.invalidRows }} /
                  {{ batch.duplicateRows }}
                </td>
                <td>{{ batch.skippedRows }} / {{ batch.unsupportedRows }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details></template
    >
  </section>
</template>
