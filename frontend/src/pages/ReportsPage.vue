<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { FileText } from "@lucide/vue";
import { api, errorMessage, safeLink } from "../api";
import type { Report } from "../types";
import StateNotice from "../components/StateNotice.vue";
import SleepSummary from "../components/SleepSummary.vue";
const route = useRoute();
const reports = ref<Report[]>([]),
  report = ref<Report | null>(null),
  loading = ref(true),
  error = ref(""),
  detailLoading = ref(false),
  detailError = ref("");
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
watch(selected, loadDetail, { immediate: true });
onMounted(load);
</script>
<template>
  <StateNotice
    :loading="loading"
    :error="error"
    :empty="!reports.length"
    title="还没有睡眠报告"
    description="在健康助手中选择“生成睡眠周报”，指定日期与来源后创建。"
    @retry="load"
    ><RouterLink class="button" to="/agent"
      >生成睡眠周报</RouterLink
    ></StateNotice
  >
  <div
    v-if="!loading && !error && (reports.length || selected)"
    class="workspace-grid"
  >
    <section class="panel">
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
            ><span class="tag" :class="{ warm: !entry.includeDiary }">{{
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
    <article class="panel report-detail">
      <StateNotice
        :loading="detailLoading"
        :error="detailError"
        @retry="loadDetail"
      /><template v-if="report && !detailLoading && !detailError"
        ><p class="eyebrow">睡眠回顾</p>
        <h2>{{ report.title }}</h2>
        <p class="small muted">
          {{ report.startDate }} 至 {{ report.endDate }} ·
          {{ report.createdAt.replace("T", " ") }}
        </p>
        <p class="small">
          <span class="tag" :class="{ warm: !report.includeDiary }">{{
            report.includeDiary
              ? "来源设置：睡眠与授权日记"
              : "来源设置：睡眠数据"
          }}</span>
        </p>
        <p v-if="report.includeDiary" class="footnote">
          生成时启用了授权日记背景，实际读取范围以正文说明为准。日记是用户自述，不作诊断；日记内容或授权变化会使相关报告失效。
        </p>
        <div v-if="report.sourceDeleted" class="notice error" role="status">
          <strong>这份报告的数据已失效</strong>
          <p>
            关联健康记录或日记授权已发生变化，原有健康明细不再展示。请使用当前有效数据重新生成报告。
          </p>
          <RouterLink class="button secondary" to="/agent"
            >重新生成报告</RouterLink
          >
        </div>
        <template v-else
          ><div class="prose report-content">{{ report.content }}</div>
          <SleepSummary
            v-if="report.statistics"
            :statistics="report.statistics"
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
              </li>
            </ul>
          </section>
          <p class="footnote">
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
