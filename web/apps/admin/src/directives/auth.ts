import type { Directive, DirectiveBinding } from "vue";
import { useSessionStore } from "@/store/session";

export function hasAuth(value: string | string[]): boolean {
  if (!value || (Array.isArray(value) && value.length === 0)) {
    return false;
  }
  const { permissions } = useSessionStore();
  if (typeof value === "string") {
    return permissions.includes(value);
  }
  return value.every((code) => permissions.includes(code));
}

export const auth: Directive<HTMLElement, string | string[]> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const { value } = binding;
    if (!value) {
      throw new Error('[Directive: auth]: need auths! Like v-auth="\'identity:admin-user:create\'"');
    }
    if (!hasAuth(value)) {
      el.parentNode?.removeChild(el);
    }
  },
};
