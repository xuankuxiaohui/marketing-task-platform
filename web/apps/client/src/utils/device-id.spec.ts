import { afterEach, describe, expect, it, vi } from "vitest";
import { DEVICE_ID_KEY, NIL_DEVICE_ID, ensureDeviceId, isUuidV4, newDeviceId } from "./device-id";

function memoryStorage(initial?: string) {
  const store = new Map<string, string>();
  if (initial !== undefined) {
    store.set(DEVICE_ID_KEY, initial);
  }
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    store,
  };
}

describe("ensureDeviceId", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("reuses a stored uuid v4 and replaces illegal values", () => {
    const storage = memoryStorage("not-a-uuid");
    const created = ensureDeviceId(storage);
    expect(isUuidV4(created)).toBe(true);
    expect(created).not.toBe(NIL_DEVICE_ID);
    expect(ensureDeviceId(storage)).toBe(created);
  });

  it("treats the all-zero fallback as invalid and writes a new id", () => {
    const storage = memoryStorage(NIL_DEVICE_ID);
    const created = ensureDeviceId(storage);
    expect(isUuidV4(created)).toBe(true);
    expect(created).not.toBe(NIL_DEVICE_ID);
    expect(storage.getItem(DEVICE_ID_KEY)).toBe(created);
    expect(ensureDeviceId(storage)).toBe(created);
  });

  it("still mints a unique v4 when crypto.randomUUID is missing", () => {
    vi.stubGlobal("crypto", { getRandomValues: crypto.getRandomValues.bind(crypto) });
    const first = newDeviceId();
    const second = newDeviceId();
    expect(isUuidV4(first)).toBe(true);
    expect(isUuidV4(second)).toBe(true);
    expect(first).not.toBe(NIL_DEVICE_ID);
    expect(second).not.toBe(NIL_DEVICE_ID);
    expect(first).not.toBe(second);

    const storage = memoryStorage();
    const persisted = ensureDeviceId(storage);
    expect(persisted).toBe(storage.getItem(DEVICE_ID_KEY));
    expect(persisted).not.toBe(NIL_DEVICE_ID);
  });
});
