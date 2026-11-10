<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Plus } from "@lucide/vue";
import { api, errorMessage, json, recentRange } from "../api";
import type { Page, Workout } from "../types";
import StateNotice from "./StateNotice.vue";
import WorkoutEditor from "./WorkoutEditor.vue";
import ActivityStatistics from "./ActivityStatistics.vue";
const filters = reactive({ ...recentRange(), sourceName: "" });
const records = ref<Page<Workout> | null>(null),
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
  editingRecord = ref<Workout | null>(null),
  editorKey = ref(0);
const number = (value: number) =>
  value.toLocaleString("zh-CN", { maximumFractionDigits: 4 });
async function loadRecords() {
  loading.value = true;
  records.value = null;
  error.value = "";
  const { sourceName, ...dates } = filters;
  try {
    records.value = await api<Page<Workout>>(
      `/activity/workouts?${new URLSearchParams({ ...dates, ...(sourceName ? { sourceName } : {}), page: String(page.value), pageSize: "20" })}`,
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
    sources.value = await api<string[]>("/activity/workouts/sources");
  } catch (cause) {
    sourceError.value = errorMessage(cause);
  } finally {
    sourcesLoading.value = false;
  }
}
async function load() {
  await Promise.all([loadRecords(), loadSources()]);
}
function edit(record?: Workout) {
  editingRecord.value = record ?? null;
  editorKey.value++;
  editor.value = true;
  actionError.value = "";
  message.value = "";
}
async function saved() {
  editor.value = false;
  message.value =
    "运动记录已保存。如需完成关联的计划任务，请单独点击“确认打卡”。";
  page.value = 1;
  revision.value++;
  await load();
}
async function remove(record: Workout) {
  if (
    !window.confirm(
      `删除 ${record.activityDate} 的“${record.exerciseType}”记录？该操作只删除运动记录，计划打卡需在计划页单独管理。`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(`/activity/workouts/${record.id}`, json("DELETE"));
    message.value = "运动记录已删除。";
    page.value = 1;
    revision.value++;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function checkin(record: Workout) {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(
      `/plan-items/${record.planItemId}/checkins`,
      json("POST", { date: record.activityDate }),
    );
    message.value = "关联计划任务已打卡。";
    await loadRecords();
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
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="section-heading">
      <div>
        <h2>运动记录</h2>
        <p>记录运动类型、起止时间和来源，可自选关联计划任务。</p>
      </div>
      <button :disabled="busy" @click="edit()">
        <Plus :size="16" />录入运动
      </button>
    </div>
    <form class="filter-bar" @submit.prevent="filter">
      <label
        >开始日期（按开始时间）<input
          v-model="filters.startDate"
          type="date"
          required
          :max="filters.endDate"
          :disabled="loading || busy"
      /></label>
      <label
        >结束日期（按开始时间）<input
          v-model="filters.endDate"
          type="date"
          required
          :min="filters.startDate"
          :disabled="loading || busy"
      /></label>
      <label
        >运动来源<select
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
    <WorkoutEditor
      v-if="editor"
      :key="editorKey"
      :record="editingRecord"
      :sources="sources"
      @busy="busy = $event"
      @close="editor = false"
      @saved="saved"
    />
    <p v-if="actionError" class="feedback error" role="alert">
      {{ actionError }}
    </p>
    <p v-if="message" class="feedback success" role="status">{{ message }}</p>
    <StateNotice
      :loading="loading"
      :error="error"
      :empty="records?.total === 0"
      title="当前范围没有运动记录"
      description="可调整日期、来源筛选，或录入实际运动。"
      @retry="loadRecords"
    />
    <template v-if="!loading && !error && records && records.total">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>运动 / 归档日期</th>
              <th>开始 / 结束</th>
              <th>时长</th>
              <th>来源 / 备注</th>
              <th>关联计划任务</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records.items" :key="record.id">
              <td>
                <strong>{{ record.exerciseType }}</strong>
                <p class="small">{{ record.activityDate }}</p>
              </td>
              <td>
                {{ record.startTime.replace("T", " ") }}<br />{{
                  record.endTime.replace("T", " ")
                }}
              </td>
              <td>{{ number(record.durationMinutes) }} 分钟</td>
              <td class="note-cell">
                {{ record.sourceName }}
                <p v-if="record.note" class="small">{{ record.note }}</p>
              </td>
              <td class="note-cell">
                <template v-if="record.planItemId !== null"
                  ><RouterLink
                    :to="{ path: '/plans', query: { id: record.planId } }"
                    >{{ record.planItemTitle }}</RouterLink
                  >
                  <p class="small">
                    <span
                      :class="record.planItemCompleted ? 'tag' : 'tag warm'"
                      >{{
                        record.planItemCompleted === null
                          ? "打卡状态未提供"
                          : record.planItemCompleted
                            ? "已打卡"
                            : "未打卡"
                      }}</span
                    >
                  </p>
                  <button
                    v-if="record.planItemCompleted === false"
                    class="text-button"
                    :disabled="busy"
                    @click="checkin(record)"
                  >
                    确认打卡
                  </button></template
                >
                <span v-else class="muted">未关联</span>
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
    <p class="footnote">
      记录按开始日期归档；统计按每日实际覆盖时段计算。运动保存、删除与任务打卡分别操作，暂停计划需遵守计划页现有规则。
    </p>
  </section>
  <ActivityStatistics
    kind="workouts"
    :sources="sources"
    :refresh-key="revision"
  />
</template>
