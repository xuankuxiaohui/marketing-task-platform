<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { AdminMenuNode } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE, groupSidebarMenus } from "@/router/dynamic";
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
const menuGroups = computed(() => groupSidebarMenus(permission.menus));
const menuPath = ref<string | null>(null);
const contentEl = ref<HTMLElement | null>(null);

watch(
  () => route.fullPath,
  async () => {
    await nextTick();
    contentEl.value?.scrollTo({ top: 0 });
  },
);

function menuIndex(node: AdminMenuNode): string {
  return node.route ?? "";
}

async function onLogout(): Promise<void> {
  await logoutAndReset(router);
}

function onTagClick(path: string): void {
  menuPath.value = null;
  void router.push(path);
}

function navigateIfMissing(): void {
  if (tags.items.some((item) => item.path === route.path)) {
    return;
  }
  const next = tags.items[tags.items.length - 1];
  void router.push(next?.path ?? DASHBOARD_ROUTE);
}

function onTagClose(path: string): void {
  tags.close(path);
  menuPath.value = null;
  navigateIfMissing();
}

function onCloseOthers(path: string): void {
  tags.closeOthers(path);
  menuPath.value = null;
  navigateIfMissing();
}

function onCloseLeft(path: string): void {
  tags.closeLeft(path);
  menuPath.value = null;
  navigateIfMissing();
}

function onCloseRight(path: string): void {
  tags.closeRight(path);
  menuPath.value = null;
  navigateIfMissing();
}

function onTagContext(path: string, event: MouseEvent): void {
  event.preventDefault();
  menuPath.value = menuPath.value === path ? null : path;
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-layout__aside" data-testid="admin-sidebar">
      <div class="admin-layout__brand">{{ zhCN.appTitle }}</div>
      <el-menu :default-active="activePath" router background-color="#0f172a" text-color="#cbd5e1" active-text-color="#fff">
        <el-menu-item-group
          v-for="(group, index) in menuGroups"
          :key="group.key"
          :title="group.title"
          :class="{ 'admin-layout__group--divided': index > 0 }"
        >
          <el-menu-item v-for="item in group.items" :key="item.id" :index="menuIndex(item)">
            {{ item.name }}
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
      <p v-if="permission.menus.length === 0" class="admin-layout__empty">{{ zhCN.layout.emptyMenu }}</p>
    </aside>
    <section class="admin-layout__main">
      <header class="admin-layout__header">
        <div class="admin-layout__tags" role="tablist" :aria-label="zhCN.layout.tags">
          <div v-for="tag in tags.items" :key="tag.path" class="admin-tag-wrap">
            <button
              type="button"
              class="admin-tag"
              :class="{ 'admin-tag--active': tag.path === activePath }"
              :data-testid="`admin-tag-${tag.path}`"
              @click="onTagClick(tag.path)"
              @contextmenu="onTagContext(tag.path, $event)"
            >
              <span>{{ tag.title }}</span>
              <span class="admin-tag__close" @click.stop="onTagClose(tag.path)">×</span>
            </button>
            <div v-if="menuPath === tag.path" class="admin-tag-menu" role="menu" data-testid="tag-context-menu">
              <el-button text data-testid="tag-close" @click="onTagClose(tag.path)">{{ zhCN.layout.close }}</el-button>
              <el-button text data-testid="tag-close-others" @click="onCloseOthers(tag.path)">
                {{ zhCN.layout.closeOthers }}
              </el-button>
              <el-button text data-testid="tag-close-left" @click="onCloseLeft(tag.path)">
                {{ zhCN.layout.closeLeft }}
              </el-button>
              <el-button text data-testid="tag-close-right" @click="onCloseRight(tag.path)">
                {{ zhCN.layout.closeRight }}
              </el-button>
            </div>
          </div>
        </div>
        <div class="admin-layout__user">
          <span>{{ session.nickname || session.username }}</span>
          <el-button data-testid="logout-button" @click="onLogout">{{ zhCN.layout.logout }}</el-button>
        </div>
      </header>
      <main ref="contentEl" class="admin-layout__content" data-testid="admin-content">
        <router-view />
      </main>
    </section>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}
.admin-layout__aside {
  width: 220px;
  height: 100vh;
  background: #0f172a;
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}
.admin-layout__brand {
  flex-shrink: 0;
  padding: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}
.admin-layout__empty {
  padding: 16px;
  color: #94a3b8;
}
.admin-layout__main {
  flex: 1;
  min-width: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f1f5f9;
}
.admin-layout__header {
  display: flex;
  justify-content: space-between;
  flex-shrink: 0;
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
.admin-tag-wrap {
  position: relative;
}
.admin-tag {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 4px;
  padding: 4px 8px;
  cursor: pointer;
}
.admin-tag--active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.admin-tag__close {
  margin-left: 6px;
}
.admin-tag-menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  min-width: 128px;
  padding: 4px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}
.admin-layout__user {
  display: flex;
  gap: 12px;
  align-items: center;
}
.admin-layout__content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}
.admin-layout__aside :deep(.el-menu) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border-right: none;
  background-color: #0f172a;
}
.admin-layout__aside :deep(.el-menu-item-group__title) {
  color: #94a3b8;
  font-size: 12px;
  padding: 12px 20px 6px;
}
.admin-layout__aside :deep(.admin-layout__group--divided) {
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  margin-top: 4px;
  padding-top: 4px;
}
</style>
