<script setup lang="ts">
import type { WaterStatistics } from "../types";
import { nutritionNumber } from "../nutrition";
import StateNotice from "./StateNotice.vue";
defineProps<{ statistics: WaterStatistics }>();
</script>
<template>
  <div class="metrics nutrition-metrics">
    <div class="metric primary-metric">
      <span>已记录饮水合计</span
      ><strong
        >{{ nutritionNumber(statistics.totalMl) }}<small>毫升</small></strong
      >
      <p>{{ statistics.recordCount }} 条实际记录；无记录时合计为 0</p>
    </div>
    <div class="metric">
      <span>有记录日均水量</span
      ><strong
        >{{
          statistics.averageMl === null
            ? "—"
            : nutritionNumber(statistics.averageMl)
        }}<small>毫升 / 天</small></strong
      >
      <p>仅以有记录日期为分母；无记录时未计算</p>
    </div>
    <div class="metric">
      <span>数据覆盖</span
      ><strong
        >{{ statistics.validDays
        }}<small>/ {{ statistics.periodDays }} 天</small></strong
      >
      <p>覆盖率 {{ nutritionNumber(statistics.coverageRate) }}%</p>
    </div>
  </div>
  <StateNotice
    :empty="statistics.validDays === 0"
    title="这段时间没有饮水记录"
    description="合计 0 仅表示没有已保存水量；日均与逐日缺失保留为空，不代表真实零饮水。"
  />
  <details class="data-details" open>
    <summary>逐日饮水数据</summary>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>日期</th>
            <th>已记录水量</th>
            <th>记录条数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="day in statistics.daily" :key="day.date">
            <td>{{ day.date }}</td>
            <td>
              {{
                day.amountMl === null
                  ? "未记录"
                  : `${nutritionNumber(day.amountMl)} 毫升`
              }}
            </td>
            <td>{{ day.recordCount }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </details>
  <p class="footnote">
    只累加所选来源的实际保存记录；缺失日期不作为 0
    纳入日均，不预设每日饮水目标。
  </p>
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
