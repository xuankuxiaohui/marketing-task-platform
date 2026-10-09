import { defineStore } from "pinia";
import { computed, onScopeDispose, ref } from "vue";
import type { PortalAuthData, PortalProfileData } from "@/api/auth";
import { PORTAL_TOKEN_KEY, readPortalToken, writePortalToken } from "@/utils/token";

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
    const nextToken = data.token ?? "";
    const nextUserId = data.userId != null ? Number(data.userId) : null;
    if (nextToken !== token.value || nextUserId !== userId.value) {
      resetProfile();
    }
    userId.value = nextUserId;
    nickname.value = data.nickname ?? "";
    mustChangePassword.value = Boolean(data.mustChangePassword);
    persistToken(nextToken);
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

  function resetProfile(): void {
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

  function clear(): void {
    resetProfile();
    persistToken("");
  }

  function syncStoredSession(event: StorageEvent): void {
    if (event.key !== PORTAL_TOKEN_KEY && event.key !== null) {
      return;
    }
    if (event.storageArea !== null && event.storageArea !== window.localStorage) {
      return;
    }
    // Read the latest value rather than a potentially superseded queued event.
    const nextToken = readPortalToken();
    if (nextToken !== token.value) {
      resetProfile();
      token.value = nextToken;
    }
  }

  if (typeof window !== "undefined") {
    window.addEventListener("storage", syncStoredSession);
    onScopeDispose(() => window.removeEventListener("storage", syncStoredSession));
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
