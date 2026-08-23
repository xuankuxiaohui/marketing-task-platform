<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useSessionReload } from "@/composables/useSessionReload";
import { useRouter } from "vue-router";
import { Badge, Cell, CellGroup, NavBar, showConfirmDialog } from "vant";
import { isOk } from "@mkt/shared";
import { fetchMineTasks } from "@/api/task";
import { zhCN } from "@/locales/zh-CN";
import { logoutAndReset } from "@/router/session";
import { useSessionStore } from "@/store/session";

defineOptions({ name: "MinePage" });

const router = useRouter();
const session = useSessionStore();
const inProgressCount = ref(0);

async function loadBadge(): Promise<void> {
  if (!session.authenticated) {
    inProgressCount.value = 0;
    return;
  }
  const result = await fetchMineTasks({ status: "IN_PROGRESS", page: 1, pageSize: 1 });
  if (isOk(result) && result.data) {
    inProgressCount.value = Number(result.data.total ?? 0);
  }
}

async function onLogout(): Promise<void> {
  try {
    await showConfirmDialog({
      title: zhCN.mine.logout,
      message: zhCN.mine.logoutConfirm,
    });
  } catch {
    return;
  }
  await logoutAndReset(router);
}

useSessionReload(() => {
  void loadBadge();
});

onMounted(() => {
  void loadBadge();
});
</script>

<template>
  <section class="mine-page">
    <NavBar :title="zhCN.mine.title" />
    <button class="profile-card" type="button" data-testid="profile-card" @click="router.push('/mine/profile')">
      <span class="profile-card__avatar" :aria-label="zhCN.mine.avatarAlt">{{
        (session.nickname || session.username || "用").slice(0, 1)
      }}</span>
      <span class="profile-card__meta">
        <strong data-testid="profile-nickname">{{ session.nickname || session.username }}</strong>
        <span data-testid="profile-points">{{ zhCN.mine.points }} {{ session.pointsBalance }}</span>
      </span>
    </button>
    <CellGroup inset>
      <Cell is-link data-testid="entry-tasks" @click="router.push('/mine/tasks')">
        <template #title>
          <span>{{ zhCN.mine.tasks }}</span>
          <Badge v-if="inProgressCount > 0" :content="inProgressCount" data-testid="tasks-badge" />
        </template>
      </Cell>
      <Cell :title="zhCN.mine.prizes" is-link data-testid="entry-prizes" @click="router.push('/mine/prizes')" />
      <Cell :title="zhCN.mine.pointsDetail" is-link data-testid="entry-points" @click="router.push('/mine/points')" />
      <Cell
        :title="zhCN.mine.signin"
        :label="zhCN.mine.signinHint"
        is-link
        data-testid="entry-signin"
        @click="router.push('/signin')"
      />
      <Cell
        :title="zhCN.mine.activity"
        :label="zhCN.mine.activityHint"
        is-link
        data-testid="entry-activity"
        @click="router.push('/activities')"
      />
    </CellGroup>
    <CellGroup inset class="mine-actions">
      <Cell :title="zhCN.mine.password" is-link data-testid="entry-password" @click="router.push('/mine/password')" />
      <Cell :title="zhCN.mine.logout" data-testid="entry-logout" @click="onLogout" />
    </CellGroup>
  </section>
</template>

<style scoped>
.mine-page {
  min-height: 100%;
  background: var(--portal-bg);
}
.profile-card {
  display: flex;
  gap: 12px;
  align-items: center;
  width: calc(100% - 32px);
  margin: 12px 16px;
  padding: 16px;
  border: 0;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
  text-align: left;
}
.profile-card__avatar {
  display: flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--portal-primary);
  color: #fff;
  font-size: 20px;
}
.profile-card__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.profile-card__meta strong {
  font-size: 16px;
}
.profile-card__meta span {
  color: var(--portal-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.mine-actions {
  margin-top: 12px;
}
</style>
