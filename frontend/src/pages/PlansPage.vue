<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { CalendarCheck, Check, RotateCcw } from "@lucide/vue";
import { api, errorMessage, json, statusText } from "../api";
import type { Plan } from "../types";
import StateNotice from "../components/StateNotice.vue";
import PlanRevisions from "../components/PlanRevisions.vue";
import PlanReview from "../components/PlanReview.vue";
import PlanOutcome from "../components/PlanOutcome.vue";
import PlanCoordination from "../components/PlanCoordination.vue";
import PlanCheckinCalendar from "../components/PlanCheckinCalendar.vue";
import HealthGoals from "../components/HealthGoals.vue";
const route = useRoute();
const selectedDate = ref<string | null>(null);
const reasonItem = ref<number | null>(null);
const reason = ref("");
const reasonError = ref("");
const revisionEditor = ref<InstanceType<typeof PlanRevisions> | null>(null);
const plans = ref<Plan[]>([]),
  plan = ref<Plan | null>(null),
  loading = ref(true),
  error = ref(""),
  detailLoading = ref(false),
  detailError = ref(""),
  actionError = ref(""),
  busy = ref(false);
const selected = computed(() => {
  const id = Number(route.query.id);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
});
const visibleItems = computed(() => (plan.value?.items ?? []).filter(item => !selectedDate.value || item.date === selectedDate.value));
const requestedItem = computed(() => {
  const id = Number(route.query.item);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    plans.value = await api<Plan[]>("/plans");
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function loadDetail(keepVisible = false) {
  if (!keepVisible) plan.value = null;
  detailError.value = "";
  actionError.value = "";
  detailLoading.value = false;
  if (!selected.value) return;
  detailLoading.value = !keepVisible;
  const id = selected.value;
  try {
    const result = await api<Plan>(`/plans/${id}`);
    if (selected.value === id) plan.value = result;
  } catch (cause) {
    if (selected.value === id) detailError.value = errorMessage(cause);
  } finally {
    if (selected.value === id) detailLoading.value = false;
  }
}
async function status(value: Plan["status"]) {
  if (!plan.value) return;
  busy.value = true;
  actionError.value = "";
  try {
    await api(
      `/plans/${plan.value.id}/status`,
      json("PATCH", { status: value }),
    );
    await refreshPlan();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function refreshPlan() {
  await Promise.all([load(), loadDetail()]);
}
function goalLinked(updated: Plan) {
  if (plan.value?.id === updated.id) plan.value.goalId = updated.goalId;
}
function goalDeleted(id: number) {
  if (plan.value?.goalId === id) plan.value.goalId = null;
}
async function checkin(item: NonNullable<Plan["items"]>[number]) {
  const planId = selected.value;
  busy.value = true;
  actionError.value = "";
  try {
    await api(
      `/plan-items/${item.id}/checkins`,
      json(
        item.completed ? "DELETE" : "POST",
        item.completed ? undefined : { date: item.date },
      ),
    );
    if (reasonItem.value === item.id) reasonItem.value = null;
    if (selected.value === planId) await loadDetail(true);
  } catch (cause) {
    if (selected.value === planId) actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function editReason(item: NonNullable<Plan["items"]>[number]) {
  reasonItem.value = item.id;
  reason.value = item.missedReason;
  reasonError.value = "";
}
async function saveReason(itemId: number) {
  const planId = selected.value;
  busy.value = true;
  reasonError.value = "";
  try {
    await api(`/plan-items/${itemId}/missed-reason`, json("PUT", { reason: reason.value }));
    if (selected.value === planId) {
      reasonItem.value = null;
      await loadDetail(true);
    }
  } catch (cause) {
    if (selected.value === planId) reasonError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
watch(selected, () => {
  selectedDate.value = null;
  reasonItem.value = null;
  void loadDetail();
}, { immediate: true });
watch([requestedItem, () => plan.value?.id, loading, detailLoading], async ([itemId]) => {
  const current = plan.value;
  if (itemId === null || loading.value || detailLoading.value || !current || current.id !== selected.value) return;
  const item = current.items?.find(row => row.id === itemId);
  selectedDate.value = item?.date ?? null;
  if (!item) return;
  await nextTick();
  document.getElementById(`plan-task-${item.id}`)?.focus();
}, { flush: "post" });
onMounted(load);
</script>
<template>
  <HealthGoals :plan="plan" @linked="goalLinked" @deleted="goalDeleted" />
  <StateNotice
    :loading="loading"
    :error="error"
    :empty="!plans.length"
    title="你的第一个计划，从一个目标开始"
    description="在健康助手中制定计划，核对草稿并确认后，就能在这里按日行动。"
    @retry="load"
    ><RouterLink class="button" to="/agent">制定计划</RouterLink></StateNotice
  >
  <div
    v-if="!loading && !error && (plans.length || selected)"
    class="workspace-grid"
  >
    <section class="panel">
      <div class="section-heading">
        <h2>计划列表</h2>
        <span class="small muted">{{ plans.length }} 份</span>
      </div>
      <ul class="selection-list">
        <li v-for="entry in plans" :key="entry.id">
          <RouterLink
            :to="{ query: { id: entry.id } }"
            :class="{ selected: selected === entry.id }"
            ><strong>{{ entry.title }}</strong
            ><span class="small muted"
              >{{ entry.startDate }} — {{ entry.endDate }}</span
            ><span class="tag">{{ statusText[entry.status] }}</span></RouterLink
          >
        </li>
      </ul>
    </section>
    <section class="panel">
      <StateNotice
        :loading="detailLoading"
        :error="detailError"
        @retry="loadDetail()"
      /><template v-if="plan && !detailLoading && !detailError"
        ><div class="section-heading">
          <div>
            <span class="eyebrow">我的行动计划</span>
            <h2>{{ plan.title }}</h2>
          </div>
          <span class="tag">{{ statusText[plan.status] }}</span>
        </div>
        <p class="prose muted">{{ plan.reason }}</p>
        <div class="plan-progress">
          <CalendarCheck :size="20" /><span
            >{{ plan.startDate }} 至 {{ plan.endDate }}</span
          >
          <strong v-if="plan.progress">
            {{ plan.progress.dueItems ? `已完成 ${plan.progress.completedItems} / ${plan.progress.dueItems} 项（${plan.progress.completionRate}%）` : "暂无到期任务" }}
          </strong>
        </div>
        <p v-if="plan.progress" class="small muted">
          当前连续 {{ plan.progress.currentStreak }} 天 · 最长连续 {{ plan.progress.longestStreak }} 天
        </p>
        <div class="actions plan-status">
          <button
            v-if="plan.status !== 'ACTIVE'"
            class="secondary"
            :disabled="busy"
            @click="status('ACTIVE')"
          >
            开始 / 恢复计划</button
          ><button
            v-if="plan.status === 'ACTIVE'"
            class="secondary"
            :disabled="busy"
            @click="status('PAUSED')"
          >
            暂停计划</button
          ><button
            v-if="plan.status !== 'ENDED'"
            class="secondary"
            :disabled="busy"
            @click="status('ENDED')"
          >
            结束计划
          </button>
        </div>
        <p v-if="actionError" class="feedback error" role="alert">
          {{ actionError }}
        </p>
        <p v-if="plan.status !== 'ACTIVE'" class="feedback">
          此计划{{ statusText[plan.status] }}；恢复进行后可继续打卡。
        </p>
        <PlanReview :key="plan.id" :plan="plan"
          @propose="(items, date, version) => revisionEditor?.proposeMove(items, date, version)"
          @review-draft="(query, version) => revisionEditor?.proposeReview(query, version)" />
        <PlanOutcome :key="plan.id" :plan="plan" />
        <PlanCoordination :key="`coordination-${plan.id}`" :plan="plan" :plans="plans"
          @propose="(content, note, version, latest) => revisionEditor?.proposeCoordination(content, note, version, latest)" />
        <PlanRevisions ref="revisionEditor" :key="plan.id" :plan="plan" @refresh="refreshPlan" />
        <PlanCheckinCalendar v-if="plan.progress" :key="plan.id" :plan="plan" :today="plan.progress.today"
          :selected="selectedDate" @select="selectedDate = $event" />
        <h3>{{ selectedDate ? `${selectedDate} 的任务` : "每日任务" }}</h3>
        <p v-if="!visibleItems.length" class="muted">没有安排任务。</p>
        <ul class="checkin-list">
          <li
            v-for="item in visibleItems"
            :key="item.id"
            :id="`plan-task-${item.id}`"
            tabindex="-1"
            :class="{ completed: item.completed }"
          >
            <div class="checkin-date">
              <strong>{{ item.date.slice(5) }}</strong
              ><span>{{ item.time.slice(0, 5) }}</span>
            </div>
            <div class="checkin-body">
              <h3>{{ item.title }}</h3>
              <p v-if="item.description" class="prose">
                <strong>执行说明</strong><br />{{ item.description }}
              </p>
              <p v-if="item.reminderTime" class="small muted">提醒时间 {{ item.reminderTime.slice(0, 5) }}</p>
              <p v-if="item.completed && item.checkedAt" class="small muted">
                打卡时间 {{ item.checkedAt.replace("T", " ") }}
              </p>
              <p v-if="!item.completed && item.missedReason" class="small muted">未完成原因 {{ item.missedReason }}</p>
              <template v-if="!item.completed && plan.progress && item.date < plan.progress.today">
                <form v-if="reasonItem === item.id" @submit.prevent="saveReason(item.id)">
                  <label>未完成原因
                    <input v-model="reason" :disabled="busy" maxlength="500" placeholder="留空可清除原因" />
                  </label>
                  <p v-if="reasonError" class="feedback error" role="alert">{{ reasonError }}</p>
                  <div class="actions">
                    <button class="secondary" :disabled="busy">保存原因</button>
                    <button type="button" class="text-button" :disabled="busy" @click="reasonItem = null">取消</button>
                  </div>
                </form>
                <button v-else type="button" class="text-button" :disabled="busy" @click="editReason(item)">
                  {{ item.missedReason ? "修改未完成原因" : "填写未完成原因" }}
                </button>
              </template>
            </div>
            <button
              :class="item.completed ? 'secondary' : ''"
              :disabled="busy || (!item.completed && (plan.status !== 'ACTIVE' || item.date > (plan.progress?.today ?? item.date)))"
              @click="checkin(item)"
            >
              <RotateCcw v-if="item.completed" :size="15" /><Check
                v-else
                :size="15"
              />{{ item.completed ? "撤销打卡" : item.date > (plan.progress?.today ?? item.date) ? "未到打卡日期"
                : item.date < (plan.progress?.today ?? item.date) ? "补打卡" : "完成打卡" }}
            </button>
          </li>
        </ul></template
      >
      <div v-else-if="!selected" class="workspace-empty">
        <CalendarCheck :size="28" />
        <h2>一天一个小行动</h2>
        <p>选择计划，查看每天的行动与打卡记录。</p>
      </div>
    </section>
  </div>
</template>
