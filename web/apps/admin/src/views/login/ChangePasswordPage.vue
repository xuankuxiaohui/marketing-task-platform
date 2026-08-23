<script setup lang="ts">
import { LockOutlined } from "@ant-design/icons-vue";
import { ref } from "vue";
import { useRouter } from "vue-router";
import { isFail, isOk } from "@mkt/shared";
import { changePassword } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE } from "@/router/dynamic";
import { ensureDynamicRoutes } from "@/router/session";
import { useSessionStore } from "@/store/session";
import LoginShell from "./LoginShell.vue";

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
  <LoginShell>
    <a-form layout="vertical" class="login-form" @submit.prevent="submit">
      <header class="login-form__head">
        <h2>{{ zhCN.password.title }}</h2>
        <p>{{ zhCN.password.hint }}</p>
      </header>
      <a-form-item :label="zhCN.password.oldPassword">
        <div data-testid="change-password-old">
          <a-input-password
            v-model:value="oldPassword"
            size="large"
            autocomplete="current-password"
            :placeholder="zhCN.password.oldPassword"
            required
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </div>
      </a-form-item>
      <a-form-item :label="zhCN.password.newPassword">
        <div data-testid="change-password-new">
          <a-input-password
            v-model:value="newPassword"
            size="large"
            autocomplete="new-password"
            :placeholder="zhCN.password.newPassword"
            required
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </div>
      </a-form-item>
      <p class="login-form__policy">{{ zhCN.common.passwordPolicy }}</p>
      <a-alert
        v-if="errorMessage"
        type="error"
        show-icon
        class="login-form__error"
        data-testid="change-password-error"
        role="alert"
        :message="errorMessage"
      />
      <a-form-item class="login-form__actions">
        <a-button
          block
          type="primary"
          size="large"
          html-type="submit"
          data-testid="change-password-submit"
          :loading="loading"
          :disabled="loading"
        >
          {{ zhCN.password.submit }}
        </a-button>
      </a-form-item>
    </a-form>
  </LoginShell>
</template>

<style scoped>
.login-form__head {
  margin-bottom: 32px;
}
.login-form__head h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--admin-ink);
}
.login-form__head p {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--admin-muted);
}
.login-form :deep(.ant-form-item) {
  margin-bottom: 24px;
}
.login-form :deep(.ant-input-prefix),
.login-form :deep(.ant-input-affix-wrapper .anticon) {
  color: rgba(0, 0, 0, 0.25);
}
.login-form :deep(.ant-input-affix-wrapper),
.login-form :deep(.ant-input) {
  width: 100%;
}
.login-form__policy {
  margin: -8px 0 24px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--admin-muted);
}
.login-form__error {
  margin: 0 0 24px;
}
.login-form__actions {
  margin-bottom: 0 !important;
  padding-top: 8px;
}
</style>
