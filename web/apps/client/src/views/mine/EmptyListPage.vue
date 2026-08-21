<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, NavBar } from "vant";
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "EmptyListPage" });

const route = useRoute();
const router = useRouter();

const kind = computed(() => {
  const raw = route.meta.emptyKind;
  return raw === "prizes" || raw === "points" ? raw : "tasks";
});

const description = computed(() => zhCN.empty[kind.value]);
const title = computed(() => (typeof route.meta.title === "string" ? route.meta.title : zhCN.mine.title));
</script>

<template>
  <section>
    <NavBar :title="title" left-arrow @click-left="router.back()" />
    <Empty :description="description" data-testid="empty-list">
      <Button v-if="kind === 'tasks'" type="primary" size="small" data-testid="empty-go-home" @click="router.push('/home')">
        {{ zhCN.empty.goHome }}
      </Button>
    </Empty>
  </section>
</template>
