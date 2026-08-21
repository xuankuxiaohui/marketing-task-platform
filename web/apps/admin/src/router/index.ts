import { createRouter, createWebHistory } from "vue-router";
import { setUnauthorizedHandler } from "@/api/http";
import { zhCN } from "@/locales/zh-CN";
import { ADMIN_ROOT_NAME, DASHBOARD_ROUTE, LOGIN_ROUTE } from "@/router/dynamic";
import { resolveAuthNavigation } from "@/router/guards";
import { ensureDynamicRoutes, resetClientSession } from "@/router/session";
import { usePermissionStore } from "@/store/permission";
import { useSessionStore } from "@/store/session";
import { useTagsStore } from "@/store/tags";

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
      path: "/change-password",
      name: "ChangePasswordPage",
      component: () => import("@/views/login/ChangePasswordPage.vue"),
      meta: { title: zhCN.password.title },
    },
    {
      path: "/",
      name: ADMIN_ROOT_NAME,
      component: () => import("@/layout/AdminLayout.vue"),
      redirect: DASHBOARD_ROUTE,
      children: [],
    },
  ],
});

router.beforeEach(async (to) => {
  const permission = usePermissionStore();
  const session = useSessionStore();
  const decision = await resolveAuthNavigation(
    { path: to.path, fullPath: to.fullPath },
    {
      routesReady: permission.ready,
      sessionKnown: session.authenticated,
      mustChangePassword: session.mustChangePassword,
      ensureSession: () => ensureDynamicRoutes(router),
    },
  );
  if (decision.type === "redirect") {
    return { path: decision.path, query: decision.query };
  }
  if (decision.type === "replace") {
    return { path: decision.path, query: to.query, hash: to.hash, replace: true };
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta.title === "string" ? to.meta.title : zhCN.appTitle;
  document.title = `${title} · ${zhCN.appTitle}`;
  if (to.path !== LOGIN_ROUTE) {
    useTagsStore().push({ path: to.path, title });
  }
});

setUnauthorizedHandler(() => {
  resetClientSession(router);
  if (router.currentRoute.value.path !== LOGIN_ROUTE) {
    void router.replace({
      path: LOGIN_ROUTE,
      query: { redirect: router.currentRoute.value.fullPath },
    });
  }
});

export default router;
