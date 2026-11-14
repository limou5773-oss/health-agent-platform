<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { Plus, X } from "@lucide/vue";
import { api, errorMessage, json, recentRange } from "../api";
import type { Page, StepMode, StepRecord } from "../types";
import StateNotice from "./StateNotice.vue";
import ActivityStatistics from "./ActivityStatistics.vue";
const props = defineProps<{ refreshKey: number }>();
const filters = reactive({ ...recentRange(), sourceName: "" });
const records = ref<Page<StepRecord> | null>(null),
  sources = ref<string[]>([]),
  page = ref(1);
const loading = ref(true),
  sourcesLoading = ref(false),
  busy = ref(false),
  revision = ref(0);
const error = ref(""),
  sourceError = ref(""),
  actionError = ref(""),
  message = ref("");
const editor = ref(false),
  editingId = ref<number | null>(null);
const form = reactive({
  startTime: "",
  endTime: "",
  steps: "" as number | "",
  mode: "CUMULATIVE" as StepMode,
  sourceName: "",
  note: "",
});
const modeText = { CUMULATIVE: "累计快照", DELTA: "区间增量" };
async function loadRecords() {
  loading.value = true;
  error.value = "";
  records.value = null;
  const { sourceName, ...dates } = filters;
  try {
    records.value = await api<Page<StepRecord>>(
      `/activity/steps?${new URLSearchParams({ ...dates, ...(sourceName ? { sourceName } : {}), page: String(page.value), pageSize: "20" })}`,
    );
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function loadSources() {
  sourcesLoading.value = true;
  sourceError.value = "";
  try {
    sources.value = await api<string[]>("/activity/steps/sources");
  } catch (cause) {
    sourceError.value = errorMessage(cause);
  } finally {
    sourcesLoading.value = false;
  }
}
async function load() {
  await Promise.all([loadRecords(), loadSources()]);
}
function edit(record?: StepRecord) {
  editingId.value = record?.id ?? null;
  Object.assign(
    form,
    record
      ? {
          startTime: record.startTime.slice(0, 19),
          endTime: record.endTime.slice(0, 19),
          steps: record.steps,
          mode: record.mode,
          sourceName: record.sourceName,
          note: record.note,
        }
      : {
          startTime: "",
          endTime: "",
          steps: "",
          mode: "CUMULATIVE",
          sourceName: "",
          note: "",
        },
  );
  actionError.value = "";
  message.value = "";
  editor.value = true;
}
async function save() {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(
      editingId.value === null
        ? "/activity/steps"
        : `/activity/steps/${editingId.value}`,
      json(editingId.value === null ? "POST" : "PUT", {
        ...form,
        steps: Number(form.steps),
      }),
    );
    editor.value = false;
    message.value = "步数记录已保存。";
    page.value = 1;
    revision.value++;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function remove(record: StepRecord) {
  if (
    !window.confirm(
      `删除 ${record.activityDate} 来自“${record.sourceName}”的这条步数记录？统计将重新计算。`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(`/activity/steps/${record.id}`, json("DELETE"));
    message.value = "步数记录已删除。";
    page.value = 1;
    revision.value++;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function filter() {
  page.value = 1;
  void loadRecords();
}
function changePage(next: number) {
  page.value = next;
  void loadRecords();
}
watch(
  () => props.refreshKey,
  () => {
    page.value = 1;
    void load();
  },
);
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="section-heading">
      <div>
        <h2>步数记录</h2>
        <p>记录真实步数与来源，区分累计快照和区间增量。</p>
      </div>
      <button :disabled="busy" @click="edit()">
        <Plus :size="16" />录入步数
      </button>
    </div>
    <form class="filter-bar" @submit.prevent="filter">
      <label
        >记录开始日期<input
          v-model="filters.startDate"
          type="date"
          required
          :max="filters.endDate"
          :disabled="loading || busy"
      /></label>
      <label
        >记录结束日期<input
          v-model="filters.endDate"
          type="date"
          required
          :min="filters.startDate"
          :disabled="loading || busy"
      /></label>
      <label
        >步数来源<select
          v-model="filters.sourceName"
          :disabled="loading || busy || sourcesLoading"
        >
          <option value="">所有来源</option>
          <option v-for="source in sources" :key="source" :value="source">
            {{ source }}
          </option>
        </select></label
      >
      <button class="secondary" :disabled="loading || busy">筛选记录</button>
    </form>
    <StateNotice :error="sourceError" @retry="loadSources" />
    <section v-if="editor" class="editor-panel" aria-label="步数编辑表单">
      <div class="section-heading">
        <h3>{{ editingId === null ? "新增步数记录" : "编辑步数记录" }}</h3>
        <button
          class="icon-button"
          :disabled="busy"
          aria-label="关闭步数编辑"
          @click="editor = false"
        >
          <X :size="18" />
        </button>
      </div>
      <form class="form-grid" @submit.prevent="save">
        <label
          >步数语义<select v-model="form.mode" :disabled="busy">
            <option value="CUMULATIVE">累计快照 CUMULATIVE</option>
            <option value="DELTA">区间增量 DELTA</option>
          </select></label
        >
        <label
          >步数（非负整数）<input
            v-model="form.steps"
            type="number"
            min="0"
            max="2147483647"
            step="1"
            required
            :disabled="busy"
        /></label>
        <label
          >开始时间<input
            v-model="form.startTime"
            type="datetime-local"
            step="1"
            required
            :disabled="busy"
        /></label>
        <label
          >结束时间<input
            v-model="form.endTime"
            type="datetime-local"
            step="1"
            :min="form.startTime"
            required
            :disabled="busy"
        /></label>
        <label
          >来源名称<input
            v-model="form.sourceName"
            :readonly="editingId !== null"
            list="step-sources"
            maxlength="100"
            required
            :disabled="busy"
            placeholder="填写实际设备或手工记录来源"
        /></label>
        <label
          >备注（选填）<textarea
            v-model="form.note"
            rows="2"
            maxlength="1000"
            :disabled="busy"
          />
        </label>
        <p class="field-hint span-all">
          {{
            form.mode === "CUMULATIVE"
              ? "累计快照：开始时间须为当天 00:00:00；步数为截至结束时间的当日累计值。同日同来源统计只取最新快照，更正时编辑原记录。"
              : "区间增量：步数为起止区间内的新增步数。同日同来源区间不能重叠，首尾相接允许。"
          }}
          两种语义不能在同日同来源混用；区间至少 1
          秒，须在同一天内，可恰好结束于次日零点。
        </p>
        <div class="actions span-all">
          <button :disabled="busy">{{ busy ? "保存中…" : "保存记录" }}</button
          ><button
            type="button"
            class="secondary"
            :disabled="busy"
            @click="editor = false"
          >
            取消
          </button>
        </div>
      </form>
    </section>
    <p v-if="actionError" class="feedback error" role="alert">
      {{ actionError }}
    </p>
    <p v-if="message" class="feedback success" role="status">{{ message }}</p>
    <StateNotice
      :loading="loading"
      :error="error"
      :empty="records?.total === 0"
      title="当前范围没有步数记录"
      description="调整筛选范围，或录入、导入真实步数。"
      @retry="loadRecords"
    />
    <template v-if="!loading && !error && records && records.total">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>日期 / 时间区间</th>
              <th>步数</th>
              <th>语义</th>
              <th>来源 / 备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records.items" :key="record.id">
              <td>
                {{ record.activityDate }}
                <p class="small">
                  {{ record.startTime.replace("T", " ") }}<br />至
                  {{ record.endTime.replace("T", " ") }}
                </p>
              </td>
              <td>{{ record.steps.toLocaleString("zh-CN") }}</td>
              <td>
                <span class="tag">{{ modeText[record.mode] }}</span>
              </td>
              <td class="note-cell">
                {{ record.sourceName }}
                <span v-if="record.corrected" class="tag">已更正</span>
                <p v-if="record.note" class="small">{{ record.note }}</p>
              </td>
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
        <span>共 {{ records.total }} 条 · 第 {{ page }} 页</span
        ><button
          class="secondary"
          :disabled="busy || page === 1"
          @click="changePage(page - 1)"
        >
          上一页</button
        ><button
          class="secondary"
          :disabled="busy || page * records.pageSize >= records.total"
          @click="changePage(page + 1)"
        >
          下一页
        </button>
      </div>
    </template>
    <datalist id="step-sources">
      <option v-for="source in sources" :key="source" :value="source" />
    </datalist>
  </section>
  <ActivityStatistics
    kind="steps"
    :sources="sources"
    :refresh-key="refreshKey + revision"
  />
</template>
