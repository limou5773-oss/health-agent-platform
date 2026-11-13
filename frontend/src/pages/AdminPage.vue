<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Plus, X } from "@lucide/vue";
import { api, errorMessage, json, kindText, session, statusText } from "../api";
import type { AdminTask, AdminUser, Article } from "../types";
import StateNotice from "../components/StateNotice.vue";
import AdminImports from "../components/AdminImports.vue";
import AdminPlanTemplates from "../components/AdminPlanTemplates.vue";
import AdminSystemSettings from "../components/AdminSystemSettings.vue";
import AdminRuntimeStats from "../components/AdminRuntimeStats.vue";
import AdminTaskAudit from "../components/AdminTaskAudit.vue";
const tab = ref("users"),
  users = ref<AdminUser[]>([]),
  tasks = ref<AdminTask[]>([]),
  articles = ref<Article[]>([]);
const auditId = ref<string | null>(null);
const loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  message = ref(""),
  busy = ref(false),
  editor = ref(false),
  editingId = ref<number | null>(null);
const form = reactive({
  title: "",
  content: "",
  sourceName: "",
  sourceUrl: "",
  published: false,
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [u, t, a] = await Promise.all([
      api<AdminUser[]>("/admin/users"),
      api<AdminTask[]>("/admin/tasks"),
      api<Article[]>("/admin/knowledge"),
    ]);
    users.value = u;
    tasks.value = t;
    articles.value = a;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function toggle(user: AdminUser) {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(
      `/admin/users/${user.id}/status`,
      json("PATCH", { enabled: !user.enabled }),
    );
    user.enabled = !user.enabled;
    message.value = `账号 ${user.username} 已${user.enabled ? "启用" : "停用"}。`;
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
function edit(article?: Article) {
  editingId.value = article?.id || null;
  Object.assign(
    form,
    article
      ? {
          title: article.title,
          content: article.content,
          sourceName: article.sourceName,
          sourceUrl: article.sourceUrl,
          published: article.published,
        }
      : {
          title: "",
          content: "",
          sourceName: "",
          sourceUrl: "",
          published: false,
        },
  );
  editor.value = true;
  actionError.value = "";
  message.value = "";
}
async function save() {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    await api(
      editingId.value
        ? `/admin/knowledge/${editingId.value}`
        : "/admin/knowledge",
      json(editingId.value ? "PUT" : "POST", form),
    );
    editor.value = false;
    message.value = "知识资料已保存。";
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
  <div class="tabs" role="group" aria-label="后台模块">
    <button
      :class="{ active: tab === 'users' }"
      :aria-pressed="tab === 'users'"
      @click="tab = 'users'"
    >
      账号管理</button
    ><button
      :class="{ active: tab === 'knowledge' }"
      :aria-pressed="tab === 'knowledge'"
      @click="tab = 'knowledge'"
    >
      知识资料</button
    ><button
      :class="{ active: tab === 'tasks' }"
      :aria-pressed="tab === 'tasks'"
      @click="tab = 'tasks'"
    >
      任务运行
    </button>
    <button :class="{ active: tab === 'imports' }" :aria-pressed="tab === 'imports'" @click="tab = 'imports'">
      导入管理
    </button>
    <button :class="{ active: tab === 'templates' }" :aria-pressed="tab === 'templates'" @click="tab = 'templates'">
      计划模板
    </button>
    <button :class="{ active: tab === 'system' }" :aria-pressed="tab === 'system'" @click="tab = 'system'">
      系统配置
    </button>
    <button :class="{ active: tab === 'runtime' }" :aria-pressed="tab === 'runtime'" @click="tab = 'runtime'">
      运行统计
    </button>
  </div>
  <AdminImports v-if="tab === 'imports'" />
  <AdminPlanTemplates v-if="tab === 'templates'" />
  <AdminSystemSettings v-if="tab === 'system'" />
  <AdminRuntimeStats v-if="tab === 'runtime'" />
  <p v-if="actionError" class="feedback error" role="alert">
    {{ actionError }}
  </p>
  <p v-if="message" class="feedback success" role="status">{{ message }}</p>
  <StateNotice :loading="loading" :error="error" @retry="load" /><template
    v-if="!loading && !error"
    ><section v-if="tab === 'users'" class="panel">
      <div class="section-heading">
        <h2>平台账号</h2>
        <span class="small muted">{{ users.length }} 个账号</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户名</th>
              <th>昵称</th>
              <th>角色</th>
              <th>状态</th>
              <th>创建日期</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.username }}</td>
              <td>{{ user.nickname }}</td>
              <td>{{ user.role === "ADMIN" ? "管理员" : "普通用户" }}</td>
              <td>
                <span class="tag" :class="{ danger: !user.enabled }">{{
                  user.enabled ? "已启用" : "已停用"
                }}</span>
              </td>
              <td>{{ user.createdAt.slice(0, 10) }}</td>
              <td>
                <span v-if="user.id === session.user?.id" class="small muted"
                  >当前账号</span
                ><button
                  v-else
                  class="text-button"
                  :disabled="busy"
                  @click="toggle(user)"
                >
                  {{ user.enabled ? "停用账号" : "启用账号" }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section v-if="tab === 'knowledge'" class="panel">
      <div class="section-heading">
        <div>
          <h2>知识资料管理</h2>
          <p>发布后对用户与健康助手可见，请准确标注资料来源。</p>
        </div>
        <button @click="edit()"><Plus :size="16" />新增资料</button>
      </div>
      <section v-if="editor" class="editor-panel">
        <div class="section-heading">
          <h3>{{ editingId ? "编辑资料" : "新增资料" }}</h3>
          <button
            class="icon-button"
            aria-label="关闭编辑"
            @click="editor = false"
          >
            <X :size="19" />
          </button>
        </div>
        <form @submit.prevent="save">
          <div class="form-grid">
            <label class="span-all"
              >标题<input
                v-model="form.title"
                required
                maxlength="200" /></label
            ><label
              >来源名称<input
                v-model="form.sourceName"
                required
                maxlength="200" /></label
            ><label
              >来源网页（可选）<input
                v-model="form.sourceUrl"
                type="url"
                maxlength="1000"
                placeholder="https://" /></label
            ><label class="span-all"
              >正文<textarea
                v-model="form.content"
                required
                rows="10"
                maxlength="50000"
              />
            </label>
          </div>
          <label class="checkbox-label"
            ><input v-model="form.published" type="checkbox" />发布此资料</label
          ><button :disabled="busy">保存资料</button>
        </form>
      </section>
      <StateNotice
        :empty="!articles.length"
        title="还没有知识资料"
        description="添加平台规则或有明确来源的资料。"
      />
      <div v-if="articles.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>标题</th>
              <th>来源</th>
              <th>发布状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="article in articles" :key="article.id">
              <td>{{ article.title }}</td>
              <td>{{ article.sourceName }}</td>
              <td>
                <span class="tag">{{
                  article.published ? "已发布" : "草稿"
                }}</span>
              </td>
              <td>{{ article.updatedAt.slice(0, 10) }}</td>
              <td>
                <button class="text-button" @click="edit(article)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section v-if="tab === 'tasks'" class="panel">
      <div class="section-heading">
        <div>
          <h2>任务运行情况</h2>
          <p>仅展示运行元数据与失败摘要，不展示个人健康明细。</p>
        </div>
        <button class="secondary" @click="load">刷新</button>
      </div>
      <StateNotice
        :empty="!tasks.length"
        title="暂无任务"
        description="用户发起任务后将在这里展示运行信息。"
      />
      <div v-if="tasks.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>类型</th>
              <th>状态</th>
              <th>分析日期</th>
              <th>耗时</th>
              <th>失败摘要</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in tasks" :key="task.id">
              <td>#{{ task.id }}</td>
              <td>{{ kindText[task.kind] }}</td>
              <td>
                <span
                  class="tag"
                  :class="{ danger: task.status === 'FAILED' }"
                  >{{ statusText[task.status] }}</span
                >
              </td>
              <td>{{ task.startDate }} 至 {{ task.endDate }}</td>
              <td>
                {{
                  task.durationMs == null
                    ? "—"
                    : `${(task.durationMs / 1000).toFixed(1)} 秒`
                }}
              </td>
              <td class="note-cell">{{ task.error || "—" }}</td>
              <td><button class="secondary" @click="auditId = task.id">查看</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <AdminTaskAudit v-if="tab === 'tasks' && auditId" :key="auditId" :id="auditId" @close="auditId = null" />
    </template
  >
</template>
