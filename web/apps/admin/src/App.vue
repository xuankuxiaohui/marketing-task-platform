<script setup lang="ts">
import zhCN from "ant-design-vue/es/locale/zh_CN";

defineOptions({ name: "AdminApp" });
</script>

<template>
  <a-style-provider hash-priority="high">
    <a-config-provider :locale="zhCN" :auto-insert-space-in-button="false" component-size="middle">
      <router-view />
    </a-config-provider>
  </a-style-provider>
</template>
