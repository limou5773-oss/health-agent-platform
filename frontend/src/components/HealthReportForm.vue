<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api, dateAfter, dateString, errorMessage, json } from "../api";
import type { Report } from "../types";

const emit = defineEmits<{ generated: [] }>();
const router = useRouter();
const today = dateString(new Date());
const yesterday = dateAfter(today, -1);
const sourceFields = {
  sleepSource: "睡眠",
  stepSource: "步数",
  workoutSource: "运动",
  mealSource: "饮食",
  waterSource: "饮水",
  weightSource: "体重",
};
const form = reactive({ period: "WEEK", date: yesterday });
const sources = reactive({
  sleepSource: "", stepSource: "", workoutSource: "",
  mealSource: "", waterSource: "", weightSource: "",
});
const lastMonth = dateAfter(`${today.slice(0, 7)}-01`, -1).slice(0, 7);
const month = ref(lastMonth);
const busy = ref(false);
const error = ref("");
const date = computed(() => form.period === "WEEK" ? form.date : `${month.value}-01`);

async function generate() {
  error.value = "";
  busy.value = true;
  try {
    const report = await api<Report>(
      "/reports/health",
      json("POST", { period: form.period, date: date.value, ...sources }),
    );
    emit("generated");
    await router.push({ query: { id: report.id } });
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <section id="generate-health-report" class="panel">
    <h2>生成综合健康报告</h2>
    <form @submit.prevent="generate">
      <div class="filter-bar">
        <label>
          报告类型
          <select v-model="form.period" :disabled="busy">
            <option value="WEEK">综合周报</option>
            <option value="MONTH">综合月报</option>
          </select>
        </label>
        <label v-if="form.period === 'WEEK'">
          结束日期（含当天共 7 天）
          <input v-model="form.date" type="date" :max="yesterday" required />
        </label>
        <label v-else>
          报告月份
          <input v-model="month" type="month" :max="lastMonth" required />
        </label>
        <button type="submit" :disabled="busy">{{ busy ? "正在生成…" : "生成报告" }}</button>
      </div>
      <details>
        <summary>指定数据来源</summary>
        <p class="muted">填写对应指标的来源全名。留空时汇总全部来源，存在来源冲突时请选择单一来源。</p>
        <div class="filter-bar">
          <label v-for="(label, key) in sourceFields" :key="key">
            {{ label }}来源
            <input v-model="sources[key]" maxlength="100" placeholder="全部来源" />
          </label>
        </div>
      </details>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    </form>
  </section>
</template>
