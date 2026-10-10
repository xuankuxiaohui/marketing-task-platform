import http from "k6/http";
import { check } from "k6";
import { Counter, Rate } from "k6/metrics";
import { BASE, DURATION, loadState, portalHeaders } from "./lib.js";

/**
 * NFR 性能 3: 领取 → 50 步满配级联 → REWARD 完成链, 事务 P95 ≤ 500 ms.
 *
 * F14 data lifecycle:
 * - Seed publishes a finite set of cascade tasks and users (cycleType NONE).
 * - Each (user, task) pair yields at most one first real grant; further /start calls are
 *   idempotent replays of the existing instance and must not be counted as first grants.
 * - This scenario walks the cartesian product once (no modulo wrap). When the pool is
 *   exhausted the iteration fails so the run cannot pass on replay-only traffic.
 * - Cascade start may finish the instance in the same request (COMPLETED is still a first
 *   grant for an unused slot). Replay is prevented by not wrapping the pool.
 *
 * Thresholds are structural (zero-success / exhaustion / check failure), not DEC-006 targets.
 */

const grantFirstOk = new Counter("grant_first_ok");
const grantDataExhausted = new Counter("grant_data_exhausted");
const grantBusinessOk = new Rate("grant_business_ok");

const COMPLETE_VUS = Number(__ENV.COMPLETE_VUS || 5);

export const options = {
  scenarios: {
    complete: {
      executor: "constant-vus",
      vus: COMPLETE_VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:grant}": ["p(95)<=500"],
    checks: ["rate>0.99"],
    grant_business_ok: ["rate>0.99"],
    grant_first_ok: ["count>0"],
    grant_data_exhausted: ["count==0"],
  },
};

export function setup() {
  const state = loadState();
  const users = state.users || [];
  const tasks = state.cascadeTaskIds || [];
  state.grantCapacity = users.length * tasks.length;
  return state;
}

export default function (state) {
  const users = state.users || [];
  const tasks = state.cascadeTaskIds || [];
  const capacity = state.grantCapacity || users.length * tasks.length;
  // Linear index across VUs (no modulo wrap over the finite pool).
  const index = (__VU - 1) + __ITER * COMPLETE_VUS;

  if (users.length === 0 || tasks.length === 0 || index >= capacity) {
    grantDataExhausted.add(1);
    grantBusinessOk.add(false);
    check(null, {
      "grant pool not exhausted (fail rather than idempotent replay)": () => false,
    });
    return;
  }

  const user = users[index % users.length];
  const taskId = tasks[Math.floor(index / users.length)];
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
  const data = payload.data || {};
  const httpOk = res.status === 200;
  const codeOk = payload.code === 0;
  const hasInstance = data.instanceId != null && data.instanceId !== "";
  // First real grant for an unused (user, task) slot. Seed does not pre-start cascade tasks.
  const firstGrant = httpOk && codeOk && hasInstance;

  check(res, {
    "grant http 200": () => httpOk,
    "grant accepted (code 0)": () => codeOk,
    "grant first real (instance id on unused slot)": () => firstGrant,
  });

  if (firstGrant) {
    grantFirstOk.add(1);
    grantBusinessOk.add(true);
  } else {
    grantBusinessOk.add(false);
  }
}
