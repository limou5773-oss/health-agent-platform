<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import { templateCategories } from "../plan-templates";
import type { PlanTemplate } from "../plan-templates";
import StateNotice from "./StateNotice.vue";

const templates = ref<PlanTemplate[]>([]);
const editor = ref<PlanTemplate | null>(null);
const loading = ref(true), busy = ref(false), error = ref(""), actionError = ref("");
async function load() {
  loading.value = true;
  error.value = "";
  try {
    templates.value = await api<PlanTemplate[]>("/admin/plan-templates");
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
function edit(template?: PlanTemplate) {
  actionError.value = "";
  editor.value = template ? { ...template, items: template.items.map(item => ({ ...item })) }
    : { id: 0, version: 0, category: "SLEEP", title: "", instructions: "", days: 7, enabled: false,
        items: [{ day: 1, time: "", title: "", description: "" }] };
}
async function save(template: PlanTemplate) {
  busy.value = true;
  actionError.value = "";
  try {
    const { id, ...body } = template;
    await api(id ? `/admin/plan-templates/${id}` : "/admin/plan-templates", json(id ? "PUT" : "POST", body));
    editor.value = null;
    await load();
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
    <div class="section-heading">
      <h2>计划模板</h2>
      <button v-if="!editor" :disabled="loading" @click="edit()">新建模板</button>
    </div>
    <StateNotice :loading="loading" :error="error" :empty="!templates.length && !editor" title="暂无模板" @retry="load" />
    <form v-if="editor" @submit.prevent="save(editor)">
      <fieldset :disabled="busy" class="template-fields">
        <div class="form-grid">
          <label>标题<input v-model="editor.title" maxlength="200" required /></label>
          <label>分类<select v-model="editor.category">
            <option v-for="(label, key) in templateCategories" :key="key" :value="key">{{ label }}</option>
          </select></label>
          <label>周期天数<input v-model.number="editor.days" type="number" min="1" max="31" required /></label>
          <label class="checkbox-label"><input v-model="editor.enabled" type="checkbox" />允许用户使用</label>
          <label class="span-all">适用说明<textarea v-model="editor.instructions" maxlength="4000" required /></label>
        </div>
        <fieldset v-for="(item, index) in editor.items" :key="index">
          <legend>任务 {{ index + 1 }}</legend>
          <div class="form-grid">
            <label>第几天<input v-model.number="item.day" type="number" min="1" :max="editor.days" required /></label>
            <label>时间<input v-model="item.time" type="time" step="1" required /></label>
            <label>任务名称<input v-model="item.title" maxlength="200" required /></label>
            <label>任务说明<textarea v-model="item.description" maxlength="2000" /></label>
          </div>
          <button type="button" class="secondary" @click="editor.items.splice(index, 1)">移除任务</button>
        </fieldset>
        <div class="actions">
          <button type="button" class="secondary" :disabled="editor.items.length >= 93"
            @click="editor.items.push({ day: 1, time: '', title: '', description: '' })">添加任务</button>
          <button :disabled="!editor.items.length">保存模板</button>
          <button type="button" class="secondary" @click="editor = null">取消</button>
        </div>
      </fieldset>
      <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
    </form>
    <div v-else-if="!loading && !error && templates.length" class="table-wrap">
      <table>
        <thead><tr><th>名称</th><th>分类</th><th>周期</th><th>版本</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="template in templates" :key="template.id">
          <td>{{ template.title }}</td><td>{{ templateCategories[template.category] }}</td>
          <td>{{ template.days }} 天</td><td>{{ template.version }}</td>
          <td>{{ template.enabled ? "启用" : "停用" }}</td>
          <td><button class="secondary" @click="edit(template)">编辑</button></td>
        </tr></tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.template-fields { border: 0; padding: 0; margin: 0; }
.template-fields fieldset { margin-block: 0.75rem; border: 1px solid var(--line); }
</style>
