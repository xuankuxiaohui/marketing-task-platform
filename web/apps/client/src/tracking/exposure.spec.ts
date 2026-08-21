import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EXPOSURE_MS, EXPOSURE_RATIO, createExposureObserver } from "./exposure";
import { TRACK } from "./codes";

type ObserverInstance = {
  callback: IntersectionObserverCallback;
  observe: ReturnType<typeof vi.fn>;
  unobserve: ReturnType<typeof vi.fn>;
  disconnect: ReturnType<typeof vi.fn>;
};

describe("task card exposure", () => {
  let instances: ObserverInstance[] = [];
  let now = 0;

  beforeEach(() => {
    vi.useFakeTimers();
    instances = [];
    now = 0;
    class FakeIntersectionObserver {
      callback: IntersectionObserverCallback;
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
      constructor(callback: IntersectionObserverCallback) {
        this.callback = callback;
        instances.push(this);
      }
      takeRecords(): IntersectionObserverEntry[] {
        return [];
      }
      root = null;
      rootMargin = "";
      thresholds = [EXPOSURE_RATIO];
    }
    globalThis.IntersectionObserver = FakeIntersectionObserver as unknown as typeof IntersectionObserver;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function entry(target: Element, ratio: number, intersecting = ratio >= EXPOSURE_RATIO): IntersectionObserverEntry {
    return {
      target,
      intersectionRatio: ratio,
      isIntersecting: intersecting,
      time: now,
      boundingClientRect: {} as DOMRectReadOnly,
      intersectionRect: {} as DOMRectReadOnly,
      rootBounds: null,
    } as IntersectionObserverEntry;
  }

  it("counts one exposure after 50% visible for 500ms and ignores repeats in the page session", () => {
    const emit = vi.fn();
    const observer = createExposureObserver(emit, () => now);
    const el = document.createElement("article");
    observer.observe(el, { taskId: 9, taskCode: "daily" });
    instances[0]?.callback([entry(el, 0.5)], instances[0] as unknown as IntersectionObserver);
    now = EXPOSURE_MS;
    vi.advanceTimersByTime(EXPOSURE_MS);
    expect(emit).toHaveBeenCalledTimes(1);
    expect(emit).toHaveBeenCalledWith(TRACK.TASK_CARD_EXPOSURE, { taskId: 9, taskCode: "daily" });

    instances[0]?.callback([entry(el, 0.9)], instances[0] as unknown as IntersectionObserver);
    now += EXPOSURE_MS;
    vi.advanceTimersByTime(EXPOSURE_MS);
    expect(emit).toHaveBeenCalledTimes(1);
    observer.dispose();
  });

  it("does not count when the card leaves the viewport before 500ms", () => {
    const emit = vi.fn();
    const observer = createExposureObserver(emit, () => now);
    const el = document.createElement("article");
    observer.observe(el, { taskId: 3, taskCode: "t3" });
    instances[0]?.callback([entry(el, 0.6)], instances[0] as unknown as IntersectionObserver);
    now = 200;
    vi.advanceTimersByTime(200);
    instances[0]?.callback([entry(el, 0.1, false)], instances[0] as unknown as IntersectionObserver);
    now = EXPOSURE_MS;
    vi.advanceTimersByTime(EXPOSURE_MS);
    expect(emit).not.toHaveBeenCalled();
    observer.dispose();
  });
});
