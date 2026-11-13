<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  Activity,
  Moon,
  List,
  Footprints,
  Utensils,
  Scale,
  NotebookPen,
  CalendarRange,
  Sparkles,
  CalendarCheck,
  FileText,
  BookOpen,
  UserRound,
  Settings,
  LogOut,
  Menu,
  X,
} from "@lucide/vue";
import { api, errorMessage, logout, session } from "./api";
import type { User } from "./types";
import LoginPage from "./pages/LoginPage.vue";
const route = useRoute();
const booting = ref(!!session.token);
const bootstrapError = ref("");
const menuOpen = ref(false);
const links = [
  { to: "/", label: "睡眠概览", icon: Moon },
  { to: "/records", label: "睡眠记录", icon: List },
  { to: "/activity", label: "运动管理", icon: Footprints },
  { to: "/nutrition", label: "饮食与饮水", icon: Utensils },
  { to: "/weight", label: "体重管理", icon: Scale },
  { to: "/diary", label: "情绪与日记", icon: NotebookPen },
  { to: "/timeline", label: "健康时间线", icon: CalendarRange },
  { to: "/agent", label: "健康助手", icon: Sparkles },
  { to: "/plans", label: "我的计划", icon: CalendarCheck },
  { to: "/reports", label: "睡眠报告", icon: FileText },
  { to: "/knowledge", label: "知识资料", icon: BookOpen },
];
async function bootstrap() {
  booting.value = true;
  bootstrapError.value = "";
  try {
    session.user = await api<User>("/auth/me");
  } catch (error) {
    bootstrapError.value = errorMessage(error);
  } finally {
    booting.value = false;
  }
}
watch(
  () => session.token,
  (value) => {
    if (value && !session.user) void bootstrap();
  },
);
watch(
  () => route.path,
  () => {
    menuOpen.value = false;
  },
);
onMounted(() => {
  if (session.token) void bootstrap();
});
function signOut() {
  logout();
  menuOpen.value = false;
}
</script>
<template>
  <LoginPage v-if="!session.token" />
  <div v-else-if="booting || !session.user" class="startup">
    <Activity :size="32" />
    <p v-if="booting">正在恢复你的空间…</p>
    <template v-else
      ><p role="alert">{{ bootstrapError }}</p>
      <button @click="bootstrap">重新连接</button
      ><button class="secondary" @click="signOut">返回登录</button></template
    >
  </div>
  <div v-else class="app-shell">
    <a class="skip-link" href="#main-content">跳转到主要内容</a>
    <div class="mobile-bar">
      <RouterLink to="/" class="brand"
        ><Activity :size="24" /><span>眠序</span></RouterLink
      ><button
        class="icon-button"
        :aria-expanded="menuOpen"
        aria-label="切换导航"
        @click="menuOpen = !menuOpen"
      >
        <X v-if="menuOpen" /><Menu v-else />
      </button>
    </div>
    <aside class="sidebar" :class="{ 'is-open': menuOpen }">
      <RouterLink to="/" class="brand"
        ><span class="brand-symbol"><Activity :size="25" /></span
        ><span>眠序<small>睡眠健康管理</small></span></RouterLink
      >
      <div class="nav-label">我的健康空间</div>
      <nav aria-label="主要导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          :exact-active-class="'active'"
          ><component :is="link.icon" :size="19" />{{ link.label }}</RouterLink
        >
      </nav>
      <div class="sidebar-bottom">
        <RouterLink to="/profile" active-class="active"
          ><UserRound :size="18" />个人档案</RouterLink
        ><RouterLink
          v-if="session.user.role === 'ADMIN'"
          to="/admin"
          active-class="active"
          ><Settings :size="18" />管理后台</RouterLink
        >
        <div class="user-line">
          <span class="avatar">{{ session.user.nickname.slice(0, 1) }}</span
          ><span
            ><strong>{{ session.user.nickname }}</strong
            ><small>{{
              session.user.role === "ADMIN" ? "管理员" : "个人账户"
            }}</small></span
          ><button
            class="icon-button"
            aria-label="退出登录"
            title="退出登录"
            @click="signOut"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </div>
    </aside>
    <main id="main-content" class="main-content" tabindex="-1">
      <header class="page-header">
        <div>
          <p class="eyebrow">SLEEP & WELLBEING</p>
          <h1>{{ route.meta.title }}</h1>
          <p class="muted">{{ route.meta.caption }}</p>
        </div>
        <span class="date-label">{{
          new Date().toLocaleDateString("zh-CN", {
            month: "long",
            day: "numeric",
            weekday: "long",
          })
        }}</span>
      </header>
      <div
        v-if="route.meta.admin && session.user.role !== 'ADMIN'"
        class="notice error"
        role="alert"
      >
        此页面仅对管理员开放。
      </div>
      <RouterView v-else />
    </main>
  </div>
</template>
