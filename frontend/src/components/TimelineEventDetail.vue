<script setup lang="ts">
import { computed } from "vue";
import { statusText } from "../api";
import {
  calorieModes,
  calorieText,
  mealTypes,
  nutritionNumber,
  portionUnits,
  recordTime,
} from "../nutrition";
import type { TimelineEventDetail } from "../timeline";
import { deviceBrands, deviceTypes, sourceApps } from "../wearables";
import { weightText } from "../weight";

const props = defineProps<{ event: TimelineEventDetail }>();
const planId = computed(() =>
  "planId" in props.event.details ? props.event.details.planId : null,
);
const fields = computed<[string, string][]>(() => {
  const { type, details } = props.event;
  switch (type) {
    case "SLEEP":
      return [
        ["入睡时间", recordTime(details.startTime)],
        ["醒来时间", recordTime(details.endTime)],
        ["醒来归属日期", details.sleepDate],
        ["睡眠时长", `${nutritionNumber(details.durationMinutes, 4)} 分钟`],
        ["来源应用", sourceApps[details.sourceApp]],
        ["设备品牌", deviceBrands[details.deviceBrand]],
        ["设备类型", deviceTypes[details.deviceType]],
        ["设备型号", details.deviceModel || "未填写"],
      ];
    case "STEPS":
      return [
        ["区间开始时间", recordTime(details.startTime)],
        ["区间结束时间", recordTime(details.endTime)],
        ["步数", `${nutritionNumber(details.steps)} 步`],
        ["记录方式", details.mode === "CUMULATIVE" ? "累计快照" : "区间增量"],
      ];
    case "WORKOUT":
      return [
        ["运动类型", details.exerciseType],
        ["开始时间", recordTime(details.startTime)],
        ["结束时间", recordTime(details.endTime)],
        ["运动时长", `${nutritionNumber(details.durationMinutes, 4)} 分钟`],
        [
          "关联计划事项",
          details.planItemId === null ? "未关联" : `#${details.planItemId}`,
        ],
      ];
    case "MEAL": {
      const unit = portionUnits[details.portionUnit];
      const meal: [string, string][] = [
        ["餐次", mealTypes[details.mealType]],
        ["食物", details.foodName],
        [
          "份量",
          details.portionAmount === null
            ? "未记录"
            : `${nutritionNumber(details.portionAmount, 3)} ${unit}`,
        ],
        ["热量方式", calorieModes[details.calorieMode]],
        [
          "热量",
          details.caloriesKcal === null
            ? "未知"
            : calorieText(details.caloriesKcal),
        ],
      ];
      if (details.calorieMode === "ESTIMATED") {
        meal.push(
          [
            "参考份量",
            details.referenceAmount === null
              ? "未提供"
              : `${nutritionNumber(details.referenceAmount, 3)} ${unit}`,
          ],
          ["参考热量", calorieText(details.referenceCaloriesKcal)],
        );
      }
      meal.push(["营养出处", details.nutritionSource || "未填写"]);
      return meal;
    }
    case "WATER":
      return [["饮水量", `${nutritionNumber(details.amountMl)} 毫升`]];
    case "WEIGHT":
      return [["体重", weightText(details.weightKg)]];
    case "DIARY":
      return [
        ["情绪自述", details.mood || "未填写"],
        [
          "主观压力",
          details.stressLevel === null
            ? "未填写"
            : `${details.stressLevel} / 10`,
        ],
        ["身体感受", details.bodyFeeling || "未填写"],
        ["相关事件", details.events || "未填写"],
        ["自述标签", details.tags || "未填写"],
        [
          "Agent 权限",
          details.agentAllowed ? "允许后续任务读取" : "私密 · 仅自己可见",
        ],
      ];
    case "CHECKIN":
      return [
        ["计划名称", details.planTitle],
        ["打卡事项", details.itemTitle],
        ["计划安排日期", details.scheduledDate],
        ["实际打卡时间", recordTime(details.checkedAt)],
      ];
    case "PLAN_CREATED":
      return [
        ["计划名称", details.planTitle],
        ["计划开始日期", details.startDate],
        ["计划结束日期", details.endDate],
        ["创建时间", recordTime(details.createdAt)],
      ];
    case "PLAN_STATUS":
      return [
        ["计划名称", details.planTitle],
        ["原状态", statusText[details.previousStatus]],
        ["新状态", statusText[details.status]],
        ["变更时间", recordTime(details.changedAt)],
      ];
  }
});
</script>

<template>
  <dl class="meta-list timeline-detail-fields">
    <div>
      <dt>归属日期</dt>
      <dd>
        <time :datetime="event.eventDate">{{ event.eventDate }}</time>
      </dd>
    </div>
    <div>
      <dt>发生时间</dt>
      <dd>
        <time :datetime="event.occurredAt">{{
          recordTime(event.occurredAt)
        }}</time>
        <span class="muted"
          >（{{ planId === null ? "档案时区" : "系统记录时间" }}）</span
        >
      </dd>
    </div>
    <div>
      <dt>来源</dt>
      <dd class="prose">{{ event.sourceName }}</dd>
    </div>
    <div v-for="[label, value] in fields" :key="label">
      <dt>{{ label }}</dt>
      <dd class="prose">{{ value }}</dd>
    </div>
    <div v-if="'note' in event.details">
      <dt>备注</dt>
      <dd class="prose">{{ event.details.note || "未填写" }}</dd>
    </div>
  </dl>
  <RouterLink
    v-if="planId !== null"
    class="button secondary"
    :to="{ path: '/plans', query: { id: planId } }"
    >查看原计划</RouterLink
  >
</template>

<style scoped>
.timeline-detail-fields dt {
  flex: 0 0 7rem;
}
.timeline-detail-fields dd {
  min-width: 0;
  line-height: inherit;
}
@media (max-width: 500px) {
  .timeline-detail-fields > div {
    flex-direction: column;
    gap: 0.25rem;
  }
  .timeline-detail-fields dt {
    flex: auto;
  }
}
</style>
