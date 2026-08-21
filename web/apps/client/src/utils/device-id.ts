export const DEVICE_ID_KEY = "mkt.deviceId";
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isUuidV4(value: string | null | undefined): boolean {
  return Boolean(value && UUID_V4.test(value));
}

export function ensureDeviceId(storage: Pick<Storage, "getItem" | "setItem"> | null = defaultStorage()): string {
  if (!storage) {
    return newDeviceId();
  }
  const existing = storage.getItem(DEVICE_ID_KEY);
  if (isUuidV4(existing)) {
    return existing as string;
  }
  const created = newDeviceId();
  storage.setItem(DEVICE_ID_KEY, created);
  return created;
}

function newDeviceId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "00000000-0000-4000-8000-000000000000";
}

function defaultStorage(): Storage | null {
  if (typeof localStorage === "undefined") {
    return null;
  }
  return localStorage;
}
