import { check } from "k6";
import { Counter, Rate } from "k6/metrics";
import { DURATION, loadState, internalPost } from "./lib.js";

/**
 * DEC-006 formal gate (NFR 性能 2): internal progress/callback 300 QPS, P95 ≤ 200 ms,
 * business failure ≈ 0; constant-arrival-rate dropped_iterations rate<0.05.
 * Steady duration default 5m. appId rate limit raised by seed to 5000 (within app A).
 *
 * F14 data lifecycle (structural floors alongside DEC-006):
 * - progressInstances: seed uses a huge progressTarget, so reuse is lasting progress writes.
 * - callbackInstances: seed prepares 100 single-step CALLBACK instances; each supports one
 *   real advance then becomes post-complete. This VU partitions the pool and consumes each
 *   slot at most once for business-advance metrics; after the slot is spent, traffic falls
 *   back to progress. Random reuse of completed callbacks is not treated as lasting advance.
 * - If both the VU callback slice and progress pool are unavailable, the iteration fails
 *   (data exhaustion) instead of silently measuring empty work.
 *
 * Residual dropped cut (post-#147): formal 5m after merge showed ~40 drops (~0.133/s) with
 * VU scale-up 60→99 while QPS/P95/checks/http_fail/business all green. This script:
 * - warms portal internal path (progress-only) before the gate window
 * - pre-allocates enough VUs to absorb brief stalls without arrival drops
 * - scopes DEC-006 thresholds to scenario:advance (warmup excluded = drop window)
 * Threshold numbers unchanged (no DEC-006 reopen).
 *
 * Thresholds: QPS/P95/dropped_iterations are DEC-006 formal gates; advance_business_ok /
 * progress_advance_ok / advance_data_exhausted / checks are F14 structural floors.
 */

const advanceBusinessOk = new Rate("advance_business_ok");
const callbackRealAdvance = new Counter("callback_real_advance");
const progressAdvanceOk = new Counter("progress_advance_ok");
const advanceDataExhausted = new Counter("advance_data_exhausted");

const ADVANCE_QPS = Number(__ENV.ADVANCE_QPS || 300);
const ADVANCE_MAX_VUS = Number(__ENV.ADVANCE_MAX_VUS || 300);
const ADVANCE_PRE_VUS = Number(__ENV.ADVANCE_PRE_VUS || 120);
const WARM_QPS = Number(__ENV.ADVANCE_WARM_QPS || 100);
const WARM_DURATION = __ENV.ADVANCE_WARM_DURATION || "30s";
const GATE_START = __ENV.ADVANCE_GATE_START || "35s";

/** Per-VU cursor into this VU's callback partition (init copy is per VU in k6). */
let callbackCursor = 0;
let callbackPartition = null;

function partitionCallbacks(all, vuId, preAllocatedVUs) {
  if (!all || all.length === 0) {
    return [];
  }
  const buckets = Math.max(1, preAllocatedVUs);
  const bucket = (vuId - 1) % buckets;
  return all.filter((_, i) => i % buckets === bucket);
}

function parsePayload(res) {
  try {
    return res.json();
  } catch (e) {
    return {};
  }
}

export const options = {
  scenarios: {
    warm: {
      executor: "constant-arrival-rate",
      rate: WARM_QPS,
      timeUnit: "1s",
      duration: WARM_DURATION,
      preAllocatedVUs: Math.min(40, ADVANCE_PRE_VUS),
      maxVUs: Math.min(80, ADVANCE_MAX_VUS),
      exec: "warmProgress",
      tags: { gate: "warm" },
    },
    advance: {
      executor: "constant-arrival-rate",
      rate: ADVANCE_QPS,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: ADVANCE_PRE_VUS,
      maxVUs: ADVANCE_MAX_VUS,
      startTime: GATE_START,
      exec: "advanceIter",
      tags: { gate: "formal" },
    },
  },
  thresholds: {
    // Formal scenario only so warm cold-hits do not inflate gate metrics.
    // Gate values unchanged (no DEC-006 reopen).
    "http_req_failed{scenario:advance}": ["rate==0"],
    "http_req_duration{name:/internal/task/progress,scenario:advance}": ["p(95)<=200"],
    "http_req_duration{name:/internal/task/callback,scenario:advance}": ["p(95)<=200"],
    "checks{scenario:advance}": ["rate>0.99"],
    "advance_business_ok{scenario:advance}": ["rate>0.99"],
    "dropped_iterations{scenario:advance}": ["rate<0.05"],
    "advance_data_exhausted{scenario:advance}": ["count==0"],
    // Catch runs that never recorded a lasting progress write or a real callback advance.
    "progress_advance_ok{scenario:advance}": ["count>0"],
  },
};

export function setup() {
  return loadState();
}

/** Warm: progress-only so formal keeps the callback pool for real-advance metrics. */
export function warmProgress(state) {
  const progresses = state.progressInstances || [];
  if (progresses.length === 0) {
    return;
  }
  const inst = progresses[(__VU + __ITER) % progresses.length];
  internalPost(state, "/internal/task/progress", {
    instanceId: inst.instanceId,
    stepCode: inst.stepCode,
    value: 1,
    reportId: `k6-warm-${__VU}-${__ITER}-${Date.now()}`,
  });
}

export function advanceIter(state) {
  const progresses = state.progressInstances || [];
  const allCallbacks = state.callbackInstances || [];
  if (callbackPartition === null) {
    callbackPartition = partitionCallbacks(allCallbacks, __VU, ADVANCE_PRE_VUS);
  }

  if (callbackCursor < callbackPartition.length) {
    const inst = callbackPartition[callbackCursor];
    callbackCursor += 1;
    const res = internalPost(state, "/internal/task/callback", {
      instanceId: inst.instanceId,
      stepCode: inst.stepCode,
      bizNo: `k6-${__VU}-${__ITER}`,
    });
    const payload = parsePayload(res);
    const data = payload.data || {};
    const httpOk = res.status === 200;
    const codeOk = payload.code === 0;
    const status = data.instanceStatus || "";
    // Single-step callback: first successful call completes the instance (real advance).
    const realAdvance = httpOk && codeOk && (status === "COMPLETED" || status === "IN_PROGRESS");
    check(res, {
      "callback http 200": () => httpOk,
      "callback code 0": () => codeOk,
      "callback real advance (not silent miss)": () => realAdvance,
    });
    advanceBusinessOk.add(realAdvance);
    if (realAdvance) {
      callbackRealAdvance.add(1);
    }
    return;
  }

  if (progresses.length === 0) {
    advanceDataExhausted.add(1);
    advanceBusinessOk.add(false);
    check(null, {
      "advance data not exhausted": () => false,
    });
    return;
  }

  const inst = progresses[(__VU + __ITER) % progresses.length];
  const res = internalPost(state, "/internal/task/progress", {
    instanceId: inst.instanceId,
    stepCode: inst.stepCode,
    value: 1,
    reportId: `k6-${__VU}-${__ITER}-${Date.now()}`,
  });
  const payload = parsePayload(res);
  const data = payload.data || {};
  const httpOk = res.status === 200;
  const codeOk = payload.code === 0;
  const hasProgress =
    typeof data.progressCurrent === "number" || data.progressCurrent === 0;
  const lasting = httpOk && codeOk && hasProgress;
  check(res, {
    "progress http 200": () => httpOk,
    "progress code 0": () => codeOk,
    "progress lasting write fields": () => lasting,
  });
  advanceBusinessOk.add(lasting);
  if (lasting) {
    progressAdvanceOk.add(1);
  }
}
