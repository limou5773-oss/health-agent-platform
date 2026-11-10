<script setup lang="ts">
import type { MealRecord, WaterRecord } from "../types";
import {
  calorieModes,
  calorieText,
  mealTypes,
  nutritionNumber,
  portionUnits,
  recordTime,
} from "../nutrition";
defineProps<{
  items: (MealRecord | WaterRecord)[];
  kind: "meals" | "water";
  busy: boolean;
}>();
defineEmits<{
  edit: [record: MealRecord | WaterRecord];
  remove: [record: MealRecord | WaterRecord];
}>();
</script>
<template>
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>实际{{ kind === "meals" ? "食用" : "饮用" }}时间</th>
          <th>{{ kind === "meals" ? "食物 / 餐次" : "饮水量" }}</th>
          <th v-if="kind === 'meals'">份量 / 热量</th>
          <th>来源 / 可核对详情</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="record in items" :key="record.id">
          <td>{{ recordTime(record.consumedAt) }}</td>
          <template v-if="'foodName' in record">
            <td class="note-cell">
              <strong>{{ record.foodName }}</strong>
              <p class="small">{{ mealTypes[record.mealType] }}</p>
            </td>
            <td>
              <p>
                {{
                  record.portionAmount === null
                    ? "份量未记录"
                    : `${nutritionNumber(record.portionAmount, 3)} ${portionUnits[record.portionUnit]}`
                }}
              </p>
              <span
                :class="record.calorieMode === 'ESTIMATED' ? 'tag warm' : 'tag'"
                >{{ calorieModes[record.calorieMode] }}</span
              >
              <p>
                {{
                  record.caloriesKcal === null
                    ? "热量未知"
                    : calorieText(record.caloriesKcal)
                }}{{ record.calorieMode === "ESTIMATED" ? "（估算）" : "" }}
              </p>
            </td>
          </template>
          <td v-else>
            <strong>{{ nutritionNumber(record.amountMl) }} 毫升</strong>
          </td>
          <td class="note-cell">
            {{ record.sourceName }}
            <details class="data-details">
              <summary>查看记录详情</summary>
              <dl class="meta-list nutrition-record-meta">
                <div>
                  <dt>记录编号</dt>
                  <dd>{{ record.id }}</dd>
                </div>
                <div>
                  <dt>实际时间</dt>
                  <dd>{{ recordTime(record.consumedAt) }}（档案时区）</dd>
                </div>
                <div>
                  <dt>创建时间</dt>
                  <dd>{{ recordTime(record.createdAt) }}</dd>
                </div>
                <div>
                  <dt>更新时间</dt>
                  <dd>{{ recordTime(record.updatedAt) }}</dd>
                </div>
                <template v-if="'foodName' in record">
                  <div>
                    <dt>热量方式</dt>
                    <dd>{{ calorieModes[record.calorieMode] }}</dd>
                  </div>
                  <div v-if="record.calorieMode === 'ESTIMATED'">
                    <dt>参考依据</dt>
                    <dd>
                      {{
                        record.referenceAmount === null
                          ? "未提供"
                          : `${nutritionNumber(record.referenceAmount, 3)} ${portionUnits[record.portionUnit]}`
                      }}
                      对应
                      {{
                        calorieText(record.referenceCaloriesKcal)
                      }}；本次按相同单位换算，结果为估算值。
                    </dd>
                  </div>
                  <div>
                    <dt>营养出处</dt>
                    <dd>{{ record.nutritionSource || "未填写" }}</dd>
                  </div>
                </template>
                <div>
                  <dt>记录来源</dt>
                  <dd>{{ record.sourceName }}</dd>
                </div>
                <div>
                  <dt>备注</dt>
                  <dd class="prose">{{ record.note || "未填写" }}</dd>
                </div>
              </dl>
            </details>
          </td>
          <td>
            <div class="actions">
              <button
                class="text-button"
                :disabled="busy"
                @click="$emit('edit', record)"
              >
                编辑</button
              ><button
                class="text-button danger-text"
                :disabled="busy"
                @click="$emit('remove', record)"
              >
                删除
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
<style scoped>
.nutrition-record-meta {
  min-width: 18rem;
  margin-top: 0.75rem;
}
.nutrition-record-meta > div {
  flex-direction: column;
  gap: 0.25rem;
}
</style>
