<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { AdminMenuNode } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { logoutAndReset } from "@/router/session";
import { usePermissionStore } from "@/store/permission";
import { useSessionStore } from "@/store/session";
import { useTagsStore } from "@/store/tags";

defineOptions({ name: "AdminLayout" });

const route = useRoute();
const router = useRouter();
const permission = usePermissionStore();
const session = useSessionStore();
const tags = useTagsStore();

const activePath = computed(() => route.path);

function menuIndex(node: AdminMenuNode): string {
  return node.route ?? "";
}

async function onLogout(): Promise<void> {
  await logoutAndReset(router);
}

function onTagClick(path: string): void {
  void router.push(path);
}

function onTagClose(path: string): void {
  tags.close(path);
  if (route.path === path) {
    const next = tags.items[tags.items.length - 1];
    void router.push(next?.path ?? "/dashboard");
  }
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-layout__aside">
      <div class="admin-layout__brand">{{ zhCN.appTitle }}</div>
      <el-menu :default-active="activePath" router background-color="#0f172a" text-color="#cbd5e1" active-text-color="#fff">
        <el-menu-item v-for="item in permission.menus" :key="item.id" :index="menuIndex(item)">
          {{ item.name }}
        </el-menu-item>
      </el-menu>
      <p v-if="permission.menus.length === 0" class="admin-layout__empty">{{ zhCN.layout.emptyMenu }}</p>
    </aside>
    <section class="admin-layout__main">
      <header class="admin-layout__header">
        <div class="admin-layout__tags" role="tablist" :aria-label="zhCN.layout.tags">
          <button
            v-for="tag in tags.items"
            :key="tag.path"
            type="button"
            class="admin-tag"
            :class="{ 'admin-tag--active': tag.path === activePath }"
            @click="onTagClick(tag.path)"
          >
            <span>{{ tag.title }}</span>
            <span class="admin-tag__close" @click.stop="onTagClose(tag.path)">×</span>
          </button>
        </div>
        <div class="admin-layout__user">
          <span>{{ session.nickname || session.username }}</span>
          <button type="button" data-testid="logout-button" @click="onLogout">{{ zhCN.layout.logout }}</button>
        </div>
      </header>
      <main class="admin-layout__content">
        <router-view />
      </main>
    </section>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}
.admin-layout__aside {
  width: 220px;
  background: #0f172a;
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
}
.admin-layout__brand {
  padding: 16px;
  font-weight: 600;
}
.admin-layout__empty {
  padding: 16px;
  color: #94a3b8;
}
.admin-layout__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f1f5f9;
}
.admin-layout__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}
.admin-layout__tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.admin-tag {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 4px;
  padding: 4px 8px;
  cursor: pointer;
}
.admin-tag--active {
  border-color: #2563eb;
  color: #2563eb;
}
.admin-tag__close {
  margin-left: 6px;
}
.admin-layout__user {
  display: flex;
  gap: 12px;
  align-items: center;
}
.admin-layout__content {
  padding: 16px;
}
</style>
