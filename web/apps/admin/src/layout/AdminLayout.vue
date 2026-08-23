<script setup lang="ts">
import {
  CalendarOutlined,
  DesktopOutlined,
  DollarOutlined,
  FlagOutlined,
  GiftOutlined,
  LeftOutlined,
  LineChartOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MoreOutlined,
  PictureOutlined,
  RightOutlined,
  SettingOutlined,
  TagsOutlined,
  WarningOutlined,
} from "@ant-design/icons-vue";
import { computed, h, nextTick, onMounted, onUnmounted, ref, watch, type Component, type VNode } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { AdminMenuNode } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import {
  DASHBOARD_ROUTE,
  groupSidebarMenus,
  sidebarGroupKey,
  type SidebarGroupKey,
} from "@/router/dynamic";
import { iconForMenuRoute } from "./sidebar-icons";
import { logoutAndReset } from "@/router/session";
import { usePermissionStore } from "@/store/permission";
import { useSessionStore } from "@/store/session";
import { useTagsStore } from "@/store/tags";

defineOptions({ name: "AdminLayout" });

const GROUP_ICONS: Record<SidebarGroupKey, Component> = {
  dashboard: DesktopOutlined,
  system: SettingOutlined,
  task: FlagOutlined,
  reward: GiftOutlined,
  points: DollarOutlined,
  risk: WarningOutlined,
  track: LineChartOutlined,
  signin: CalendarOutlined,
  activity: TagsOutlined,
  ad: PictureOutlined,
  other: MoreOutlined,
};

type TagActionKey = "close" | "closeOthers" | "closeLeft" | "closeRight";

const TAG_ACTIONS: { key: TagActionKey; testid: string; label: string }[] = [
  { key: "close", testid: "tag-close", label: zhCN.layout.close },
  { key: "closeOthers", testid: "tag-close-others", label: zhCN.layout.closeOthers },
  { key: "closeLeft", testid: "tag-close-left", label: zhCN.layout.closeLeft },
  { key: "closeRight", testid: "tag-close-right", label: zhCN.layout.closeRight },
];

const route = useRoute();
const router = useRouter();
const permission = usePermissionStore();
const session = useSessionStore();
const tags = useTagsStore();

const collapsed = ref(false);
const openKeys = ref<string[]>([]);
const menuPath = ref<string | null>(null);
const contentEl = ref<HTMLElement | null>(null);
const tagsEl = ref<HTMLElement | null>(null);
const tagsOverflow = ref(false);

const activePath = computed(() => route.path);
const selectedKeys = computed(() => [activePath.value]);
const menuGroups = computed(() => groupSidebarMenus(permission.menus));
const crumbs = computed(() => {
  const path = activePath.value;
  if (path === DASHBOARD_ROUTE) {
    return [] as { title: string; path?: string }[];
  }
  const group = menuGroups.value.find((entry) =>
    entry.items.some((item) => item.route === path || Boolean(item.route && path.startsWith(`${item.route}/`))),
  );
  const item = group?.items.find(
    (entry) => entry.route === path || Boolean(entry.route && path.startsWith(`${entry.route}/`)),
  );
  const items: { title: string; path?: string }[] = [];
  if (group && group.key !== "dashboard") {
    items.push({ title: group.title });
  }
  const title = item?.name ?? (typeof route.meta.title === "string" ? route.meta.title : "");
  if (title) {
    items.push({ title, path: item?.route });
  }
  return items;
});

type SidebarMenuItem = {
  key: string;
  icon?: () => VNode;
  label: string | VNode;
  onClick?: () => void;
  children?: { key: string; label: string | VNode; icon?: () => VNode; onClick?: () => void }[];
};

const menuItems = computed<SidebarMenuItem[]>(() =>
  menuGroups.value.map((group) => {
    const icon = GROUP_ICONS[group.key];
    if (group.items.length === 1) {
      const item = group.items[0]!;
      const key = menuIndex(item);
      return {
        key,
        icon: () => h(icon),
        label: h("span", { "data-testid": `sidebar-group-${group.key}` }, item.name ?? ""),
        onClick: () => go(key),
      };
    }
    return {
      key: groupMenuKey(group.key),
      icon: () => h(icon),
      label: h("span", { "data-testid": `sidebar-group-${group.key}` }, group.title),
      children: group.items.map((item) => {
        const key = menuIndex(item);
        return {
          key,
          icon: () => h(iconForMenuRoute(key)),
          label: h("span", { "data-testid": `sidebar-item-${key}` }, item.name ?? ""),
          onClick: () => go(key),
        };
      }),
    };
  }),
);

