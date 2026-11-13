<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { api, errorMessage, json } from "../api";
import StateNotice from "../components/StateNotice.vue";
import type { Page } from "../types";

const labels = {
  CHECKIN: "打卡提醒",
  PLAN_DUE: "计划到期",
  REPORT: "报告生成",
  DATA_CHANGE: "数据变化",
  IMPORT_FAILED: "导入失败",
} as const;
type Message = {
  id: number;
  kind: keyof typeof labels;
  title: string;
  body: string;
  target: string;
  createdAt: string;
  readAt: string | null;
  dataCutoff: string | null;
};
type Settings = {
  checkinEnabled: boolean;
  checkinFrequency: "PER_TASK" | "DAILY";
  dailyTime: string;
  planDueEnabled: boolean;
  reportEnabled: boolean;
  dataChangeEnabled: boolean;
  importFailedEnabled: boolean;
};
const settings = ref<Settings | null>(null);
const list = ref<Page<Message> | null>(null);
const unread = ref(0);
const unreadOnly = ref(false);
const page = ref(1);
const listLoading = ref(false);
const settingsLoading = ref(true);
const listError = ref("");
const settingsError = ref("");
const actionError = ref("");
const feedback = ref("");
const busy = ref(false);
const saving = ref(false);
let loadRevision = 0;

async function loadMessages() {
  const revision = ++loadRevision;
  listLoading.value = true;
  listError.value = "";
  try {
    const [data, count] = await Promise.all([
      api<Page<Message>>(`/notifications?page=${page.value}&unreadOnly=${unreadOnly.value}`),
      api<{ count: number }>("/notifications/unread-count"),
    ]);
    if (revision !== loadRevision) return;
    const lastPage = Math.max(1, Math.ceil(data.total / data.pageSize));
    if (page.value > lastPage) {
      page.value = lastPage;
      return;
    }
    list.value = data;
    unread.value = count.count;
  } catch (cause) {
    if (revision === loadRevision) listError.value = errorMessage(cause);
  } finally {
    if (revision === loadRevision) listLoading.value = false;
  }
}

async function loadSettings() {
  settingsLoading.value = true;
  settingsError.value = "";
  try {
    settings.value = await api<Settings>("/notifications/settings");
  } catch (cause) {
    settingsError.value = errorMessage(cause);
  } finally {
    settingsLoading.value = false;
  }
}

async function save() {
  saving.value = true;
  settingsError.value = "";
  feedback.value = "";
  try {
    settings.value = await api<Settings>("/notifications/settings", json("PUT", settings.value));
    feedback.value = "提醒设置已保存。";
  } catch (cause) {
    settingsError.value = errorMessage(cause);
  } finally {
    saving.value = false;
  }
}

async function mark(id?: number) {
  busy.value = true;
  actionError.value = "";
  try {
    await api(`/notifications/${id === undefined ? "read-all" : `${id}/read`}`, json("PUT"));
    await loadMessages();
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

watch([page, unreadOnly], loadMessages, { immediate: true });
onMounted(loadSettings);
</script>

<template>
  <section class="panel" aria-labelledby="notifications-title">
    <div class="section-heading">
      <h2 id="notifications-title">消息 <span class="muted small">{{ unread }} 条未读</span></h2>
      <div class="actions">
        <label class="checkbox-label">
          <input v-model="unreadOnly" type="checkbox" @change="page = 1" />只看未读
        </label>
        <button class="secondary" :disabled="busy || listLoading || !unread" @click="mark()">全部已读</button>
        <button class="secondary" :disabled="busy || listLoading" @click="loadMessages">刷新</button>
      </div>
    </div>
    <p v-if="actionError" class="feedback error" role="alert">{{ actionError }}</p>
    <StateNotice :loading="listLoading" :error="listError" :empty="!list?.items.length"
      title="暂无消息" description="开启对应提醒后，消息会显示在这里。" @retry="loadMessages" />
    <ul v-if="list && !listLoading && !listError && list.items.length" class="notification-list">
      <li v-for="message in list.items" :key="message.id">
        <div class="section-heading">
          <h3>{{ message.title }}</h3>
          <span class="tag">{{ message.readAt ? "已读" : "未读" }}</span>
        </div>
        <p class="muted small">{{ labels[message.kind] }} · {{ message.createdAt.replace("T", " ") }}</p>
        <p>{{ message.body }}</p>
        <p v-if="message.dataCutoff" class="muted small">数据截至 {{ message.dataCutoff.replace("T", " ") }}</p>
        <div class="actions">
          <RouterLink :to="message.target" class="button secondary">查看相关内容</RouterLink>
          <button v-if="!message.readAt" class="secondary" :disabled="busy" @click="mark(message.id)">标为已读</button>
        </div>
      </li>
    </ul>
    <div v-if="list && !listLoading && !listError && list.total > list.pageSize" class="actions">
      <button class="secondary" :disabled="page === 1" @click="page--">上一页</button>
      <span>第 {{ page }} / {{ Math.ceil(list.total / list.pageSize) }} 页</span>
      <button class="secondary" :disabled="page * list.pageSize >= list.total" @click="page++">下一页</button>
    </div>
  </section>

  <section class="panel" aria-labelledby="notification-settings-title">
    <h2 id="notification-settings-title">提醒设置</h2>
    <StateNotice v-if="!settings" :loading="settingsLoading" :error="settingsError" @retry="loadSettings" />
    <form v-if="settings" @submit.prevent="save">
      <div class="form-grid">
        <label class="checkbox-label"><input v-model="settings.checkinEnabled" type="checkbox" />打卡提醒</label>
        <label v-if="settings.checkinEnabled">提醒频率
          <select v-model="settings.checkinFrequency">
            <option value="PER_TASK">按任务时间</option>
            <option value="DAILY">每天汇总一次</option>
          </select>
        </label>
        <label v-if="settings.checkinEnabled && settings.checkinFrequency === 'DAILY'">每日提醒时间
          <input v-model="settings.dailyTime" type="time" required step="1" />
        </label>
        <label class="checkbox-label"><input v-model="settings.planDueEnabled" type="checkbox" />计划到期</label>
        <label class="checkbox-label"><input v-model="settings.reportEnabled" type="checkbox" />报告生成</label>
        <label class="checkbox-label"><input v-model="settings.dataChangeEnabled" type="checkbox" />体重记录变化</label>
        <label class="checkbox-label"><input v-model="settings.importFailedEnabled" type="checkbox" />导入失败</label>
      </div>
      <p class="muted small">定时提醒按个人档案的时区计算。关闭后，历史消息仍可查看。</p>
      <p v-if="settingsError" class="feedback error" role="alert">{{ settingsError }}</p>
      <p v-if="feedback" class="feedback success" role="status">{{ feedback }}</p>
      <button :disabled="saving">{{ saving ? "正在保存…" : "保存提醒设置" }}</button>
    </form>
  </section>
</template>

<style scoped>
.notification-list { list-style: none; padding: 0; }
.notification-list li { padding-block: 1rem; border-top: 1px solid var(--line); }
.notification-list .section-heading { margin-bottom: 0; }
.notification-list p { overflow-wrap: anywhere; }
</style>
