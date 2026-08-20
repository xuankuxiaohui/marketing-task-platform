<script setup lang="ts">
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "RiskRulePage" });
</script>

<template>
  <section class="admin-page" data-testid="rule-page">
    <h2>{{ zhCN.rule.title }}</h2>
    <p class="hint" data-testid="rule-no-api">{{ zhCN.rule.noApiHint }}</p>
  </section>
</template>
