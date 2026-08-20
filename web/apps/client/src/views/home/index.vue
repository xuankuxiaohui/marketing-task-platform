<script setup lang="ts">
import { Empty, NavBar } from "vant";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "HomePage" });
</script>

<template>
  <section class="home-page">
    <NavBar :title="zhCN.home.title" />
    <Empty :description="zhCN.home.empty" data-testid="home-empty" />
  </section>
</template>
