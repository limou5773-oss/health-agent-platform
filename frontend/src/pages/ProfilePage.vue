<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ShieldCheck, LockKeyhole } from "@lucide/vue";
import { api, download, errorMessage, json, logout, session } from "../api";
import type { Profile } from "../types";
import StateNotice from "../components/StateNotice.vue";
import PreferenceMemory from "../components/PreferenceMemory.vue";
const form = reactive<
  Omit<Profile, "heightCm" | "weightKg"> & {
    heightCm: number | "" | null;
    weightKg: number | "" | null;
  }
>({
  nickname: "",
  heightCm: null,
  weightKg: null,
  timezone: "",
  goal: "",
  preferences: "",
  aiConsent: false,
  aiSleep: true,
  aiActivity: false,
  aiWeight: false,
  aiHeartRate: false,
  aiDiary: true,
});
const permissionFields = {
  aiSleep: "睡眠",
  aiActivity: "步数与运动",
  aiWeight: "体重",
  aiHeartRate: "心率",
  aiDiary: "日记",
} as const;
const loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  message = ref(""),
  busy = ref(false);
const passwords = reactive({ oldPassword: "", newPassword: "" }),
  passwordBusy = ref(false),
  passwordError = ref(""),
  passwordMessage = ref("");
const exportBusy = ref(false);
const exportError = ref("");
const deleting = ref(false);
const deletionOpen = ref(false);
const deletionPassword = ref("");
const deletionError = ref("");

function cancelDeletion() {
  deletionOpen.value = false;
  deletionPassword.value = "";
  deletionError.value = "";
}

async function deleteAccount() {
  deleting.value = true;
  deletionError.value = "";
  try {
    await api("/profile/account", json("DELETE", { password: deletionPassword.value }), 120_000);
    cancelDeletion();
    logout("账号及个人数据已删除。");
  } catch (cause) {
    deletionError.value = errorMessage(cause);
  } finally {
    deleting.value = false;
  }
}

