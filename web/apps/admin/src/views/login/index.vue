<script setup lang="ts">
import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from "@ant-design/icons-vue";
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { isFail, isOk } from "@mkt/shared";
import { CAPTCHA_ERROR_CODES, fetchCaptcha, login } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE } from "@/router/dynamic";
import { ensureDynamicRoutes } from "@/router/session";
import { useSessionStore } from "@/store/session";
import LoginShell from "./LoginShell.vue";

defineOptions({ name: "LoginPage" });

const router = useRouter();
const route = useRoute();
const username = ref("");
const password = ref("");
const captchaCode = ref("");
const captchaId = ref("");
const captchaImage = ref("");
const errorMessage = ref("");
const loading = ref(false);

async function refreshCaptcha(): Promise<void> {
  const result = await fetchCaptcha();
  if (isOk(result) && result.data) {
    captchaId.value = result.data.captchaId ?? "";
    captchaImage.value = result.data.imageBase64 ?? "";
    return;
  }
  if (isFail(result)) {
    errorMessage.value = result.message;
  }
}

async function submit(): Promise<void> {
  errorMessage.value = "";
  loading.value = true;
  try {
    const result = await login({
      username: username.value,
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value,
    });
    if (isOk(result) && result.data) {
      const session = useSessionStore();
      session.setLogin(result.data);
      await ensureDynamicRoutes(router);
      if (session.mustChangePassword) {
        await router.replace("/change-password");
        return;
      }
      const redirect =
        typeof route.query.redirect === "string" && route.query.redirect.startsWith("/")
          ? route.query.redirect
          : DASHBOARD_ROUTE;
      await router.replace(redirect);
      return;
    }
    if (isFail(result)) {
      errorMessage.value = result.message;
      if (CAPTCHA_ERROR_CODES.has(result.code)) {
        captchaCode.value = "";
        await refreshCaptcha();
      }
    }
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void refreshCaptcha();
});
</script>

<template>
  <LoginShell>
    <a-form layout="vertical" class="login-form" @submit.prevent="submit">
      <header class="login-form__head">
        <h2>{{ zhCN.login.title }}</h2>
        <p>{{ zhCN.login.subtitle }}</p>
      </header>
      <a-form-item>
        <div data-testid="login-username">
          <a-input
            v-model:value="username"
            size="large"
            name="username"
            autocomplete="username"
            :placeholder="zhCN.login.usernamePlaceholder"
            :aria-label="zhCN.login.username"
            required
          >
            <template #prefix>
              <UserOutlined />
            </template>
          </a-input>
        </div>
      </a-form-item>
      <a-form-item>
        <div data-testid="login-password">
          <a-input-password
            v-model:value="password"
            size="large"
            name="password"
            autocomplete="current-password"
            :placeholder="zhCN.login.passwordPlaceholder"
            :aria-label="zhCN.login.password"
            required
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </div>
      </a-form-item>
      <a-form-item class="login-form__captcha-item">
        <div class="login-form__captcha">
          <div data-testid="login-captcha" class="login-form__captcha-field">
            <a-input
              v-model:value="captchaCode"
              size="large"
              name="captchaCode"
              autocomplete="off"
              :placeholder="zhCN.login.captchaPlaceholder"
              :aria-label="zhCN.login.captcha"
              required
            >
              <template #prefix>
                <SafetyCertificateOutlined />
              </template>
            </a-input>
          </div>
          <button
            class="login-form__captcha-image"
            type="button"
            data-testid="login-captcha-refresh"
            :aria-label="zhCN.login.captchaAlt"
            @click="refreshCaptcha"
          >
            <img
              v-if="captchaImage"
              data-testid="login-captcha-image"
              :src="captchaImage"
              :alt="zhCN.login.captchaAlt"
            />
          </button>
        </div>
      </a-form-item>
      <a-alert
        v-if="errorMessage"
        type="error"
        show-icon
        class="login-form__error"
        data-testid="login-error"
        role="alert"
        :message="errorMessage"
      />
      <a-form-item class="login-form__actions">
        <a-button
          block
          type="primary"
          size="large"
          html-type="submit"
          data-testid="login-submit"
          :loading="loading"
          :disabled="loading"
        >
          {{ zhCN.login.submit }}
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
.login-form__captcha {
  display: flex;
  align-items: center;
  gap: 12px;
}
.login-form__captcha-field {
  flex: 1;
  min-width: 0;
}
.login-form__captcha-image {
  flex: 0 0 116px;
  width: 116px;
  height: 40px;
  padding: 0;
  overflow: hidden;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}
.login-form__captcha-image:hover {
  border-color: var(--admin-primary);
}
.login-form__captcha-image:focus-visible {
  outline: 2px solid var(--admin-primary);
  outline-offset: 1px;
}
.login-form__captcha-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.login-form__error {
  margin: 0 0 24px;
}
.login-form__actions {
  margin-bottom: 0 !important;
  padding-top: 8px;
}
</style>
