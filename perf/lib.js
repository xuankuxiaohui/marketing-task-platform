import crypto from "k6/crypto";
import http from "k6/http";
import { check } from "k6";

export const BASE = __ENV.BASE_URL || "http://nginx";
export const INTERNAL = __ENV.INTERNAL_URL || "http://portal-app-1:8081";
export const DURATION = __ENV.DURATION || "5m";
export const STATE_FILE = __ENV.STATE_FILE || "./state.json";

export function loadState() {
  return JSON.parse(open(STATE_FILE));
}

export function jsonHeaders(extra) {
  const headers = { Accept: "application/json", "Content-Type": "application/json" };
  if (extra) {
    Object.assign(headers, extra);
  }
  return headers;
}

export function portalHeaders(token, deviceId) {
  return jsonHeaders({
    Authorization: `Bearer ${token}`,
    "X-Device-Id": deviceId || "k6-device",
    "X-Client-Platform": "WEB",
  });
}

export function adminHeaders(state) {
  return jsonHeaders({
    Cookie: state.admin.cookie,
    "X-CSRF-Token": state.admin.csrfToken,
  });
}

export function sha256Hex(body) {
  return crypto.sha256(body || "", "hex");
}

export function signInternal(secret, method, path, timestamp, nonce, body) {
  const stringToSign = `${method}\n${path}\n${timestamp}\n${nonce}\n${sha256Hex(body)}`;
  return crypto.hmac("sha256", secret, stringToSign, "hex");
}

export function internalPost(state, path, payload) {
  const body = JSON.stringify(payload);
  const timestamp = Date.now().toString();
  const nonce = `${__VU}-${__ITER}-${timestamp}-${Math.random().toString(16).slice(2)}`;
  const sign = signInternal(state.internal.secret, "POST", path, timestamp, nonce, body);
  return http.post(`${INTERNAL}${path}`, body, {
    headers: jsonHeaders({
      "X-App-Id": state.internal.appId,
      "X-Timestamp": timestamp,
      "X-Nonce": nonce,
      "X-Sign": sign,
    }),
    tags: { name: path },
  });
}

export function requireCode0(res, name) {
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  const ok = check(res, {
    [`${name} http 200`]: (r) => r.status === 200,
    [`${name} code 0`]: () => payload.code === 0,
  });
  return { ok, payload };
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}