function groupMenuKey(key: SidebarGroupKey): string {
  return `group-${key}`;
}

function menuIndex(node: AdminMenuNode): string {
  return node.route ?? "";
}

function activeSubmenuKey(): string | null {
  const key = sidebarGroupKey(activePath.value);
  const group = menuGroups.value.find((item) => item.key === key);
  if (!group || group.items.length <= 1) {
    return null;
  }
  return groupMenuKey(key);
}

function go(path: string): void {
  if (!path || path.startsWith("group-")) {
    return;
  }
  void router.push(path);
}

function onMenuClick(info: { key: string | number }): void {
  go(String(info.key));
}

function onOpenChange(keys: (string | number)[]): void {
  openKeys.value = keys.map(String);
}

function tagLabel(tag: { path: string; title: string }): string {
  return tag.path === DASHBOARD_ROUTE ? zhCN.layout.home : tag.title;
}

function onToggleSider(): void {
  collapsed.value = !collapsed.value;
}

function syncOpenKeysToRoute(): void {
  const key = activeSubmenuKey();
  if (key && !openKeys.value.includes(key)) {
    openKeys.value = [...openKeys.value, key];
  }
}

watch(activePath, () => {
  syncOpenKeysToRoute();
});

watch(
  menuGroups,
  () => {
    if (openKeys.value.length === 0) {
      syncOpenKeysToRoute();
    }
  },
  { immediate: true },
);

watch(
  () => route.fullPath,
  async () => {
    await nextTick();
    contentEl.value?.scrollTo({ top: 0 });
    const active = tagsEl.value?.querySelector<HTMLElement>(".admin-tag--active");
    active?.scrollIntoView({ inline: "nearest", block: "nearest" });
  },
);

function closeMenus(): void {
  menuPath.value = null;
}

function popupContainer(node: HTMLElement): HTMLElement {
  return node.parentElement ?? document.body;
}

async function onLogout(): Promise<void> {
  await logoutAndReset(router);
}

function onTagClick(path: string): void {
  closeMenus();
  void router.push(path);
}

function navigateIfMissing(): void {
  if (tags.items.some((item) => item.path === route.path)) {
    return;
  }
  const next = tags.items[tags.items.length - 1];
  void router.push(next?.path ?? DASHBOARD_ROUTE);
}

function hasCloseableLeft(path: string): boolean {
  const index = tags.items.findIndex((item) => item.path === path);
  if (index <= 0) {
    return false;
  }
  return tags.items.slice(0, index).some((item) => !item.pinned);
}

function hasCloseableRight(path: string): boolean {
  const index = tags.items.findIndex((item) => item.path === path);
  if (index < 0) {
    return false;
  }
  return tags.items.slice(index + 1).some((item) => !item.pinned);
}

function isTagActionDisabled(action: TagActionKey, path: string): boolean {
  if (action === "closeOthers") {
    return !tags.items.some((item) => item.path !== path && !item.pinned);
  }
  if (action === "closeLeft") {
    return !hasCloseableLeft(path);
  }
  if (action === "closeRight") {
    return !hasCloseableRight(path);
  }
  return false;
}

function runTagAction(action: TagActionKey, path: string): void {
  if (action === "close") {
    tags.close(path);
  } else if (action === "closeOthers") {
    tags.closeOthers(path);
  } else if (action === "closeLeft") {
    tags.closeLeft(path);
  } else {
    tags.closeRight(path);
  }
  closeMenus();
  navigateIfMissing();
}

function onTagMenuOpenChange(path: string, open: boolean): void {
  menuPath.value = open ? path : menuPath.value === path ? null : menuPath.value;
}

function scrollTags(direction: -1 | 1): void {
  tagsEl.value?.scrollBy({ left: direction * 180, behavior: "smooth" });
}

