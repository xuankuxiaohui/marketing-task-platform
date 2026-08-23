<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Cell, CellGroup, Field, NavBar } from "vant";
import { isFail, isOk } from "@mkt/shared";
import { fetchProfile, updateProfile } from "@/api/auth";
import { useSessionReload } from "@/composables/useSessionReload";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { portalNicknameSatisfied } from "@/utils/username";

defineOptions({ name: "ProfilePage" });

const router = useRouter();
const session = useSessionStore();
const nickname = ref(session.nickname);
const errorMessage = ref("");
const loading = ref(false);

const tagsText = computed(() => (session.tags.length > 0 ? session.tags.join("、") : zhCN.profile.emptyValue));

async function load(): Promise<void> {
  if (!session.authenticated) {
    return;
  }
  const result = await fetchProfile();
  if (isOk(result) && result.data) {
    session.setProfile(result.data);
    nickname.value = result.data.nickname ?? "";
  }
}

async function submit(): Promise<void> {
  errorMessage.value = "";
  if (!portalNicknameSatisfied(nickname.value)) {
    errorMessage.value = zhCN.profile.nicknameHint;
    return;
  }
  loading.value = true;
  try {
    const result = await updateProfile({ nickname: nickname.value.trim() });
    if (isOk(result)) {
      const refreshed = await fetchProfile();
      if (isOk(refreshed) && refreshed.data) {
        session.setProfile(refreshed.data);
      } else {
        session.nickname = nickname.value.trim();
      }
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

useSessionReload(() => {
  void load();
});

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="profile-page">
    <NavBar :title="zhCN.profile.title" left-arrow @click-left="router.back()" />
    <form @submit.prevent="submit">
      <Field
        v-model="nickname"
        :label="zhCN.profile.nickname"
        :placeholder="zhCN.profile.nicknameHint"
        maxlength="30"
        data-testid="profile-nickname-input"
      />
      <CellGroup :title="zhCN.profile.readonly">
        <Cell :title="zhCN.profile.username" :value="session.username || zhCN.profile.emptyValue" />
        <Cell :title="zhCN.profile.province" :value="session.province || zhCN.profile.emptyValue" />
        <Cell :title="zhCN.profile.level" :value="session.userLevel || zhCN.profile.emptyValue" />
        <Cell :title="zhCN.profile.role" :value="session.userRole || zhCN.profile.emptyValue" />
        <Cell :title="zhCN.profile.tags" :value="tagsText" />
      </CellGroup>
      <p v-if="errorMessage" class="auth-error" data-testid="profile-error" role="alert">{{ errorMessage }}</p>
      <div class="auth-actions">
        <Button type="primary" native-type="submit" block :loading="loading" :disabled="loading" data-testid="profile-save">
          {{ zhCN.profile.save }}
        </Button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.profile-page {
  min-height: 100%;
  background: var(--portal-bg);
}
</style>
