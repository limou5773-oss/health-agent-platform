import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./style.css";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      component: () => import("./pages/DashboardPage.vue"),
      meta: { title: "睡眠概览", caption: "从每一晚的记录，了解自己的节律。" },
    },
    {
      path: "/records",
      component: () => import("./pages/RecordsPage.vue"),
      meta: { title: "睡眠记录", caption: "让每一晚有迹可循。" },
    },
    {
      path: "/activity",
      component: () => import("./pages/ActivityPage.vue"),
      meta: {
        title: "运动管理",
        caption: "记录每一步、每次运动，回顾自己的节奏。",
      },
    },
    {
      path: "/nutrition",
      component: () => import("./pages/NutritionPage.vue"),
      meta: {
        title: "饮食与饮水",
        caption: "记录实际饮食与饮水，保留份量、热量依据和数据缺口。",
      },
    },
    {
      path: "/weight",
      component: () => import("./pages/WeightPage.vue"),
      meta: {
        title: "体重管理",
        caption: "保留每次实际测量，按每日代表值回顾变化与个人目标。",
      },
    },
    {
      path: "/diary",
      component: () => import("./pages/DiaryPage.vue"),
      meta: {
        title: "情绪与日记",
        caption: "用自己的文字记录感受，决定哪些内容可供健康助手参考。",
      },
    },
    {
      path: "/agent",
      component: () => import("./pages/AgentPage.vue"),
      meta: {
        title: "健康助手",
        caption: "设定目标，查看过程，由你确认行动。",
      },
    },
    {
      path: "/plans",
      component: () => import("./pages/PlansPage.vue"),
      meta: { title: "我的计划", caption: "把建议落在每天的小行动里。" },
    },
    {
      path: "/reports",
      component: () => import("./pages/ReportsPage.vue"),
      meta: { title: "睡眠报告", caption: "回顾一段时间，看见习惯的变化。" },
    },
    {
      path: "/knowledge",
      component: () => import("./pages/KnowledgePage.vue"),
      meta: { title: "知识资料", caption: "查看资料来源与平台统计口径。" },
    },
    {
      path: "/profile",
      component: () => import("./pages/ProfilePage.vue"),
      meta: { title: "个人档案", caption: "你的目标、偏好与数据授权。" },
    },
    {
      path: "/admin",
      component: () => import("./pages/AdminPage.vue"),
      meta: {
        title: "管理后台",
        caption: "管理账号、知识资料与任务运行情况。",
        admin: true,
      },
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
createApp(App).use(router).mount("#app");
