<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Field, NavBar, showSuccessToast } from "vant";
import { isFail, isOk } from "@mkt/shared";
import { changePassword } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { portalPasswordSatisfied } from "@/utils/password";

defineOptions({ name: "PasswordPage" });

const router = useRouter();
const oldPassword = ref("");
const newPassword = ref("");
const errorMessage = ref("");
const loading = ref(false);

const newPasswordHint = computed(() => {
  if (!newPassword.value) {
    return zhCN.register.passwordHint;
  }
  return portalPasswordSatisfied(newPassword.value) ? zhCN.register.passwordOk : zhCN.register.passwordHint;
});

async function submit(): Promise<void> {
  errorMessage.value = "";
  if (!portalPasswordSatisfied(newPassword.value)) {
    errorMessage.value = zhCN.register.passwordHint;
    return;
  }
  loading.value = true;
  try {
    const result = await changePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    });
    if (isOk(result)) {
      showSuccessToast(zhCN.password.success);
      await router.replace("/mine");
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
  <section>
    <NavBar :title="zhCN.password.title" left-arrow @click-left="router.back()" />
    <form @submit.prevent="submit">
      <Field
        v-model="oldPassword"
        :label="zhCN.password.oldPassword"
        type="password"
        autocomplete="current-password"
        data-testid="password-old"
      />
      <Field
        v-model="newPassword"
        :label="zhCN.password.newPassword"
        type="password"
        autocomplete="new-password"
        data-testid="password-new"
      />
      <p class="auth-hint" data-testid="password-hint">{{ newPasswordHint }}</p>
      <p v-if="errorMessage" class="auth-error" data-testid="password-error" role="alert">{{ errorMessage }}</p>
      <div class="auth-actions">
        <Button
          type="primary"
          native-type="submit"
          block
          :loading="loading"
          :disabled="loading"
          data-testid="password-submit"
        >
          {{ zhCN.password.submit }}
        </Button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.auth-hint {
  margin: 0 16px 8px;
  color: #646566;
  font-size: 12px;
}
.auth-error {
  margin: 8px 16px 0;
  color: #ee0a24;
  font-size: 13px;
}
.auth-actions {
  padding: 16px;
}
</style>
