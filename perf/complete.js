import http from "k6/http";
import { check } from "k6";
import { BASE, DURATION, loadState, portalHeaders } from "./lib.js";

/**
 * NFR 性能 3: 领取 → 50 步满配级联 → REWARD 完成链, 事务 P95 ≤ 500 ms.
 */
export const options = {
  scenarios: {
    complete: {
      executor: "constant-vus",
      vus: Number(__ENV.COMPLETE_VUS || 5),
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:grant}": ["p(95)<=500"],
  },
};

export function setup() {
  return loadState();
}

export default function (state) {
  const index = (__VU - 1) * 100000 + __ITER;
  const user = state.users[index % state.users.length];
  const taskId = state.cascadeTaskIds[Math.floor(index / state.users.length) % state.cascadeTaskIds.length];
  const res = http.post(`${BASE}/api/common/task/${taskId}/start`, "{}", {
    headers: portalHeaders(user.token, user.deviceId),
    tags: { name: "grant" },
  });
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  check(res, {
    "grant http 200": (r) => r.status === 200,
    "grant accepted": () => payload.code === 0,
  });
}
