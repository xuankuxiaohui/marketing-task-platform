import type { Router } from "vue-router";
import { TRACK } from "./codes";
import { flushTracks, replayPendingTracks, track } from "./client";

export function installTracking(router: Router): void {
  replayPendingTracks();

  let currentRoute: string | undefined;
  let enteredAt = Date.now();
  let left = false;

  function emitLeave(): void {
    if (!currentRoute || left) {
      return;
    }
    const durationSeconds = Math.max(0, Math.round((Date.now() - enteredAt) / 1000));
    track(TRACK.PAGE_LEAVE, { route: currentRoute, durationSeconds });
    left = true;
  }

  router.afterEach((to) => {
    const previous = currentRoute;
    if (previous) {
      emitLeave();
    }
    currentRoute = to.path;
    enteredAt = Date.now();
    left = false;
    const props: Record<string, unknown> = { route: to.path };
    if (previous) {
      props.refRoute = previous;
    }
    track(TRACK.PAGE_VIEW, props);
  });

  const onHide = (): void => {
    emitLeave();
    void flushTracks({ beacon: true });
  };

  if (typeof window !== "undefined") {
    window.addEventListener("pagehide", onHide);
  }
}
