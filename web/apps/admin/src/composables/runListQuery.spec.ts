import { ref } from "vue";
import { describe, expect, it, vi } from "vitest";
import { applyListQueryOutcome, runListQuery } from "./runListQuery";

describe("runListQuery", () => {
  it("returns ok when fn succeeds and request is current", async () => {
    const loading = ref(false);
    const outcome = await runListQuery(loading, () => true, async () => true);
    expect(outcome).toBe("ok");
    expect(loading.value).toBe(false);
  });

  it("returns fail when fn reports API failure", async () => {
    const loading = ref(false);
    const outcome = await runListQuery(loading, () => true, async () => false);
    expect(outcome).toBe("fail");
    expect(loading.value).toBe(false);
  });

  it("returns fail and clears loading when fn throws (network)", async () => {
    const loading = ref(false);
    const outcome = await runListQuery(loading, () => true, async () => {
      throw new Error("network");
    });
    expect(outcome).toBe("fail");
    expect(loading.value).toBe(false);
  });

  it("returns stale without clearing a newer request's spinner", async () => {
    const loading = ref(false);
    let generation = 0;
    const begin = () => {
      const g = ++generation;
      return () => g === generation;
    };

    let resolveFirst!: () => void;
    const firstGate = new Promise<void>((resolve) => {
      resolveFirst = resolve;
    });
    const isFirst = begin();
    const firstRun = runListQuery(loading, isFirst, async () => {
      await firstGate;
      return true;
    });
    expect(loading.value).toBe(true);

    let resolveSecond!: () => void;
    const secondGate = new Promise<void>((resolve) => {
      resolveSecond = resolve;
    });
    const isSecond = begin();
    const secondRun = runListQuery(loading, isSecond, async () => {
      await secondGate;
      return true;
    });
    expect(loading.value).toBe(true);

    resolveFirst();
    expect(await firstRun).toBe("stale");
    // Stale finally must not clear the newer request's spinner.
    expect(loading.value).toBe(true);
    expect(isFirst()).toBe(false);
    expect(isSecond()).toBe(true);

    resolveSecond();
    expect(await secondRun).toBe("ok");
    expect(loading.value).toBe(false);
  });
});

describe("applyListQueryOutcome", () => {
  it("marks failed and clears rows on fail", () => {
    const listFailed = ref(false);
    const clear = vi.fn();
    applyListQueryOutcome("fail", listFailed, clear);
    expect(listFailed.value).toBe(true);
    expect(clear).toHaveBeenCalledOnce();
  });

  it("clears failed flag on ok without clearing rows", () => {
    const listFailed = ref(true);
    const clear = vi.fn();
    applyListQueryOutcome("ok", listFailed, clear);
    expect(listFailed.value).toBe(false);
    expect(clear).not.toHaveBeenCalled();
  });

  it("ignores stale", () => {
    const listFailed = ref(false);
    const clear = vi.fn();
    applyListQueryOutcome("stale", listFailed, clear);
    expect(listFailed.value).toBe(false);
    expect(clear).not.toHaveBeenCalled();
  });
});
