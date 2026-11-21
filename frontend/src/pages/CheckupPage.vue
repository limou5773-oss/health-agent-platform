<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import StateNotice from "../components/StateNotice.vue";
import type { Page } from "../types";

type Item = { name: string; value: string; unit: string; referenceRange: string; location: string; excerpt: string;
  pageNumber: number | null };
type Report = { id: number; reportDate: string; title: string; items: Item[] };
type Draft = { reportDate: string; title: string; items: Item[] };
const file = ref<File>();
const originalUrl = ref("");
const isPdf = computed(() => file.value?.type === "application/pdf");
const pageNumber = ref(1);
const pageCount = ref(0);
const pdfUrl = computed(() => `${originalUrl.value}#page=${pageNumber.value}`);
const draft = ref<Draft>();
const result = ref<Page<Report>>();
const selected = ref<Report>();
const busy = ref(false);
const actionError = ref("");
const saved = ref("");
const loading = ref(false);
const historyError = ref("");

function selectFile(event: Event) {
  URL.revokeObjectURL(originalUrl.value);
  const selectedFile = (event.target as HTMLInputElement).files?.[0];
  file.value = undefined;
  originalUrl.value = "";
  pageNumber.value = 1;
  pageCount.value = 0;
  draft.value = undefined;
  actionError.value = "";
  saved.value = "";
  if (!selectedFile) return;
  if (selectedFile.type === "application/pdf" || /\.pdf$/i.test(selectedFile.name)) {
    file.value = new File([selectedFile], selectedFile.name, { type: "application/pdf" });
  } else if (["image/jpeg", "image/png"].includes(selectedFile.type)) {
    file.value = selectedFile;
  } else {
    actionError.value = "请选择 PDF、JPEG 或 PNG 文件。";
    return;
  }
  originalUrl.value = URL.createObjectURL(file.value);
}

