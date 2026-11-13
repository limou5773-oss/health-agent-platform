<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import { downloadImportTemplate, getImportTemplate } from "../import-template";
import StateNotice from "./StateNotice.vue";

interface Batch {
  id: number;
  status: "PREVIEW" | "CONFIRMED";
  totalRows: number;
  duplicateRows: number;
  insertedRows: number;
  firstMeasurement: string;
  lastMeasurement: string;
  createdAt: string;
  confirmedAt: string | null;
}

const props = defineProps<{ module: "weight" | "heart-rate" }>();
const emit = defineEmits<{ imported: [] }>();
const path = computed(() => `/${props.module}/imports`);
const metric = computed(() => props.module === "weight" ? "体重" : "心率");
const unit = computed(() => props.module === "weight" ? "千克" : "次/分");
const file = ref<File>();
const input = ref<HTMLInputElement>();
const preview = ref<{ batch: Batch; sources: string[] }>();
const batches = ref<Batch[]>([]);
const busy = ref(false);
const message = ref("");
const error = ref("");
const historyError = ref("");
const historyLoading = ref(false);
const templateBusy = ref(false);
const templateError = ref("");
const templateNote = ref("");

async function loadHistory() {
  historyLoading.value = true;
  historyError.value = "";
  try {
    batches.value = await api<Batch[]>(path.value);
  } catch (cause) {
    historyError.value = errorMessage(cause);
  } finally {
    historyLoading.value = false;
  }
}

async function upload(selected: File) {
  const body = new FormData();
  body.append("file", selected);
  await openPreview(`${path.value}/preview`, { method: "POST", body });
}

function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
  preview.value = undefined;
  message.value = "";
  error.value = "";
}

async function openPreview(url: string, options?: RequestInit) {
  busy.value = true;
  preview.value = undefined;
  message.value = "";
  error.value = "";
  try {
    preview.value = await api<{ batch: Batch; sources: string[] }>(url, options);
    if (options) {
      input.value!.value = "";
      file.value = undefined;
      await loadHistory();
    }
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function confirm(id: number) {
  busy.value = true;
  message.value = "";
  error.value = "";
  try {
    const batch = await api<Batch>(`${path.value}/${id}/confirm`, json("POST"));
    message.value = `批次 #${batch.id} 已确认，写入 ${batch.insertedRows} 条，跳过重复 ${batch.duplicateRows} 条。`;
    preview.value = undefined;
    emit("imported");
    await loadHistory();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function template() {
  templateBusy.value = true;
  templateError.value = "";
  try {
    const data = await getImportTemplate(props.module === "weight" ? "WEIGHT" : "HEART_RATE");
    templateNote.value = data.instructions;
    downloadImportTemplate(data);
  } catch (cause) {
    templateError.value = errorMessage(cause);
  } finally {
    templateBusy.value = false;
  }
}

const time = (value: string) => value.replace("T", " ");
onMounted(loadHistory);
</script>

<template>
  <section class="panel">
    <div class="section-heading">
      <h2>导入{{ metric }} CSV</h2>
      <button type="button" class="secondary" :disabled="templateBusy" @click="template">下载空白模板</button>
    </div>
    <p class="muted">使用 UTF-8，测量时间按档案时区填写，如 2026-01-01T08:00:00。{{ metric }}单位为{{ unit }}。</p>
    <p v-if="templateNote" class="prose muted">{{ templateNote }}</p>
    <p v-if="templateError" class="feedback error" role="alert">{{ templateError }}</p>
    <form class="filter-bar" @submit.prevent="file && upload(file)">
      <label>选择 CSV
        <input ref="input" type="file" accept=".csv,text/csv" :disabled="busy" required
          @change="selectFile" />
      </label>
      <button :disabled="busy || !file">上传并预览</button>
    </form>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="message" class="feedback success" role="status">{{ message }}</p>
    <div v-if="preview && preview.batch.status === 'PREVIEW'">
      <h3>批次 #{{ preview.batch.id }} · 导入预览</h3>
      <p>测量时间 {{ time(preview.batch.firstMeasurement) }} 至 {{ time(preview.batch.lastMeasurement) }}</p>
      <p>来源 {{ preview.sources.join("、") }} · 可导入 {{ preview.batch.totalRows - preview.batch.duplicateRows }} 条 · 重复 {{ preview.batch.duplicateRows }} 条</p>
      <button type="button" :disabled="busy" @click="confirm(preview.batch.id)">确认导入</button>
    </div>
    <details>
      <summary>最近 50 个导入批次</summary>
      <StateNotice :loading="historyLoading" :error="historyError" @retry="loadHistory" />
      <template v-if="!historyLoading && !historyError">
        <p v-if="!batches.length" class="muted">暂无导入批次。</p>
        <div v-else class="table-wrap">
          <table>
            <thead><tr><th>批次</th><th>预览时间</th><th>文件行数</th><th>写入 / 重复</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="batch in batches" :key="batch.id">
                <td>#{{ batch.id }}</td>
                <td>{{ time(batch.createdAt) }}</td>
                <td>{{ batch.totalRows }}</td>
                <td>{{ batch.status === "CONFIRMED" ? `${batch.insertedRows} / ${batch.duplicateRows}` : "—" }}</td>
                <td>
                  <span v-if="batch.status === 'CONFIRMED'">已确认</span>
                  <button v-else type="button" class="secondary" :disabled="busy"
                    @click="openPreview(`${path}/${batch.id}`)">继续确认</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </details>
  </section>
</template>
