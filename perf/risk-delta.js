import http from "k6/http";
import { check } from "k6";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.4/index.js";
import { BASE, DURATION, loadState, portalHeaders } from "./lib.js";

/**
 * NFR 性能 4: 全部规则启用 vs 全部停用, 领取/发奖 P95 差值 ≤ 20 ms.
 * 由 ci/perf-p0.sh 跑两轮 VARIANT=on|off 后比较 reports.
 */
export const options = {
  scenarios: {
    risk: {
      executor: "constant-vus",
      vus: Number(__ENV.RISK_VUS || 5),
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:grant}": ["p(95)<=500"],
  },
};

export function setup() {
  const state = loadState();
  state.variant = __ENV.VARIANT || "on";
  return state;
}

export default function (state) {
  const index = (__VU - 1) * 100000 + __ITER;
  const user = state.users[index % state.users.length];
  const tasks = state.riskTaskIds || state.cascadeTaskIds;
  const taskId = tasks[Math.floor(index / state.users.length) % tasks.length];
  const res = http.post(`${BASE}/api/common/task/${taskId}/start`, "{}", {
    headers: portalHeaders(user.token, `${user.deviceId}-${state.variant}`),
    tags: { name: "grant", variant: state.variant },
  });
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  check(res, {
    "risk grant http 200": (r) => r.status === 200,
    "risk grant accepted": () => payload.code === 0,
  });
}

export function handleSummary(data) {
  const variant = __ENV.VARIANT || "on";
  const metric = data.metrics["http_req_duration{name:grant}"] || data.metrics.http_req_duration;
  const p95 = metric && metric.values ? metric.values["p(95)"] : null;
  const report = {
    variant,
    p95,
    nfr: "性能4",
    maxDeltaMs: 20,
  };
  const dir = __ENV.REPORT_DIR || "./reports";
  return {
    stdout: textSummary(data, { indent: " ", enableColors: false }),
    [`${dir}/risk-${variant}.json`]: JSON.stringify(report, null, 2),
  };
}
