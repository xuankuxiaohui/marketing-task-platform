<script setup lang="ts">
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";

defineOptions({ name: "DashboardPage" });

const session = useSessionStore();
</script>

<template>
  <section class="dashboard-page" data-testid="dashboard-page">
    <h2>{{ zhCN.dashboard.title }}</h2>
    <p>{{ zhCN.dashboard.welcome }}{{ session.nickname ? `，${session.nickname}` : "" }}</p>
    <p class="dashboard-page__hint">{{ zhCN.dashboard.hint }}</p>
  </section>
</template>

<style scoped>
.dashboard-page {
  padding: 8px;
}
.dashboard-page__hint {
  color: #64748b;
}
</style>
