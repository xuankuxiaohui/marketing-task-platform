import type { Ref } from "vue";

/**
 * Sets `loading` while `fn` runs and always clears it in `finally` when the
 * request is still current. Pair with `useLatestRequest` so a superseded
 * request does not clear a newer load's spinner.
 */
export async function runWithLoading(
  loading: Ref<boolean>,
  isCurrent: () => boolean,
  fn: () => Promise<void>,
): Promise<void> {
  loading.value = true;
  try {
    await fn();
  } finally {
    if (isCurrent()) {
      loading.value = false;
    }
  }
}
