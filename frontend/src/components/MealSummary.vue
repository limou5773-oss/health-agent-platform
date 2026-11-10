<script setup lang="ts">
import type { MealStatistics } from "../types";
import { calorieText, nutritionNumber } from "../nutrition";
import StateNotice from "./StateNotice.vue";
defineProps<{ statistics: MealStatistics }>();
</script>
<template>
  <div class="metrics nutrition-metrics">
    <div class="metric primary-metric">
      <span>已知部分热量</span
      ><strong
        >{{
          statistics.knownCaloriesKcal === null
            ? "—"
            : nutritionNumber(statistics.knownCaloriesKcal)
        }}<small>kcal</small></strong
      >
      <p>
        {{ statistics.knownCaloriesCount }} 条已知 /
        {{ statistics.recordCount }} 条记录
      </p>
    </div>
    <div class="metric">
      <span>手动录入</span
      ><strong
        >{{
          statistics.manualCaloriesKcal === null
            ? "—"
            : nutritionNumber(statistics.manualCaloriesKcal)
        }}<small>kcal</small></strong
      >
      <p>{{ statistics.manualCaloriesCount }} 条；只采用明确填写值</p>
    </div>
    <div class="metric">
      <span>依据估算</span
      ><strong
        >{{
          statistics.estimatedCaloriesKcal === null
            ? "—"
            : nutritionNumber(statistics.estimatedCaloriesKcal)
        }}<small>kcal（估算）</small></strong
      >
      <p>{{ statistics.estimatedCaloriesCount }} 条；按所填营养依据换算</p>
    </div>
  </div>
  <p class="feedback" role="status">
    热量未知
    {{
      statistics.unknownCaloriesCount
    }}
    条。以上仅为已知部分，不能代表完整全天摄入；“—”表示无该类热量记录，明确录入的
    0 kcal 会显示为 0。
  </p>
  <p class="small muted">
    数据覆盖 {{ statistics.validDays }} / {{ statistics.periodDays }} 天 ·
    覆盖率 {{ nutritionNumber(statistics.coverageRate) }}% · 饮食记录
    {{ statistics.recordCount }} 条
  </p>
  <StateNotice
    :empty="statistics.validDays === 0"
    title="这段时间没有饮食记录"
    description="未记录日期保留缺失；热量未知的食物不会被补成零。"
  />
  <details class="data-details" open>
    <summary>逐日已知部分热量与记录数量</summary>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>日期</th>
            <th>饮食记录</th>
            <th>手动热量 / 条数</th>
            <th>估算热量 / 条数</th>
            <th>已知部分热量 / 条数</th>
            <th>热量未知条数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="day in statistics.daily" :key="day.date">
            <td>{{ day.date }}</td>
            <td>
              {{ day.recordCount === 0 ? "未记录" : `${day.recordCount} 条` }}
            </td>
            <td>
              {{ calorieText(day.manualCaloriesKcal) }} /
              {{ day.manualCaloriesCount }} 条
            </td>
            <td>
              {{ calorieText(day.estimatedCaloriesKcal) }} /
              {{ day.estimatedCaloriesCount }} 条<span
                v-if="day.estimatedCaloriesKcal !== null"
                class="small"
                >（估算）</span
              >
            </td>
            <td>
              {{ calorieText(day.knownCaloriesKcal) }} /
              {{ day.knownCaloriesCount }} 条
            </td>
            <td>{{ day.unknownCaloriesCount }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </details>
</template>
<style scoped>
.nutrition-metrics .metric strong {
  font-size: 1.9rem;
  overflow-wrap: anywhere;
}
.nutrition-metrics .metric small {
  display: block;
  margin-left: 0;
}
@media (max-width: 700px) {
  .nutrition-metrics {
    grid-template-columns: 1fr;
  }
  .nutrition-metrics .metric {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
  .nutrition-metrics .metric:last-child {
    border-bottom: 0;
  }
}
</style>
