<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ChevronRight } from "@lucide/vue";
import { api, dateAfter, download, errorMessage, recentRange } from "../api";
import StateNotice from "../components/StateNotice.vue";
import TimelineEventDetail from "../components/TimelineEventDetail.vue";
import { recordTime } from "../nutrition";
import { isTimelineType, timelineTypeText } from "../timeline";
import type {
  TimelineEvent,
  TimelineEventDetail as TimelineDetail,
} from "../timeline";
import type { Page } from "../types";

const route = useRoute();
const router = useRouter();
const defaults = recentRange();
const appliedFilters = computed(() => ({
  startDate:
    typeof route.query.startDate === "string"
      ? route.query.startDate
      : defaults.startDate,
  endDate:
    typeof route.query.endDate === "string"
      ? route.query.endDate
      : defaults.endDate,
  type: typeof route.query.type === "string" ? route.query.type : "",
  sourceName:
    typeof route.query.sourceName === "string" ? route.query.sourceName : "",
}));
const filters = reactive({ ...appliedFilters.value });
const filterParams = computed(() => {
  const { type, sourceName, ...dates } = appliedFilters.value;
  return new URLSearchParams({
    ...dates,
    ...(type ? { type } : {}),
    ...(sourceName ? { sourceName } : {}),
  });
});
const listPath = computed(() => {
  const params = new URLSearchParams(filterParams.value);
  params.set("page", typeof route.query.page === "string" ? route.query.page : "1");
  params.set("pageSize", "20");
  return `/timeline/events?${params}`;
});
const data = ref<Page<TimelineEvent> | null>(null);
const listLoading = ref(true);
const listError = ref("");
const exporting = ref(false);
const exportError = ref("");
const listRevision = ref(0);
const groups = computed(() => {
  const byDate = new Map<string, TimelineEvent[]>();
  for (const event of data.value?.items ?? []) {
    const entries = byDate.get(event.eventDate);
    if (entries) entries.push(event);
    else byDate.set(event.eventDate, [event]);
  }
  return byDate;
});

watch(listPath, () => Object.assign(filters, appliedFilters.value));
watch(
  [listPath, listRevision],
  async ([path], _previous, onCleanup) => {
    let active = true;
    onCleanup(() => {
      active = false;
    });
    listLoading.value = true;
    listError.value = "";
    data.value = null;
    try {
      const result = await api<Page<TimelineEvent>>(path);
      if (active) data.value = result;
    } catch (cause) {
      if (active) listError.value = errorMessage(cause);
    } finally {
      if (active) listLoading.value = false;
    }
  },
  { immediate: true },
);

const hasDetailQuery = computed(
  () =>
    route.query.eventType !== undefined || route.query.eventId !== undefined,
);
const selected = computed(() => {
  const { eventType, eventId } = route.query;
  return typeof eventType === "string" &&
    isTimelineType(eventType) &&
    typeof eventId === "string" &&
    /^[1-9]\d*$/.test(eventId)
    ? { type: eventType, id: eventId }
    : null;
});
const detailPath = computed(() =>
  selected.value
    ? `/timeline/events/${selected.value.type}/${selected.value.id}`
    : "",
);
const listQuery = computed(() => ({
  ...route.query,
  eventType: undefined,
  eventId: undefined,
}));
const detail = ref<TimelineDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref("");
const detailRevision = ref(0);
const detailSection = ref<HTMLElement | null>(null);

watch(
  [detailPath, detailRevision],
  async ([path], _previous, onCleanup) => {
    detail.value = null;
    detailError.value = "";
    detailLoading.value = Boolean(path);
    if (!path) return;

    let active = true;
    onCleanup(() => {
      active = false;
    });
    try {
      const result = await api<TimelineDetail>(path);
      if (active) detail.value = result;
    } catch (cause) {
      if (active) detailError.value = errorMessage(cause);
    } finally {
      if (active) detailLoading.value = false;
    }
  },
  { immediate: true },
);
watch(
  [selected, hasDetailQuery],
  async ([, open], [previous]) => {
    await nextTick();
    if (open) {
      detailSection.value?.focus();
    } else if (previous) {
      document
        .getElementById(`timeline-event-${previous.type}-${previous.id}`)
        ?.focus();
    }
  },
  { immediate: true },
);

async function applyFilters() {
  const previousPath = listPath.value;
  const query = {
    startDate: filters.startDate,
    endDate: filters.endDate,
    type: filters.type || undefined,
    sourceName: filters.sourceName || undefined,
  };
  await router.push({ query });
  if (listPath.value === previousPath) listRevision.value++;
}

async function exportRecords() {
  exporting.value = true;
  exportError.value = "";
  const { startDate, endDate } = appliedFilters.value;
  try {
    await download(`/timeline/events/export?${filterParams.value}`, `健康记录_${startDate}_${endDate}.json`);
  } catch (cause) {
    exportError.value = errorMessage(cause);
  } finally {
    exporting.value = false;
  }
}

function changePage(page: number) {
  void router.push({
    query: { ...listQuery.value, page: page === 1 ? undefined : page },
  });
}

function closeDetail() {
  const list = { query: listQuery.value };
  if (router.options.history.state.back === router.resolve(list).fullPath) {
    router.back();
  } else {
    void router.replace(list);
  }
}
</script>

