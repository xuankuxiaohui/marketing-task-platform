<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import LoginForm from "@/components/LoginForm.vue";
import { zhCN } from "@/locales/zh-CN";
import { PASSWORD_ROUTE } from "@/router/guards";
import { redirectAfterAuth } from "@/router/session";
import { useSessionStore } from "@/store/session";

defineOptions({ name: "LoginPage" });

const router = useRouter();
const route = useRoute();

async function onSuccess(): Promise<void> {
  const session = useSessionStore();
  if (session.mustChangePassword) {
    await router.replace(PASSWORD_ROUTE);
    return;
  }
  await redirectAfterAuth(router, route.query.redirect);
}
</script>

<template>
  <main class="auth-page">
    <h1>{{ zhCN.appTitle }}</h1>
    <p class="auth-page__sub">{{ zhCN.login.title }}</p>
    <LoginForm register-to="/register" @success="onSuccess" />
  </main>
</template>
