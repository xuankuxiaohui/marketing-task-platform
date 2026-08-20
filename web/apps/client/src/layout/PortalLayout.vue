<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { Tabbar, TabbarItem } from "vant";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "PortalLayout" });

const route = useRoute();
const showTabbar = computed(() => route.meta.tab === "home" || route.meta.tab === "mine");
</script>

<template>
  <div class="portal-layout" :class="{ 'portal-layout--tab': showTabbar }">
    <router-view />
    <Tabbar v-if="showTabbar" route placeholder safe-area-inset-bottom data-testid="portal-tabbar">
      <TabbarItem replace to="/home" icon="home-o" data-testid="tab-home">{{ zhCN.tab.home }}</TabbarItem>
      <TabbarItem replace to="/mine" icon="user-o" data-testid="tab-mine">{{ zhCN.tab.mine }}</TabbarItem>
    </Tabbar>
  </div>
</template>

<style scoped>
.portal-layout {
  min-height: 100vh;
}
</style>
