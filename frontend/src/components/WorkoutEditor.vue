<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { X } from "@lucide/vue";
import { api, errorMessage, json } from "../api";
import type { ActivityPlanItem, Workout } from "../types";
import StateNotice from "./StateNotice.vue";
const props = defineProps<{ record: Workout | null; sources: string[] }>();
const emit = defineEmits<{ saved: []; close: []; busy: [value: boolean] }>();
const form = reactive({
  exerciseType: props.record?.exerciseType ?? "",
  startTime: props.record?.startTime.slice(0, 19) ?? "",
  endTime: props.record?.endTime.slice(0, 19) ?? "",
  sourceName: props.record?.sourceName ?? "",
  note: props.record?.note ?? "",
  planItemId: props.record?.planItemId ?? (null as number | null),
});
const busy = ref(false),
  error = ref(""),
  planLoading = ref(false),
  planError = ref("");
const planItems = ref<ActivityPlanItem[]>([]),
  loadedDate = ref("");
const startDate = computed(() => form.startTime.slice(0, 10));
const currentUnavailable = computed(
  () =>
    form.planItemId !== null &&
    props.record?.planItemId === form.planItemId &&
    !planItems.value.some((item) => item.id === form.planItemId),
);
async function loadPlanItems() {
  loadedDate.value = startDate.value;
  planItems.value = [];
  planError.value = "";
  if (!startDate.value) return;
  planLoading.value = true;
  try {
    planItems.value = await api<ActivityPlanItem[]>(
      `/activity/plan-items?${new URLSearchParams({ date: startDate.value })}`,
    );
  } catch (cause) {
    planError.value = errorMessage(cause);
  } finally {
    planLoading.value = false;
  }
}
function changeStart() {
  if (startDate.value === loadedDate.value) return;
  form.planItemId = null;
  void loadPlanItems();
}
async function save() {
  busy.value = true;
  emit("busy", true);
  error.value = "";
  try {
    await api(
      props.record
        ? `/activity/workouts/${props.record.id}`
        : "/activity/workouts",
      json(props.record ? "PUT" : "POST", form),
    );
    emit("saved");
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
    emit("busy", false);
  }
}
onMounted(loadPlanItems);
</script>
<template>
  <section class="editor-panel" aria-label="运动编辑表单">
    <div class="section-heading">
      <h3>{{ record ? "编辑运动记录" : "新增运动记录" }}</h3>
      <button
        class="icon-button"
        :disabled="busy"
        aria-label="关闭运动编辑"
        @click="$emit('close')"
      >
        <X :size="18" />
      </button>
    </div>
    <form class="form-grid" @submit.prevent="save">
      <label
        >运动类型<input
          v-model="form.exerciseType"
          required
          maxlength="50"
          placeholder="例如：步行、骑行、力量训练"
          :disabled="busy"
      /></label>
      <label
        >来源名称<input
          v-model="form.sourceName"
          list="workout-sources"
          maxlength="100"
          required
          placeholder="填写实际设备或手工记录来源"
          :disabled="busy"
      /></label>
      <label
        >开始时间<input
          v-model="form.startTime"
          type="datetime-local"
          step="1"
          required
          :disabled="busy || planLoading"
          @change="changeStart"
      /></label>
      <label
        >结束时间<input
          v-model="form.endTime"
          type="datetime-local"
          step="1"
          :min="form.startTime"
          required
          :disabled="busy"
      /></label>
      <div class="span-all">
        <label
          >关联计划任务（选填）<select
            v-model="form.planItemId"
            :disabled="busy || planLoading || !startDate"
          >
            <option :value="null">不关联计划任务</option>
            <option v-if="currentUnavailable" :value="record!.planItemId">
              当前关联：{{ record!.planItemTitle }}（不在当前可选列表）
            </option>
            <option v-for="item in planItems" :key="item.id" :value="item.id">
              {{ item.planTitle }} · {{ item.title }} ·
              {{ item.completed ? "已打卡" : "未打卡" }}
            </option>
          </select></label
        >
        <StateNotice
          :loading="planLoading"
          :error="planError"
          @retry="loadPlanItems"
        />
        <p v-if="!startDate" class="field-hint">
          选择开始时间后，显示当天进行中计划的可关联任务。
        </p>
        <p
          v-else-if="!planLoading && !planError && !planItems.length"
          class="field-hint"
        >
          {{ startDate }} 没有可关联的进行中计划任务，可直接保存运动记录。
        </p>
        <p v-if="currentUnavailable && !planLoading" class="field-hint">
          当前关联未出现在可选列表中。开始日期不变时，可以保留原关联并修改运动记录；更改开始日期会清除关联，替换任务仅能从当天进行中的计划中选择。
        </p>
        <p class="field-hint">
          新增或替换关联仅能选择本人、同开始日期、进行中计划的任务。更改开始日期会清除本次表单中的关联选择；保存运动后，可在记录列表单独打卡，打卡仍要求计划进行中。
        </p>
      </div>
      <label class="span-all"
        >备注（选填）<textarea
          v-model="form.note"
          rows="2"
          maxlength="1000"
          :disabled="busy"
        />
      </label>
      <p class="field-hint span-all">
        时长按真实起止时间计算。记录按开始日期归档；统计按每日实际覆盖时段计算。
      </p>
      <div class="actions span-all">
        <button :disabled="busy || planLoading">
          {{ busy ? "保存中…" : "保存运动记录" }}</button
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
    <datalist id="workout-sources">
      <option v-for="source in sources" :key="source" :value="source" />
    </datalist>
  </section>
</template>
