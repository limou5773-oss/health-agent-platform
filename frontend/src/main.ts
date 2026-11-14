import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./style.css";

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash };
  },
  routes: [
    {
      path: "/",
      component: () => import("./pages/DashboardPage.vue"),
      meta: { title: "综合健康看板", caption: "按同一日期范围查看各项健康记录。" },
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
      path: "/heart-rate",
      component: () => import("./pages/HeartRatePage.vue"),
      meta: { title: "心率采样", caption: "查看与管理实际测量记录。" },
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
      path: "/timeline",
      component: () => import("./pages/TimelinePage.vue"),
      meta: {
        title: "健康时间线",
        caption: "按日期回顾健康记录、日记与计划动态。",
      },
    },
    {
      path: "/baseline",
      component: () => import("./pages/BaselinePage.vue"),
      meta: { title: "个人基线", caption: "比较近期与自己的历史记录。" },
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
      path: "/agent/triggers",
      component: () => import("./pages/AgentTriggersPage.vue"),
      meta: { title: "主动任务", caption: "设置睡眠复盘和导入后分析，查看触发记录。" },
    },
    {
      path: "/plans",
      component: () => import("./pages/PlansPage.vue"),
      meta: { title: "我的计划", caption: "把建议落在每天的小行动里。" },
    },
    {
      path: "/reports",
      component: () => import("./pages/ReportsPage.vue"),
      meta: { title: "健康报告", caption: "生成综合周报和月报，查看历史报告。" },
    },
    {
      path: "/checkups",
      component: () => import("./pages/CheckupPage.vue"),
      meta: { title: "体检报告整理", caption: "核对并保存报告原文中的指标与参考范围。" },
    },
    {
      path: "/notifications",
      component: () => import("./pages/NotificationsPage.vue"),
      meta: { title: "消息中心", caption: "查看站内消息，设置提醒频率。" },
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
