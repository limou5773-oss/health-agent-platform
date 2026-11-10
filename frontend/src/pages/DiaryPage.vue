<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import { NotebookPen, ShieldCheck } from "@lucide/vue";
import { api, errorMessage } from "../api";
import StateNotice from "../components/StateNotice.vue";
import DiaryRecordsPanel from "../components/DiaryRecordsPanel.vue";
import DiaryRecordDetail from "../components/DiaryRecordDetail.vue";

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
    sources.value = await api<string[]>("/diary/sources");
  } catch (cause) {
    sourceError.value = errorMessage(cause);
  } finally {
    sourceLoading.value = false;
  }
}
function changed(deletedId?: number) {
  if (detailId.value === deletedId) detailId.value = null;
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
  <div class="intro-strip">
    <NotebookPen :size="24" aria-hidden="true" />
    <div>
      <p>感受由你描述，分享由你决定。</p>
      <span class="small muted"
        >记录自己的情绪、压力、身体感受与事件；可以补写和修改历史日记。</span
      >
    </div>
  </div>
  <div class="feedback diary-privacy">
    <ShieldCheck :size="20" aria-hidden="true" />
    <div>
      <strong>新日记默认仅自己可见</strong>
      <p>
        健康助手只有在个人档案开启 AI
        授权、这条日记允许读取、且本次任务主动勾选日记时，才可读取任务日期内的授权内容。睡眠来源筛选不限制日记来源。
      </p>
      <p>
        撤销控制后续读取与平台缓存，已发送给外部模型的信息无法追回。已确认的正式计划会保留。
      </p>
    </div>
  </div>
  <StateNotice
    :loading="sourceLoading"
    :error="sourceError"
    @retry="loadSources"
  />
  <DiaryRecordsPanel
    :sources="sources"
    :refresh-key="revision"
    @changed="changed"
    @detail="showDetail"
  />
  <div
    v-if="detailId !== null"
    ref="detailSection"
    tabindex="-1"
    class="diary-detail-anchor"
  >
    <DiaryRecordDetail
      :record-id="detailId"
      :refresh-key="revision"
      @close="detailId = null"
    />
  </div>
</template>
<style scoped>
.diary-privacy {
  display: flex;
  gap: 0.75rem;
  margin: 0 0 1.5rem;
}
.diary-privacy > svg {
  flex: none;
  color: var(--green);
}
.diary-privacy p {
  margin: 0.5rem 0 0;
}
.diary-detail-anchor {
  scroll-margin-top: 5rem;
}
</style>
