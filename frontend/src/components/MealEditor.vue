<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { X } from "@lucide/vue";
import { api, errorMessage, json } from "../api";
import type { CalorieMode, MealRecord, MealType, PortionUnit } from "../types";
import { calorieModes, mealTypes, portionUnits } from "../nutrition";
import MealPhotoPreview from "./MealPhotoPreview.vue";
const props = defineProps<{ record: MealRecord | null; sources: string[] }>();
const emit = defineEmits<{ saved: []; close: []; busy: [value: boolean] }>();
const form = reactive({
  consumedAt: props.record?.consumedAt.slice(0, 19) ?? "",
  mealType: props.record?.mealType ?? ("BREAKFAST" as MealType),
  foodName: props.record?.foodName ?? "",
  portionAmount: props.record?.portionAmount ?? ("" as string | number),
  portionUnit: props.record?.portionUnit ?? ("G" as PortionUnit),
  calorieMode: props.record?.calorieMode ?? ("UNKNOWN" as CalorieMode),
  caloriesKcal:
    props.record?.calorieMode === "MANUAL"
      ? (props.record.caloriesKcal ?? "")
      : ("" as string | number),
  referenceAmount: props.record?.referenceAmount ?? ("" as string | number),
  referenceCaloriesKcal:
    props.record?.referenceCaloriesKcal ?? ("" as string | number),
  nutritionSource: props.record?.nutritionSource ?? "",
  sourceName: props.record?.sourceName ?? "",
  note: props.record?.note ?? "",
});
const busy = ref(false),
  error = ref("");
const recognizing = ref(false);

function applyCandidate(foodName: string, portionGrams: number | null, confidence: string) {
  form.foodName = foodName;
  form.portionAmount = portionGrams ?? "";
  form.portionUnit = "G";
  form.calorieMode = "UNKNOWN";
  form.caloriesKcal = "";
  form.referenceAmount = "";
  form.referenceCaloriesKcal = "";
  form.nutritionSource = "";
  form.sourceName = "照片识别后人工核对";
  const estimate = portionGrams === null ? "份量未估计" : `初始份量粗估 ${portionGrams} 克`;
  form.note = `照片识别候选：${foodName}，识别把握${confidence}，${estimate}。`;
}
watch(
  () => form.calorieMode,
  (mode) => {
    form.caloriesKcal = "";
    if (mode !== "ESTIMATED") {
      form.referenceAmount = "";
      form.referenceCaloriesKcal = "";
    }
  },
);
const optionalNumber = (value: string | number) =>
  value === "" ? null : Number(value);
