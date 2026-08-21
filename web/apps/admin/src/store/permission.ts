import { defineStore } from "pinia";
import { ref } from "vue";
import type { RouteRecordRaw } from "vue-router";
import type { AdminMenuNode } from "@/api/auth";
import { menusToRoutes, sidebarMenus } from "@/router/dynamic";

export const usePermissionStore = defineStore("permission", () => {
  const menus = ref<AdminMenuNode[]>([]);
  const routes = ref<RouteRecordRaw[]>([]);
  const ready = ref(false);

  function setMenus(next: AdminMenuNode[]): RouteRecordRaw[] {
    menus.value = sidebarMenus(next);
    routes.value = menusToRoutes(next);
    ready.value = true;
    return routes.value;
  }

  function reset(): void {
    menus.value = [];
    routes.value = [];
    ready.value = false;
  }

  return { menus, routes, ready, setMenus, reset };
});
