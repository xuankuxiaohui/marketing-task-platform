import http from "k6/http";
import { check } from "k6";
import { BASE, DURATION, loadState, portalHeaders, pick } from "./lib.js";

/**
 * NFR 性能 1: 门户任务列表 500 QPS, 2 实例 P95 ≤ 300 ms, 错误率 = 0.
 */
export const options = {
  scenarios: {
    list: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.LIST_QPS || 500),
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 80,
      maxVUs: Number(__ENV.LIST_MAX_VUS || 400),
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:list}": ["p(95)<=300"],
  },
};

export function setup() {
  return loadState();
}

export default function (state) {
  const user = pick(state.users);
  const res = http.get(`${BASE}/api/common/task/list?page=1&pageSize=20`, {
    headers: portalHeaders(user.token, user.deviceId),
    tags: { name: "list" },
  });
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  check(res, {
    "list http 200": (r) => r.status === 200,
    "list code 0": () => payload.code === 0,
  });
}
