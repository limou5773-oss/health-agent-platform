<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { Plus } from "@lucide/vue";
import { api, errorMessage, json, recentRange } from "../api";
import type { Page, WeightRecord } from "../types";
import { weightText, weightTime } from "../weight";
import StateNotice from "./StateNotice.vue";
import WeightEditor from "./WeightEditor.vue";

const props = defineProps<{ sources: string[]; refreshKey: number }>();
const emit = defineEmits<{ changed: []; detail: [id: number] }>();
const filters = reactive({ ...recentRange(), sourceName: "" });
const records = ref<Page<WeightRecord> | null>(null),
  page = ref(1);
const loading = ref(false),
  busy = ref(false),
  editor = ref(false);
const editing = ref<WeightRecord | null>(null),
  editorKey = ref(0);
const error = ref(""),
  actionError = ref(""),
  message = ref("");
let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  records.value = null;
  error.value = "";
  const { sourceName, startDate, endDate } = filters;
  try {
    const result = await api<Page<WeightRecord>>(
      `/weight/records?${new URLSearchParams({
        ...(startDate ? { startDate } : {}),
        ...(endDate ? { endDate } : {}),
        ...(sourceName ? { sourceName } : {}),
        page: String(page.value),
        pageSize: "20",
      })}`,
    );
    if (currentRequest === requestId) records.value = result;
  } catch (cause) {
    if (currentRequest === requestId) error.value = errorMessage(cause);
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}
function edit(record: WeightRecord | null = null) {
  editing.value = record;
  editorKey.value++;
  editor.value = true;
  actionError.value = "";
  message.value = "";
}
function saved() {
  editor.value = false;
  message.value = "测量已保存。";
  page.value = 1;
  emit("changed");
}
async function remove(record: WeightRecord) {
  if (
    !window.confirm(
      `删除 ${weightTime(record.measuredAt)} 的测量 #${record.id}？`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(`/weight/records/${record.id}`, json("DELETE"));
    message.value = "测量已删除。";
    page.value = 1;
    emit("changed");
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function filter() {
  page.value = 1;
  void load();
}
function changePage(next: number) {
  page.value = next;
  void load();
}
watch(() => props.refreshKey, load);
onMounted(load);
</script>
<template>
  <section
    id="weight-records"
    class="panel weight-records"
    aria-labelledby="weight-records-title"
  >
    <div class="section-heading">
      <div>
        <h2 id="weight-records-title">测量记录</h2>
        <p>每条测量独立保存，可查看原始明细或更正。</p>
      </div>
      <button :disabled="busy || editor" @click="edit()">
        <Plus :size="16" />录入测量
      </button>
    </div>
    <form class="filter-bar" @submit.prevent="filter">
      <label
        >开始日期<input
          v-model="filters.startDate"
          type="date"
          :max="filters.endDate"
          :disabled="loading || busy"
      /></label>
      <label
        >结束日期<input
          v-model="filters.endDate"
          type="date"
          :min="filters.startDate"
          :disabled="loading || busy"
      /></label>
      <label
        >记录来源<select
          v-model="filters.sourceName"
          :disabled="loading || busy"
        >
          <option value="">所有来源</option>
          <option
            v-if="filters.sourceName && !sources.includes(filters.sourceName)"
            :value="filters.sourceName"
          >
            {{ filters.sourceName }}（当前筛选）
          </option>
          <option v-for="source in sources" :key="source" :value="source">
            {{ source }}
          </option>
        </select></label
      >
      <button class="secondary" :disabled="loading || busy">筛选记录</button>
    </form>
    <WeightEditor
      v-if="editor"
      :key="editorKey"
      :record="editing"
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
      title="当前筛选范围没有测量"
      description="可调整日期或来源，或录入实际测量。清空日期可查看全部记录。"
      @retry="load"
    />
    <template v-if="!loading && !error && records && records.total">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>实际测量时间</th>
              <th>体重</th>
              <th>来源 / 编号</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records.items" :key="record.id">
              <td>{{ weightTime(record.measuredAt) }}</td>
              <td>
                <strong>{{ weightText(record.weightKg) }}</strong>
              </td>
              <td class="note-cell">
                {{ record.sourceName }}
                <p class="small muted">#{{ record.id }}</p>
              </td>
              <td>
                <div class="actions">
                  <button
                    class="text-button"
                    @click="emit('detail', record.id)"
                  >
                    原始详情
                  </button>
                  <button
                    class="text-button"
                    :disabled="busy || editor"
                    @click="edit(record)"
                  >
                    编辑
                  </button>
                  <button
                    class="text-button danger-text"
                    :disabled="busy || editor"
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
        <span>共 {{ records.total }} 条 · 第 {{ page }} 页</span>
        <button
          class="secondary"
          :disabled="busy || page === 1"
          @click="changePage(page - 1)"
        >
          上一页
        </button>
        <button
          class="secondary"
          :disabled="busy || page * records.pageSize >= records.total"
          @click="changePage(page + 1)"
        >
          下一页
        </button>
      </div>
    </template>
    <p class="footnote">
      列表按实际测量日期筛选，不合并同日记录。实际测量时间、创建时间和更新时间可在原始详情中分别核对。
    </p>
  </section>
</template>
<style scoped>
.weight-records {
  scroll-margin-top: 5rem;
}
</style>
