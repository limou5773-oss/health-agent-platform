<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ApiError, api, dateAfter, errorMessage, recentRange } from "../api";
import type { WeightStatistics } from "../types";
import { signedWeight, weightNumber, weightText, weightTime } from "../weight";
import StateNotice from "./StateNotice.vue";
import WeightTrend from "./WeightTrend.vue";

const props = defineProps<{ sources: string[]; refreshKey: number }>();
defineEmits<{ detail: [id: number] }>();
const filters = reactive({ ...recentRange(), sourceName: "" });
const statistics = ref<WeightStatistics | null>(null);
const loading = ref(false),
  error = ref(""),
  selectionRequired = ref(false);
let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  error.value = "";
  selectionRequired.value = false;
  statistics.value = null;
  try {
    const result = await api<WeightStatistics>(
      `/weight/statistics?${new URLSearchParams({
        startDate: filters.startDate,
        endDate: filters.endDate,
        ...(filters.sourceName ? { sourceName: filters.sourceName } : {}),
      })}`,
    );
    if (currentRequest === requestId) statistics.value = result;
  } catch (cause) {
    if (currentRequest === requestId) {
      error.value = errorMessage(cause);
      selectionRequired.value =
        cause instanceof ApiError && cause.code === "SOURCE_SELECTION_REQUIRED";
    }
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}
watch(() => props.refreshKey, load);
onMounted(load);
</script>
<template>
  <section
    id="weight-statistics"
    class="panel weight-statistics"
    aria-labelledby="weight-statistics-title"
  >
    <div class="section-heading">
      <div>
        <h2 id="weight-statistics-title">趋势与阶段对比</h2>
        <p>每日固定取最后一次测量；同日其余记录仍保留在测量列表。</p>
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
          :max="dateAfter(filters.startDate, 365)"
          :disabled="loading"
      /></label>
      <label
        >统计来源<select v-model="filters.sourceName" :disabled="loading">
          <option value="">全部来源</option>
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
      <button :disabled="loading">
        {{ loading ? "统计中…" : "查看统计" }}
      </button>
    </form>
    <p v-if="selectionRequired" class="feedback" role="alert">
      同秒测量存在不同来源，请选择统计来源后重新查询。更换日期也会按当前范围重新判断。
    </p>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <template v-if="!loading && !error && statistics">
      <p class="small muted">
        {{ statistics.startDate }} 至 {{ statistics.endDate }} ·
        {{ statistics.sourceName || "全部来源" }}
      </p>
      <div class="metrics">
        <div class="metric primary-metric">
          <span>有效日代表值均重</span
          ><strong
            >{{
              statistics.averageKg === null
                ? "—"
                : weightNumber(statistics.averageKg)
            }}<small>kg</small></strong
          >
          <p>每个有记录日期只计一个代表值</p>
        </div>
        <div class="metric">
          <span>阶段体重变化</span
          ><strong>{{ signedWeight(statistics.changeKg) }}</strong>
          <p>最后有效日 − 首个有效日</p>
        </div>
        <div class="metric">
          <span>数据覆盖</span
          ><strong
            >{{ statistics.validDays
            }}<small>/ {{ statistics.periodDays }} 天</small></strong
          >
          <p>
            {{ weightNumber(statistics.coverageRate, 2) }}% ·
            {{ statistics.recordCount }} 条原始测量
          </p>
        </div>
      </div>
      <StateNotice
        :empty="statistics.validDays === 0"
        title="这段时间没有体重测量"
        description="可调整日期或来源。缺失日期保留为空，不从档案体重补入测量。"
      />
      <WeightTrend :daily="statistics.daily" />
      <div v-if="statistics.first && statistics.last" class="weight-comparison">
        <div>
          <h3>阶段起点 · 首个有效日</h3>
          <strong>{{ weightText(statistics.first.weightKg) }}</strong>
          <p class="small">
            {{ weightTime(statistics.first.measuredAt) }} ·
            {{ statistics.first.sourceName }}
          </p>
          <button
            class="text-button"
            @click="$emit('detail', statistics.first.id)"
          >
            查看原始测量 #{{ statistics.first.id }}
          </button>
        </div>
        <div>
          <h3>阶段终点 · 最后有效日</h3>
          <strong>{{ weightText(statistics.last.weightKg) }}</strong>
          <p class="small">
            {{ weightTime(statistics.last.measuredAt) }} ·
            {{ statistics.last.sourceName }}
          </p>
          <button
            class="text-button"
            @click="$emit('detail', statistics.last.id)"
          >
            查看原始测量 #{{ statistics.last.id }}
          </button>
        </div>
      </div>
      <details class="data-details" open>
        <summary>逐日代表值与原始测量</summary>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>原始条数</th>
                <th>代表体重</th>
                <th>实际测量时间 / 来源</th>
                <th>原始记录</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="day in statistics.daily" :key="day.date">
                <td>{{ day.date }}</td>
                <td>{{ day.recordCount }}</td>
                <template v-if="day.representative">
                  <td>{{ weightText(day.representative.weightKg) }}</td>
                  <td>
                    {{ weightTime(day.representative.measuredAt) }}
                    <p class="small">{{ day.representative.sourceName }}</p>
                  </td>
                  <td>
                    <button
                      class="text-button"
                      @click="$emit('detail', day.representative.id)"
                    >
                      查看 #{{ day.representative.id }}
                    </button>
                  </td>
                </template>
                <template v-else
                  ><td>未记录</td>
                  <td>—</td>
                  <td>—</td></template
                >
              </tr>
            </tbody>
          </table>
        </div>
      </details>
      <p class="footnote">
        按测量时间、记录编号降序选择每日代表值。阶段对比使用实际有效日，不补造周期边界测量。数据截止：{{
          statistics.dataCutoff === null
            ? "无记录"
            : weightTime(statistics.dataCutoff)
        }}。
      </p>
    </template>
  </section>
</template>
<style scoped>
.weight-statistics {
  scroll-margin-top: 5rem;
}
.weight-comparison {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin: 1.5rem 0;
}
.weight-comparison > div {
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.weight-comparison strong {
  display: block;
  margin-top: 0.6rem;
  font-size: 1.5rem;
}
@media (max-width: 650px) {
  .weight-comparison {
    grid-template-columns: 1fr;
  }
}
</style>
