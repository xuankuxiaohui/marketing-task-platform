import http from "k6/http";
import { check } from "k6";
import { BASE, DURATION, loadState, portalHeaders, pick } from "./lib.js";

/**
 * NFR 性能 6 (P1): 广告位拉取 P95 ≤ 100 ms @ 300 QPS.
 */
export const options = {
  scenarios: {
    ad: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.AD_QPS || 300),
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 60,
      maxVUs: Number(__ENV.AD_MAX_VUS || 300),
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:ad}": ["p(95)<=100"],
  },
};

export function setup() {
  return loadState();
}

export default function (state) {
  const codes = state.adCodes || ["home_banner", "home_popup", "home_float", "app_splash"];
  const code = pick(codes);
  const headers =
    Math.random() < 0.5 && state.users && state.users.length > 0
      ? portalHeaders(pick(state.users).token, `k6-ad-${__VU}`)
      : {
          Accept: "application/json",
          "X-Device-Id": `k6-ad-anon-${__VU}-${__ITER}`,
          "X-Client-Platform": "WEB",
        };
  const res = http.get(`${BASE}/api/common/ad/positions/${code}`, {
    headers,
    tags: { name: "ad" },
  });
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  check(res, {
    "ad http 200": (r) => r.status === 200,
    "ad code 0": () => payload.code === 0,
  });
}
