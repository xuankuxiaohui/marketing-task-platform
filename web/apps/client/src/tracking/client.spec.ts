import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TRACK_FLUSH_MS, TRACK_FLUSH_SIZE, TRACK_PENDING_KEY, createTrackClient, type TrackTransport } from "./client";

function memoryStorage() {
  const store = new Map<string, string>();
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    store,
  };
}

function makeTransport(overrides: Partial<TrackTransport> = {}) {
  const storage = memoryStorage();
  const post = vi.fn<(body: unknown) => Promise<boolean>>().mockResolvedValue(true);
  const beacon = vi.fn<(body: unknown) => boolean>().mockReturnValue(true);
  const transport: TrackTransport = {
    post,
    beacon,
    now: () => 1_700_000_000_000,
    storage,
    ...overrides,
  };
  return { transport, post, beacon, storage };
}

describe("track client", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("flushes when the local buffer reaches 20 events", async () => {
    const { transport, post } = makeTransport();
    const client = createTrackClient(transport);
    for (let i = 0; i < TRACK_FLUSH_SIZE; i += 1) {
      client.track("page.view", { n: i });
    }
    await vi.runAllTimersAsync();
    expect(post).toHaveBeenCalledTimes(1);
    const body = post.mock.calls[0]?.[0] as { events?: unknown[] };
    expect(body.events).toHaveLength(20);
    expect(client.size()).toBe(0);
    client.dispose();
  });

  it("flushes after 5 seconds if the buffer is below 20", async () => {
    const { transport, post } = makeTransport();
    const client = createTrackClient(transport);
    client.track("page.view", { route: "/home" });
    expect(post).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(TRACK_FLUSH_MS);
    expect(post).toHaveBeenCalledTimes(1);
    client.dispose();
  });

  it("retries a failed batch at most twice then persists for replay", async () => {
    const { transport, post, storage } = makeTransport();
    post.mockResolvedValue(false);
    const client = createTrackClient(transport);
    client.track("page.view");
    await vi.advanceTimersByTimeAsync(TRACK_FLUSH_MS);
    expect(post).toHaveBeenCalledTimes(3);
    const pending = JSON.parse(storage.getItem(TRACK_PENDING_KEY) ?? "[]") as unknown[];
    expect(pending).toHaveLength(1);
    client.dispose();
  });

  it("uses sendBeacon on unload and persists when beacon returns false", async () => {
    const { transport, beacon, post, storage } = makeTransport();
    beacon.mockReturnValue(false);
    const client = createTrackClient(transport);
    client.track("page.leave", { route: "/home", durationSeconds: 3 });
    await client.flush({ beacon: true });
    expect(beacon).toHaveBeenCalledTimes(1);
    expect(post).not.toHaveBeenCalled();
    const pending = JSON.parse(storage.getItem(TRACK_PENDING_KEY) ?? "[]") as unknown[];
    expect(pending).toHaveLength(1);
    client.dispose();
  });

  it("replays persisted events on next visit", async () => {
    const { transport, post, storage } = makeTransport();
    storage.setItem(
      TRACK_PENDING_KEY,
      JSON.stringify([{ code: "page.leave", clientTime: "2026-08-20T00:00:00.000Z", props: { route: "/home" } }]),
    );
    const client = createTrackClient(transport);
    client.replay();
    await vi.runAllTimersAsync();
    expect(post).toHaveBeenCalledTimes(1);
    const body = post.mock.calls[0]?.[0] as { events?: { code?: string }[] };
    expect(body.events?.[0]?.code).toBe("page.leave");
    expect(storage.getItem(TRACK_PENDING_KEY)).toBeNull();
    client.dispose();
  });

  it("does not throw when the transport rejects", async () => {
    const { transport, post } = makeTransport();
    post.mockRejectedValue(new Error("offline"));
    const client = createTrackClient(transport);
    expect(() => client.track("page.view")).not.toThrow();
    await vi.advanceTimersByTimeAsync(TRACK_FLUSH_MS);
    expect(post).toHaveBeenCalledTimes(3);
    client.dispose();
  });
});
