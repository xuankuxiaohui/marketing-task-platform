<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Field } from "vant";
import { isFail, isOk } from "@mkt/shared";
import { CAPTCHA_ERROR_CODES, fetchCaptcha, login } from "@/api/auth";
import CaptchaField from "@/components/CaptchaField.vue";
import { zhCN } from "@/locales/zh-CN";
import { redirectAfterAuth } from "@/router/session";
import { useSessionStore } from "@/store/session";

defineOptions({ name: "LoginPage" });

const router = useRouter();
const route = useRoute();
const username = ref("");
const password = ref("");
const showPassword = ref(false);
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
      if (session.mustChangePassword) {
        await router.replace("/mine/password");
        return;
      }
      await redirectAfterAuth(router, route.query.redirect);
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
  <main class="auth-page">
    <h1>{{ zhCN.appTitle }}</h1>
    <p class="auth-page__sub">{{ zhCN.login.title }}</p>
    <form @submit.prevent="submit">
      <Field
        v-model="username"
        :label="zhCN.login.username"
        name="username"
        autocomplete="username"
        data-testid="login-username"
      />
      <Field
        v-model="password"
        :label="zhCN.login.password"
        name="password"
        :type="showPassword ? 'text' : 'password'"
        :right-icon="showPassword ? 'eye-o' : 'closed-eye'"
        autocomplete="current-password"
        data-testid="login-password"
        :aria-label="showPassword ? zhCN.login.hidePassword : zhCN.login.showPassword"
        @click-right-icon="showPassword = !showPassword"
      />
      <CaptchaField v-model="captchaCode" :image="captchaImage" @refresh="refreshCaptcha" />
      <p v-if="errorMessage" class="auth-error" data-testid="login-error" role="alert">{{ errorMessage }}</p>
      <div class="auth-actions">
        <Button
          type="primary"
          native-type="submit"
          block
          :loading="loading"
          :disabled="loading"
          data-testid="login-submit"
        >
          {{ zhCN.login.submit }}
        </Button>
      </div>
    </form>
    <router-link class="auth-link" to="/register" data-testid="to-register">{{ zhCN.login.toRegister }}</router-link>
  </main>
</template>

