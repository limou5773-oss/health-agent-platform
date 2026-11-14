<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api, errorMessage, json } from "../api";
import type { DraftItem, Plan } from "../types";
import StateNotice from "./StateNotice.vue";

type Item = DraftItem & { id: number | null; reminderTime: string | null };
type Content = Pick<Plan, "title" | "reason" | "startDate" | "endDate"> & { items: Item[] };
type Revision = { version: number; note: string; createdAt: string; content: Content };
type Revisions = { activeVersion: number; latestVersion: number; draft: Revision | null; history: Revision[] };
const props = defineProps<{ plan: Plan }>();
const emit = defineEmits<{ refresh: [] }>();
const revisions = ref<Revisions | null>(null);
const editor = ref<Content | null>(null);
const selectedVersion = ref<number | null>(null);
const note = ref("");
const loading = ref(true);
const error = ref("");
const actionError = ref("");
const busy = ref(false);
const instruction = ref("");
const section = ref<HTMLElement | null>(null);

const current = computed<Content>(() => {
  const { title, reason, startDate, endDate, items } = props.plan;
  return { title, reason, startDate, endDate, items: (items ?? []).map(
    ({ id, title, description, date, time, reminderTime }) => ({ id, title, description, date, time, reminderTime }),
  ) };
});
const locked = computed(() => new Set(props.plan.items?.filter(item => item.completed).map(item => item.id)));
const comparison = computed(() => {
  const data = revisions.value;
  if (!data) return null;
  return selectedVersion.value === null ? data.draft : data.history.find(row => row.version === selectedVersion.value);
});
const changes = computed(() => {
  const next = comparison.value?.content;
  if (!next) return [];
  const originals = new Map(current.value.items.map(item => [item.id, item]));
  const result: { before?: Item; after?: Item }[] = [];
  for (const item of next.items) {
    const before = item.id === null ? undefined : originals.get(item.id);
    if (!before || before.date !== item.date || before.time !== item.time || before.title !== item.title
        || before.description !== item.description || before.reminderTime !== item.reminderTime) {
      result.push({ before, after: item });
    }
    originals.delete(item.id);
  }
  for (const before of originals.values()) result.push({ before });
  return result;
});
const endpoint = `/plans/${props.plan.id}/revisions`;