async function save() {
  busy.value = true;
  emit("busy", true);
  error.value = "";
  try {
    await api(
      props.record ? `/nutrition/meals/${props.record.id}` : "/nutrition/meals",
      json(props.record ? "PUT" : "POST", {
        ...form,
        consumedAt:
          form.consumedAt.length === 16
            ? `${form.consumedAt}:00`
            : form.consumedAt,
        portionAmount: optionalNumber(form.portionAmount),
        caloriesKcal:
          form.calorieMode === "MANUAL"
            ? optionalNumber(form.caloriesKcal)
            : null,
        referenceAmount:
          form.calorieMode === "ESTIMATED"
            ? optionalNumber(form.referenceAmount)
            : null,
        referenceCaloriesKcal:
          form.calorieMode === "ESTIMATED"
            ? optionalNumber(form.referenceCaloriesKcal)
            : null,
      }),
    );
    emit("saved");
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
    emit("busy", false);
  }
}
</script>
<template>
  <section class="editor-panel" aria-label="饮食编辑表单">
    <div class="section-heading">
      <h3>{{ record ? "编辑饮食记录" : "新增饮食记录" }}</h3>
      <button
        class="icon-button"
        :disabled="busy || recognizing"
        aria-label="关闭饮食编辑"
        @click="$emit('close')"
      >
        <X :size="18" />
      </button>
    </div>
    <MealPhotoPreview v-if="!record" :disabled="busy" @candidate="applyCandidate"
      @busy="recognizing = $event; emit('busy', $event)" />
    <form class="form-grid" @submit.prevent="!recognizing && save()">
      <label
        >食用时间（档案时区）<input
          v-model="form.consumedAt"
          type="datetime-local"
          step="1"
          required
          :disabled="busy"
      /></label>
      <label
        >餐次<select v-model="form.mealType" :disabled="busy">
          <option
            v-for="(label, value) in mealTypes"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select></label
      >
      <label
        >食物名称<input
          v-model="form.foodName"
          required
          maxlength="100"
          placeholder="填写实际食物名称"
          :disabled="busy"
      /></label>
      <label
        >记录来源<input
          v-model="form.sourceName"
          list="meal-sources"
          required
          maxlength="100"
          placeholder="例如：本人手工记录"
          :disabled="busy"
      /></label>
      <label
        >食用份量{{
          form.calorieMode === "ESTIMATED" ? "（估算必填）" : "（选填）"
        }}<input
          v-model="form.portionAmount"
          type="number"
          min="0.001"
          max="999999999.999"
          step="0.001"
          :required="form.calorieMode === 'ESTIMATED'"
          :disabled="busy"
      /></label>
      <label
        >份量单位<select v-model="form.portionUnit" :disabled="busy">
          <option
            v-for="(label, value) in portionUnits"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select></label
      >
      <label
        >热量记录方式<select v-model="form.calorieMode" :disabled="busy">
          <option
            v-for="(label, value) in calorieModes"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select></label
      >
      <label v-if="form.calorieMode === 'MANUAL'"
        >本次食物热量（kcal）<input
          v-model="form.caloriesKcal"
          type="number"
          min="0"
          max="9999999999.99"
          step="0.01"
          required
          :disabled="busy"
      /></label>
      <template v-if="form.calorieMode === 'ESTIMATED'">
        <p class="field-hint span-all">
          请提供同一食物的参考份量与热量。保存后由服务端按「食用份量 × 参考热量
          ÷ 参考份量」计算并四舍五入至两位小数，结果标为估算。
        </p>
        <label
          >参考份量（{{ portionUnits[form.portionUnit] }}）<input
            v-model="form.referenceAmount"
            type="number"
            min="0.001"
            max="999999999.999"
            step="0.001"
            required
            :disabled="busy"
        /></label>
        <label
          >上述参考份量的热量（kcal）<input
            v-model="form.referenceCaloriesKcal"
            type="number"
            min="0"
            max="9999999999.99"
            step="0.01"
            required
            :disabled="busy"
        /></label>
      </template>
      <label class="span-all"
        >营养依据 / 出处{{
          form.calorieMode === "ESTIMATED" ? "（必填）" : "（选填）"
        }}<input
          v-model="form.nutritionSource"
          maxlength="500"
          :required="form.calorieMode === 'ESTIMATED'"
          placeholder="例如：食品包装营养成分表，写明可核对的出处"
          :disabled="busy"
      /></label>
      <p class="field-hint span-all">
        份量可以未知；选择估算时必须补齐份量与依据。热量未知不会计为零；手动明确填写的
        0 kcal 会保留，修改份量不会自动缩放手动热量。
      </p>
      <label class="span-all"
        >备注（选填）<textarea
          v-model="form.note"
          rows="2"
          maxlength="1000"
          :disabled="busy"
        />
      </label>
      <div class="actions span-all">
        <button :disabled="busy || recognizing">{{ busy ? "保存中…" : "保存饮食记录" }}</button
        ><button
          type="button"
          class="secondary"
          :disabled="busy"
          @click="$emit('close')"
        >
          取消
        </button>
      </div>
    </form>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <datalist id="meal-sources">
      <option v-for="source in sources" :key="source" :value="source" />
    </datalist>
  </section>
</template>
