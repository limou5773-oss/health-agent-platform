<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Download, Upload } from "@lucide/vue";
import { api, errorMessage, json, saveBlob } from "../api";
import type { ImportBatch, ImportPreview, Profile } from "../types";
import StateNotice from "./StateNotice.vue";
const emit = defineEmits<{ imported: [] }>();
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
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [profile, history] = await Promise.all([
      api<Profile>("/profile"),
      api<ImportBatch[]>("/imports"),
    ]);
    timezone.value = profile.timezone;
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
  form.append("timezone", timezone.value);
  try {
    preview.value = await api<ImportPreview>("/imports/preview", {
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
        <h2>从 CSV 导入</h2>
        <p>先预览并核对，再确认写入睡眠记录。</p>
      </div>
      <button type="button" class="secondary" @click="template">
        <Download :size="16" />下载空白模板
      </button>
    </div>
    <StateNotice :loading="loading" :error="error" @retry="load" /><template
      v-if="!loading && !error"
      ><form class="import-form" @submit.prevent="upload">
        <label
          >CSV 文件<input
            type="file"
            accept=".csv,text/csv"
            required
            @change="selectFile" /></label
        ><label
          >文件时区<input
            v-model="timezone"
            required
            placeholder="Asia/Shanghai" /></label
        ><button :disabled="busy || !file">
          <Upload :size="16" />{{ busy ? "处理中…" : "上传并预览" }}
        </button>
      </form>
      <p class="footnote">
        最多 10 MB / 10,000 行。表头为
        startTime,endTime,sourceName,note；时间格式如
        2026-11-01T23:00:00。此处格式仅作说明，请填入自己的真实数据。
      </p>
      <p v-if="actionError" class="feedback error" role="alert">
        {{ actionError }}
      </p>
      <p v-if="result" class="feedback success" role="status">
        导入完成：写入 {{ result.insertedRows }} 条，重复
        {{ result.duplicateRows }} 条，无效 {{ result.invalidRows }} 条。
      </p>
      <div v-if="preview" class="import-preview">
        <div class="section-heading">
          <div>
            <h3>导入预览 · 批次 #{{ preview.id }}</h3>
            <p>
              共 {{ preview.totalRows }} 行 · 有效 {{ preview.validRows }} ·
              无效 {{ preview.invalidRows }} · 重复 {{ preview.duplicateRows }}
            </p>
          </div>
          <button :disabled="busy" @click="confirm">确认导入有效记录</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>行号</th>
                <th>入睡 / 醒来</th>
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
                <td>{{ row.sourceName || "—" }}</td>
                <td>
                  <span :class="row.valid ? 'tag' : 'tag danger'">{{
                    row.valid ? "有效" : "无效"
                  }}</span>
                  <p v-if="row.error" class="small">{{ row.error }}</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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
                <th>状态</th>
                <th>总行数</th>
                <th>有效 / 无效 / 重复</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="batch in batches" :key="batch.id">
                <td>#{{ batch.id }}</td>
                <td>{{ batch.status }}</td>
                <td>{{ batch.totalRows }}</td>
                <td>
                  {{ batch.validRows }} / {{ batch.invalidRows }} /
                  {{ batch.duplicateRows }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details></template
    >
  </section>
</template>
