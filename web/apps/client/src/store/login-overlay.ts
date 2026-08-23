import { defineStore } from "pinia";
import { ref } from "vue";

export type LoginOverlayRequest = {
  redirect?: string;
  resume?: () => void;
  code?: string;
  message?: string;
};

export const useLoginOverlayStore = defineStore("loginOverlay", () => {
  const visible = ref(false);
  const code = ref<string | undefined>();
  const message = ref("");
  const pendingRedirect = ref<string | undefined>();
  const pendingResume = ref<(() => void) | undefined>();

  function request(opts: LoginOverlayRequest = {}): void {
    pendingRedirect.value = opts.redirect;
    pendingResume.value = opts.resume;
    code.value = opts.code;
    message.value = opts.message ?? "";
    visible.value = true;
  }

  function close(): void {
    visible.value = false;
  }

  function consumeResume(): (() => void) | undefined {
    const fn = pendingResume.value;
    pendingResume.value = undefined;
    return fn;
  }

  function consumeRedirect(): string | undefined {
    const value = pendingRedirect.value;
    pendingRedirect.value = undefined;
    return value;
  }

  return {
    visible,
    code,
    message,
    pendingRedirect,
    pendingResume,
    request,
    close,
    consumeResume,
    consumeRedirect,
  };
});
