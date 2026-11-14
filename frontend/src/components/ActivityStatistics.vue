<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ApiError, api, errorMessage, recentRange } from "../api";
import type { StepStatistics, WorkoutStatistics } from "../types";
import StateNotice from "./StateNotice.vue";
const props = defineProps<{
  kind: "steps" | "workouts";
  sources: string[];
  refreshKey: number;
}>();
const filters = reactive({ ...recentRange(), sourceName: "" });
const loading = ref(false),
  error = ref(""),
  selectionRequired = ref(false);
const steps = ref<StepStatistics | null>(null);
const workouts = ref<WorkoutStatistics | null>(null);
const summary = computed(() => steps.value || workouts.value);
const daily = computed(() =>
  steps.value
    ? steps.value.daily.map((day) => ({ date: day.date, value: day.steps }))
    : workouts.value?.daily.map((day) => ({
        date: day.date,
        value: day.minutes,
      })) || [],
);
const maximum = computed(() =>
  Math.max(1, ...daily.value.map((day) => day.value ?? 0)),
);
const column = computed(() => 740 / Math.max(1, daily.value.length));
const tickStep = computed(() =>
  Math.max(1, Math.ceil(daily.value.length / 10)),
);
const number = (value: number) =>
  value.toLocaleString("zh-CN", { maximumFractionDigits: 2 });