function updateTagsOverflow(): void {
  const el = tagsEl.value;
  tagsOverflow.value = Boolean(el && el.scrollWidth > el.clientWidth + 1);
}

watch(
  () => [tags.items.length, collapsed.value] as const,
  async () => {
    await nextTick();
    updateTagsOverflow();
  },
);

onMounted(() => {
  updateTagsOverflow();
  window.addEventListener("resize", updateTagsOverflow);
});

onUnmounted(() => {
  window.removeEventListener("resize", updateTagsOverflow);
});
</script>


<template>
  <a-layout class="admin-layout">
    <a-layout-sider
      class="admin-layout__aside"
      data-testid="admin-sidebar"
      theme="light"
      :width="232"
      :collapsed-width="64"
      :collapsed="collapsed"
      collapsible
      :trigger="null"
    >
      <div class="admin-layout__brand">
        <div class="admin-layout__brand-title">{{ collapsed ? zhCN.appTitle.slice(0, 1) : zhCN.appTitle }}</div>
        <div v-if="!collapsed" class="admin-layout__brand-sub">{{ zhCN.consoleSubtitle }}</div>
      </div>
      <a-menu
        class="admin-layout__menu"
        mode="inline"
        theme="light"
        :selected-keys="selectedKeys"
        :open-keys="openKeys"
        :items="menuItems"
        @click="onMenuClick"
        @open-change="onOpenChange"
      />
      <p v-if="permission.menus.length === 0" class="admin-layout__empty">{{ zhCN.layout.emptyMenu }}</p>
    </a-layout-sider>
    <a-layout class="admin-layout__main">
      <div class="admin-layout__chrome">
        <div class="admin-layout__topbar">
          <div class="admin-layout__topbar-left">
            <a-button
              type="text"
              data-testid="sidebar-collapse"
              :aria-label="collapsed ? zhCN.layout.expandMenu : zhCN.layout.collapseMenu"
              @click="onToggleSider"
            >
              <MenuUnfoldOutlined v-if="collapsed" />
              <MenuFoldOutlined v-else />
            </a-button>
            <a-breadcrumb class="admin-layout__breadcrumb" data-testid="admin-breadcrumb">
              <a-breadcrumb-item>
                <a data-testid="breadcrumb-home" @click.prevent="go(DASHBOARD_ROUTE)">{{ zhCN.layout.home }}</a>
              </a-breadcrumb-item>
              <a-breadcrumb-item v-for="(crumb, index) in crumbs" :key="`${crumb.title}-${index}`">
                {{ crumb.title }}
              </a-breadcrumb-item>
            </a-breadcrumb>
          </div>
          <div class="admin-layout__user">
            <span>{{ session.nickname || session.username }}</span>
            <a-button type="text" data-testid="logout-button" @click="onLogout">{{ zhCN.layout.logout }}</a-button>
          </div>
        </div>
        <div class="admin-layout__tags-bar">
          <a-button
            v-if="tagsOverflow"
            class="admin-layout__tag-nav"
            type="text"
            data-testid="tag-scroll-left"
            :aria-label="zhCN.layout.scrollLeft"
            @click="scrollTags(-1)"
          >
            <LeftOutlined />
          </a-button>
          <div
            ref="tagsEl"
            class="admin-layout__tags"
            role="tablist"
            :aria-label="zhCN.layout.tags"
            data-testid="admin-tags"
          >
            <div v-for="tag in tags.items" :key="tag.path" class="admin-tag-wrap">
              <a-dropdown
                :trigger="['contextmenu']"
                :open="menuPath === tag.path"
                :get-popup-container="popupContainer"
                @open-change="(open) => onTagMenuOpenChange(tag.path, open)"
              >
                <div
                  class="admin-tag"
                  role="tab"
                  :class="{ 'admin-tag--active': tag.path === activePath }"
                  :data-testid="`admin-tag-${tag.path}`"
                  @click="onTagClick(tag.path)"
                  @contextmenu.prevent="onTagMenuOpenChange(tag.path, true)"
                >
                  <i v-if="tag.path === activePath" class="admin-tag__dot" />
                  <span class="admin-tag__title">{{ tagLabel(tag) }}</span>
                  <span
                    v-if="!tag.pinned"
                    class="admin-tag__close"
                    @click.stop="runTagAction('close', tag.path)"
                  >×</span>
                </div>
                <template #overlay>
                  <a-menu v-if="menuPath === tag.path">
                    <a-menu-item
                      v-for="action in TAG_ACTIONS"
                      :key="action.key"
                      :disabled="isTagActionDisabled(action.key, tag.path)"
                      :data-testid="action.testid"
                      @click="runTagAction(action.key, tag.path)"
                    >
                      {{ action.label }}
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
          </div>
          <a-button
            v-if="tagsOverflow"
            class="admin-layout__tag-nav"
            type="text"
            data-testid="tag-scroll-right"
            :aria-label="zhCN.layout.scrollRight"
            @click="scrollTags(1)"
          >
            <RightOutlined />
          </a-button>
        </div>
      </div>
      <a-layout-content>
        <main ref="contentEl" class="admin-layout__content" data-testid="admin-content">
          <router-view />
        </main>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
  overflow: hidden;
}
.admin-layout__aside {
  background: var(--admin-sider) !important;
  border-inline-end: 1px solid #e8eaed;
}
.admin-layout__aside :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--admin-sider);
}
.admin-layout__brand {
  flex-shrink: 0;
  padding: 16px 12px;
  line-height: 1.3;
  text-align: center;
}
.admin-layout__brand-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--admin-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-layout__brand-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--admin-muted);
}
.admin-layout__empty {
  padding: 16px;
  color: var(--admin-muted);
}
.admin-layout__menu {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  border-inline-end: none;
  background: transparent !important;
  scrollbar-width: none;
}
.admin-layout__menu :deep(.ant-menu-item-selected) {
  background: #fff !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.admin-layout__menu::-webkit-scrollbar {
  width: 0;
  height: 0;
}
.admin-layout__main {
  min-width: 0;
}
.admin-layout__chrome {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  background: var(--admin-surface);
}
.admin-layout__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: var(--admin-header);
  padding-inline: 8px 16px;
  border-bottom: 1px solid var(--admin-border);
}
.admin-layout__topbar-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}
.admin-layout__breadcrumb {
  min-width: 0;
}
.admin-layout__tags-bar {
  display: flex;
  align-items: center;
  height: var(--admin-tags);
  padding-right: 4px;
  border-bottom: 1px solid #d8dce5;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.12), 0 4px 10px rgba(0, 0, 0, 0.06);
  box-sizing: border-box;
}
.admin-layout__tag-nav {
  flex-shrink: 0;
  width: 32px;
  height: 32px !important;
  padding: 0;
  border-radius: 0;
}
.admin-layout__tags {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.admin-layout__tags::-webkit-scrollbar {
  display: none;
}
.admin-tag-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 26px;
  margin: 0 0 0 5px;
}
.admin-tag-wrap :deep(.ant-dropdown-trigger) {
  display: flex !important;
  align-items: center;
  height: 26px;
  line-height: 26px;
}
.admin-tag {
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  height: 26px;
  margin: 0;
  border: 1px solid #d8dce5 !important;
  border-radius: 2px;
  background: #fff;
  padding: 0 12px !important;
  font-size: 12px;
  line-height: 24px;
  white-space: nowrap;
  cursor: pointer;
  color: #495060;
}
.admin-tag--active {
  color: #fff;
  background: var(--admin-primary);
  border-color: var(--admin-primary);
  font-weight: 400;
}
.admin-tag__dot {
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
  background: #fff;
  flex-shrink: 0;
}
.admin-tag__close {
  margin-left: 6px;
  font-size: 12px;
  line-height: 1;
  color: #495060;
}
.admin-tag--active .admin-tag__close {
  color: #fff;
}
.admin-layout__user {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  white-space: nowrap;
  color: var(--admin-ink);
}
.admin-layout__content {
  height: calc(100vh - var(--admin-header) - var(--admin-tags));
  overflow-y: auto;
  padding: 16px 24px 24px;
  background: var(--admin-page-bg);
}
</style>
