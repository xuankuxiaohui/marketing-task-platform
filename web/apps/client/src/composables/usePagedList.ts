import { computed, onScopeDispose, ref, shallowRef, watch } from "vue";
import { isOk, type Result } from "@mkt/shared";
import { zhCN } from "@/locales/zh-CN";
import { resultMessage } from "@/utils/portal-error";

type PagedListOptions<T> = {
  scope: () => readonly unknown[];
  enabled: () => boolean;
  fetchPage: (page: number, pageSize: number) => Promise<Result<{ records?: T[]; total?: number }>>;
  pageSize?: number;
};

/** Owns request state independently of Vant's loading flag and isolates replaced queries. */
export function usePagedList<T>(options: PagedListOptions<T>) {
  const records = shallowRef<T[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const refreshing = ref(false);
  const loaded = ref(false);
  const finished = ref(false);
  const error = ref(false);
  const errorMessage = ref("");
  const empty = computed(() => options.enabled() && loaded.value && !loading.value
    && !error.value && records.value.length === 0);
  const pageSize = options.pageSize ?? 20;
  let generation = 0;
  let nextPage = 1;
  let disposed = false;
  let activeRequest: object | null = null;

  async function loadMore(): Promise<void> {
    // Vant sets loading=true before emitting load; only this request handle is a lock.
    if (disposed || activeRequest !== null) {
      return;
    }
    if (!options.enabled() || finished.value) {
      loading.value = false;
      refreshing.value = false;
      return;
    }

    const request = {};
    const requestGeneration = generation;
    const requestedPage = nextPage;
    activeRequest = request;
    loading.value = true;
    error.value = false;
    errorMessage.value = "";
    const current = () => !disposed && generation === requestGeneration && activeRequest === request;

    try {
      const result = await options.fetchPage(requestedPage, pageSize);
      if (!current()) {
        return;
      }
      if (!isOk(result) || !result.data) {
        error.value = true;
        errorMessage.value = resultMessage(result);
        return;
      }
      const next = result.data.records ?? [];
      records.value = requestedPage === 1 ? next : [...records.value, ...next];
      total.value = Number(result.data.total ?? records.value.length);
      nextPage = requestedPage + 1;
      finished.value = records.value.length >= total.value || next.length === 0;
    } catch {
      if (current()) {
        error.value = true;
        errorMessage.value = zhCN.common.networkError;
      }
    } finally {
      if (current()) {
        activeRequest = null;
        loading.value = false;
        refreshing.value = false;
        loaded.value = true;
      }
    }
  }

  async function reset(preserveRecords: boolean): Promise<void> {
    if (disposed) {
      return;
    }
    generation += 1;
    activeRequest = null;
    nextPage = 1;
    error.value = false;
    errorMessage.value = "";
    finished.value = false;
    loaded.value = false;
    loading.value = false;
    refreshing.value = preserveRecords;
    if (!preserveRecords || !options.enabled()) {
      records.value = [];
      total.value = 0;
    }
    if (!options.enabled()) {
      finished.value = true;
      refreshing.value = false;
      return;
    }
    await loadMore();
  }

  function reload(): Promise<void> {
    return reset(false);
  }

  function refresh(): Promise<void> {
    return reset(true);
  }

  function retry(): Promise<void> {
    return loadMore();
  }

  watch(options.scope, () => void reload(), { immediate: true, flush: "sync" });
  onScopeDispose(() => {
    disposed = true;
    generation += 1;
    activeRequest = null;
  });

  return {
    records,
    total,
    loading,
    refreshing,
    loaded,
    finished,
    error,
    errorMessage,
    empty,
    loadMore,
    reload,
    refresh,
    retry,
  };
}
