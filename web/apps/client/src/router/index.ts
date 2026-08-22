import { showFailToast } from "vant";
import { createRouter, createWebHistory } from "vue-router";
import { setUnauthorizedHandler } from "@/api/http";
import { zhCN } from "@/locales/zh-CN";
import { HOME_ROUTE, LOGIN_ROUTE, resolveAuthNavigation } from "@/router/guards";
import { ensurePortalSession, resetPortalSession } from "@/router/session";
import { useSessionStore } from "@/store/session";
import { sessionMessage } from "@/utils/session-reason";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: LOGIN_ROUTE,
      name: "LoginPage",
      component: () => import("@/views/login/index.vue"),
      meta: { title: zhCN.login.title, public: true },
    },
    {
      path: "/register",
      name: "RegisterPage",
      component: () => import("@/views/register/index.vue"),
      meta: { title: zhCN.register.title, public: true },
    },
    {
      path: "/",
      name: "PortalLayout",
      component: () => import("@/layout/PortalLayout.vue"),
      redirect: HOME_ROUTE,
      children: [
        {
          path: "home",
          name: "HomePage",
          component: () => import("@/views/home/index.vue"),
          meta: { title: zhCN.home.title, tab: "home", public: true },
        },
        {
          path: "mine",
          name: "MinePage",
          component: () => import("@/views/mine/index.vue"),
          meta: { title: zhCN.mine.title, tab: "mine" },
        },
        {
          path: "mine/profile",
          name: "ProfilePage",
          component: () => import("@/views/mine/ProfilePage.vue"),
          meta: { title: zhCN.profile.title },
        },
        {
          path: "mine/password",
          name: "PasswordPage",
          component: () => import("@/views/mine/PasswordPage.vue"),
          meta: { title: zhCN.password.title },
        },
        {
          path: "task/:taskId",
          name: "TaskDetailPage",
          component: () => import("@/views/task/TaskDetailPage.vue"),
          meta: { title: zhCN.home.title },
        },
        {
          path: "tasks",
          redirect: "/mine/tasks",
        },
        {
          path: "prizes",
          redirect: "/mine/prizes",
        },
        {
          path: "mine/tasks",
          name: "MineTasksPage",
          component: () => import("@/views/mine/MineTasksPage.vue"),
          meta: { title: zhCN.mine.tasks, tab: "tasks" },
        },
        {
          path: "mine/prizes",
          name: "MinePrizesPage",
          component: () => import("@/views/mine/MinePrizesPage.vue"),
          meta: { title: zhCN.mine.prizes, tab: "prizes" },
        },
        {
          path: "mine/points",
          name: "MinePointsPage",
          component: () => import("@/views/mine/MinePointsPage.vue"),
          meta: { title: zhCN.mine.pointsDetail },
        },
        {
          path: "signin",
          name: "SigninPage",
          component: () => import("@/views/signin/index.vue"),
          meta: { title: zhCN.signin.title },
        },
        {
          path: "activity",
          name: "ActivityPage",
          component: () => import("@/views/activity/index.vue"),
          meta: { title: zhCN.activity.title, public: true },
        },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const session = useSessionStore();
  const decision = await resolveAuthNavigation(
    { path: to.path, fullPath: to.fullPath },
    {
      sessionKnown: session.authenticated && session.userId != null,
      mustChangePassword: session.mustChangePassword,
      ensureSession: () => ensurePortalSession(),
    },
  );
  if (decision.type === "redirect") {
    return { path: decision.path, query: decision.query };
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta.title === "string" ? to.meta.title : zhCN.appTitle;
  document.title = `${title} · ${zhCN.appTitle}`;
});

setUnauthorizedHandler((payload) => {
  showFailToast(sessionMessage(payload.code, payload.message));
  resetPortalSession();
  if (router.currentRoute.value.path !== LOGIN_ROUTE) {
    void router.replace({
      path: LOGIN_ROUTE,
      query: { redirect: router.currentRoute.value.fullPath },
    });
  }
});

export default router;
