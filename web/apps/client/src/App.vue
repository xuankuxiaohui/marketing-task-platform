<script setup lang="ts">
import LoginOverlay from "@/components/LoginOverlay.vue";

defineOptions({ name: "PortalApp" });
</script>

<template>
  <router-view />
  <LoginOverlay />
</template>
