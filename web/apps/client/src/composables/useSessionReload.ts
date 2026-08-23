import { watch } from "vue";
import { useSessionStore } from "@/store/session";

/** Private lists skip guest fetches; reload when overlay login sets a session. */
export function useSessionReload(load: () => void): void {
  const session = useSessionStore();
  watch(
    () => session.authenticated,
    (ok, wasOk) => {
      if (ok && wasOk !== true) {
        load();
      }
    },
  );
}
