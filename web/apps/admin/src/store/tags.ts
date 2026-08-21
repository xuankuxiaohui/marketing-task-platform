import { defineStore } from "pinia";
import { ref } from "vue";

export type TagItem = {
  path: string;
  title: string;
};

export const useTagsStore = defineStore("tags", () => {
  const items = ref<TagItem[]>([]);

  function push(tag: TagItem): void {
    if (!tag.path || items.value.some((item) => item.path === tag.path)) {
      return;
    }
    items.value.push(tag);
  }

  function close(path: string): void {
    items.value = items.value.filter((item) => item.path !== path);
  }

  function reset(): void {
    items.value = [];
  }

  return { items, push, close, reset };
});
