<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Badge, Cell, CellGroup, NavBar, showConfirmDialog, showFailToast } from "vant";
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
  const result = await fetchMineTasks("IN_PROGRESS");
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
        data-testid="entry-signin"
        @click="showFailToast(zhCN.mine.signinHint)"
      />
    </CellGroup>
    <CellGroup inset class="mine-actions">
      <Cell :title="zhCN.mine.password" is-link data-testid="entry-password" @click="router.push('/mine/password')" />
      <Cell :title="zhCN.mine.logout" data-testid="entry-logout" @click="onLogout" />
    </CellGroup>
  </section>
</template>

<style scoped>
.profile-card {
  display: flex;
  gap: 12px;
  align-items: center;
  width: calc(100% - 32px);
  margin: 12px 16px;
  padding: 16px;
  border: 0;
  border-radius: 12px;
  background: #fff;
  text-align: left;
}
.profile-card__avatar {
  display: flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #1989fa;
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
  color: #646566;
  font-size: 13px;
}
.mine-actions {
  margin-top: 12px;
}
</style>
