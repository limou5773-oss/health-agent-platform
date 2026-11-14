<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { api, dateAfter, errorMessage, json, recentRange } from "../api";
import { reportMetrics, type ReportMetric } from "../report-metrics";
import type { Report } from "../types";
import StateNotice from "../components/StateNotice.vue";
import SleepSummary from "../components/SleepSummary.vue";
import HealthReportSummary from "../components/HealthReportSummary.vue";
import FocusedReportSummary from "../components/FocusedReportSummary.vue";

type Grant = {
  id: number; ownerName: string; memberName: string; incoming: boolean;
  accepted: boolean; expired: boolean; expiresAt: string; metrics: ReportMetric[]; reportIds: number[];
};
type Period = {
  average: number | null; validDays: number; periodDays: number; recordCount: number | null;
  startDate: string; endDate: string; missingDates: string[];
};

const grants = ref<Grant[]>([]);
const reports = ref<Report[]>([]);
const loading = ref(true);
const busy = ref(false);
const error = ref("");
const actionError = ref("");
const message = ref("");
const selectedId = ref<number>();
const selected = computed(() => grants.value.find(grant => grant.id === selectedId.value));
const form = reactive({ username: "", days: 30, metrics: [] as ReportMetric[], reportIds: [] as number[] });
const dates = reactive(recentRange());
const sourceName = ref("");
const period = ref<{ metric: ReportMetric; data: Period }>();
const report = ref<Report>();

function clearSelection() {
  selectedId.value = undefined;
  period.value = undefined;
  report.value = undefined;
}

