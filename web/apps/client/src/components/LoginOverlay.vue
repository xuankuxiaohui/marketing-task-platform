<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Popup } from "vant";
import LoginForm from "@/components/LoginForm.vue";
import { zhCN } from "@/locales/zh-CN";
import { HOME_ROUTE, PASSWORD_ROUTE, safeRedirect } from "@/router/guards";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";

defineOptions({ name: "LoginOverlay" });

const OVERLAY_HEIGHT = "62vh";

const route = useRoute();
const router = useRouter();
const overlay = useLoginOverlayStore();
const session = useSessionStore();

const open = computed({
  get: () => overlay.visible,
  set: (value: boolean) => {
    if (!value) {
      overlay.close();
    }
  },
});

async function onSuccess(): Promise<void> {
  overlay.close();
  if (session.mustChangePassword) {
    overlay.consumeResume();
    overlay.consumeRedirect();
    await router.replace(PASSWORD_ROUTE);
    return;
  }
  const resume = overlay.consumeResume();
  const redirect = overlay.consumeRedirect();
  if (resume) {
    resume();
    return;
  }
  if (redirect && redirect !== route.fullPath) {
    await router.replace(safeRedirect(redirect, HOME_ROUTE));
  }
}
</script>

<template>
  <Popup
    v-model:show="open"
    position="bottom"
    round
    closeable
    :style="{ height: OVERLAY_HEIGHT }"
    data-testid="login-overlay"
  >
    <section class="login-overlay">
      <h2 class="login-overlay__title">{{ zhCN.login.sheetTitle }}</h2>
      <p v-if="overlay.message" class="login-overlay__hint" data-testid="login-overlay-reason">
        {{ overlay.message }}
      </p>
      <LoginForm register-to="/register" @success="onSuccess" />
    </section>
  </Popup>
</template>

<style scoped>
.login-overlay {
  height: 100%;
  overflow: auto;
  padding: 12px 8px 24px;
  background: var(--portal-bg);
}
.login-overlay__title {
  margin: 4px 16px 8px;
  font-size: 18px;
}
.login-overlay__hint {
  margin: 0 16px 8px;
  color: var(--portal-accent);
  font-size: 13px;
}
</style>
