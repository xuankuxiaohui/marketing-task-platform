<script setup lang="ts">
import {
  Calendar,
  Coin,
  DataLine,
  Flag,
  Monitor,
  More,
  Picture,
  Present,
  Setting,
  Ticket,
  Warning,
} from "@element-plus/icons-vue";
import { computed, nextTick, ref, watch, type Component } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { AdminMenuNode } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE, groupSidebarMenus, type SidebarGroupKey } from "@/router/dynamic";
import { logoutAndReset } from "@/router/session";
import { usePermissionStore } from "@/store/permission";
import { useSessionStore } from "@/store/session";
import { useTagsStore } from "@/store/tags";

defineOptions({ name: "AdminLayout" });

const GROUP_ICONS: Record<SidebarGroupKey, Component> = {
  dashboard: Monitor,
  system: Setting,
  task: Flag,
  reward: Present,
  points: Coin,
  risk: Warning,
  track: DataLine,
  signin: Calendar,
  activity: Ticket,
  ad: Picture,
  other: More,
};

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
      <div class="admin-layout__brand">
        <div class="admin-layout__brand-title">{{ zhCN.appTitle }}</div>
        <div class="admin-layout__brand-sub">{{ zhCN.consoleSubtitle }}</div>
      </div>
      <el-menu
        :default-active="activePath"
        router
        background-color="var(--admin-aside)"
        text-color="#cbd5e1"
        active-text-color="#fff"
      >
        <el-menu-item-group
          v-for="(group, index) in menuGroups"
          :key="group.key"
          :class="{ 'admin-layout__group--divided': index > 0 }"
        >
          <template #title>
            <span class="admin-layout__group-title">
              <el-icon><component :is="GROUP_ICONS[group.key]" /></el-icon>
              {{ group.title }}
            </span>
          </template>
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
          <el-button text data-testid="logout-button" @click="onLogout">{{ zhCN.layout.logout }}</el-button>
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
  width: 232px;
  height: 100vh;
  background: var(--admin-aside);
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}
.admin-layout__brand {
  flex-shrink: 0;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}
.admin-layout__brand-title {
  font-weight: 600;
  font-size: 15px;
  color: #fff;
}
.admin-layout__brand-sub {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
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
  background: var(--admin-page-bg);
}
.admin-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  gap: 12px;
  height: var(--admin-header);
  min-height: var(--admin-header);
  max-height: var(--admin-header);
  padding: 0 16px;
  background: var(--admin-surface);
  border-bottom: 1px solid var(--admin-border);
  overflow: hidden;
}
.admin-layout__tags {
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 0;
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}
.admin-tag-wrap {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
}
.admin-tag {
  display: inline-flex;
  align-items: center;
  height: 100%;
  border: none;
  background: transparent;
  border-radius: 0;
  padding: 0 14px;
  white-space: nowrap;
  cursor: pointer;
  color: var(--admin-muted);
  border-bottom: 2px solid transparent;
}
.admin-tag--active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  font-weight: 500;
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
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(17, 24, 39, 0.12);
}
.admin-layout__user {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  white-space: nowrap;
  font-size: 13px;
  color: var(--admin-ink);
}
.admin-layout__content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px 24px;
  background: var(--admin-page-bg);
}
.admin-layout__aside :deep(.el-menu) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border-right: none;
  background-color: var(--admin-aside);
}
.admin-layout__aside :deep(.el-menu-item-group__title) {
  color: #94a3b8;
  font-size: 12px;
  padding: 12px 20px 6px;
}
.admin-layout__group-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.admin-layout__aside :deep(.el-menu-item) {
  border-left: 3px solid transparent;
}
.admin-layout__aside :deep(.el-menu-item.is-active) {
  background-color: var(--admin-aside-active) !important;
  border-left-color: var(--el-color-primary);
  color: #fff !important;
}
.admin-layout__aside :deep(.admin-layout__group--divided) {
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  margin-top: 4px;
  padding-top: 4px;
}
</style>
