import http from "k6/http";
import { check } from "k6";
import { BASE, DURATION, adminHeaders, loadState } from "./lib.js";

/**
 * NFR 性能 7: 后台任务/实例/流水典型列表（50 万实例数据量）P95 ≤ 800 ms.
 */
export const options = {
  scenarios: {
    adminList: {
      executor: "constant-vus",
      vus: Number(__ENV.ADMIN_VUS || 10),
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:admin-instances}": ["p(95)<=800"],
    "http_req_duration{name:admin-definitions}": ["p(95)<=800"],
    "http_req_duration{name:admin-grants}": ["p(95)<=800"],
  },
};

export function setup() {
  return loadState();
}

export default function (state) {
  const headers = adminHeaders(state);
  const paths = [
    { url: `${BASE}/admin/task/instances?page=1&pageSize=20`, name: "admin-instances" },
    { url: `${BASE}/admin/task/definitions?page=1&pageSize=20`, name: "admin-definitions" },
    { url: `${BASE}/admin/points/transactions?page=1&pageSize=20`, name: "admin-grants" },
  ];
  const item = paths[__ITER % paths.length];
  const res = http.get(item.url, { headers, tags: { name: item.name } });
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  check(res, {
    "admin list http 200": (r) => r.status === 200,
    "admin list code 0": () => payload.code === 0,
  });
}
