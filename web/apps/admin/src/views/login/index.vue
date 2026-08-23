<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { isFail, isOk } from "@mkt/shared";
import { CAPTCHA_ERROR_CODES, fetchCaptcha, login } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE } from "@/router/dynamic";
import { ensureDynamicRoutes } from "@/router/session";
import { useSessionStore } from "@/store/session";

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
  <main class="login-page">
    <aside class="login-brand">
      <h1>{{ zhCN.appTitle }}</h1>
      <p class="login-brand__sub">{{ zhCN.consoleSubtitle }}</p>
      <p class="login-brand__hint">{{ zhCN.login.brandHint }}</p>
    </aside>
    <section class="login-panel">
      <a-form layout="vertical" class="login-card" @submit.prevent="submit">
        <h2>{{ zhCN.login.title }}</h2>
        <a-form-item :label="zhCN.login.username">
          <div data-testid="login-username">
            <a-input v-model:value="username" size="large" name="username" autocomplete="username" required />
          </div>
        </a-form-item>
        <a-form-item :label="zhCN.login.password">
          <div data-testid="login-password">
            <a-input-password v-model:value="password" size="large" name="password" autocomplete="current-password" required />
          </div>
        </a-form-item>
        <div class="login-captcha">
          <a-form-item :label="zhCN.login.captcha" class="login-field--grow">
            <div data-testid="login-captcha">
              <a-input v-model:value="captchaCode" size="large" name="captchaCode" autocomplete="off" required />
            </div>
          </a-form-item>
          <button
            class="login-captcha__image"
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
        <a-alert
          v-if="errorMessage"
          type="error"
          show-icon
          class="login-error"
          data-testid="login-error"
          role="alert"
          :message="errorMessage"
        />
        <a-button class="login-submit" type="primary" size="large" html-type="submit" data-testid="login-submit" :loading="loading" :disabled="loading">
          {{ zhCN.login.submit }}
        </a-button>
      </a-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
}
.login-brand {
  width: 44%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 40px;
  background: var(--admin-aside);
  color: #fff;
}
.login-brand h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
}
.login-brand__sub {
  margin: 12px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
}
.login-brand__hint {
  margin: 24px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}
.login-panel {
  width: 56%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--admin-page-bg);
}
.login-card {
  width: 400px;
  max-width: 100%;
  padding: 28px 28px 24px;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
  background: var(--admin-surface);
  box-shadow: var(--admin-card-shadow);
}
.login-card h2 {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: var(--admin-ink);
}
.login-card :deep(.ant-input-affix-wrapper),
.login-card :deep(.ant-input) {
  width: 100%;
}
.login-captcha {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.login-field--grow {
  flex: 1;
  margin-bottom: 0;
}
.login-captcha__image {
  width: 120px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: var(--admin-surface);
  cursor: pointer;
}
.login-captcha__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.login-error {
  margin: 0 0 12px;
}
.login-submit {
  width: 100%;
}
</style>
