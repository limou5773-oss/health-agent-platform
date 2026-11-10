<script setup lang="ts">
import type { WeightGoalProgress } from "../types";
import { signedWeight, weightNumber, weightText, weightTime } from "../weight";
import StateNotice from "./StateNotice.vue";
defineProps<{ progress: WeightGoalProgress }>();
defineEmits<{ detail: [id: number] }>();
const directions = {
  LOSE: "降低至目标",
  GAIN: "增加至目标",
  MAINTAIN: "维持目标数值",
};
</script>
<template>
  <div class="weight-goal-progress">
    <h3>目标进度</h3>
    <p class="small muted">
      {{ progress.goal.startDate }} 至 {{ progress.goal.targetDate }} · 目标
      {{ weightText(progress.goal.targetWeightKg) }} ·
      {{ progress.goal.sourceName || "全部来源" }}
    </p>
    <p class="small">
      有效测量 {{ progress.validDays }} / {{ progress.periodDays }} 天 · 覆盖率
      {{ weightNumber(progress.coverageRate, 2) }}%
    </p>
    <StateNotice
      :empty="progress.validDays === 0"
      title="目标周期内还没有测量"
      description="目标已保存，基线和进度将在记录测量后计算；不会使用档案体重填充。"
    />
    <template
      v-if="progress.baseline && progress.current && progress.direction"
    >
      <p>
        {{ directions[progress.direction] }} ·
        {{ progress.goalReached ? "数值已达到目标" : "尚未达到目标数值" }}
      </p>
      <div class="metrics">
        <div class="metric primary-metric">
          <span>向目标的变化比例</span
          ><strong>{{
            progress.progressRate === null
              ? "不计算百分比"
              : `${weightNumber(progress.progressRate, 2)}%`
          }}</strong>
          <p>
            {{
              progress.direction === "MAINTAIN"
                ? "维持目标只核对差值与数值是否相等"
                : "保留负进度及超过 100% 的实际比例"
            }}
          </p>
        </div>
        <div class="metric">
          <span>当前 − 基线</span
          ><strong>{{ signedWeight(progress.changeKg) }}</strong>
          <p>目标周期内实际变化</p>
        </div>
        <div class="metric">
          <span>当前 − 目标</span
          ><strong>{{ signedWeight(progress.differenceToTargetKg) }}</strong>
          <p>正数高于目标，负数低于目标</p>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>阶段</th>
              <th>体重</th>
              <th>实际测量时间 / 来源</th>
              <th>原始记录</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>基线 · 首个有效日</td>
              <td>{{ weightText(progress.baseline.weightKg) }}</td>
              <td>
                {{ weightTime(progress.baseline.measuredAt) }}
                <p class="small">{{ progress.baseline.sourceName }}</p>
              </td>
              <td>
                <button
                  class="text-button"
                  @click="$emit('detail', progress.baseline.id)"
                >
                  查看 #{{ progress.baseline.id }}
                </button>
              </td>
            </tr>
            <tr>
              <td>当前 · 最后有效日</td>
              <td>{{ weightText(progress.current.weightKg) }}</td>
              <td>
                {{ weightTime(progress.current.measuredAt) }}
                <p class="small">{{ progress.current.sourceName }}</p>
              </td>
              <td>
                <button
                  class="text-button"
                  @click="$emit('detail', progress.current.id)"
                >
                  查看 #{{ progress.current.id }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <p class="footnote">
      基线和当前值均取对应有效日的最后一次测量，修改或删除原记录后重新计算。数据截止：{{
        progress.dataCutoff === null
          ? "无测量"
          : weightTime(progress.dataCutoff)
      }}。
    </p>
  </div>
</template>
<style scoped>
.weight-goal-progress {
  margin-top: 1.5rem;
}
</style>