async function load() {
  loading.value = true;
  error.value = "";
  clearSelection();
  try {
    [grants.value, reports.value] = await Promise.all([api<Grant[]>("/family/grants"), api<Report[]>("/reports")]);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function change(path: string, method: string, success: string, body?: unknown) {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(path, json(method, body));
    message.value = success;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

function select(grant: Grant) {
  clearSelection();
  selectedId.value = grant.id;
  actionError.value = "";
}

async function read(grant: Grant, metric?: ReportMetric, reportId?: number) {
  busy.value = true;
  actionError.value = "";
  period.value = undefined;
  report.value = undefined;
  try {
    if (metric) {
      const params = new URLSearchParams({ ...dates, ...(sourceName.value ? { sourceName: sourceName.value } : {}) });
      const data = await api<Period>(`/family/grants/${grant.id}/statistics/${metric}?${params}`);
      period.value = { metric, data };
    } else {
      report.value = await api<Report>(`/family/grants/${grant.id}/reports/${reportId}`);
    }
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<template>
  <StateNotice :loading="loading" :error="error" @retry="load" />
  <template v-if="!loading && !error">
    <section class="panel">
      <h2>邀请家人查看</h2>
      <p class="muted">对方接受后可在有效期内查看所选指标和报告。更新同一成员的授权会替换原邀请，需重新接受。</p>
      <form @submit.prevent="change('/family/grants', 'POST', '邀请已发出。', form)">
        <fieldset :disabled="busy">
          <div class="form-grid">
            <label>成员用户名<input v-model.trim="form.username" maxlength="32" required /></label>
            <label>有效天数<input v-model.number="form.days" type="number" min="1" max="365" step="1" required /></label>
          </div>
          <fieldset>
            <legend>可查看的指标</legend>
            <label v-for="(item, key) in reportMetrics" :key="key" class="checkbox-label">
              <input v-model="form.metrics" type="checkbox" :value="key" /> {{ item[0] }}
            </label>
          </fieldset>
          <fieldset v-if="reports.some(item => !item.sourceDeleted)">
            <legend>可查看的报告</legend>
            <label v-for="item in reports.filter(entry => !entry.sourceDeleted)" :key="item.id" class="checkbox-label">
              <input v-model="form.reportIds" type="checkbox" :value="item.id"
                :disabled="form.reportIds.length >= 50 && !form.reportIds.includes(item.id)" />
              {{ item.title }} · {{ item.startDate }} 至 {{ item.endDate }} · 第 {{ item.reportVersion }} 版
            </label>
          </fieldset>
          <button :disabled="!form.metrics.length && !form.reportIds.length">发出邀请</button>
        </fieldset>
      </form>
    </section>
    <section class="panel">
      <h2>邀请与授权</h2>
      <p v-if="message" class="feedback success" role="status">{{ message }}</p>
      <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
      <p v-if="!grants.length" class="muted">暂无家庭邀请或授权。</p>
      <ul class="selection-list">
        <li v-for="grant in grants" :key="grant.id">
          <strong>{{ grant.incoming ? `来自 ${grant.ownerName}` : `发给 ${grant.memberName}` }}</strong>
          <p class="small muted">{{ grant.expired ? "已过期" : grant.accepted ? "已接受" : "待接受" }}
            · 截至 {{ new Date(`${grant.expiresAt}Z`).toLocaleString("zh-CN") }}</p>
          <p>{{ grant.metrics.map(key => reportMetrics[key][0]).join("、") || "仅报告" }} · {{ grant.reportIds.length }} 份报告</p>
          <div class="actions">
            <button v-if="grant.incoming && !grant.accepted && !grant.expired" :disabled="busy"
              @click="change(`/family/grants/${grant.id}/accept`, 'POST', '已接受邀请。')">接受</button>
            <button v-if="grant.incoming && grant.accepted && !grant.expired" class="secondary" :disabled="busy"
              @click="select(grant)">查看授权内容</button>
            <button class="secondary" :disabled="busy"
              @click="change(`/family/grants/${grant.id}`, 'DELETE', '授权已移除。')">{{ grant.incoming ? "移除" : "撤销" }}</button>
          </div>
        </li>
      </ul>
    </section>
    <section v-if="selected" class="panel family-shared">
      <div class="section-heading">
        <h2>{{ selected.ownerName }} 的共享内容</h2>
        <button class="secondary" :disabled="busy" @click="clearSelection">关闭</button>
      </div>
      <div v-if="selected.metrics.length" class="filter-bar">
        <label>开始日期<input v-model="dates.startDate" type="date" required :max="dates.endDate" :disabled="busy" /></label>
        <label>结束日期<input v-model="dates.endDate" type="date" required :min="dates.startDate"
          :max="dateAfter(dates.startDate, 365)" :disabled="busy" /></label>
        <label>来源全名<input v-model.trim="sourceName" maxlength="100" :disabled="busy" placeholder="全部来源" /></label>
      </div>
      <div class="actions">
        <button v-for="key in selected.metrics" :key="key" class="secondary" :disabled="busy || !dates.startDate || !dates.endDate"
          @click="read(selected, key)">{{ reportMetrics[key][0] }}</button>
        <button v-for="id in selected.reportIds" :key="id" class="secondary" :disabled="busy"
          @click="read(selected, undefined, id)">报告 #{{ id }}</button>
      </div>
      <p v-if="busy" role="status">正在读取…</p>
      <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
      <div v-if="period">
        <h3>{{ reportMetrics[period.metric][0] }}</h3>
        <p>{{ period.data.startDate }} 至 {{ period.data.endDate }} · 覆盖 {{ period.data.validDays }} / {{ period.data.periodDays }} 天</p>
        <p>{{ period.metric === "HEART_RATE" ? "采样均值" : "日均" }} {{ period.data.average === null ? "无有效记录" : `${period.data.average} ${reportMetrics[period.metric][1]}` }}</p>
        <p v-if="period.data.recordCount !== null">采样 {{ period.data.recordCount }} 条</p>
        <p v-if="period.data.missingDates.length" class="small muted">缺失日期 {{ period.data.missingDates.join("、") }}</p>
      </div>
      <article v-if="report">
        <h3>{{ report.title }} · 第 {{ report.reportVersion }} 版</h3>
        <p>{{ report.startDate }} 至 {{ report.endDate }}</p>
        <p v-if="report.content" class="prose">{{ report.content }}</p>
        <SleepSummary v-if="report.reportType === 'SLEEP' && report.statistics" :statistics="report.statistics" />
        <HealthReportSummary v-if="(report.reportType === 'HEALTH_WEEK' || report.reportType === 'HEALTH_MONTH') && report.statistics"
          :snapshot="report.statistics" :start-date="report.startDate" :end-date="report.endDate" :link-records="false" />
        <FocusedReportSummary v-if="(report.reportType === 'HEALTH_FOCUSED' || report.reportType === 'HEALTH_COMPARISON') && report.statistics"
          :snapshot="report.statistics" :link-records="false" />
      </article>
    </section>
  </template>
</template>