async function exportData() {
  exportBusy.value = true;
  exportError.value = "";
  try {
    await download("/profile/export", "personal-health-data.json");
  } catch (cause) {
    exportError.value = errorMessage(cause);
  } finally {
    exportBusy.value = false;
  }
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    Object.assign(form, await api<Profile>("/profile"));
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
async function save() {
  busy.value = true;
  actionError.value = "";
  message.value = "";
  try {
    const profile = await api<Profile>(
      "/profile",
      json("PUT", {
        ...form,
        heightCm: form.heightCm === "" ? null : form.heightCm,
        weightKg: form.weightKg === "" ? null : form.weightKg,
      }),
    );
    Object.assign(form, profile);
    if (session.user) session.user.nickname = profile.nickname;
    message.value = "档案与授权设置已保存。";
  } catch (cause) {
    actionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
async function changePassword() {
  passwordBusy.value = true;
  passwordError.value = "";
  passwordMessage.value = "";
  try {
    await api("/auth/password", json("PUT", passwords));
    passwords.oldPassword = "";
    passwords.newPassword = "";
    passwordMessage.value = "密码已更新。";
  } catch (cause) {
    passwordError.value = errorMessage(cause);
  } finally {
    passwordBusy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <StateNotice :loading="loading" :error="error" @retry="load" />
  <div v-if="!loading && !error" class="profile-layout">
    <section class="panel">
      <div class="section-heading">
        <div>
          <h2>我的档案</h2>
          <p>身高和体重可以留空，不会使用默认健康数值。</p>
        </div>
      </div>
      <form @submit.prevent="save">
        <div class="form-grid">
          <label
            >昵称<input
              v-model="form.nickname"
              required
              maxlength="40"
              autocomplete="nickname" /></label
          ><label
            >时区<input
              v-model="form.timezone"
              required
              placeholder="Asia/Shanghai"
            /><span class="field-hint"
              >使用 IANA 时区，如 Asia/Shanghai。</span
            ></label
          ><label
            >身高（厘米，可选）<input
              v-model.number="form.heightCm"
              type="number"
              min="30"
              max="260"
              step="0.1"
              placeholder="未填写" /></label
          ><label
            >体重（千克，可选）<input
              v-model.number="form.weightKg"
              type="number"
              min="1"
              max="500"
              step="0.1"
              placeholder="未填写" /></label
          ><label class="span-all"
            >健康目标<textarea
              v-model="form.goal"
              rows="3"
              maxlength="1000"
              placeholder="你希望改善什么？"
            /></label
          ><label class="span-all"
            >生活偏好与注意事项<textarea
              v-model="form.preferences"
              rows="3"
              maxlength="2000"
              placeholder="例如作息限制、运动偏好等"
            />
          </label>
        </div>
        <div class="consent-setting">
          <ShieldCheck :size="23" />
          <div>
            <h3>AI 分析授权</h3>
            <p>
              开启总授权后，健康助手可向配置的外部模型发送下方允许的数据、必要的档案信息和任务上下文。制定计划时也会发送你主动保存的长期偏好。
            </p>
            <label class="checkbox-label"
              ><input
                v-model="form.aiConsent"
                type="checkbox"
              />允许使用外部模型进行 AI 分析</label
            >
            <fieldset :disabled="!form.aiConsent || busy">
              <legend>允许用于 AI 分析的数据</legend>
              <label v-for="(label, field) in permissionFields" :key="field" class="checkbox-label">
                <input v-model="form[field]" type="checkbox" />{{ label }}
              </label>
            </fieldset>
            <p class="small muted">
              日记还需逐条授权，并在任务中勾选。撤回任一授权并保存后，现有任务无法继续读取或重试。历史结果可查看或删除。
            </p>
          </div>
        </div>
        <p v-if="actionError" class="feedback error" role="alert">
          {{ actionError }}
        </p>
        <p v-if="message" class="feedback success" role="status">
          {{ message }}
        </p>
        <button :disabled="busy">
          {{ busy ? "保存中…" : "保存档案与授权" }}
        </button>
      </form>
    </section>
    <section class="panel password-panel">
      <LockKeyhole :size="23" />
      <h2>账号安全</h2>
      <p class="muted small">定期更新密码，妥善保管登录信息。</p>
      <form @submit.prevent="changePassword">
        <label
          >当前密码<input
            v-model="passwords.oldPassword"
            type="password"
            autocomplete="current-password"
            required /></label
        ><label
          >新密码<input
            v-model="passwords.newPassword"
            type="password"
            autocomplete="new-password"
            required
            minlength="8"
            placeholder="至少 8 位，最多 72 字节"
        /></label>
        <p v-if="passwordError" class="feedback error" role="alert">
          {{ passwordError }}
        </p>
        <p v-if="passwordMessage" class="feedback success" role="status">
          {{ passwordMessage }}
        </p>
        <button class="secondary" :disabled="passwordBusy">
          {{ passwordBusy ? "更新中…" : "更新密码" }}
        </button>
      </form>
    </section>
    <section class="panel">
      <h2>个人数据导出</h2>
      <p class="small muted">下载已保存的档案、健康记录、偏好、任务、计划和报告。文件包含个人健康信息。</p>
      <p v-if="exportError" class="feedback error" role="alert">{{ exportError }}</p>
      <button type="button" class="secondary" :disabled="exportBusy" @click="exportData">
        {{ exportBusy ? "正在导出…" : "下载 JSON" }}
      </button>
    </section>
    <PreferenceMemory />
    <section v-if="session.user?.role === 'USER'" class="panel">
      <h2>删除账号和个人数据</h2>
      <p>将永久删除本人账号、档案、健康记录、日记、导入批次、计划、报告、任务与本地 Agent 检查点。成功后会退出登录。</p>
      <button v-if="!deletionOpen" type="button" class="secondary" @click="deletionOpen = true">继续删除</button>
      <form v-else @submit.prevent="deleteAccount">
        <label>
          输入当前密码确认
          <input v-model="deletionPassword" type="password" autocomplete="current-password" required
            minlength="8" maxlength="72" :disabled="deleting" />
        </label>
        <p v-if="deletionError" class="feedback error" role="alert">{{ deletionError }}</p>
        <div class="actions">
          <button class="secondary danger-text" :disabled="deleting">{{ deleting ? "正在删除…" : "永久删除我的账号" }}</button>
          <button type="button" class="secondary" :disabled="deleting" @click="cancelDeletion">取消</button>
        </div>
      </form>
    </section>
  </div>
</template>