async function load() {
  error.value = "";
  loading.value = true;
  try {
    revisions.value = await api<Revisions>(endpoint);
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

function edit(content: Content, reason = "") {
  actionError.value = "";
  editor.value = { ...content, items: content.items.map(item => ({ ...item })) };
  note.value = reason;
}

function proposeMove(itemId: number, date: string, reason: string, version: number) {
  section.value?.scrollIntoView({ block: "start" });
  actionError.value = "";
  const data = revisions.value;
  if (!data || data.activeVersion !== version || props.plan.version !== version) {
    actionError.value = "计划版本已变化，请刷新后重新复盘。";
    return;
  }
  if (editor.value || data.draft) {
    actionError.value = "请先处理正在编辑或已保存的修订草稿。";
    return;
  }
  const item = current.value.items.find(entry => entry.id === itemId);
  if (!item || locked.value.has(itemId)) {
    actionError.value = "该任务已变更，请刷新后重新复盘。";
    return;
  }
  const startDate = date < current.value.startDate ? date : current.value.startDate;
  const endDate = date > current.value.endDate ? date : current.value.endDate;
  edit({
    ...current.value,
    startDate,
    endDate,
    items: current.value.items.map(entry => entry.id === itemId ? { ...entry, date } : entry),
  }, `周期复盘：将「${item.title}」从 ${item.date} 调整到 ${date}${reason ? `。未完成原因：${reason}` : ""}`);
}

defineExpose({ proposeMove });

async function suggest(expectedVersion: number) {
  busy.value = true;
  actionError.value = "";
  try {
    await api(`${endpoint}/suggestions`, json("POST", { expectedVersion, instruction: instruction.value }), 105_000);
    instruction.value = "";
    selectedVersion.value = null;
    await load();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function act(action: "save" | "confirm" | "discard") {
  if (!revisions.value) return;
  busy.value = true;
  actionError.value = "";
  try {
    if (action === "save" && editor.value) {
      await api(endpoint, json("POST", {
        expectedVersion: revisions.value.latestVersion,
        note: note.value,
        content: {
          ...editor.value,
          items: editor.value.items.map(item => ({ ...item, reminderTime: item.reminderTime || null })),
        },
      }));
      editor.value = null;
      selectedVersion.value = null;
      await load();
    } else if (revisions.value.draft && action !== "save") {
      const path = `${endpoint}/${revisions.value.draft.version}`;
      if (action === "confirm") {
        await api(`${path}/confirm`, json("POST"));
        emit("refresh");
      } else {
        await api(path, json("DELETE"));
        await load();
      }
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
  <section ref="section" class="plan-revisions" aria-labelledby="plan-revisions-title">
    <h3 id="plan-revisions-title">计划修订 · 当前版本 {{ plan.version }}</h3>
    <StateNotice :loading="loading" :error="error" @retry="load" />
    <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
    <p v-if="revisions && !loading && !error && revisions.activeVersion !== plan.version" class="feedback">
      当前计划已更新。
      <button class="secondary" @click="emit('refresh')">刷新计划</button>
    </p>
    <template v-else-if="revisions && !loading && !error">
      <form v-if="editor" @submit.prevent="act('save')">
        <fieldset :disabled="busy">
          <div class="form-grid">
            <label>标题<input v-model="editor.title" required maxlength="200" /></label>
            <label>调整说明<input v-model="note" required maxlength="1000" /></label>
            <label>开始日期<input v-model="editor.startDate" type="date" required /></label>
            <label>结束日期<input v-model="editor.endDate" type="date" :min="editor.startDate" required /></label>
            <label class="span-all">计划依据<textarea v-model="editor.reason" required maxlength="4000" /></label>
          </div>
          <fieldset v-for="(item, index) in editor.items" :key="index"
            :disabled="item.id !== null && locked.has(item.id)">
            <legend>任务 {{ index + 1 }}{{ item.id !== null && locked.has(item.id) ? "（已打卡）" : "" }}</legend>
            <div class="form-grid">
              <label>任务名称<input v-model="item.title" required maxlength="200" /></label>
              <label>执行日期<input v-model="item.date" type="date" :min="editor.startDate" :max="editor.endDate" required /></label>
              <label>执行时间<input v-model="item.time" type="time" step="1" required /></label>
              <label>提醒时间<input v-model="item.reminderTime" type="time" step="1" /></label>
              <label class="span-all">任务内容<textarea v-model="item.description" maxlength="2000" /></label>
            </div>
            <button type="button" class="secondary" @click="editor.items.splice(index, 1)">移除任务</button>
          </fieldset>
          <div class="actions">
            <button type="button" class="secondary" :disabled="editor.items.length >= 93"
              @click="editor.items.push({ id: null, title: '', description: '', date: editor.startDate, time: '', reminderTime: null })">
              添加任务
            </button>
            <button :disabled="!editor.items.length">保存草稿</button>
            <button type="button" class="secondary" @click="editor = null">退出编辑</button>
            <button v-if="actionError" type="button" class="secondary" @click="emit('refresh')">刷新计划</button>
          </div>
        </fieldset>
      </form>
      <template v-else>
        <form @submit.prevent="suggest(revisions.latestVersion)">
          <label>用一句话修订
            <textarea v-model="instruction" required maxlength="2000" rows="2" :disabled="busy"
              placeholder="例如，把周五的散步改到晚上七点，提前十五分钟提醒" />
          </label>
          <p class="small muted">会发送本份计划和调整要求给外部模型。生成后可比较草稿并另行确认。</p>
          <button class="secondary" :disabled="busy || !instruction.trim()">生成修订草稿</button>
        </form>
        <div class="actions">
          <button v-if="revisions.draft" class="secondary" :disabled="busy"
            @click="edit(revisions.draft.content, revisions.draft.note)">编辑草稿</button>
          <button v-else class="secondary" :disabled="busy" @click="edit(current)">修订计划</button>
          <button v-if="actionError" class="secondary" :disabled="busy" @click="emit('refresh')">刷新计划</button>
        </div>
        <label v-if="revisions.history.length">比较版本
          <select v-model="selectedVersion" :disabled="busy">
            <option :value="null">{{ revisions.draft ? `草稿第 ${revisions.draft.version} 版` : "请选择历史版本" }}</option>
            <option v-for="row in revisions.history" :key="row.version" :value="row.version">
              第 {{ row.version }} 版 · {{ row.createdAt.slice(0, 10) }}
            </option>
          </select>
        </label>
        <div v-if="comparison" class="revision-comparison">
          <h4>第 {{ comparison.version }} 版 · {{ comparison.note }}</h4>
          <dl>
            <dt>当前计划</dt>
            <dd>{{ current.title }} · {{ current.startDate }} 至 {{ current.endDate }}<br />{{ current.reason }}</dd>
            <dt>比较版本</dt>
            <dd>{{ comparison.content.title }} · {{ comparison.content.startDate }} 至 {{ comparison.content.endDate }}<br />{{ comparison.content.reason }}</dd>
          </dl>
          <p v-if="!changes.length">任务内容相同。</p>
          <ul v-else>
            <li v-for="(change, index) in changes" :key="index">
              <p v-for="side in (['before', 'after'] as const)" v-show="change[side]" :key="side">
                <template v-if="change[side]">
                  <strong>{{ side === "before" ? "当前" : "比较版本" }}</strong>
                  {{ change[side].date }} {{ change[side].time }} · {{ change[side].title }}
                  <span> · 提醒 {{ change[side].reminderTime || "未设置" }}</span>
                  <br />{{ change[side].description }}
                </template>
              </p>
            </li>
          </ul>
          <div v-if="revisions.draft && selectedVersion === null" class="actions">
            <button :disabled="busy" @click="act('confirm')">确认并应用修订</button>
            <button class="secondary" :disabled="busy" @click="act('discard')">放弃草稿</button>
          </div>
        </div>
      </template>
    </template>
  </section>
</template>

<style scoped>
.plan-revisions { margin-block: 1.5rem; }
.plan-revisions fieldset {
  border: 1px solid var(--line);
  border-radius: 6px;
  margin-block: 0.75rem;
  min-width: 0;
}
.revision-comparison { margin-top: 1rem; }
.revision-comparison dd { margin: 0 0 0.75rem; overflow-wrap: anywhere; }
.revision-comparison li { overflow-wrap: anywhere; }
</style>
