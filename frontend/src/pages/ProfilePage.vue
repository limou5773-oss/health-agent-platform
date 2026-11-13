<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ShieldCheck, LockKeyhole } from "@lucide/vue";
import { api, errorMessage, json, session } from "../api";
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
});
const loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  message = ref(""),
  busy = ref(false);
const passwords = reactive({ oldPassword: "", newPassword: "" }),
  passwordBusy = ref(false),
  passwordError = ref(""),
  passwordMessage = ref("");
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
              开启后，健康助手可将任务所需的统计、档案偏好及必要上下文发送给配置的外部模型。综合分析可使用所选日期内的睡眠、步数、运动、体重和心率统计。制定计划时也会发送你主动保存的长期偏好。
            </p>
            <label class="checkbox-label"
              ><input
                v-model="form.aiConsent"
                type="checkbox"
              />我同意使用上述数据进行 AI 健康分析</label
            >
            <p class="small muted">
              关闭并保存后，不再允许新任务或继续工具查询。历史结果可在相应页面查看或删除任务。
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
    <PreferenceMemory />
  </div>
</template>
