import { describe, expect, it } from "vitest";
import { DEVICE_ID_KEY, ensureDeviceId, isUuidV4 } from "./device-id";

describe("ensureDeviceId", () => {
  it("reuses a stored uuid v4 and replaces illegal values", () => {
    const store = new Map<string, string>();
    const storage = {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => {
        store.set(key, value);
      },
    };
    store.set(DEVICE_ID_KEY, "not-a-uuid");
    const created = ensureDeviceId(storage);
    expect(isUuidV4(created)).toBe(true);
    expect(ensureDeviceId(storage)).toBe(created);
  });
});
