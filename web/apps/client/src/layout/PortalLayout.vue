<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { Tabbar, TabbarItem } from "vant";
import AdFloat from "@/components/AdFloat.vue";
import AdPopup from "@/components/AdPopup.vue";
import AdSplash from "@/components/AdSplash.vue";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "PortalLayout" });

const route = useRoute();
const TAB_KEYS = new Set(["home", "tasks", "prizes", "mine"]);
const showTabbar = computed(() => TAB_KEYS.has(String(route.meta.tab ?? "")));
const showHomeAds = computed(() => route.meta.tab === "home");
</script>

<template>
  <div class="portal-layout" :class="{ 'portal-layout--tab': showTabbar }">
    <AdSplash />
    <AdPopup v-if="showHomeAds" />
    <AdFloat v-if="showHomeAds" />
    <router-view />
    <Tabbar v-if="showTabbar" route placeholder safe-area-inset-bottom data-testid="portal-tabbar">
      <TabbarItem replace to="/home" icon="home-o" data-testid="tab-home">{{ zhCN.tab.home }}</TabbarItem>
      <TabbarItem replace to="/mine/tasks" icon="todo-list-o" data-testid="tab-tasks">{{ zhCN.tab.tasks }}</TabbarItem>
      <TabbarItem replace to="/mine/prizes" icon="gift-o" data-testid="tab-prizes">{{ zhCN.tab.prizes }}</TabbarItem>
      <TabbarItem replace to="/mine" icon="user-o" data-testid="tab-mine">{{ zhCN.tab.mine }}</TabbarItem>
    </Tabbar>
  </div>
</template>

<style scoped>
.portal-layout {
  box-sizing: border-box;
  min-height: 100vh;
  min-height: 100dvh;
  max-width: 480px;
  margin: 0 auto;
  background: var(--portal-bg);
  color: var(--portal-ink);
}
.portal-layout :deep(.van-tabbar) {
  border-top: 1px solid var(--portal-line);
  background: var(--portal-surface);
}
.portal-layout :deep(.van-tabbar-item--active) {
  color: var(--portal-primary);
}
</style>
