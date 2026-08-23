import { showFailToast } from "vant";
import { createRouter, createWebHistory } from "vue-router";
import { setUnauthorizedHandler } from "@/api/http";
import { zhCN } from "@/locales/zh-CN";
import { HOME_ROUTE, LOGIN_ROUTE, isPublicPath, resolveAuthNavigation } from "@/router/guards";
import { ensurePortalSession, resetPortalSession } from "@/router/session";
import { handlePortalUnauthorized } from "@/router/unauthorized";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";

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
        {
          path: "activities",
          name: "ActivityHubPage",
          component: () => import("@/views/activity/ActivityHubPage.vue"),
          meta: { title: zhCN.mine.activityHub, public: true },
        },
        {
          path: "mine/prizes/:recordId",
          name: "PrizeDetailPage",
          component: () => import("@/views/mine/PrizeDetailPage.vue"),
          meta: { title: zhCN.prize.detailTitle },
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
  if (!session.authenticated && !isPublicPath(to.path)) {
    useLoginOverlayStore().request({ redirect: to.fullPath });
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta.title === "string" ? to.meta.title : zhCN.appTitle;
  document.title = `${title} · ${zhCN.appTitle}`;
});

setUnauthorizedHandler((payload) => {
  handlePortalUnauthorized(payload, {
    currentPath: router.currentRoute.value.fullPath,
    reset: () => resetPortalSession(),
    toast: (message) => showFailToast(message),
    openOverlay: (opts) => useLoginOverlayStore().request(opts),
  });
});

export default router;
