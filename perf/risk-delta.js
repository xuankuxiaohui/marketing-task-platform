import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.4/index.js";
import { BASE, DURATION, loadState, portalHeaders } from "./lib.js";

/**
 * DEC-006 formal gate (NFR 性能 4): risk rules all-on vs all-off grant P95 delta ≤ 20 ms.
 * Runners execute VARIANT=on|off then compare reports (maxDeltaMs: 20).
 * Per-variant grant P95 ≤ 500 ms is the cascade ceiling; the DEC-006 gate is the delta.
 *
 * Both variants MUST record real business success (HTTP 200 + code 0). HTTP 403 /
 * risk.blocked is never success. Seed `--risk on` keeps the engine evaluating
 * (R-a/R-b/R-d) while bypassing compose IP-claim artifacts (R-c/R-f) and instant
 * cascade elapsed (R-e).
 *
 * warm → formal (same pattern as complete.js): cold JIT excluded from gate P95.
 * Finite striped (user × riskTask) pool — no modulo wrap; grant_data_exhausted
 * count==0 stays mandatory. Default RISK_POOL=shared uses full fresh users for
 * each variant (expand via perf/seed/expand_risk_stripe.py). RISK_POOL=half = legacy.
 */
const grantDataExhausted = new Counter("grant_data_exhausted");

const RISK_VUS = Number(__ENV.RISK_VUS || 5);
const WARM_USER_N = Number(__ENV.RISK_WARM_USERS || 20);
const WARM_TASK_N = Number(__ENV.RISK_WARM_TASKS || 2);

export const options = {
  scenarios: {
    warm: {
      executor: "constant-vus",
      vus: Math.min(3, RISK_VUS),
      duration: "30s",
      exec: "warmIter",
      tags: { gate: "warm" },
    },
    risk: {
      executor: "constant-vus",
      vus: RISK_VUS,
      duration: DURATION,
      startTime: "35s",
      exec: "riskIter",
      tags: { gate: "formal" },
    },
  },
  thresholds: {
    // Formal scenario only — warm cold-hits must not inflate / fail the gate.
    // Real business success required: HTTP 200 + code 0 (403/risk.blocked never pass).
    "http_req_failed{scenario:risk}": ["rate==0"],
    "http_req_duration{name:grant,scenario:risk}": ["p(95)<=500"],
    "checks{scenario:risk}": ["rate==1"],
    "grant_data_exhausted{scenario:risk}": ["count==0"],
  },
};

export function setup() {
  const state = loadState();
  state.variant = __ENV.VARIANT || "on";
  const allUsers = state.users || [];
  const tasks = state.riskTaskIds || state.cascadeTaskIds || [];
  // Skip pre-started progress/callback users ([:150]) — they already burned a daily claim.
  const fresh = allUsers.slice(150);
  // Shared fresh pool for on/off (deviceId already suffixes variant). Disjoint half-split
  // capped formal users at ~905 × 20 tasks = 18100 — post-hygiene 5m @ 5 VUs burns past that
  // (dailyLimit=20 binds success capacity to formal_users×20; expanding tasks alone does not).
  // Runners MUST clear_today_claim_counts between paired on/off so daily quota is independent.
  // Optional RISK_POOL=half restores legacy disjoint split.
  const poolMode = __ENV.RISK_POOL || "shared";
  let pool;
  if (poolMode === "half") {
    const half = Math.floor(fresh.length / 2);
    const variantPool =
      state.variant === "off" ? fresh.slice(half) : fresh.slice(0, half || fresh.length);
    pool = variantPool.length
      ? variantPool
      : allUsers.slice(0, Math.max(1, Math.floor(allUsers.length / 2)));
  } else {
    pool = fresh.length ? fresh : allUsers;
  }

  // Warm burns a small user prefix only; formal keeps the full riskTask list for capacity.
  const warmUsers = pool.slice(0, Math.min(WARM_USER_N, pool.length));
  const warmTasks = tasks.slice(0, Math.min(WARM_TASK_N, tasks.length));
  const formalUsers = pool.slice(warmUsers.length);

  state.warmUsers = warmUsers;
  state.warmTasks = warmTasks;
  state.warmCapacity = warmUsers.length * warmTasks.length;
  state.riskUsers = formalUsers;
  state.riskTasks = tasks;
  state.riskCapacity = state.riskUsers.length * state.riskTasks.length;
  return state;
}

function postGrant(user, taskId, variant, extraTags) {
  const res = http.post(`${BASE}/api/common/task/${taskId}/start`, "{}", {
    headers: portalHeaders(user.token, `${user.deviceId}-${variant}`),
    tags: Object.assign({ name: "grant", variant }, extraTags || {}),
  });
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  return { res, payload };
}

export function warmIter(state) {
  const users = state.warmUsers || [];
  const tasks = state.warmTasks || [];
  const capacity = state.warmCapacity || 0;
  const vus = Math.min(3, RISK_VUS);
  const index = (__VU - 1) + __ITER * vus;
  if (!users.length || !tasks.length || index >= capacity) {
    sleep(0.5);
    return;
  }
  const user = users[index % users.length];
  const taskId = tasks[Math.floor(index / users.length)];
  const { res, payload } = postGrant(user, taskId, state.variant, { gate: "warm" });
  // Warm is observational only — do not fail the suite on warm misses.
  check(res, {
    "warm grant http 200": (r) => r.status === 200,
    "warm grant accepted": () => payload.code === 0,
  });
}

export function riskIter(state) {
  const users = state.riskUsers || [];
  const tasks = state.riskTasks || [];
  const capacity = state.riskCapacity || 0;
  const index = (__VU - 1) + __ITER * RISK_VUS;
  if (users.length === 0 || tasks.length === 0 || index >= capacity) {
    grantDataExhausted.add(1);
    check(null, {
      "risk grant pool not exhausted": () => false,
    });
    sleep(0.5);
    return;
  }
  const user = users[index % users.length];
  const taskId = tasks[Math.floor(index / users.length)];
  const { res, payload } = postGrant(user, taskId, state.variant, {});
  // Explicit: 403 / risk.blocked must fail (never count as success).
  check(res, {
    "risk grant http 200": (r) => r.status === 200,
    "risk grant accepted": () => payload.code === 0,
  });
}

export function handleSummary(data) {
  const variant = __ENV.VARIANT || "on";
  const metric =
    data.metrics["http_req_duration{name:grant,scenario:risk}"] ||
    data.metrics["http_req_duration{name:grant}"] ||
    data.metrics.http_req_duration;
  const p95 = metric && metric.values ? metric.values["p(95)"] : null;
  const failed =
    data.metrics["http_req_failed{scenario:risk}"] || data.metrics.http_req_failed;
  const checks = data.metrics["checks{scenario:risk}"] || data.metrics.checks;
  const report = {
    variant,
    p95,
    httpFailRate: failed && failed.values ? failed.values.rate : null,
    checksRate: checks && checks.values ? checks.values.rate : null,
    nfr: "性能4",
    maxDeltaMs: 20,
  };
  const dir = __ENV.REPORT_DIR || "./reports";
  return {
    stdout: textSummary(data, { indent: " ", enableColors: false }),
    [`${dir}/risk-${variant}.json`]: JSON.stringify(report, null, 2),
  };
}
