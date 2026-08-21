import { DURATION, loadState, internalPost, pick } from "./lib.js";

/**
 * NFR 性能 2: internal progress/callback 300 QPS, P95 ≤ 200 ms.
 * appId 限流由 seed 调至 5000（附录 A 上限内）。
 */
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
  },
};

export function setup() {
  return loadState();
}

export default function (state) {
  if (Math.random() < 0.5 && state.progressInstances && state.progressInstances.length > 0) {
    const inst = pick(state.progressInstances);
    internalPost(state, "/internal/task/progress", {
      instanceId: inst.instanceId,
      stepCode: inst.stepCode,
      value: 1,
      reportId: `k6-${__VU}-${__ITER}-${Date.now()}`,
    });
    return;
  }
  const inst = pick(state.callbackInstances);
  internalPost(state, "/internal/task/callback", {
    instanceId: inst.instanceId,
    stepCode: inst.stepCode,
    bizNo: `k6-${__VU}-${__ITER}`,
  });
}
