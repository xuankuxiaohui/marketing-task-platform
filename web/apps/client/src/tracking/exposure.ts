import { TRACK } from "./codes";
import { track } from "./client";

export const EXPOSURE_RATIO = 0.5;
export const EXPOSURE_MS = 500;

export type ExposureTarget = {
  taskId?: number;
  taskCode?: string;
};

export type ExposureObserverApi = {
  observe(el: Element, target: ExposureTarget): () => void;
  seen(): ReadonlySet<string>;
  dispose(): void;
};

function exposureKey(target: ExposureTarget): string | undefined {
  if (target.taskId == null) {
    return undefined;
  }
  return String(target.taskId);
}

export function createExposureObserver(
  emit: (code: string, props: Record<string, unknown>) => void = track,
  now: () => number = () => Date.now(),
): ExposureObserverApi {
  const seen = new Set<string>();
  const pending = new Map<Element, { key: string; target: ExposureTarget; timer?: ReturnType<typeof setTimeout> }>();

  function fire(record: { key: string; target: ExposureTarget }): void {
    if (seen.has(record.key)) {
      return;
    }
    seen.add(record.key);
    const props: Record<string, unknown> = { taskId: record.target.taskId };
    if (record.target.taskCode) {
      props.taskCode = record.target.taskCode;
    }
    emit(TRACK.TASK_CARD_EXPOSURE, props);
  }

  const observer =
    typeof IntersectionObserver === "undefined"
      ? null
      : new IntersectionObserver(
          (entries) => {
            for (const entry of entries) {
              const record = pending.get(entry.target);
              if (!record) {
                continue;
              }
              const visible = entry.isIntersecting && entry.intersectionRatio >= EXPOSURE_RATIO;
              if (!visible) {
                if (record.timer != null) {
                  clearTimeout(record.timer);
                  record.timer = undefined;
                }
                continue;
              }
              if (seen.has(record.key) || record.timer != null) {
                continue;
              }
              const started = now();
              record.timer = setTimeout(() => {
                record.timer = undefined;
                if (now() - started < EXPOSURE_MS) {
                  return;
                }
                fire(record);
              }, EXPOSURE_MS);
            }
          },
          { threshold: [EXPOSURE_RATIO] },
        );

  function observe(el: Element, target: ExposureTarget): () => void {
    const key = exposureKey(target);
    if (!key || !observer) {
      return () => undefined;
    }
    pending.set(el, { key, target });
    observer.observe(el);
    return () => {
      const record = pending.get(el);
      if (record?.timer != null) {
        clearTimeout(record.timer);
      }
      pending.delete(el);
      observer.unobserve(el);
    };
  }

  function dispose(): void {
    for (const record of pending.values()) {
      if (record.timer != null) {
        clearTimeout(record.timer);
      }
    }
    pending.clear();
    observer?.disconnect();
  }

  return {
    observe,
    seen: () => seen,
    dispose,
  };
}

let singleton: ExposureObserverApi | undefined;

export function getExposureObserver(): ExposureObserverApi {
  if (!singleton) {
    singleton = createExposureObserver();
  }
  return singleton;
}

export function observeTaskCardExposure(el: Element, target: ExposureTarget): () => void {
  return getExposureObserver().observe(el, target);
}
