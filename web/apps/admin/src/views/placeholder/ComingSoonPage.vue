<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "ComingSoonPage" });

const route = useRoute();
const title = computed(() => (typeof route.meta.title === "string" ? route.meta.title : zhCN.placeholder.title));
</script>

<template>
  <section class="admin-page coming-soon-page" data-testid="coming-soon-page">
    <h2>{{ title }}</h2>
    <p>{{ zhCN.placeholder.hint }}</p>
  </section>
</template>

<style scoped>
.coming-soon-page {
  color: #475569;
}
</style>
