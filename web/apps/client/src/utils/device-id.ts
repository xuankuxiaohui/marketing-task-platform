export const DEVICE_ID_KEY = "mkt.deviceId";
export const NIL_DEVICE_ID = "00000000-0000-4000-8000-000000000000";
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isUuidV4(value: string | null | undefined): boolean {
  return Boolean(value && UUID_V4.test(value));
}

export function isUsableDeviceId(value: string | null | undefined): boolean {
  return isUuidV4(value) && value !== NIL_DEVICE_ID;
}

export function ensureDeviceId(storage: Pick<Storage, "getItem" | "setItem"> | null = defaultStorage()): string {
  if (!storage) {
    return newDeviceId();
  }
  const existing = storage.getItem(DEVICE_ID_KEY);
  if (isUsableDeviceId(existing)) {
    return existing as string;
  }
  const created = newDeviceId();
  storage.setItem(DEVICE_ID_KEY, created);
  return created;
}

export function newDeviceId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    const generated = crypto.randomUUID();
    if (isUsableDeviceId(generated)) {
      return generated;
    }
  }
  return randomUuidV4();
}

function randomUuidV4(): string {
  const bytes = new Uint8Array(16);
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function defaultStorage(): Storage | null {
  if (typeof localStorage === "undefined") {
    return null;
  }
  return localStorage;
}
