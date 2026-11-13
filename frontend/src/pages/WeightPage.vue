<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import { Scale, ChartLine, Flag } from "@lucide/vue";
import { api, errorMessage } from "../api";
import StateNotice from "../components/StateNotice.vue";
import WeightRecordsPanel from "../components/WeightRecordsPanel.vue";
import WeightStatisticsPanel from "../components/WeightStatisticsPanel.vue";
import WeightGoalPanel from "../components/WeightGoalPanel.vue";
import WeightRecordDetail from "../components/WeightRecordDetail.vue";
import MeasurementImportPanel from "../components/MeasurementImportPanel.vue";

const sources = ref<string[]>([]);
const sourceLoading = ref(false),
  sourceError = ref("");
const revision = ref(0),
  detailId = ref<number | null>(null);
const detailSection = ref<HTMLElement | null>(null);

async function loadSources() {
  sourceLoading.value = true;
  sourceError.value = "";
  try {
    sources.value = await api<string[]>("/weight/sources");
  } catch (cause) {
    sourceError.value = errorMessage(cause);
  } finally {
    sourceLoading.value = false;
  }
}
function changed() {
  revision.value++;
  void loadSources();
}
async function showDetail(id: number) {
  detailId.value = id;
  await nextTick();
  detailSection.value?.focus();
  detailSection.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}
onMounted(loadSources);
</script>
<template>
  <nav class="page-actions weight-jump" aria-label="体重管理分区">
    <a class="text-link" href="#weight-records"><Scale :size="18" />测量记录</a>
    <a class="text-link" href="#weight-statistics"
      ><ChartLine :size="18" />趋势与阶段对比</a
    >
    <a class="text-link" href="#weight-goal"><Flag :size="18" />个人目标</a>
  </nav>
  <p class="footnote">
    统一使用
    kg（千克），按个人档案时区填写实际测量时间。同日多次测量完整保留；档案体重与此处测量记录分别维护。
  </p>
  <StateNotice
    :loading="sourceLoading"
    :error="sourceError"
    @retry="loadSources"
  />
  <div
    v-if="detailId !== null"
    ref="detailSection"
    tabindex="-1"
    class="weight-detail-anchor"
  >
    <WeightRecordDetail
      :record-id="detailId"
      :refresh-key="revision"
      @close="detailId = null"
    />
  </div>
  <MeasurementImportPanel module="weight" @imported="changed" />
  <WeightRecordsPanel
    :sources="sources"
    :refresh-key="revision"
    @changed="changed"
    @detail="showDetail"
  />
  <WeightStatisticsPanel
    :sources="sources"
    :refresh-key="revision"
    @detail="showDetail"
  />
  <WeightGoalPanel
    :sources="sources"
    :refresh-key="revision"
    @detail="showDetail"
  />
</template>
<style scoped>
.weight-jump {
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 1.5rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--line);
}
.weight-detail-anchor {
  scroll-margin-top: 5rem;
}
</style>
