import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./client", () => ({
  track: vi.fn(),
  flushTracks: vi.fn().mockResolvedValue(undefined),
  replayPendingTracks: vi.fn(),
}));

import { flushTracks, replayPendingTracks, track } from "./client";
import { TRACK } from "./codes";
import { installTracking } from "./page";

describe("installTracking", () => {
  beforeEach(() => {
    vi.mocked(track).mockReset();
    vi.mocked(flushTracks).mockReset();
    vi.mocked(replayPendingTracks).mockReset();
    vi.mocked(flushTracks).mockResolvedValue(undefined);
  });

  it("replays pending events, reports page.view/leave, and beacons on pagehide", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/home", component: { template: "<div />" } },
        { path: "/mine", component: { template: "<div />" } },
      ],
    });
    installTracking(router);
    expect(replayPendingTracks).toHaveBeenCalled();
    await router.push("/home");
    await router.isReady();
    expect(track).toHaveBeenCalledWith(TRACK.PAGE_VIEW, { route: "/home" });

    await router.push("/mine");
    expect(track).toHaveBeenCalledWith(
      TRACK.PAGE_LEAVE,
      expect.objectContaining({ route: "/home", durationSeconds: expect.any(Number) }),
    );
    expect(track).toHaveBeenCalledWith(TRACK.PAGE_VIEW, { route: "/mine", refRoute: "/home" });

    window.dispatchEvent(new Event("pagehide"));
    expect(track).toHaveBeenCalledWith(
      TRACK.PAGE_LEAVE,
      expect.objectContaining({ route: "/mine" }),
    );
    expect(flushTracks).toHaveBeenCalledWith({ beacon: true });
  });
});
