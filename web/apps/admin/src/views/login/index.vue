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
      useSessionStore().setLogin(result.data);
      await ensureDynamicRoutes(router);
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
    <form class="login-card" @submit.prevent="submit">
      <h1>{{ zhCN.appTitle }}</h1>
      <p class="login-card__sub">{{ zhCN.login.title }}</p>
      <label class="login-field">
        <span>{{ zhCN.login.username }}</span>
        <input
          v-model="username"
          data-testid="login-username"
          name="username"
          autocomplete="username"
          required
        />
      </label>
      <label class="login-field">
        <span>{{ zhCN.login.password }}</span>
        <input
          v-model="password"
          data-testid="login-password"
          name="password"
          type="password"
          autocomplete="current-password"
          required
        />
      </label>
      <div class="login-captcha">
        <label class="login-field login-field--grow">
          <span>{{ zhCN.login.captcha }}</span>
          <input
            v-model="captchaCode"
            data-testid="login-captcha"
            name="captchaCode"
            autocomplete="off"
            required
          />
        </label>
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
      <p v-if="errorMessage" class="login-error" data-testid="login-error" role="alert">
        {{ errorMessage }}
      </p>
      <button class="login-submit" type="submit" data-testid="login-submit" :disabled="loading">
        {{ zhCN.login.submit }}
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
.login-captcha {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.login-field--grow {
  flex: 1;
}
.login-captcha__image {
  width: 120px;
  height: 56px;
  padding: 0;
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  cursor: pointer;
}
.login-captcha__image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
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
}
</style>
