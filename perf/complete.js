import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";
import { BASE, DURATION, loadState, portalHeaders, pick, internalPost } from "./lib.js";

/**
 * DEC-006 formal gate (NFR 性能 3): claim → 50-step cascade → REWARD completion chain,
 * grant P95 ≤ 500 ms, business failure ≈ 0. Steady duration default 5m.
 *
 * F14 data lifecycle (structural floors alongside DEC-006):
 * - Seed publishes a finite set of cascade tasks and users (cycleType NONE).
 * - Each (user, task) pair yields at most one first real grant; further /start calls are
 *   idempotent replays of the existing instance and must not be counted as first grants.
 * - This scenario walks the cartesian product once (no modulo wrap). When the pool is
 *   exhausted the iteration fails so the run cannot pass on replay-only traffic.
 * - Cascade start may finish the instance in the same request (COMPLETED is still a first
 *   grant for an unused slot). Replay is prevented by not wrapping the pool.
 *
 * Residual complete cut (post-#148): baseline formal failed risk.blocked (seed leaves risk ON)
 * and same-day user reuse hit TaskSettings dailyLimitPerUser=20 (not ConfigService-backed).
 * This script mirrors list/advance residual pattern:
 * - warm (list + progress + small reserved grant pool) before the gate window
 * - scopes DEC-006 thresholds to scenario:complete (warmup excluded)
 * Threshold numbers unchanged (no DEC-006 reopen).
 *
 * Ops: run `seed.py --risk off` before this scenario (run-p0/run-full do that). p0 seed
 * sizes users×cascade within the live daily claim limit so formal 5m does not exhaust.
 */

const grantFirstOk = new Counter("grant_first_ok");
const grantDataExhausted = new Counter("grant_data_exhausted");
const grantBusinessOk = new Rate("grant_business_ok");

const COMPLETE_VUS = Number(__ENV.COMPLETE_VUS || 5);
const WARM_VUS = Number(__ENV.COMPLETE_WARM_VUS || 5);
const WARM_DURATION = __ENV.COMPLETE_WARM_DURATION || "30s";
const GATE_START = __ENV.COMPLETE_GATE_START || "35s";
/** Reserved (user,task) pairs for warm grants so formal keeps a disjoint pool. */
const WARM_USER_N = Number(__ENV.COMPLETE_WARM_USERS || 40);
const WARM_TASK_N = Number(__ENV.COMPLETE_WARM_TASKS || 2);

export const options = {
  scenarios: {
    warm: {
      executor: "constant-vus",
      vus: WARM_VUS,
      duration: WARM_DURATION,
      exec: "warmIter",
      tags: { gate: "warm" },
    },
    complete: {
      executor: "constant-vus",
      vus: COMPLETE_VUS,
      duration: DURATION,
      startTime: GATE_START,
      exec: "grantIter",
      tags: { gate: "formal" },
    },
  },
  thresholds: {
    // Formal scenario only so warm cold-hits do not inflate gate metrics.
    // Gate values unchanged (no DEC-006 reopen).
    "http_req_failed{scenario:complete}": ["rate==0"],
    "http_req_duration{name:grant,scenario:complete}": ["p(95)<=500"],
    "checks{scenario:complete}": ["rate>0.99"],
    "grant_business_ok{scenario:complete}": ["rate>0.99"],
    "grant_first_ok{scenario:complete}": ["count>0"],
    "grant_data_exhausted{scenario:complete}": ["count==0"],
  },
};

export function setup() {
  const state = loadState();
  const users = state.users || [];
  const tasks = state.cascadeTaskIds || [];
  const warmUsers = users.slice(0, Math.min(WARM_USER_N, users.length));
  const warmTasks = tasks.slice(0, Math.min(WARM_TASK_N, tasks.length));
  const formalUsers = users.slice(warmUsers.length);
  const formalTasks = tasks.slice(warmTasks.length);
  state.warmUsers = warmUsers;
  state.warmTasks = warmTasks;
  state.warmCapacity = warmUsers.length * warmTasks.length;
  state.formalUsers = formalUsers;
  state.formalTasks = formalTasks;
  state.grantCapacity = formalUsers.length * formalTasks.length;
  return state;
}

/** Warm: list + progress (no formal pool) + reserved grant pairs for cascade JIT. */
export function warmIter(state) {
  const listUsers = state.users || [];
  if (listUsers.length > 0) {
    const u = pick(listUsers);
    http.get(`${BASE}/api/common/task/list?page=1&pageSize=20`, {
      headers: portalHeaders(u.token, u.deviceId),
      tags: { name: "warm-list" },
    });
  }
  const progresses = state.progressInstances || [];
  if (progresses.length > 0) {
    const inst = progresses[(__VU + __ITER) % progresses.length];
    internalPost(state, "/internal/task/progress", {
      instanceId: inst.instanceId,
      stepCode: inst.stepCode,
      value: 1,
      reportId: `k6-complete-warm-${__VU}-${__ITER}-${Date.now()}`,
    });
  }
  const warmUsers = state.warmUsers || [];
  const warmTasks = state.warmTasks || [];
  const warmCap = state.warmCapacity || 0;
  if (warmCap > 0) {
    const index = (__VU - 1) + __ITER * WARM_VUS;
    if (index < warmCap) {
      const user = warmUsers[index % warmUsers.length];
      const taskId = warmTasks[Math.floor(index / warmUsers.length)];
      http.post(`${BASE}/api/common/task/${taskId}/start`, "{}", {
        headers: portalHeaders(user.token, user.deviceId),
        tags: { name: "warm-grant" },
      });
    }
  }
}

export function grantIter(state) {
  const users = state.formalUsers || [];
  const tasks = state.formalTasks || [];
  const capacity = state.grantCapacity || users.length * tasks.length;
  // Linear index across VUs (no modulo wrap over the finite formal pool).
  const index = (__VU - 1) + __ITER * COMPLETE_VUS;

  if (users.length === 0 || tasks.length === 0 || index >= capacity) {
    grantDataExhausted.add(1);
    grantBusinessOk.add(false);
    check(null, {
      "grant pool not exhausted (fail rather than idempotent replay)": () => false,
    });
    // Avoid busy-spinning millions of empty iterations after the finite pool is done.
    sleep(1);
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
