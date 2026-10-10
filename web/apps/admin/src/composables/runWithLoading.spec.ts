import { defineComponent, ref } from "vue";
import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { runWithLoading } from "./runWithLoading";
import { useLatestRequest } from "./useLatestRequest";

function deferred<T = void>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

describe("runWithLoading", () => {
  it("clears loading when the request throws", async () => {
    const loading = ref(false);
    const isCurrent = () => true;
    await expect(
      runWithLoading(loading, isCurrent, async () => {
        expect(loading.value).toBe(true);
        throw new Error("network");
      }),
    ).rejects.toThrow("network");
    expect(loading.value).toBe(false);
  });

  it("does not clear loading for a superseded request", async () => {
    const loading = ref(false);
    let generation = 0;
    const begin = () => {
      const g = ++generation;
      return () => g === generation;
    };

    const first = deferred();
    const isFirst = begin();
    const firstRun = runWithLoading(loading, isFirst, async () => {
      await first.promise;
    });
    expect(loading.value).toBe(true);

    const second = deferred();
    const isSecond = begin();
    const secondRun = runWithLoading(loading, isSecond, async () => {
      await second.promise;
    });
    expect(loading.value).toBe(true);

    first.resolve();
    await firstRun;
    // Stale finally must not clear the newer request's spinner.
    expect(loading.value).toBe(true);
    expect(isFirst()).toBe(false);
    expect(isSecond()).toBe(true);

    second.resolve();
    await secondRun;
    expect(loading.value).toBe(false);
  });

  it("ignores stale finally after useLatestRequest generation bump", async () => {
    const loading = ref(false);
    const scope = ref("a");
    let begin!: () => () => boolean;
    const wrapper = mount(
      defineComponent({
        setup() {
          begin = useLatestRequest(() => scope.value);
          return () => null;
        },
      }),
    );

    const old = deferred();
    const isOld = begin();
    const oldRun = runWithLoading(loading, isOld, async () => {
      await old.promise;
    });
    expect(loading.value).toBe(true);

    scope.value = "b";
    const next = deferred();
    const isNext = begin();
    const nextRun = runWithLoading(loading, isNext, async () => {
      await next.promise;
    });

    old.reject(new Error("stale"));
    await expect(oldRun).rejects.toThrow("stale");
    expect(loading.value).toBe(true);
    expect(isOld()).toBe(false);

    next.resolve();
    await nextRun;
    expect(loading.value).toBe(false);
    wrapper.unmount();
  });

  it("clears loading on success when still current", async () => {
    const loading = ref(false);
    const work = vi.fn().mockResolvedValue(undefined);
    await runWithLoading(loading, () => true, work);
    expect(work).toHaveBeenCalledOnce();
    expect(loading.value).toBe(false);
    await flushPromises();
  });
});
