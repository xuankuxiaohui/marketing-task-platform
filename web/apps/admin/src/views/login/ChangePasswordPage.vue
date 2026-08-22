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
    <el-form class="login-card" label-position="top" @submit.prevent="submit">
      <h1>{{ zhCN.password.title }}</h1>
      <p class="login-card__sub">{{ zhCN.password.hint }}</p>
      <el-form-item :label="zhCN.password.oldPassword">
        <div data-testid="change-password-old">
          <el-input
            v-model="oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
            required
          />
        </div>
      </el-form-item>
      <el-form-item :label="zhCN.password.newPassword">
        <div data-testid="change-password-new">
          <el-input
            v-model="newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            required
          />
        </div>
      </el-form-item>
      <p class="login-card__sub">{{ zhCN.common.passwordPolicy }}</p>
      <p v-if="errorMessage" class="login-error" data-testid="change-password-error" role="alert">
        {{ errorMessage }}
      </p>
      <el-button
        class="login-submit"
        type="primary"
        native-type="submit"
        data-testid="change-password-submit"
        :loading="loading"
        :disabled="loading"
      >
        {{ zhCN.password.submit }}
      </el-button>
    </el-form>
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
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.28);
}
.login-card h1 {
  margin: 0;
  font-size: 22px;
}
.login-card__sub {
  margin: 0 0 8px;
  color: #64748b;
}
.login-error {
  margin: 0 0 12px;
  color: #f56c6c;
  font-size: 13px;
}
.login-submit {
  width: 100%;
}
</style>
