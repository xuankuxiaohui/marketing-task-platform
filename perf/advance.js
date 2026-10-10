import { check } from "k6";
import { Counter, Rate } from "k6/metrics";
import { DURATION, loadState, internalPost } from "./lib.js";

/**
 * NFR 性能 2: internal progress/callback 300 QPS, P95 ≤ 200 ms.
 * appId 限流由 seed 调至 5000（附录 A 上限内）。
 *
 * F14 data lifecycle:
 * - progressInstances: seed uses a huge progressTarget, so reuse is lasting progress writes.
 * - callbackInstances: seed prepares 100 single-step CALLBACK instances; each supports one
 *   real advance then becomes post-complete. This VU partitions the pool and consumes each
 *   slot at most once for business-advance metrics; after the slot is spent, traffic falls
 *   back to progress. Random reuse of completed callbacks is not treated as lasting advance.
 * - If both the VU callback slice and progress pool are unavailable, the iteration fails
 *   (data exhaustion) instead of silently measuring empty work.
 *
 * Thresholds below are structural (catch zero-success / high drop / check failure), not DEC-006
 * capacity targets.
 */

const advanceBusinessOk = new Rate("advance_business_ok");
const callbackRealAdvance = new Counter("callback_real_advance");
const progressAdvanceOk = new Counter("progress_advance_ok");
const advanceDataExhausted = new Counter("advance_data_exhausted");

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
    advance: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.ADVANCE_QPS || 300),
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 60,
      maxVUs: Number(__ENV.ADVANCE_MAX_VUS || 300),
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:/internal/task/progress}": ["p(95)<=200"],
    "http_req_duration{name:/internal/task/callback}": ["p(95)<=200"],
    checks: ["rate>0.99"],
    advance_business_ok: ["rate>0.99"],
    dropped_iterations: ["rate<0.05"],
    advance_data_exhausted: ["count==0"],
    // Catch runs that never recorded a lasting progress write or a real callback advance.
    progress_advance_ok: ["count>0"],
  },
};

export function setup() {
  return loadState();
}

export default function (state) {
  const progresses = state.progressInstances || [];
  const allCallbacks = state.callbackInstances || [];
  if (callbackPartition === null) {
    callbackPartition = partitionCallbacks(allCallbacks, __VU, 60);
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
