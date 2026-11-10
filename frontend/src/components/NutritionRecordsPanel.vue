<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Plus } from "@lucide/vue";
import { api, errorMessage, json, recentRange } from "../api";
import type { MealRecord, Page, WaterRecord } from "../types";
import MealEditor from "./MealEditor.vue";
import WaterEditor from "./WaterEditor.vue";
import NutritionRecordList from "./NutritionRecordList.vue";
import NutritionStatistics from "./NutritionStatistics.vue";
import StateNotice from "./StateNotice.vue";
const props = defineProps<{ kind: "meals" | "water" }>();
const label = computed(() => (props.kind === "meals" ? "饮食" : "饮水"));
const filters = reactive({ ...recentRange(), sourceName: "" });
const records = ref<Page<MealRecord | WaterRecord> | null>(null);
const sources = ref<string[]>([]),
  page = ref(1),
  revision = ref(0);
const loading = ref(false),
  sourceLoading = ref(false),
  busy = ref(false);
const error = ref(""),
  sourceError = ref(""),
  actionError = ref(""),
  message = ref("");
const editor = ref(false),
  editorKey = ref(0);
const editingMeal = ref<MealRecord | null>(null),
  editingWater = ref<WaterRecord | null>(null);
async function loadRecords() {
  loading.value = true;
  records.value = null;
  error.value = "";
  const { sourceName, ...dates } = filters;
  try {
    records.value = await api<Page<MealRecord | WaterRecord>>(
      `/nutrition/${props.kind}?${new URLSearchParams({ ...dates, ...(sourceName ? { sourceName } : {}), page: String(page.value), pageSize: "20" })}`,
    );
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function loadSources() {
  sourceLoading.value = true;
  sourceError.value = "";
  try {
    sources.value = await api<string[]>(`/nutrition/${props.kind}/sources`);
  } catch (cause) {
    sourceError.value = errorMessage(cause);
  } finally {
    sourceLoading.value = false;
  }
}
async function load() {
  await Promise.all([loadRecords(), loadSources()]);
}
function edit(record?: MealRecord | WaterRecord) {
  editingMeal.value = record && "foodName" in record ? record : null;
  editingWater.value = record && "amountMl" in record ? record : null;
  editorKey.value++;
  editor.value = true;
  actionError.value = "";
  message.value = "";
}
async function saved() {
  editor.value = false;
  message.value = `${label.value}记录已保存。`;
  page.value = 1;
  revision.value++;
  await load();
}
async function remove(record: MealRecord | WaterRecord) {
  if (
    !window.confirm(
      `删除 ${record.consumedAt.replace("T", " ")} 的${label.value}记录？`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(`/nutrition/${props.kind}/${record.id}`, json("DELETE"));
    if (
      editingMeal.value?.id === record.id ||
      editingWater.value?.id === record.id
    )
      editor.value = false;
    message.value = `${label.value}记录已删除。`;
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
onMounted(load);
</script>
<template>
  <section class="panel" :aria-label="`${label}记录`">
    <div class="section-heading">
      <div>
        <h2>{{ label }}记录</h2>
        <p>
          {{
            kind === "meals"
              ? "记录三餐与加餐，保留真实份量、热量方式和依据。"
              : "以毫升记录每次实际饮水，回顾已记录水量。"
          }}
        </p>
      </div>
      <button :disabled="busy || editor" @click="edit()">
        <Plus :size="16" />录入{{ label }}
      </button>
    </div>
    <form class="filter-bar" @submit.prevent="filter">
      <label
        >开始日期（实际时间）<input
          v-model="filters.startDate"
          type="date"
          required
          :max="filters.endDate"
          :disabled="loading || busy"
      /></label>
      <label
        >结束日期（实际时间）<input
          v-model="filters.endDate"
          type="date"
          required
          :min="filters.startDate"
          :disabled="loading || busy"
      /></label>
      <label
        >记录来源<select
          v-model="filters.sourceName"
          :disabled="loading || busy || sourceLoading"
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
    <StateNotice
      :loading="sourceLoading"
      :error="sourceError"
      @retry="loadSources"
    />
    <MealEditor
      v-if="editor && kind === 'meals'"
      :key="editorKey"
      :record="editingMeal"
      :sources="sources"
      @busy="busy = $event"
      @close="editor = false"
      @saved="saved"
    />
    <WaterEditor
      v-if="editor && kind === 'water'"
      :key="editorKey"
      :record="editingWater"
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
      :title="`当前范围没有${label}记录`"
      description="可调整日期与来源筛选，或录入实际记录。"
      @retry="loadRecords"
    />
    <template v-if="!loading && !error && records && records.total">
      <NutritionRecordList
        :kind="kind"
        :items="records.items"
        :busy="busy || editor"
        @edit="edit"
        @remove="remove"
      />
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
      按档案时区的实际{{
        kind === "meals" ? "食用" : "饮用"
      }}日期筛选。同一来源的同秒重复事件会被拒绝；更正时请编辑原记录。详情可核对创建与更新时间。
    </p>
  </section>
  <NutritionStatistics
    :kind="kind"
    :sources="sources"
    :refresh-key="revision"
  />
</template>
