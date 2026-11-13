<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import StateNotice from "./StateNotice.vue";

const rules = {
  checkinEnabled: "打卡提醒", planDueEnabled: "计划到期", reportEnabled: "报告生成",
  dataChangeEnabled: "体重变化", importFailedEnabled: "导入失败",
  weeklyEnabled: "每周睡眠复盘", monthlyEnabled: "每月睡眠复盘", importAnalysisEnabled: "睡眠导入后分析",
} as const;
type Settings = Record<keyof typeof rules, boolean> & { modelName: string; modelTimeoutSeconds: number };
const form = ref<Settings | null>(null);
const loading = ref(true), error = ref(""), busy = ref(false), actionError = ref(""), feedback = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    form.value = await api<Settings>("/admin/system-settings");
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function submit(settings: Settings, check = false) {
  busy.value = true;
  feedback.value = "";
  actionError.value = "";
  try {
    if (check) {
      const { modelName, modelTimeoutSeconds } = settings;
      await api("/admin/system-settings/model-check", json("POST", { modelName, modelTimeoutSeconds }), 100000);
      feedback.value = "模型连接检查通过。";
    } else {
      form.value = await api<Settings>("/admin/system-settings", json("PUT", settings));
      feedback.value = "平台设置已保存。";
    }
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>

<template>
  <section class="panel">
    <h2>提醒与系统配置</h2>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <form v-if="form && !loading && !error" @submit.prevent="submit(form)">
      <fieldset class="system-fields" :disabled="busy">
        <h3>平台规则</h3>
        <p class="muted">关闭后停止对应规则的新触发，开启后仍按用户授权和个人设置执行。</p>
        <div class="form-grid">
          <label v-for="(label, key) in rules" :key="key" class="checkbox-label">
            <input v-model="form[key]" type="checkbox" />{{ label }}
          </label>
        </div>
        <h3>模型连接</h3>
        <p class="muted">地址和密钥由 Agent 服务端配置。模型名称留空时使用部署默认值。</p>
        <div class="filter-bar">
          <label>模型名称<input v-model="form.modelName" maxlength="200" /></label>
          <label>单次模型请求超时（秒）
            <input v-model.number="form.modelTimeoutSeconds" type="number" min="5" max="90" required />
          </label>
        </div>
        <div class="actions">
          <button>保存设置</button>
          <button type="button" class="secondary" :disabled="form.modelTimeoutSeconds < 5 || form.modelTimeoutSeconds > 90"
            @click="submit(form, true)">检查模型连接</button>
        </div>
      </fieldset>
      <p v-if="busy" role="status" class="muted">正在处理…</p>
      <p v-if="actionError" role="alert" class="feedback error">{{ actionError }}</p>
      <p v-if="feedback" role="status" class="feedback success">{{ feedback }}</p>
    </form>
  </section>
</template>

<style scoped>
.system-fields { border: 0; padding: 0; margin: 0; }
.system-fields h3 { margin-top: 1rem; }
</style>
