import { onScopeDispose, watch } from "vue";

/** Creates guards for the latest request within a reactive scope. */
export function useLatestRequest(scope: () => unknown) {
  let generation = 0;
  let disposed = false;

  watch(scope, () => {
    generation += 1;
  }, { flush: "sync" });

  onScopeDispose(() => {
    disposed = true;
    generation += 1;
  });

  return function beginRequest(): () => boolean {
    const requestGeneration = ++generation;
    return () => !disposed && requestGeneration === generation;
  };
}
