<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage, json, statusText } from "../api";
import StateNotice from "../components/StateNotice.vue";

type Settings = {
  weeklyEnabled: boolean;
  importEnabled: boolean;
  importDays: number;
  sourceName: string | null;
  notifyInApp: boolean;
};
type Run = {
  kind: "WEEKLY_REVIEW" | "SLEEP_IMPORT";
  eventKey: string;
  startDate: string;
  endDate: string;
  sourceName: string | null;
  createdAt: string;
  taskId: string | null;
  taskStatus: string | null;
};
const settings = ref<Settings | null>(null);
const sources = ref<string[]>([]);
const runs = ref<Run[]>([]);
const loading = ref(true);
const runLoading = ref(true);
const error = ref("");
const runError = ref("");
const saving = ref(false);
const saveError = ref("");
const saved = ref(false);

async function loadSettings() {
  loading.value = true;
  error.value = "";
  try {
    [settings.value, sources.value] = await Promise.all([
      api<Settings>("/agent/triggers/settings"),
      api<string[]>("/sleep/sources"),
    ]);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function loadRuns() {
  runLoading.value = true;
  runError.value = "";
  try {
    runs.value = await api<Run[]>("/agent/triggers/runs");
  } catch (cause) {
    runError.value = errorMessage(cause);
  } finally {
    runLoading.value = false;
  }
}

async function save() {
  saving.value = true;
  saveError.value = "";
  saved.value = false;
  try {
    settings.value = await api<Settings>("/agent/triggers/settings", json("PUT", settings.value));
    saved.value = true;
  } catch (cause) {
    saveError.value = errorMessage(cause);
  } finally {
    saving.value = false;
  }
}

onMounted(() => { void loadSettings(); void loadRuns(); });
</script>

<template>
  <section class="panel">
    <h2>触发设置</h2>
    <p class="muted">主动任务需要在个人档案中开启 AI 分析授权。</p>
    <StateNotice :loading="loading" :error="error" @retry="loadSettings" />
    <form v-if="settings && !loading && !error" @submit.prevent="save">
      <fieldset :disabled="saving" class="trigger-fields">
        <div class="form-grid">
          <label class="checkbox-label"><input v-model="settings.weeklyEnabled" type="checkbox" />每周睡眠复盘</label>
          <p class="field-hint">按档案时区，每周回顾上周一至周日的睡眠并生成报告。</p>
          <label class="checkbox-label"><input v-model="settings.importEnabled" type="checkbox" />睡眠导入后分析</label>
          <label>分析天数（含最新导入日期）
            <input v-model.number="settings.importDays" type="number" min="1" max="31" required />
          </label>
          <label>分析来源
            <input v-model="settings.sourceName" list="trigger-sources" maxlength="100" placeholder="全部来源" />
            <datalist id="trigger-sources"><option v-for="source in sources" :key="source" :value="source" /></datalist>
          </label>
          <label class="checkbox-label"><input v-model="settings.notifyInApp" type="checkbox" />创建任务时发送站内消息</label>
        </div>
        <button>{{ saving ? "正在保存…" : "保存触发设置" }}</button>
      </fieldset>
      <p v-if="saveError" class="feedback error" role="alert">{{ saveError }}</p>
      <p v-if="saved" class="feedback success" role="status">触发设置已保存。</p>
    </form>
  </section>
  <section class="panel">
    <div class="section-heading">
      <h2>最近触发记录</h2>
      <button class="secondary" :disabled="runLoading" @click="loadRuns">刷新</button>
    </div>
    <StateNotice :loading="runLoading" :error="runError" :empty="!runs.length"
      title="暂无触发记录" description="自动创建任务后可在此查看。" @retry="loadRuns" />
    <div v-if="!runLoading && !runError && runs.length" class="table-wrap">
      <table>
        <thead><tr><th>触发来源</th><th>分析周期</th><th>创建时间</th><th>任务</th></tr></thead>
        <tbody>
          <tr v-for="run in runs" :key="`${run.kind}:${run.eventKey}`">
            <td>{{ run.kind === "WEEKLY_REVIEW" ? "每周睡眠复盘" : `睡眠导入批次 #${run.eventKey}` }}</td>
            <td>{{ run.startDate }} 至 {{ run.endDate }}<br /><span class="small muted">{{ run.sourceName ?? "全部来源" }}</span></td>
            <td>{{ run.createdAt.replace("T", " ") }}</td>
            <td>
              <RouterLink v-if="run.taskId && run.taskStatus" :to="{ path: '/agent', query: { task: run.taskId } }">
                {{ statusText[run.taskStatus] }} · 查看任务
              </RouterLink>
              <span v-else>任务已删除</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.trigger-fields { border: 0; padding: 0; margin: 0; }
.trigger-fields button { margin-top: 1rem; }
</style>
