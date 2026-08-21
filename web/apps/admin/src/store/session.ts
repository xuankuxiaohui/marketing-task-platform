import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { AdminLoginData, AdminProfileData } from "@/api/auth";

export const useSessionStore = defineStore("session", () => {
  const userId = ref<number | null>(null);
  const username = ref("");
  const nickname = ref("");
  const roles = ref<string[]>([]);
  const permissions = ref<string[]>([]);
  const mustChangePassword = ref(false);
  const csrfToken = ref("");

  const authenticated = computed(() => userId.value != null);

  function setLogin(data: AdminLoginData): void {
    userId.value = Number(data.userId);
    nickname.value = data.nickname ?? "";
    roles.value = [...(data.roles ?? [])];
    permissions.value = [...(data.permissions ?? [])];
    mustChangePassword.value = Boolean(data.mustChangePassword);
    csrfToken.value = data.csrfToken ?? "";
  }

  function setProfile(data: AdminProfileData): void {
    userId.value = Number(data.userId);
    username.value = data.username ?? "";
    nickname.value = data.nickname ?? "";
    roles.value = [...(data.roles ?? [])];
    permissions.value = [...(data.permissions ?? [])];
    if (data.mustChangePassword != null) {
      mustChangePassword.value = Boolean(data.mustChangePassword);
    }
  }

  function setMustChangePassword(value: boolean): void {
    mustChangePassword.value = value;
  }

  function clear(): void {
    userId.value = null;
    username.value = "";
    nickname.value = "";
    roles.value = [];
    permissions.value = [];
    mustChangePassword.value = false;
    csrfToken.value = "";
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code);
  }

  return {
    userId,
    username,
    nickname,
    roles,
    permissions,
    mustChangePassword,
    csrfToken,
    authenticated,
    setLogin,
    setProfile,
    setMustChangePassword,
    clear,
    hasPermission,
  };
});
