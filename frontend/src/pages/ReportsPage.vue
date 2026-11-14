<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { FileText } from "@lucide/vue";
import { api, errorMessage, safeLink } from "../api";
import type { Report } from "../types";
import StateNotice from "../components/StateNotice.vue";
import SleepSummary from "../components/SleepSummary.vue";
import HealthReportForm from "../components/HealthReportForm.vue";
import HealthReportSummary from "../components/HealthReportSummary.vue";
import FocusedReportSummary from "../components/FocusedReportSummary.vue";
const route = useRoute();
const reports = ref<Report[]>([]),
  report = ref<Report | null>(null),
  loading = ref(true),
  error = ref(""),
  detailLoading = ref(false),
  detailError = ref("");
const detail = ref<HTMLElement | null>(null);
const exporting = ref(false);
const exportError = ref("");
let expandedForPrint: HTMLDetailsElement[] = [];
const selected = computed(() => {
  const id = Number(route.query.id);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    reports.value = await api<Report[]>("/reports");
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function loadDetail() {
  report.value = null;
  exportError.value = "";
  detailError.value = "";
  detailLoading.value = false;
  if (!selected.value) return;
  detailLoading.value = true;
  const id = selected.value;
  try {
    const result = await api<Report>(`/reports/${id}`);
    if (selected.value === id) report.value = result;
  } catch (cause) {
    if (selected.value === id) detailError.value = errorMessage(cause);
  } finally {
    if (selected.value === id) detailLoading.value = false;
  }
}
async function exportPdf(id: number) {
  exporting.value = true;
  exportError.value = "";
  try {
    const latest = await api<Report>(`/reports/${id}`);
    if (selected.value !== id) return;
    report.value = latest;
    if (latest.sourceDeleted) {
      await load();
      return;
    }
    await nextTick();
    if (selected.value === id) window.print();
  } catch (cause) {
    if (selected.value === id) exportError.value = errorMessage(cause);
  } finally {
    exporting.value = false;
  }
}

function preparePrint() {
  expandedForPrint = Array.from(detail.value?.querySelectorAll<HTMLDetailsElement>("details:not([open])") ?? []);
  for (const disclosure of expandedForPrint) disclosure.open = true;
}

function restorePrint() {
  for (const disclosure of expandedForPrint) disclosure.open = false;
  expandedForPrint = [];
}

watch(selected, loadDetail, { immediate: true });
onMounted(() => {
  void load();
  window.addEventListener("beforeprint", preparePrint);
  window.addEventListener("afterprint", restorePrint);
});
onUnmounted(() => {
  window.removeEventListener("beforeprint", preparePrint);
  window.removeEventListener("afterprint", restorePrint);
});
</script>
<template>
  <div class="report-screen-only"><HealthReportForm @generated="load" /></div>
  <div class="report-screen-only"><StateNotice
    :loading="loading"
    :error="error"
    :empty="!reports.length"
    title="还没有健康报告"
    description="可以在上方生成报告，或在健康助手中生成睡眠周报。"
    @retry="load"
    ><RouterLink class="button" to="/agent"
      >生成睡眠周报</RouterLink
    ></StateNotice
  ></div>
  <div
    v-if="!loading && !error && (reports.length || selected)"
    class="workspace-grid report-workspace"
  >
    <section class="panel report-screen-only">
      <div class="section-heading">
        <h2>报告列表</h2>
        <span class="small muted">{{ reports.length }} 份</span>
      </div>
      <ul class="selection-list">
        <li v-for="entry in reports" :key="entry.id">
          <RouterLink
            :to="{ query: { id: entry.id } }"
            :class="{ selected: selected === entry.id }"
            ><strong>{{ entry.title }}</strong
            ><span class="small muted"
              >{{ entry.startDate }} — {{ entry.endDate }}</span
            ><span v-if="entry.comparisonStartDate" class="small muted">
              对照 {{ entry.comparisonStartDate }} 至 {{ entry.comparisonEndDate }}
            </span><span v-if="entry.reportType !== 'SLEEP'" class="tag">第 {{ entry.reportVersion }} 版</span>
            <span v-else class="tag" :class="{ warm: !entry.includeDiary }">{{
              entry.includeDiary ? "已启用授权日记" : "仅睡眠数据"
            }}</span
            ><span v-if="entry.sourceDeleted" class="tag warm">数据已失效</span
            ><span v-else class="small muted"
              >生成于 {{ entry.createdAt.slice(0, 10) }}</span
            ></RouterLink
          >
        </li>
      </ul>
    </section>
    <article ref="detail" class="panel report-detail"
      :class="{ 'report-print-ready': report && !report.sourceDeleted && !detailLoading && !detailError }">
      <StateNotice
        :loading="detailLoading"
        :error="detailError"
        @retry="loadDetail"
      /><template v-if="report && !detailLoading && !detailError"
        ><p class="eyebrow">{{ report.reportType === "SLEEP" ? "睡眠回顾" : `第 ${report.reportVersion} 版` }}</p>
        <h2>{{ report.title }}</h2>
        <div v-if="!report.sourceDeleted" class="actions report-screen-only">
          <button type="button" class="secondary" :disabled="exporting" @click="exportPdf(report.id)">
            {{ exporting ? "正在准备…" : "导出 PDF" }}
          </button>
          <span v-if="exportError" class="feedback error" role="alert">{{ exportError }}</span>
        </div>
        <p class="small muted">
          {{ report.startDate }} 至 {{ report.endDate }} ·
          {{ report.createdAt.replace("T", " ") }}
        </p>
        <p class="report-print-only">报告 #{{ report.id }} · 第 {{ report.reportVersion }} 版</p>
        <p v-if="report.reportType === 'SLEEP'" class="small">
          <span class="tag" :class="{ warm: !report.includeDiary }">{{
            report.includeDiary
              ? "来源设置：睡眠与授权日记"
              : "来源设置：睡眠数据"
          }}</span>
        </p>
        <RouterLink v-if="report.taskId" class="report-screen-only" :to="{ path: '/agent', query: { task: report.taskId } }">
          查看生成任务
        </RouterLink>
        <p v-if="report.includeDiary" class="footnote">
          生成时启用了授权日记背景，实际读取范围以正文说明为准。日记是用户自述，不作诊断；日记内容或授权变化会使相关报告失效。
        </p>
        <div v-if="report.sourceDeleted" class="notice error" role="status">
          <strong>这份报告的数据已失效</strong>
          <p>
            关联健康记录或日记授权已发生变化，原有健康明细不再展示。请使用当前有效数据重新生成报告。
          </p>
          <RouterLink v-if="report.reportType === 'SLEEP'" class="button secondary" to="/agent"
            >重新生成报告</RouterLink
          >
          <a v-else class="button secondary" href="#generate-health-report">重新生成报告</a>
        </div>
        <template v-else
          ><div v-if="report.content" class="prose report-content">{{ report.content }}</div>
          <SleepSummary
            v-if="report.reportType === 'SLEEP' && report.statistics"
            :statistics="report.statistics"
          />
          <HealthReportSummary
            v-if="(report.reportType === 'HEALTH_WEEK' || report.reportType === 'HEALTH_MONTH') && report.statistics"
            :snapshot="report.statistics"
            :start-date="report.startDate" :end-date="report.endDate"
          />
          <FocusedReportSummary
            v-if="(report.reportType === 'HEALTH_FOCUSED' || report.reportType === 'HEALTH_COMPARISON') && report.statistics"
            :snapshot="report.statistics"
          />
          <section v-if="report.references?.length" class="references">
            <h3>参考资料</h3>
            <ul>
              <li v-for="reference in report.references" :key="reference.id">
                <a
                  v-if="safeLink(reference.sourceUrl)"
                  :href="safeLink(reference.sourceUrl)"
                  target="_blank"
                  rel="noopener noreferrer"
                  >{{ reference.title }}</a
                ><strong v-else>{{ reference.title }}</strong
                ><span class="muted small"> · {{ reference.sourceName }}</span>
                <RouterLink class="small report-screen-only" :to="{ path: '/knowledge', query: { id: reference.id } }">查看库内资料</RouterLink>
              </li>
            </ul>
          </section>
          <p v-if="report.reportType === 'SLEEP'" class="footnote">
            此报告由 AI 辅助生成，仅用于日常健康管理，不替代专业诊断。
          </p></template
        ></template
      >
      <div v-else-if="!selected" class="workspace-empty">
        <FileText :size="28" />
        <h2>回顾，也是一种进步</h2>
        <p>选择一份报告，查看对应日期的分析与资料来源。</p>
      </div>
    </article>
  </div>
</template>

<style>
.report-print-only {
  display: none;
}

@media print {
  @page {
    size: A4;
    margin: 15mm;
  }

  body:has(.report-print-ready) {
    background: white;
    color: #1a261e;
  }

  .app-shell:has(.report-print-ready),
  .report-workspace:has(.report-print-ready),
  .main-content:has(.report-print-ready),
  .report-print-ready {
    display: block;
    min-height: 0;
    max-width: none;
    width: auto;
    margin: 0;
    padding: 0;
    border: 0;
    border-radius: 0;
    box-shadow: none;
    background: white;
  }

  .app-shell:has(.report-print-ready) > :not(.main-content),
  .main-content:has(.report-print-ready) > .page-header,
  .main-content:has(.report-print-ready) .report-screen-only {
    display: none !important;
  }

  .report-print-ready {
    font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", sans-serif;
    font-size: 10pt;
    overflow-wrap: anywhere;
  }

  .report-print-ready .report-print-only {
    display: block;
  }

  .report-print-ready h2,
  .report-print-ready h3,
  .report-print-ready summary {
    break-after: avoid;
  }

  .report-print-ready .metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    break-inside: avoid;
  }

  .report-print-ready .chart-scroll,
  .report-print-ready .table-wrap {
    overflow: visible;
    max-height: none;
  }

  .report-print-ready .sleep-chart {
    min-width: 0;
    width: 100%;
    break-inside: avoid;
  }

  .report-print-ready thead {
    display: table-header-group;
  }

  .report-print-ready tr,
  .report-print-ready .health-report-metrics > li {
    break-inside: avoid;
  }
}
</style>
