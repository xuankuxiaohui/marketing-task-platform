import { defineStore } from "pinia";
import { ref } from "vue";
import { DASHBOARD_ROUTE } from "@/router/dynamic";

export type TagItem = {
  path: string;
  title: string;
  pinned?: boolean;
};

function isPinned(item: TagItem): boolean {
  return Boolean(item.pinned);
}

export const useTagsStore = defineStore("tags", () => {
  const items = ref<TagItem[]>([]);

  function push(tag: TagItem): void {
    if (!tag.path || items.value.some((item) => item.path === tag.path)) {
      return;
    }
    items.value.push({
      ...tag,
      pinned: tag.pinned ?? tag.path === DASHBOARD_ROUTE,
    });
  }

  function close(path: string): void {
    items.value = items.value.filter((item) => item.path !== path);
  }

  function closeOthers(path: string): void {
    items.value = items.value.filter((item) => item.path === path || isPinned(item));
  }

  function closeLeft(path: string): void {
    const index = items.value.findIndex((item) => item.path === path);
    if (index < 0) {
      return;
    }
    items.value = items.value.filter((item, i) => i >= index || isPinned(item));
  }

  function closeRight(path: string): void {
    const index = items.value.findIndex((item) => item.path === path);
    if (index < 0) {
      return;
    }
    items.value = items.value.filter((item, i) => i <= index || isPinned(item));
  }

  function reset(): void {
    items.value = [];
  }

  return { items, push, close, closeOthers, closeLeft, closeRight, reset };
});