async function load() {
  loading.value = true;
  error.value = "";
  selectionRequired.value = false;
  steps.value = null;
  workouts.value = null;
  const { sourceName, ...dates } = filters;
  try {
    const path = `/activity/${props.kind}/statistics?${new URLSearchParams({ ...dates, ...(sourceName ? { sourceName } : {}) })}`;
    if (props.kind === "steps") steps.value = await api<StepStatistics>(path);
    else workouts.value = await api<WorkoutStatistics>(path);
  } catch (cause) {
    error.value = errorMessage(cause);
    selectionRequired.value =
      cause instanceof ApiError && cause.code === "SOURCE_SELECTION_REQUIRED";
  } finally {
    loading.value = false;
  }
}
watch(() => props.refreshKey, load);
onMounted(load);
</script>
<template>
  <section
    class="panel chart-panel"
    :aria-label="kind === 'steps' ? '步数统计' : '运动统计'"
  >
    <div class="section-heading">
      <div>
        <h2>{{ kind === "steps" ? "步数趋势" : "运动时长趋势" }}</h2>
        <p>统计范围独立于上方记录列表，最多选择 366 天。</p>
      </div>
    </div>
    <form class="filter-bar" @submit.prevent="load">
      <label
        >统计开始日期<input
          v-model="filters.startDate"
          type="date"
          required
          :max="filters.endDate"
          :disabled="loading"
      /></label>
      <label
        >统计结束日期<input
          v-model="filters.endDate"
          type="date"
          required
          :min="filters.startDate"
          :disabled="loading"
      /></label>
      <label
        >统计来源<select
          v-model="filters.sourceName"
          :disabled="loading"
          :required="selectionRequired"
        >
          <option value="">所有来源（冲突时需选择）</option>
          <option v-for="source in sources" :key="source" :value="source">
            {{ source }}
          </option>
        </select></label
      >
      <button :disabled="loading">
        {{ loading ? "统计中…" : "查看统计" }}
      </button>
    </form>
    <p v-if="selectionRequired" class="feedback" role="alert">
      所选范围存在来源冲突。请在“统计来源”中明确选择一个真实来源，再查看统计。
    </p>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <template v-if="!loading && !error && summary">
      <p class="small muted">
        {{ summary.startDate }} 至 {{ summary.endDate }} ·
        {{
          summary.sourceName ||
          (steps ? "按每日实际来源统计" : "全部来源（按时段合并）")
        }}
      </p>
      <div class="metrics">
        <div class="metric primary-metric">
          <span>{{ steps ? "累计步数" : "累计运动" }}</span>
          <strong
            >{{ number(steps ? steps.totalSteps : workouts!.totalMinutes)
            }}<small>{{ steps ? "步" : "分钟" }}</small></strong
          >
          <p>
            {{
              steps
                ? "累计快照取最新值；增量区间相加"
                : "每日相交时段合并，避免重复计时"
            }}
          </p>
        </div>
        <div class="metric">
          <span>数据覆盖</span
          ><strong
            >{{ summary.validDays
            }}<small>/ {{ summary.periodDays }} 天</small></strong
          >
          <p>覆盖率 {{ number(summary.coverageRate) }}%</p>
        </div>
        <div v-if="steps" class="metric">
          <span>日均步数</span
          ><strong
            >{{ steps.averageSteps === null ? "—" : number(steps.averageSteps)
            }}<small>步</small></strong
          >
          <p>仅按有记录日期计算；无记录时未计算</p>
        </div>
        <div v-else-if="workouts" class="metric">
          <span>运动记录</span
          ><strong>{{ workouts.recordCount }}<small>条</small></strong>
          <p>周期内相交的不同记录数</p>
        </div>
      </div>
      <p v-if="steps" class="small muted">
        {{
          steps.dailyGoal === null
            ? "尚未设置每日步数目标；达标天数及完成比例未计算。"
            : `每日目标 ${number(steps.dailyGoal)} 步 · 达标 ${steps.goalDays} 天（按当前目标及有记录日期计算）`
        }}
      </p>
      <p v-if="steps?.dataCutoff" class="small muted">
        数据截至 {{ steps.dataCutoff.replace("T", " ") }}（档案时区）
      </p>
      <StateNotice
        :empty="summary.validDays === 0"
        title="这段时间没有记录"
        description="缺失日期保留为空；真实零值在逐日数据中显示为 0。"
      />
      <div v-if="summary.validDays" class="chart-scroll">
        <svg
          class="sleep-chart"
          viewBox="0 0 800 250"
          role="img"
          :aria-label="`${summary.startDate}至${summary.endDate}${kind === 'steps' ? '步数' : '运动分钟'}趋势；逐日数值见下表`"
        >
          <g v-for="tick in [0, 1, 2, 3, 4]" :key="tick">
            <line
              x1="45"
              x2="790"
              :y1="205 - tick * 45"
              :y2="205 - tick * 45"
              class="chart-grid"
            />
            <text
              x="38"
              :y="209 - tick * 45"
              text-anchor="end"
              class="chart-label"
            >
              {{ number((maximum * tick) / 4) }}
            </text>
          </g>
          <g v-for="(day, index) in daily" :key="day.date">
            <rect
              v-if="day.value !== null && day.value > 0"
              :x="45 + index * column + column * 0.2"
              :y="205 - (day.value / maximum) * 180"
              :width="column * 0.6"
              :height="(day.value / maximum) * 180"
              rx="2"
              class="chart-bar"
            >
              <title>
                {{ day.date }}：{{ number(day.value)
                }}{{ kind === "steps" ? "步" : "分钟" }}
              </title>
            </rect>
            <circle
              v-else-if="day.value === 0"
              :cx="45 + index * column + column / 2"
              cy="205"
              r="2.5"
              fill="var(--green)"
            >
              <title>{{ day.date }}：已记录，真实零值</title>
            </circle>
            <text
              v-if="index % tickStep === 0"
              :x="45 + index * column + column / 2"
              y="232"
              text-anchor="middle"
              class="chart-label"
            >
              {{ day.date.slice(5) }}
            </text>
          </g>
        </svg>
      </div>
      <details class="data-details">
        <summary>查看逐日数据（空白表示未记录，圆点表示真实零值）</summary>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>{{ steps ? "步数" : "运动时长" }}</th>
                <th>{{ steps ? "实际来源" : "相交记录数" }}</th>
                <th v-if="steps">目标完成比例</th>
              </tr>
            </thead>
            <tbody v-if="steps">
              <tr v-for="day in steps.daily" :key="day.date">
                <td>{{ day.date }}</td>
                <td>
                  {{
                    day.steps === null ? "未记录" : `${number(day.steps)} 步`
                  }}
                </td>
                <td>{{ day.sourceName ?? "—" }}</td>
                <td>
                  {{
                    steps.dailyGoal === null
                      ? "未设置目标"
                      : day.goalRate === null
                        ? "未记录"
                        : `${number(day.goalRate)}%`
                  }}
                </td>
              </tr>
            </tbody>
            <tbody v-else-if="workouts">
              <tr v-for="day in workouts.daily" :key="day.date">
                <td>{{ day.date }}</td>
                <td>
                  {{
                    day.minutes === null
                      ? "未记录"
                      : `${number(day.minutes)} 分钟`
                  }}
                </td>
                <td>{{ day.recordCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
      <p class="footnote">
        {{
          steps
            ? "同日多来源冲突时须由你选择来源。缺失日期不作为零步，目标完成比例允许超过 100%。"
            : "记录按开始日期归档；统计按每日实际覆盖时段计算。跨日记录会出现在多个日期，因此每日记录数相加可能大于周期记录数。"
        }}
      </p>
    </template>
  </section>
</template>
