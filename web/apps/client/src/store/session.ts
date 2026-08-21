import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { PortalAuthData, PortalProfileData } from "@/api/auth";
import { readPortalToken, writePortalToken } from "@/utils/token";

export const useSessionStore = defineStore("session", () => {
  const token = ref(readPortalToken());
  const userId = ref<number | null>(null);
  const username = ref("");
  const nickname = ref("");
  const province = ref("");
  const userLevel = ref("");
  const userRole = ref("");
  const tags = ref<string[]>([]);
  const pointsBalance = ref(0);
  const mustChangePassword = ref(false);

  const authenticated = computed(() => Boolean(token.value));

  function persistToken(next: string): void {
    token.value = next;
    writePortalToken(next);
  }

  function setLogin(data: PortalAuthData): void {
    persistToken(data.token ?? "");
    userId.value = data.userId != null ? Number(data.userId) : null;
    nickname.value = data.nickname ?? "";
    mustChangePassword.value = Boolean(data.mustChangePassword);
  }

  function setProfile(data: PortalProfileData): void {
    userId.value = data.userId != null ? Number(data.userId) : userId.value;
    username.value = data.username ?? "";
    nickname.value = data.nickname ?? "";
    province.value = data.province ?? "";
    userLevel.value = data.userLevel ?? "";
    userRole.value = data.userRole ?? "";
    tags.value = [...(data.tags ?? [])];
    pointsBalance.value = Number(data.pointsBalance ?? 0);
    if (data.mustChangePassword != null) {
      mustChangePassword.value = Boolean(data.mustChangePassword);
    }
  }

  function setMustChangePassword(value: boolean): void {
    mustChangePassword.value = value;
  }

  function setPointsBalance(balance: number): void {
    pointsBalance.value = Number(balance);
  }

  function clear(): void {
    persistToken("");
    userId.value = null;
    username.value = "";
    nickname.value = "";
    province.value = "";
    userLevel.value = "";
    userRole.value = "";
    tags.value = [];
    pointsBalance.value = 0;
    mustChangePassword.value = false;
  }

  return {
    token,
    userId,
    username,
    nickname,
    province,
    userLevel,
    userRole,
    tags,
    pointsBalance,
    mustChangePassword,
    authenticated,
    setLogin,
    setProfile,
    setMustChangePassword,
    setPointsBalance,
    clear,
  };
});
