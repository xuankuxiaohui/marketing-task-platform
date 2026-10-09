import { defineComponent, ref } from "vue";
import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { fail, ok } from "@/test-utils/result";
import { usePagedList } from "./usePagedList";

type Item = { id: number };
type Page = { total?: number; records?: Item[] };

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

function setup(fetchPage = vi.fn<Parameters<typeof usePagedList<Item>>[0]["fetchPage"]>()) {
  const token = ref("account-a");
  const filter = ref("ALL");
  let list!: ReturnType<typeof usePagedList<Item>>;
  const wrapper = mount(defineComponent({
    setup() {
      list = usePagedList<Item>({
        scope: () => [token.value, filter.value],
        enabled: () => Boolean(token.value),
        fetchPage,
      });
      return () => null;
    },
  }));
  return { list, token, filter, fetchPage, wrapper };
}

describe("usePagedList", () => {
  it("loads the next page even after Vant has raised its loading flag", async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(ok<Page>({ total: 2, records: [{ id: 1 }] }))
      .mockResolvedValueOnce(ok<Page>({ total: 2, records: [{ id: 2 }] }));
    const { list, wrapper } = setup(fetchPage);
    await flushPromises();
    list.loading.value = true;
    await list.loadMore();
    expect(fetchPage).toHaveBeenNthCalledWith(2, 2, 20);
    expect(list.records.value).toEqual([{ id: 1 }, { id: 2 }]);
    expect(list.finished.value).toBe(true);
    expect(list.loading.value).toBe(false);
    wrapper.unmount();
  });

  it("does not launch a second request while the current page is pending", async () => {
    const pending = deferred<ReturnType<typeof ok<Page>>>();
    const fetchPage = vi.fn().mockReturnValue(pending.promise);
    const { list, wrapper } = setup(fetchPage);
    await Promise.all([list.loadMore(), list.loadMore()]);
    expect(fetchPage).toHaveBeenCalledTimes(1);
    pending.resolve(ok({ total: 0, records: [] }));
    await flushPromises();
    expect(list.empty.value).toBe(true);
    wrapper.unmount();
  });

  it("shows first-page failure as an error and retries page one", async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(fail("common.server-error", "暂时无法读取"))
      .mockResolvedValueOnce(ok<Page>({ total: 0, records: [] }));
    const { list, wrapper } = setup(fetchPage);
    await flushPromises();
    expect(list.error.value).toBe(true);
    expect(list.errorMessage.value).toBe("暂时无法读取");
    expect(list.empty.value).toBe(false);
    expect(list.finished.value).toBe(false);
    await list.retry();
    expect(fetchPage).toHaveBeenNthCalledWith(2, 1, 20);
    expect(list.error.value).toBe(false);
    expect(list.empty.value).toBe(true);
    wrapper.unmount();
  });

  it("preserves loaded rows and retries the failed page without skipping it", async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(ok<Page>({ total: 2, records: [{ id: 1 }] }))
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(ok<Page>({ total: 2, records: [{ id: 2 }] }));
    const { list, wrapper } = setup(fetchPage);
    await flushPromises();
    await list.loadMore();
    expect(list.records.value).toEqual([{ id: 1 }]);
    expect(list.error.value).toBe(true);
    expect(list.finished.value).toBe(false);
    await list.retry();
    expect(fetchPage).toHaveBeenNthCalledWith(3, 2, 20);
    expect(list.records.value).toEqual([{ id: 1 }, { id: 2 }]);
    wrapper.unmount();
  });

  it("shows loading rather than empty while a first-page retry is pending", async () => {
    const pending = deferred<ReturnType<typeof ok<Page>>>();
    const fetchPage = vi.fn()
      .mockRejectedValueOnce(new Error("offline"))
      .mockReturnValueOnce(pending.promise);
    const { list, wrapper } = setup(fetchPage);
    await flushPromises();
    const retry = list.retry();
    expect(list.loading.value).toBe(true);
    expect(list.error.value).toBe(false);
    expect(list.empty.value).toBe(false);
    pending.resolve(ok({ total: 0, records: [] }));
    await retry;
    expect(list.empty.value).toBe(true);
    wrapper.unmount();
  });

  it("clears the old filter immediately and ignores its late response and finally", async () => {
    const old = deferred<ReturnType<typeof ok<Page>>>();
    const next = deferred<ReturnType<typeof ok<Page>>>();
    const fetchPage = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise);
    const { list, filter, wrapper } = setup(fetchPage);
    filter.value = "COMPLETED";
    old.resolve(ok({ total: 1, records: [{ id: 1 }] }));
    await flushPromises();
    expect(list.records.value).toEqual([]);
    expect(list.loading.value).toBe(true);
    expect(list.loaded.value).toBe(false);
    next.resolve(ok({ total: 1, records: [{ id: 2 }] }));
    await flushPromises();
    expect(list.records.value).toEqual([{ id: 2 }]);
    expect(list.loading.value).toBe(false);
    wrapper.unmount();
  });

  it("replaces the window on refresh and ignores an older next-page failure", async () => {
    const old = deferred<ReturnType<typeof ok<Page>>>();
    const refresh = deferred<ReturnType<typeof ok<Page>>>();
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(ok<Page>({ total: 3, records: [{ id: 1 }] }))
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(refresh.promise)
      .mockResolvedValueOnce(ok<Page>({ total: 2, records: [{ id: 3 }] }));
    const { list, wrapper } = setup(fetchPage);
    await flushPromises();
    const more = list.loadMore();
    const refreshing = list.refresh();
    expect(list.records.value).toEqual([{ id: 1 }]);
    old.reject(new Error("old failure"));
    await more;
    expect(list.error.value).toBe(false);
    expect(list.refreshing.value).toBe(true);
    expect(list.loading.value).toBe(true);
    refresh.resolve(ok({ total: 2, records: [{ id: 2 }] }));
    await refreshing;
    expect(list.records.value).toEqual([{ id: 2 }]);
    await list.loadMore();
    expect(fetchPage).toHaveBeenNthCalledWith(4, 2, 20);
    expect(list.records.value).toEqual([{ id: 2 }, { id: 3 }]);
    wrapper.unmount();
  });

  it("retries a failed refresh from page one while keeping the previous window", async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(ok<Page>({ total: 3, records: [{ id: 1 }] }))
      .mockRejectedValueOnce(new Error("refresh failed"))
      .mockResolvedValueOnce(ok<Page>({ total: 1, records: [{ id: 2 }] }));
    const { list, wrapper } = setup(fetchPage);
    await flushPromises();
    await list.refresh();
    expect(list.records.value).toEqual([{ id: 1 }]);
    expect(list.refreshing.value).toBe(false);
    await list.retry();
    expect(fetchPage).toHaveBeenNthCalledWith(3, 1, 20);
    expect(list.records.value).toEqual([{ id: 2 }]);
    wrapper.unmount();
  });

  it("invalidates account data synchronously and never accepts the old account response", async () => {
    const old = deferred<ReturnType<typeof ok<Page>>>();
    const fetchPage = vi.fn()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(ok<Page>({ total: 1, records: [{ id: 2 }] }));
    const { list, token, wrapper } = setup(fetchPage);
    token.value = "account-b";
    await flushPromises();
    old.resolve(ok({ total: 1, records: [{ id: 1 }] }));
    await flushPromises();
    expect(list.records.value).toEqual([{ id: 2 }]);
    token.value = "";
    expect(list.records.value).toEqual([]);
    expect(list.total.value).toBe(0);
    expect(list.empty.value).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it("discards results after logout without marking an error or loading guest data", async () => {
    const pending = deferred<ReturnType<typeof ok<Page>>>();
    const fetchPage = vi.fn().mockReturnValue(pending.promise);
    const { list, token, wrapper } = setup(fetchPage);
    token.value = "";
    pending.reject(new Error("late old-session failure"));
    await flushPromises();
    expect(list.records.value).toEqual([]);
    expect(list.error.value).toBe(false);
    expect(list.loading.value).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it("does not accept a response or load another page after disposal", async () => {
    const pending = deferred<ReturnType<typeof ok<Page>>>();
    const fetchPage = vi.fn().mockReturnValue(pending.promise);
    const { list, wrapper } = setup(fetchPage);
    wrapper.unmount();
    pending.resolve(ok({ total: 1, records: [{ id: 1 }] }));
    await flushPromises();
    await list.loadMore();
    expect(list.records.value).toEqual([]);
    expect(list.loaded.value).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });
});
