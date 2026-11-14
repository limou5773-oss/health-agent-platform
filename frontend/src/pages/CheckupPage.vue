<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import StateNotice from "../components/StateNotice.vue";
import type { Page } from "../types";

type Item = { name: string; value: string; unit: string; referenceRange: string; location: string; excerpt: string };
type Report = { id: number; reportDate: string; title: string; items: Item[] };
type Draft = { reportDate: string; title: string; items: Item[] };
const file = ref<File>();
const imageUrl = ref("");
const draft = ref<Draft>();
const result = ref<Page<Report>>();
const selected = ref<Report>();
const busy = ref(false);
const actionError = ref("");
const saved = ref("");
const loading = ref(false);
const historyError = ref("");

function selectFile(event: Event) {
  URL.revokeObjectURL(imageUrl.value);
  file.value = (event.target as HTMLInputElement).files?.[0];
  imageUrl.value = file.value ? URL.createObjectURL(file.value) : "";
  draft.value = undefined;
  actionError.value = "";
  saved.value = "";
}

async function preview(photo: File) {
  busy.value = true;
  actionError.value = "";
  saved.value = "";
  draft.value = undefined;
  try {
    const body = new FormData();
    body.append("photo", photo);
    const response = await api<{ reportDate: string | null; items: Item[] }>("/checkups/preview",
      { method: "POST", body }, 105_000);
    draft.value = { reportDate: response.reportDate ?? "", title: "体检报告", items: response.items };
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
onBeforeUnmount(() => URL.revokeObjectURL(imageUrl.value));
</script>

<template>
  <section class="panel">
    <h2>上传体检报告</h2>
    <p class="muted">选择单页 JPEG 或 PNG，最多 5 MB。识别会把处理后的图片发送给配置的外部模型。请先遮盖姓名、证件号码等无关信息。</p>
    <form class="filter-bar" @submit.prevent="file && preview(file)">
      <label>报告图片<input type="file" accept="image/jpeg,image/png" :disabled="busy" required @change="selectFile" /></label>
      <button :disabled="busy || !file">提取指标</button>
    </form>
    <details v-if="imageUrl">
      <summary>查看本次原图</summary>
      <img class="checkup-original" :src="imageUrl" alt="本次上传的体检报告原图" />
    </details>
    <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
    <p v-if="saved" class="feedback success" role="status">{{ saved }}</p>
    <form v-if="draft" @submit.prevent="save(draft)">
      <h3>核对提取结果</h3>
      <p v-if="!draft.items.length">没有读到可核对的指标，可换用更清晰的图片。</p>
      <fieldset v-else :disabled="busy">
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
            <label>原文位置<input v-model.trim="item.location" required maxlength="180" /></label>
            <label>原文摘录<textarea v-model.trim="item.excerpt" required maxlength="400" rows="2" /></label>
          </div>
          <button type="button" class="secondary" @click="draft.items.splice(index, 1)">移除此项</button>
        </fieldset>
        <button>确认保存报告</button>
      </fieldset>
    </form>
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
          <td>{{ item.referenceRange || "—" }}</td><td>{{ item.location }} · {{ item.excerpt }}</td>
        </tr></tbody>
      </table></div>
      <button type="button" class="secondary danger-text" :disabled="busy" @click="remove(selected)">删除报告</button>
    </div>
  </section>
</template>

<style scoped>
.checkup-original { display: block; width: min(100%, 58rem); height: auto; margin-block: 0.75rem; }
.checkup-item { margin-block: 0.75rem; }
</style>
