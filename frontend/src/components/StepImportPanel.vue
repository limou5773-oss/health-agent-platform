<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { Download, Upload } from "@lucide/vue";
import { api, errorMessage, json, saveBlob } from "../api";
import type { StepImportBatch, StepImportPreview } from "../types";
import StateNotice from "./StateNotice.vue";
const emit = defineEmits<{ imported: [] }>();
const file = ref<File | null>(null),
  timezone = ref("");
const preview = ref<StepImportPreview | null>(null),
  result = ref<StepImportBatch | null>(null);
const batches = ref<StepImportBatch[]>([]),
  previewPage = ref(1);
const previewBusy = ref(false),
  confirmBusy = ref(false),
  historyLoading = ref(false);
const previewError = ref(""),
  confirmError = ref(""),
  historyError = ref("");
const busy = computed(() => previewBusy.value || confirmBusy.value);
const rows = computed(
  () =>
    preview.value?.rows.slice(
      (previewPage.value - 1) * 20,
      previewPage.value * 20,
    ) || [],
);
const statusText: Record<string, string> = {
  PREVIEW: "已预览，待确认",
  CONFIRMED: "已确认",
};
function resetPreview() {
  preview.value = null;
  result.value = null;
  previewError.value = "";
  confirmError.value = "";
}
function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  resetPreview();
}
watch(timezone, resetPreview);
function template() {
  saveBlob(
    new Blob(["\uFEFFstartTime,endTime,steps,mode,sourceName,note\r\n"], {
      type: "text/csv;charset=utf-8",
    }),
    "步数记录空白模板.csv",
  );
}
async function loadHistory() {
  historyLoading.value = true;
  historyError.value = "";
  try {
    batches.value = await api<StepImportBatch[]>("/activity/step-imports");
  } catch (cause) {
    historyError.value = errorMessage(cause);
  } finally {
    historyLoading.value = false;
  }
}
async function upload() {
  if (!file.value) return;
  previewBusy.value = true;
  resetPreview();
  const body = new FormData();
  body.append("file", file.value);
  if (timezone.value.trim()) body.append("timezone", timezone.value.trim());
  try {
    preview.value = await api<StepImportPreview>(
      "/activity/step-imports/preview",
      { method: "POST", body },
    );
    previewPage.value = 1;
    await loadHistory();
  } catch (cause) {
    previewError.value = errorMessage(cause);
  } finally {
    previewBusy.value = false;
  }
}
async function confirm() {
  if (!preview.value) return;
  confirmBusy.value = true;
  confirmError.value = "";
  try {
    result.value = await api<StepImportBatch>(
      `/activity/step-imports/${preview.value.id}/confirm`,
      json("POST"),
    );
    preview.value = null;
    emit("imported");
    await loadHistory();
  } catch (cause) {
    confirmError.value = errorMessage(cause);
  } finally {
    confirmBusy.value = false;
  }
}
onMounted(loadHistory);
</script>
<template>
  <section id="step-import" class="panel">
    <div class="section-heading">
      <div>
        <h2>导入步数 CSV</h2>
        <p>先逐行预览，再确认有效记录。通用模板支持累计快照与区间增量。</p>
      </div>
      <button class="secondary" @click="template">
        <Download :size="16" />下载空白模板
      </button>
    </div>
    <form class="form-grid" @submit.prevent="upload">
      <label
        >UTF-8 CSV 文件<input
          type="file"
          accept=".csv,text/csv"
          required
          :disabled="busy"
          @change="selectFile"
      /></label>
      <label
        >文件时区（选填）<input
          v-model="timezone"
          :disabled="busy"
          placeholder="留空使用个人档案时区，例如 Asia/Shanghai"
      /></label>
      <div class="actions span-all">
        <button :disabled="busy || !file">
          <Upload :size="16" />{{ previewBusy ? "预览处理中…" : "上传并预览" }}
        </button>
      </div>
    </form>
    <p class="footnote">
      最多 10 MB、10,000 行；固定表头为
      startTime,endTime,steps,mode,sourceName,note。时间使用 ISO 本地时间或带
      offset 的时间，mode 填 CUMULATIVE 或
      DELTA。下载模板只包含表头，请填写自己的真实数据。Mi Fitness
      私有原始格式需要先整理为此模板。
    </p>
    <p v-if="previewError" class="feedback error" role="alert">
      {{ previewError }}
    </p>
    <p v-if="confirmError" class="feedback error" role="alert">
      {{ confirmError }}
      请核对当前记录与预览；确认时会重新校验，冲突不会静默跳过。
    </p>
    <p v-if="result" class="feedback success" role="status">
      批次 #{{ result.id }} 已确认：实际写入 {{ result.insertedRows }} 条，重复
      {{ result.duplicateRows }} 条，无效 {{ result.invalidRows }} 条。
    </p>
    <section v-if="preview" class="import-preview" aria-label="步数导入预览">
      <div class="section-heading">
        <div>
          <h3>批次 #{{ preview.id }} · 导入预览</h3>
          <p>
            共 {{ preview.totalRows }} 行 · 有效 {{ preview.validRows }} · 无效
            {{ preview.invalidRows }} · 重复
            {{ preview.duplicateRows }}（已包含在有效行中）
          </p>
          <p>确认后仅写入有效且不重复的记录，无效行保留错误说明。</p>
        </div>
        <button :disabled="busy || preview.validRows === 0" @click="confirm">
          {{ confirmBusy ? "确认中…" : "确认导入有效记录" }}
        </button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>行号</th>
              <th>开始 / 结束时间</th>
              <th>原始步数 / 语义</th>
              <th>来源 / 备注</th>
              <th>校验结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.rowNumber">
              <td>{{ row.rowNumber }}</td>
              <td>{{ row.startTime ?? "—" }}<br />{{ row.endTime ?? "—" }}</td>
              <td>
                {{
                  row.steps === null || row.steps === "" ? "（空）" : row.steps
                }}
                <p class="small">{{ row.mode ?? "（空）" }}</p>
              </td>
              <td class="note-cell">
                {{ row.sourceName ?? "（空）" }}
                <p v-if="row.note" class="small">{{ row.note }}</p>
              </td>
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
      <p v-if="!preview.rows.length" class="notice empty">
        文件没有可预览的记录，请核对文件内容。
      </p>
      <div class="pagination">
        <span
          >第 {{ previewPage }} /
          {{ Math.max(1, Math.ceil(preview.rows.length / 20)) }} 页</span
        ><button
          class="secondary"
          :disabled="busy || previewPage === 1"
          @click="previewPage--"
        >
          上一页</button
        ><button
          class="secondary"
          :disabled="busy || previewPage * 20 >= preview.rows.length"
          @click="previewPage++"
        >
          下一页
        </button>
      </div>
    </section>
    <details class="data-details" :open="!!historyError">
      <summary>步数导入历史</summary>
      <div class="actions">
        <button
          class="text-button"
          :disabled="historyLoading"
          @click="loadHistory"
        >
          刷新历史
        </button>
      </div>
      <StateNotice
        :loading="historyLoading"
        :error="historyError"
        :empty="!batches.length"
        title="尚无步数导入批次"
        description="上传预览后，可以在这里查看批次状态与数量。"
        @retry="loadHistory"
      />
      <div
        v-if="!historyLoading && !historyError && batches.length"
        class="table-wrap"
      >
        <table>
          <thead>
            <tr>
              <th>批次 / 创建时间</th>
              <th>状态</th>
              <th>总行数</th>
              <th>有效 / 无效 / 重复</th>
              <th>实际写入</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="batch in batches" :key="batch.id">
              <td>
                #{{ batch.id }}
                <p class="small">{{ batch.createdAt.replace("T", " ") }}</p>
              </td>
              <td>{{ statusText[batch.status] || batch.status }}</td>
              <td>{{ batch.totalRows }}</td>
              <td>
                {{ batch.validRows }} / {{ batch.invalidRows }} /
                {{ batch.duplicateRows }}
              </td>
              <td>
                {{
                  batch.status === "CONFIRMED" ? batch.insertedRows : "尚未确认"
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  </section>
</template>
