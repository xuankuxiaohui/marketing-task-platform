<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { isFail, isOk } from "@mkt/shared";
import { changePassword } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE } from "@/router/dynamic";
import { ensureDynamicRoutes } from "@/router/session";
import { useSessionStore } from "@/store/session";

defineOptions({ name: "ChangePasswordPage" });

const router = useRouter();
const oldPassword = ref("");
const newPassword = ref("");
const errorMessage = ref("");
const loading = ref(false);

async function submit(): Promise<void> {
  errorMessage.value = "";
  loading.value = true;
  try {
    const result = await changePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    });
    if (isOk(result)) {
      useSessionStore().setMustChangePassword(false);
      await ensureDynamicRoutes(router);
      await router.replace(DASHBOARD_ROUTE);
      return;
    }
    if (isFail(result)) {
      errorMessage.value = result.message;
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>{{ zhCN.password.title }}</h1>
      <p class="login-card__sub">{{ zhCN.password.hint }}</p>
      <label class="login-field">
        <span>{{ zhCN.password.oldPassword }}</span>
        <input
          v-model="oldPassword"
          data-testid="change-password-old"
          type="password"
          autocomplete="current-password"
          required
        />
      </label>
      <label class="login-field">
        <span>{{ zhCN.password.newPassword }}</span>
        <input
          v-model="newPassword"
          data-testid="change-password-new"
          type="password"
          autocomplete="new-password"
          required
        />
      </label>
      <p class="login-card__sub">{{ zhCN.common.passwordPolicy }}</p>
      <p v-if="errorMessage" class="login-error" data-testid="change-password-error" role="alert">
        {{ errorMessage }}
      </p>
      <button class="login-submit" type="submit" data-testid="change-password-submit" :disabled="loading">
        {{ zhCN.password.submit }}
      </button>
    </form>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
}
.login-card {
  width: 360px;
  padding: 32px;
  border-radius: 12px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.login-card h1 {
  margin: 0;
  font-size: 22px;
}
.login-card__sub {
  margin: 0 0 8px;
  color: #64748b;
}
.login-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #334155;
}
.login-field input {
  height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 10px;
}
.login-error {
  margin: 0;
  color: #b91c1c;
  font-size: 13px;
}
.login-submit {
  height: 40px;
  border: 0;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  cursor: pointer;
}
.login-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
