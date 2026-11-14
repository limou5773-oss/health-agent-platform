<script setup lang="ts">
import { computed } from "vue";
import type { SleepStatistics } from "../types";
const props = defineProps<{ statistics: SleepStatistics }>();
const maxHours = computed(() =>
  Math.max(
    8,
    Math.ceil(
      Math.max(...props.statistics.daily.map((day) => day.hours ?? 0)) / 2,
    ) * 2,
  ),
);
const column = computed(() => 740 / Math.max(1, props.statistics.daily.length));
const tickStep = computed(() =>
  Math.max(1, Math.ceil(props.statistics.daily.length / 10)),
);
const number = (value: number) =>
  value.toLocaleString("zh-CN", { maximumFractionDigits: 1 });
</script>
<template>
  <section class="summary-block">
    <div class="metrics">
      <div class="metric primary-metric">
        <span>平均睡眠时长</span
        ><strong
          >{{
            statistics.averageHours === null
              ? "—"
              : number(statistics.averageHours)
          }}<small>小时</small></strong
        >
        <p>
          {{
            statistics.averageHours === null
              ? "当前范围没有有效睡眠记录"
              : "按有记录的日期计算"
          }}
        </p>
      </div>
      <div class="metric">
        <span>记录覆盖</span
        ><strong
          >{{ statistics.validDays
          }}<small>/ {{ statistics.periodDays }} 天</small></strong
        >
        <p>覆盖率 {{ number(statistics.coverageRate) }}%</p>
      </div>
      <div class="metric">
        <span>累计睡眠</span
        ><strong>{{ number(statistics.totalHours) }}<small>小时</small></strong>
        <p>共 {{ statistics.recordCount }} 条记录</p>
      </div>
    </div>
    <section v-if="statistics.completion">
      <h3>计划完成情况</h3>
      <p v-if="statistics.completion.scheduled === 0">无任务</p>
      <p v-else>
        已完成 {{ statistics.completion.completed }} / {{ statistics.completion.scheduled }} 项
        （{{ statistics.completionRate }}%）
      </p>
      <p class="small muted">按报告周期内任务的安排日期和生成时的打卡状态统计。</p>
    </section>
    <section class="panel chart-panel">
      <div class="section-heading">
        <div>
          <h2>睡眠时长趋势</h2>
          <p>按醒来日期归属；缺失日期留空，不计为零。</p>
        </div>
        <span class="legend"
          ><i></i>{{ statistics.sourceName || "当前来源" }}</span
        >
      </div>
      <div v-if="!statistics.validDays" class="notice empty">
        <strong>这个时间段还没有睡眠记录</strong>
        <p>录入或导入数据后，趋势将在这里呈现。</p>
      </div>
      <div v-else class="chart-scroll">
        <svg
          class="sleep-chart"
          viewBox="0 0 800 250"
          role="img"
          :aria-label="`${statistics.startDate}至${statistics.endDate}每日睡眠时长图，逐日数据可在下方展开查看`"
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
              x="30"
              :y="209 - tick * 45"
              text-anchor="end"
              class="chart-label"
            >
              {{ number((maxHours * tick) / 4) }}h
            </text>
          </g>
          <g v-for="(day, index) in statistics.daily" :key="day.date">
            <rect
              v-if="day.hours !== null"
              :x="45 + index * column + column * 0.2"
              :y="205 - (day.hours / maxHours) * 180"
              :width="column * 0.6"
              :height="(day.hours / maxHours) * 180"
              rx="3"
              class="chart-bar"
            >
              <title>
                {{ day.date }}：{{ number(day.hours) }} 小时，{{
                  day.recordCount
                }}
                条记录
              </title>
            </rect>
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
      <details v-if="statistics.daily.length" class="data-details">
        <summary>查看逐日数据</summary>
        <p v-if="statistics.scheduleVariation" class="small muted">
          {{ statistics.scheduleVariation.comparedDays }} 对相邻有记录日期，
          入睡时间平均相差 {{ number(statistics.scheduleVariation.averageStartChangeMinutes) }} 分钟，
          醒来时间平均相差 {{ number(statistics.scheduleVariation.averageEndChangeMinutes) }} 分钟。
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>睡眠时长</th>
                <th>最早入睡</th>
                <th>最晚醒来</th>
                <th>记录数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="day in statistics.daily" :key="day.date">
                <td>{{ day.date }}</td>
                <td>
                  {{
                    day.hours === null ? "未记录" : `${number(day.hours)} 小时`
                  }}
                </td>
                <td>{{ day.firstStartTime?.replace("T", " ") ?? "—" }}</td>
                <td>{{ day.lastEndTime?.replace("T", " ") ?? "—" }}</td>
                <td>{{ day.recordCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
      <p class="footnote">
        {{
          statistics.dataCutoff
            ? `数据截至 ${statistics.dataCutoff.replace("T", " ")}`
            : "暂无数据截止时间"
        }}
        · 同一天的重叠时段按区间合并。
      </p>
    </section>
  </section>
</template>