async function preview(photo: File) {
  busy.value = true;
  actionError.value = "";
  saved.value = "";
  draft.value = undefined;
  try {
    const body = new FormData();
    body.append("photo", photo);
    const response = await api<{ reportDate: string | null; items: Item[]; pageCount: number | null }>("/checkups/preview",
      { method: "POST", body }, isPdf.value ? 120_000 : 105_000);
    draft.value = { reportDate: response.reportDate ?? "", title: "体检报告", items: response.items };
    pageCount.value = response.pageCount ?? 0;
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function history(page = 1) {
  loading.value = true;
  historyError.value = "";
  try {
    result.value = await api<Page<Report>>(`/checkups?page=${page}`);
  } catch (cause) {
    historyError.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function save(current: Draft) {
  busy.value = true;
  actionError.value = "";
  saved.value = "";
  try {
    selected.value = await api<Report>("/checkups", json("POST", current));
    draft.value = undefined;
    saved.value = "已保存核对后的体检报告。";
    await history();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function open(id: number) {
  busy.value = true;
  historyError.value = "";
  try {
    selected.value = await api<Report>(`/checkups/${id}`);
  } catch (cause) {
    historyError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function remove(report: Report) {
  if (!window.confirm(`删除「${report.title}」的已保存体检数据？`)) return;
  busy.value = true;
  historyError.value = "";
  try {
    await api(`/checkups/${report.id}`, json("DELETE"));
    selected.value = undefined;
    await history();
  } catch (cause) {
    historyError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

onMounted(() => history());
onBeforeUnmount(() => URL.revokeObjectURL(originalUrl.value));
</script>

<template>
  <section class="panel">
    <h2>上传体检报告</h2>
    <p class="muted">可上传 1 至 5 页、最多 10 MB 的未加密 PDF，或最多 5 MB 的单页 JPEG、PNG。识别会向配置的外部模型发送处理后的页面图像，请先遮盖无关个人信息。</p>
    <form class="filter-bar" @submit.prevent="file && preview(file)">
      <label>报告文件<input type="file" accept=".pdf,application/pdf,image/jpeg,image/png" :disabled="busy" required @change="selectFile" /></label>
      <button :disabled="busy || !file">提取指标</button>
    </form>
    <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
    <p v-if="saved" class="feedback success" role="status">{{ saved }}</p>
    <div :class="{ 'checkup-review': draft && originalUrl }">
      <details v-if="originalUrl" class="checkup-source" :open="!!draft">
        <summary>查看本次原报告</summary>
        <label v-if="pageCount > 1">原报告页码
          <select v-model="pageNumber">
            <option v-for="page in pageCount" :key="page" :value="page">第 {{ page }} 页</option>
          </select>
        </label>
        <a class="text-link" :href="isPdf ? pdfUrl : originalUrl" target="_blank" rel="noopener noreferrer">在新标签页查看</a>
        <iframe v-if="isPdf" class="checkup-pdf" :src="pdfUrl" title="本次上传的体检报告 PDF"></iframe>
        <img v-else class="checkup-original" :src="originalUrl" alt="本次上传的体检报告原图" />
      </details>
      <form v-if="draft" class="checkup-fields" @submit.prevent="draft.items.length && save(draft)">
        <h3>核对提取结果</h3>
        <p v-if="!draft.items.length">暂无指标。可按原报告补录，或上传更清晰的报告。</p>
        <fieldset :disabled="busy">
          <div class="form-grid">
            <label>报告标题<input v-model.trim="draft.title" required maxlength="120" /></label>
            <label>报告日期<input v-model="draft.reportDate" type="date" required /></label>
          </div>
          <fieldset v-for="(item, index) in draft.items" :key="index" class="checkup-item">
            <legend>指标 {{ index + 1 }}</legend>
            <div class="form-grid">
              <label>指标名称<input v-model.trim="item.name" required maxlength="120" /></label>
              <label>结果<input v-model.trim="item.value" required maxlength="80" /></label>
              <label>单位<input v-model="item.unit" maxlength="50" /></label>
              <label>原报告参考范围<input v-model="item.referenceRange" maxlength="200" /></label>
              <label v-if="isPdf">原报告页码
                <select v-model="item.pageNumber" required>
                  <option v-for="page in pageCount" :key="page" :value="page">第 {{ page }} 页</option>
                </select>
              </label>
              <label>原文位置<input v-model.trim="item.location" required maxlength="180" /></label>
              <label>原文摘录<textarea v-model.trim="item.excerpt" required maxlength="400" rows="2" /></label>
            </div>
            <div class="actions">
              <button v-if="item.pageNumber !== null && isPdf" type="button" class="secondary" @click="pageNumber = item.pageNumber">
                查看第 {{ item.pageNumber }} 页
              </button>
              <button type="button" class="secondary" @click="draft.items.splice(index, 1)">移除此项</button>
            </div>
          </fieldset>
          <div class="actions">
            <button type="button" class="secondary" :disabled="draft.items.length >= 50"
              @click="draft.items.push({ name: '', value: '', unit: '', referenceRange: '', location: '', excerpt: '',
                pageNumber: isPdf ? pageNumber : null })">
              补录指标
            </button>
            <button :disabled="!draft.items.length">确认保存报告</button>
          </div>
        </fieldset>
      </form>
    </div>
  </section>
  <section class="panel">
    <h2>已保存报告</h2>
    <StateNotice :loading="loading" :error="historyError" @retry="history(result?.page)" />
    <template v-if="result && !loading && !historyError">
      <p v-if="!result.total">暂无体检报告。</p>
      <ul class="selection-list">
        <li v-for="report in result.items" :key="report.id">
          <button type="button" class="secondary" :disabled="busy" @click="open(report.id)">{{ report.reportDate }} · {{ report.title }}</button>
        </li>
      </ul>
      <div v-if="result.total > result.pageSize" class="actions">
        <button class="secondary" :disabled="busy || result.page === 1" @click="history(result.page - 1)">上一页</button>
        <span>第 {{ result.page }} 页</span>
        <button class="secondary" :disabled="busy || result.page * result.pageSize >= result.total" @click="history(result.page + 1)">下一页</button>
      </div>
    </template>
    <div v-if="selected">
      <h3>{{ selected.title }} · {{ selected.reportDate }}</h3>
      <div class="table-wrap"><table>
        <thead><tr><th>指标</th><th>结果</th><th>单位</th><th>参考范围</th><th>来源位置及原文</th></tr></thead>
        <tbody><tr v-for="(item, index) in selected.items" :key="index">
          <td>{{ item.name }}</td><td>{{ item.value }}</td><td>{{ item.unit || "—" }}</td>
          <td>{{ item.referenceRange || "—" }}</td>
          <td><span v-if="item.pageNumber">第 {{ item.pageNumber }} 页 · </span>{{ item.location }} · {{ item.excerpt }}</td>
        </tr></tbody>
      </table></div>
      <button type="button" class="secondary danger-text" :disabled="busy" @click="remove(selected)">删除报告</button>
    </div>
  </section>
</template>

<style scoped>
.checkup-original { display: block; width: min(100%, 58rem); height: auto; margin-block: 0.75rem; }
.checkup-pdf { display: block; width: 100%; height: 60vh; border: 0; margin-block: 0.75rem; }
.checkup-item { margin-block: 0.75rem; }
.checkup-review {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
  gap: 1.25rem;
  align-items: start;
}
.checkup-review .checkup-source { position: sticky; top: 5rem; max-height: calc(100vh - 6rem); overflow: auto; }
.checkup-fields fieldset { min-width: 0; }
@media (max-width: 950px) {
  .checkup-review { grid-template-columns: minmax(0, 1fr); }
  .checkup-review .checkup-source { position: static; max-height: none; }
}
</style>
