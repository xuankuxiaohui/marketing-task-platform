<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Checkbox, Field } from "vant";
import { isFail, isOk } from "@mkt/shared";
import { CAPTCHA_ERROR_CODES, fetchCaptcha, register, usernameAvailable } from "@/api/auth";
import CaptchaField from "@/components/CaptchaField.vue";
import { zhCN } from "@/locales/zh-CN";
import { HOME_ROUTE } from "@/router/guards";
import { useSessionStore } from "@/store/session";
import { portalPasswordSatisfied } from "@/utils/password";
import { portalUsernameSatisfied } from "@/utils/username";

defineOptions({ name: "RegisterPage" });

const router = useRouter();
const username = ref("");
const password = ref("");
const showPassword = ref(false);
const captchaCode = ref("");
const captchaId = ref("");
const captchaImage = ref("");
const agreed = ref(false);
const errorMessage = ref("");
const usernameHint = ref("");
const loading = ref(false);

const passwordHint = computed(() => {
  if (!password.value) {
    return zhCN.register.passwordHint;
  }
  return portalPasswordSatisfied(password.value) ? zhCN.register.passwordOk : zhCN.register.passwordHint;
});

const canSubmit = computed(() => agreed.value && !loading.value);

async function refreshCaptcha(): Promise<void> {
  const result = await fetchCaptcha();
  if (isOk(result) && result.data) {
    captchaId.value = result.data.captchaId ?? "";
    captchaImage.value = result.data.imageBase64 ?? "";
  }
}

async function onUsernameBlur(): Promise<void> {
  const value = username.value.trim().toLowerCase();
  username.value = value;
  if (!value) {
    usernameHint.value = "";
    return;
  }
  if (!portalUsernameSatisfied(value)) {
    usernameHint.value = zhCN.register.usernameFormat;
    return;
  }
  const result = await usernameAvailable(value);
  if (isOk(result) && result.data) {
    if (result.data.available) {
      usernameHint.value = zhCN.register.usernameAvailable;
      return;
    }
    usernameHint.value =
      result.data.reason === "duplicate" ? zhCN.register.usernameDuplicate : zhCN.register.usernameFormat;
    return;
  }
  if (isFail(result)) {
    usernameHint.value = result.message;
  }
}

async function submit(): Promise<void> {
  errorMessage.value = "";
  if (!agreed.value) {
    errorMessage.value = zhCN.register.agreementRequired;
    return;
  }
  loading.value = true;
  try {
    const result = await register({
      username: username.value,
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value,
    });
    if (isOk(result) && result.data) {
      useSessionStore().setLogin(result.data);
      await router.replace(HOME_ROUTE);
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
    <p class="auth-page__sub">{{ zhCN.register.title }}</p>
    <form @submit.prevent="submit">
      <Field
        v-model="username"
        :label="zhCN.register.username"
        name="username"
        autocomplete="username"
        :placeholder="zhCN.register.usernameHint"
        data-testid="register-username"
        @blur="onUsernameBlur"
      />
      <p v-if="usernameHint" class="auth-hint" data-testid="register-username-hint">{{ usernameHint }}</p>
      <Field
        v-model="password"
        :label="zhCN.register.password"
        name="password"
        :type="showPassword ? 'text' : 'password'"
        :right-icon="showPassword ? 'eye-o' : 'closed-eye'"
        autocomplete="new-password"
        data-testid="register-password"
        @click-right-icon="showPassword = !showPassword"
      />
      <p class="auth-hint" data-testid="register-password-hint">{{ passwordHint }}</p>
      <CaptchaField v-model="captchaCode" :image="captchaImage" @refresh="refreshCaptcha" />
      <div class="auth-agree">
        <Checkbox v-model="agreed" shape="square" data-testid="register-agree">{{ zhCN.register.agreement }}</Checkbox>
      </div>
      <p v-if="errorMessage" class="auth-error" data-testid="register-error" role="alert">{{ errorMessage }}</p>
      <div class="auth-actions">
        <Button
          type="primary"
          native-type="submit"
          block
          :loading="loading"
          :disabled="!canSubmit"
          data-testid="register-submit"
        >
          {{ zhCN.register.submit }}
        </Button>
      </div>
    </form>
    <router-link class="auth-link" to="/login" data-testid="to-login">{{ zhCN.register.toLogin }}</router-link>
  </main>
</template>
