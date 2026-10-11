import http from "k6/http";
import { check } from "k6";
import { BASE, DURATION, loadState, portalHeaders, pick } from "./lib.js";

/**
 * DEC-006 formal gate (NFR 性能 1): portal task list 500 QPS, dual-instance P95 ≤ 300 ms,
 * business failure ≈ 0. Topology: portal×2 + admin×1 via nginx (do not extrapolate from
 * single instance). Steady duration default 5m (lib.js DURATION).
 * constant-arrival-rate: dropped_iterations rate<0.05 on the formal scenario only.
 *
 * Residual dropped cut (post-#144/#146): post-#144 5m showed ~174 drops clustered at ~4m16s
 * with a ~1.87s outlier and VU scale-up 80→243 — synchronized L1/L2 published-index TTL
 * expiry mid-run, not mass 5xx. This script:
 * - warms both portals + pools before the gate window (startTime; 45s warm / 50s gate)
 * - pre-allocates enough VUs (350/500) to absorb brief stalls without mid-run alloc drops
 * - scopes dropped_iterations to scenario:list (warmup excluded = drop window)
 * Threshold numbers unchanged (no DEC-006 reopen).
 */
const LIST_QPS = Number(__ENV.LIST_QPS || 500);
// Post-#159 residual dropped (~0.304/s): formal peak active VUs≈258 and pool grew 330→415
// while allocating — prealloc covers that spike so constant-arrival-rate does not drop.
const LIST_MAX_VUS = Number(__ENV.LIST_MAX_VUS || 500);
const LIST_PRE_VUS = Number(__ENV.LIST_PRE_VUS || 350);
const WARM_QPS = Number(__ENV.LIST_WARM_QPS || 200);
const WARM_DURATION = __ENV.LIST_WARM_DURATION || "45s";
const GATE_START = __ENV.LIST_GATE_START || "50s";

export const options = {
  scenarios: {
    warm: {
      executor: "constant-arrival-rate",
      rate: WARM_QPS,
      timeUnit: "1s",
      duration: WARM_DURATION,
      preAllocatedVUs: Math.min(80, LIST_PRE_VUS),
      maxVUs: Math.min(150, LIST_MAX_VUS),
      exec: "listIter",
      tags: { gate: "warm" },
    },
    list: {
      executor: "constant-arrival-rate",
      rate: LIST_QPS,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: LIST_PRE_VUS,
      maxVUs: LIST_MAX_VUS,
      startTime: GATE_START,
      exec: "listIter",
      tags: { gate: "formal" },
    },
  },
  thresholds: {
    // Formal scenario only so warm cold-hits do not inflate gate metrics.
    // Gate values unchanged (no DEC-006 reopen).
    "http_req_failed{scenario:list}": ["rate==0"],
    "http_req_duration{name:list,scenario:list}": ["p(95)<=300"],
    "checks{scenario:list}": ["rate>0.99"],
    "dropped_iterations{scenario:list}": ["rate<0.05"],
  },
};

export function setup() {
  return loadState();
}

export function listIter(state) {
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