<template>
  <section class="panel" aria-label="时间线筛选">
    <form class="filter-bar timeline-filters" @submit.prevent="applyFilters">
      <label>
        开始日期
        <input
          v-model="filters.startDate"
          type="date"
          required
          :max="filters.endDate"
        />
      </label>
      <label>
        结束日期
        <input
          v-model="filters.endDate"
          type="date"
          required
          :min="filters.startDate"
          :max="dateAfter(filters.startDate, 365)"
        />
      </label>
      <label>
        事件类型
        <select v-model="filters.type">
          <option value="">全部类型</option>
          <option
            v-for="(label, type) in timelineTypeText"
            :key="type"
            :value="type"
          >
            {{ label }}
          </option>
        </select>
      </label>
      <label>
        来源（全名匹配）
        <input
          v-model="filters.sourceName"
          maxlength="100"
          placeholder="全部来源"
        />
      </label>
      <button class="secondary" :disabled="listLoading">筛选事件</button>
      <button type="button" class="secondary" :disabled="listLoading || !!listError || exporting" @click="exportRecords">
        {{ exporting ? "正在导出…" : "导出已筛选记录" }}
      </button>
    </form>
    <p v-if="exportError" class="feedback error" role="alert">{{ exportError }}</p>
  </section>

  <section
    v-if="hasDetailQuery"
    id="timeline-detail"
    ref="detailSection"
    class="panel timeline-detail"
    tabindex="-1"
    aria-labelledby="timeline-detail-title"
  >
    <div class="section-heading">
      <h2 id="timeline-detail-title">
        {{ selected ? `${timelineTypeText[selected.type]}详情` : "事件详情" }}
      </h2>
      <button class="secondary" type="button" @click="closeDetail">
        关闭详情
      </button>
    </div>
    <template v-if="selected">
      <StateNotice
        :loading="detailLoading"
        :error="detailError"
        @retry="detailRevision++"
      />
      <TimelineEventDetail v-if="detail" :event="detail" />
    </template>
    <p v-else class="feedback error" role="alert">
      详情链接缺少有效的事件类型或记录编号。
    </p>
  </section>

  <section class="panel" aria-labelledby="timeline-events-title">
    <div class="section-heading">
      <h2 id="timeline-events-title">事件记录</h2>
    </div>
    <StateNotice
      :loading="listLoading"
      :error="listError"
      :empty="data?.items.length === 0"
      :title="data?.total ? '这一页没有事件' : '此筛选范围内没有事件'"
      :description="
        data?.total
          ? '重新筛选可从第一页查看。'
          : '可以调整日期、类型或来源后再查询。'
      "
      @retry="listRevision++"
    />
    <template v-if="data">
      <div v-for="[date, events] in groups" :key="date" class="timeline-group">
        <h3>
          <time :datetime="date">{{ date }}</time>
        </h3>
        <ul class="timeline-entries">
          <li v-for="event in events" :key="`${event.type}-${event.id}`">
            <RouterLink
              v-slot="{ href, navigate }"
              :to="{
                query: {
                  ...listQuery,
                  eventType: event.type,
                  eventId: event.id,
                },
              }"
              custom
            >
              <a
                :id="`timeline-event-${event.type}-${event.id}`"
                class="timeline-entry"
                :aria-current="
                  selected?.type === event.type &&
                  selected.id === String(event.id)
                    ? 'true'
                    : undefined
                "
                :href="href"
                @click="navigate"
              >
                <div class="timeline-entry-body">
                  <div class="timeline-summary">
                    <span class="tag">{{ timelineTypeText[event.type] }}</span>
                    <strong>{{ event.summary }}</strong>
                  </div>
                  <div class="timeline-meta small muted">
                    <time :datetime="event.occurredAt">{{
                      recordTime(event.occurredAt)
                    }}</time>
                    <span class="timeline-source"
                      >来源：{{ event.sourceName }}</span
                    >
                  </div>
                </div>
                <ChevronRight :size="18" aria-hidden="true" />
              </a>
            </RouterLink>
          </li>
        </ul>
      </div>
      <div v-if="data.total || data.page > 1" class="pagination">
        <span>共 {{ data.total }} 条 · 第 {{ data.page }} 页</span>
        <button
          class="secondary"
          :disabled="data.page <= 1"
          @click="changePage(data.page - 1)"
        >
          上一页
        </button>
        <button
          class="secondary"
          :disabled="data.page * data.pageSize >= data.total"
          @click="changePage(data.page + 1)"
        >
          下一页
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.timeline-filters {
  margin-bottom: 0;
}
.timeline-detail {
  scroll-margin-top: 5rem;
}
.timeline-group + .timeline-group {
  margin-top: 1.5rem;
}
.timeline-group h3 {
  margin: 0 0 0.6rem;
  color: var(--green);
}
.timeline-entries {
  list-style: none;
  margin: 0;
  padding: 0 0 0 0.8rem;
  border-left: 2px solid var(--line);
}
.timeline-entries li + li {
  margin-top: 0.4rem;
}
.timeline-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 0.8rem;
  border: 1px solid var(--line);
  border-radius: 5px;
  text-decoration: none;
}
.timeline-entry:hover,
.timeline-entry[aria-current] {
  background: var(--sage);
}
.timeline-entry svg {
  flex: none;
}
.timeline-entry-body {
  min-width: 0;
  overflow-wrap: anywhere;
}
.timeline-summary,
.timeline-meta {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.25rem 0.75rem;
}
.timeline-meta {
  margin-top: 0.3rem;
}
.timeline-source {
  white-space: pre-wrap;
}
</style>
