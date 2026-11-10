<script setup lang="ts">
import { ref } from "vue";
import { Activity, ArrowRight } from "@lucide/vue";
import { api, errorMessage, json, session } from "../api";
import type { User } from "../types";
const register = ref(false);
const username = ref(""),
  password = ref(""),
  nickname = ref("");
const busy = ref(false),
  error = ref(""),
  message = ref("");
async function submit() {
  error.value = "";
  message.value = "";
  busy.value = true;
  try {
    if (register.value) {
      await api(
        "/auth/register",
        json("POST", {
          username: username.value,
          password: password.value,
          nickname: nickname.value,
        }),
      );
      register.value = false;
      password.value = "";
      message.value = "账号已创建，请登录。";
    } else {
      const result = await api<{
        accessToken: string;
        expiresAt: string;
        user: User;
      }>(
        "/auth/login",
        json("POST", { username: username.value, password: password.value }),
      );
      sessionStorage.setItem("health-token", result.accessToken);
      session.user = result.user;
      session.token = result.accessToken;
      session.message = "";
    }
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="auth-layout">
    <section class="auth-story">
      <div class="brand"><Activity :size="30" /><span>眠序</span></div>
      <div>
        <p class="eyebrow">为每一晚，留一点关注</p>
        <h1>了解睡眠，<br />找到自己的节律。</h1>
        <p>
          记录真实的睡眠，与健康助手一起梳理趋势，<br
            class="desktop-only"
          />把可行的改变留在每天。
        </p>
      </div>
      <p class="auth-footnote">记录 · 了解 · 行动</p>
    </section>
    <section class="auth-form-wrap">
      <form class="auth-form" @submit.prevent="submit">
        <p class="eyebrow">你的健康空间</p>
        <h2>{{ register ? "创建账号" : "欢迎回来" }}</h2>
        <p class="muted">
          {{
            register
              ? "从第一条记录，开始了解自己的睡眠。"
              : "登录后，继续照顾自己的每一晚。"
          }}
        </p>
        <p v-if="session.message" class="feedback" role="status">
          {{ session.message }}
        </p>
        <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
        <p v-if="message" class="feedback success" role="status">
          {{ message }}
        </p>
        <label
          >用户名<input
            v-model="username"
            required
            minlength="3"
            maxlength="32"
            autocomplete="username"
            placeholder="3–32 个字符" /></label
        ><label v-if="register"
          >昵称<input
            v-model="nickname"
            required
            maxlength="40"
            autocomplete="nickname"
            placeholder="希望我们如何称呼你" /></label
        ><label
          >密码<input
            v-model="password"
            type="password"
            required
            :minlength="register ? 8 : 1"
            :autocomplete="register ? 'new-password' : 'current-password'"
            :placeholder="
              register ? '至少 8 位，最多 72 字节' : '输入你的密码'
            " /></label
        ><button class="full" :disabled="busy">
          {{ busy ? "正在处理…" : register ? "创建账号" : "登录"
          }}<ArrowRight :size="17" />
        </button>
        <p class="auth-switch">
          {{ register ? "已经有账号？" : "第一次来到这里？"
          }}<button
            type="button"
            class="text-button"
            @click="
              register = !register;
              error = '';
              message = '';
            "
          >
            {{ register ? "去登录" : "创建账号" }}
          </button>
        </p>
      </form>
    </section>
  </main>
</template>
