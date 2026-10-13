import type { Ref } from "vue";

export type ListQueryOutcome = "ok" | "fail" | "stale";

/**
 * Runs a list fetch under the shared loading + latest-request guards.
 * Catches thrown errors (network / 5xx) so callers can show an in-page
 * failure state instead of an unhandled rejection. `fn` returns false for
 * API-level Result failures (without putting raw codes into UI copy).
 */
export async function runListQuery(
  loading: Ref<boolean>,
  isCurrent: () => boolean,
  fn: () => Promise<boolean>,
): Promise<ListQueryOutcome> {
  loading.value = true;
  try {
    const success = await fn();
    if (!isCurrent()) {
      return "stale";
    }
    return success ? "ok" : "fail";
  } catch {
    if (!isCurrent()) {
      return "stale";
    }
    return "fail";
  } finally {
    if (isCurrent()) {
      loading.value = false;
    }
  }
}

/** Apply list outcome: clear rows on fail; never leave stale empty-vs-fail ambiguity. */
export function applyListQueryOutcome(
  outcome: ListQueryOutcome,
  listFailed: Ref<boolean>,
  clearRows: () => void,
): void {
  if (outcome === "stale") {
    return;
  }
  if (outcome === "fail") {
    listFailed.value = true;
    clearRows();
    return;
  }
  listFailed.value = false;
}
