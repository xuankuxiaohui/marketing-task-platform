export const PORTAL_TOKEN_KEY = "mkt.portal.token";

export function readPortalToken(storage: Pick<Storage, "getItem"> | null = defaultStorage()): string {
  if (!storage) {
    return "";
  }
  return storage.getItem(PORTAL_TOKEN_KEY) ?? "";
}

export function writePortalToken(
  token: string,
  storage: Pick<Storage, "setItem" | "removeItem"> | null = defaultStorage(),
): void {
  if (!storage) {
    return;
  }
  if (!token) {
    storage.removeItem(PORTAL_TOKEN_KEY);
    return;
  }
  storage.setItem(PORTAL_TOKEN_KEY, token);
}

function defaultStorage(): Storage | null {
  if (typeof localStorage === "undefined") {
    return null;
  }
  return localStorage;
}
