import { isOk } from "@mkt/shared";
import { postTrackBatch, TRACK_BATCH_PATH, type TrackBatchBody, type TrackEventCommand } from "@/api/track";
import { CLIENT_PLATFORM } from "@/api/http";

export const TRACK_FLUSH_SIZE = 20;
export const TRACK_FLUSH_MS = 5000;
export const TRACK_MAX_RETRY = 2;
export const TRACK_BATCH_MAX = 50;
export const TRACK_PENDING_KEY = "mkt.track.pending";
export const TRACK_APP_VERSION = "0.1.0";

export type TrackProps = Record<string, unknown>;

export type TrackTransport = {
  post(body: TrackBatchBody): Promise<boolean>;
  beacon(body: TrackBatchBody): boolean;
  now(): number;
  storage: Pick<Storage, "getItem" | "setItem" | "removeItem"> | null;
};

export type TrackClient = {
  track(code: string, props?: TrackProps): void;
  flush(options?: { beacon?: boolean }): Promise<void>;
  replay(): void;
  size(): number;
  dispose(): void;
};

function iso(nowMs: number): string {
  return new Date(nowMs).toISOString();
}

function readPending(storage: TrackTransport["storage"]): TrackEventCommand[] {
  if (!storage) {
    return [];
  }
  try {
    const raw = storage.getItem(TRACK_PENDING_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((row): row is TrackEventCommand => {
      return row != null && typeof row === "object" && typeof (row as TrackEventCommand).code === "string";
    });
  } catch {
    return [];
  }
}

function writePending(storage: TrackTransport["storage"], events: TrackEventCommand[]): void {
  if (!storage) {
    return;
  }
  try {
    if (events.length === 0) {
      storage.removeItem(TRACK_PENDING_KEY);
      return;
    }
    storage.setItem(TRACK_PENDING_KEY, JSON.stringify(events));
  } catch {
    // quota / private mode — tracking must not throw
  }
}

function payload(events: TrackEventCommand[]): TrackBatchBody {
  return {
    events,
    platform: CLIENT_PLATFORM,
    appVersion: TRACK_APP_VERSION,
  };
}

export function createTrackClient(transport: TrackTransport): TrackClient {
  let buffer: TrackEventCommand[] = [];
  let timer: ReturnType<typeof setTimeout> | undefined;
  let flushing = false;

  function persist(): void {
    writePending(transport.storage, buffer);
  }

  function armTimer(): void {
    if (timer != null) {
      return;
    }
    timer = setTimeout(() => {
      timer = undefined;
      void flush();
    }, TRACK_FLUSH_MS);
  }

  function clearTimer(): void {
    if (timer != null) {
      clearTimeout(timer);
      timer = undefined;
    }
  }

  async function sendHttp(batch: TrackEventCommand[]): Promise<boolean> {
    const body = payload(batch);
    for (let attempt = 0; attempt <= TRACK_MAX_RETRY; attempt += 1) {
      try {
        if (await transport.post(body)) {
          return true;
        }
      } catch {
        // retry
      }
    }
    return false;
  }

  async function flush(options: { beacon?: boolean } = {}): Promise<void> {
    if (options.beacon) {
      clearTimer();
      if (buffer.length === 0) {
        persist();
        return;
      }
      const batch = buffer.splice(0, TRACK_BATCH_MAX);
      const ok = transport.beacon(payload(batch));
      if (!ok) {
        buffer = batch.concat(buffer);
      }
      persist();
      return;
    }
    if (flushing) {
      return;
    }
    if (buffer.length === 0) {
      clearTimer();
      persist();
      return;
    }
    flushing = true;
    let failed = false;
    try {
      while (buffer.length > 0) {
        const batch = buffer.splice(0, TRACK_FLUSH_SIZE);
        const ok = await sendHttp(batch);
        if (!ok) {
          buffer = batch.concat(buffer);
          persist();
          failed = true;
          return;
        }
        persist();
      }
      clearTimer();
    } finally {
      flushing = false;
      if (!failed && buffer.length >= TRACK_FLUSH_SIZE) {
        void flush();
      } else if (!failed && buffer.length > 0) {
        armTimer();
      }
    }
  }

  function replay(): void {
    const pending = readPending(transport.storage);
    if (pending.length === 0) {
      return;
    }
    buffer = pending.concat(buffer);
    persist();
    void flush();
  }

  function track(code: string, props?: TrackProps): void {
    try {
      const event: TrackEventCommand = {
        code,
        clientTime: iso(transport.now()),
      };
      if (props && Object.keys(props).length > 0) {
        event.props = props;
      }
      buffer.push(event);
      persist();
      if (buffer.length >= TRACK_FLUSH_SIZE) {
        clearTimer();
        void flush();
        return;
      }
      armTimer();
    } catch {
      // never block UI
    }
  }

  function dispose(): void {
    clearTimer();
  }

  return {
    track,
    flush,
    replay,
    size: () => buffer.length,
    dispose,
  };
}

export function defaultTrackTransport(): TrackTransport {
  return {
    async post(body) {
      const result = await postTrackBatch(body);
      return isOk(result);
    },
    beacon(body) {
      if (typeof navigator === "undefined" || typeof navigator.sendBeacon !== "function") {
        return false;
      }
      try {
        const blob = new Blob([JSON.stringify(body)], { type: "application/json" });
        return navigator.sendBeacon(TRACK_BATCH_PATH, blob);
      } catch {
        return false;
      }
    },
    now: () => Date.now(),
    storage: typeof localStorage === "undefined" ? null : localStorage,
  };
}

let singleton: TrackClient | undefined;

export function getTrackClient(): TrackClient {
  if (!singleton) {
    singleton = createTrackClient(defaultTrackTransport());
  }
  return singleton;
}

export function track(code: string, props?: TrackProps): void {
  getTrackClient().track(code, props);
}

export function replayPendingTracks(): void {
  getTrackClient().replay();
}

export function flushTracks(options?: { beacon?: boolean }): Promise<void> {
  return getTrackClient().flush(options);
}
