<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Download, Plus, X } from "@lucide/vue";
import {
  api,
  dateAfter,
  download,
  errorMessage,
  json,
  recentRange,
} from "../api";
import type { Page, SleepRecord } from "../types";
import StateNotice from "../components/StateNotice.vue";
import ImportPanel from "../components/ImportPanel.vue";
const filters = reactive({ ...recentRange(), sourceName: "" });
const data = ref<Page<SleepRecord> | null>(null),
  sources = ref<string[]>([]),
  page = ref(1),
  loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  message = ref(""),
  busy = ref(false);
const editor = ref(false),
  editingId = ref<number | null>(null);
const form = reactive({ startTime: "", endTime: "", sourceName: "", note: "" });
async function load() {
  loading.value = true;
  error.value = "";
  data.value = null;
  const { sourceName, ...dates } = filters;
  try {
    const [records, names] = await Promise.all([
      api<Page<SleepRecord>>(
        `/sleep?${new URLSearchParams({ ...dates, ...(sourceName ? { sourceName } : {}), page: String(page.value), pageSize: "20" })}`,
      ),
      api<string[]>("/sleep/sources"),
    ]);
    data.value = records;
    sources.value = names;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
function edit(record?: SleepRecord) {
  editingId.value = record?.id || null;
  Object.assign(
    form,
    record
      ? {
          startTime: record.startTime.slice(0, 19),
          endTime: record.endTime.slice(0, 19),
          sourceName: record.sourceName,
          note: record.note,
        }
      : { startTime: "", endTime: "", sourceName: "", note: "" },
  );
  editor.value = true;
  actionError.value = "";
  message.value = "";
}
async function save() {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(
      editingId.value ? `/sleep/${editingId.value}` : "/sleep",
      json(editingId.value ? "PUT" : "POST", form),
    );
    editor.value = false;
    message.value = "睡眠记录已保存。";
    page.value = 1;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function remove(record: SleepRecord) {
  if (
    !window.confirm(
      `删除 ${record.sleepDate} 的这条记录？相关分析与报告将失效。`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(`/sleep/${record.id}`, json("DELETE"));
    message.value = "记录已删除，相关分析与报告已失效。";
    page.value = 1;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function exportCsv() {
  busy.value = true;
  actionError.value = "";
  try {
    await download(
      `/sleep/export?${new URLSearchParams({ startDate: filters.startDate, endDate: filters.endDate })}`,
      "我的睡眠记录.csv",
    );
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function changePage(next: number) {
  page.value = next;
  void load();
}
onMounted(load);
</script>
<template>
  <div class="page-actions">
    <p class="muted">以醒来日期归档，时长由系统计算。</p>
    <div class="actions">
      <button class="secondary" :disabled="busy" @click="exportCsv">
        <Download :size="16" />导出当前日期范围</button
      ><button @click="edit()"><Plus :size="16" />新增记录</button>
    </div>
  </div>
  <p v-if="message" class="feedback success" role="status">{{ message }}</p>
  <p v-if="actionError" class="feedback error" role="alert">
    {{ actionError }}
  </p>
  <section v-if="editor" class="panel editor-panel">
    <div class="section-heading">
      <h2>{{ editingId ? "编辑睡眠记录" : "新增睡眠记录" }}</h2>
      <button class="icon-button" aria-label="关闭编辑" @click="editor = false">
        <X :size="20" />
      </button>
    </div>
    <form class="form-grid" @submit.prevent="save">
      <label
        >入睡时间<input
          v-model="form.startTime"
          type="datetime-local"
          step="1"
          required /></label
      ><label
        >醒来时间<input
          v-model="form.endTime"
          type="datetime-local"
          step="1"
          required
          :min="form.startTime" /></label
      ><label
        >数据来源<input
          v-model="form.sourceName"
          required
          maxlength="100"
          list="source-list"
          placeholder="如：手动记录" /><datalist id="source-list">
          <option
            v-for="source in sources"
            :key="source"
            :value="source"
          /></datalist></label
      ><label
        >备注<input
          v-model="form.note"
          maxlength="1000"
          placeholder="可以记录影响这一晚的因素"
      /></label>
      <div class="span-all actions">
        <button :disabled="busy">{{ busy ? "保存中…" : "保存记录" }}</button
        ><button type="button" class="secondary" @click="editor = false">
          取消
        </button>
      </div>
    </form>
  </section>
  <section class="panel">
    <form
      class="filter-bar"
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <label
        >开始日期<input
          v-model="filters.startDate"
          type="date"
          required
          :max="filters.endDate" /></label
      ><label
        >结束日期<input
          v-model="filters.endDate"
          type="date"
          required
          :min="filters.startDate"
          :max="dateAfter(filters.startDate, 365)" /></label
      ><label
        >来源<select v-model="filters.sourceName">
          <option value="">全部来源</option>
          <option v-for="source in sources" :key="source">{{ source }}</option>
        </select></label
      ><button class="secondary" :disabled="loading">筛选记录</button>
    </form>
    <StateNotice
      :loading="loading"
      :error="error"
      :empty="data?.items.length === 0"
      title="还没有这个时间段的记录"
      description="新增一条记录，或在下方导入 CSV 文件。"
      @retry="load"
    /><template v-if="data && !loading && !error && data.items.length"
      ><div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>醒来日期</th>
              <th>入睡 / 醒来</th>
              <th>时长</th>
              <th>来源</th>
              <th>备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in data.items" :key="record.id">
              <td>
                <strong>{{ record.sleepDate }}</strong
                ><span v-if="record.corrected" class="tag">已修正</span>
              </td>
              <td>
                {{ record.startTime.replace("T", " ") }}<br /><span
                  class="muted"
                  >{{ record.endTime.replace("T", " ") }}</span
                >
              </td>
              <td>{{ Number(record.durationMinutes.toFixed(4)) }} 分钟</td>
              <td>{{ record.sourceName }}</td>
              <td class="note-cell">{{ record.note || "—" }}</td>
              <td>
                <div class="actions">
                  <button
                    class="text-button"
                    :disabled="busy"
                    @click="edit(record)"
                  >
                    编辑</button
                  ><button
                    class="text-button danger-text"
                    :disabled="busy"
                    @click="remove(record)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>共 {{ data.total }} 条 · 第 {{ page }} 页</span
        ><button
          class="secondary"
          :disabled="page === 1"
          @click="changePage(page - 1)"
        >
          上一页</button
        ><button
          class="secondary"
          :disabled="page * 20 >= data.total"
          @click="changePage(page + 1)"
        >
          下一页
        </button>
      </div></template
    >
  </section>
  <ImportPanel
    @imported="
      page = 1;
      load();
    "
  />
</template>
