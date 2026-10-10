import http from "k6/http";
import { check } from "k6";
import { Counter, Rate } from "k6/metrics";
import { BASE, DURATION, jsonHeaders } from "./lib.js";

/**
 * NFR 性能 5: 50 条满批（含 8KB 单条）集群吞吐 ≥ 6000 events/s, P95 ≤ 100 ms, 丢弃率 < 0.1%.
 *
 * F14: business checks feed thresholds; track_accepted has a structural floor (catch
 * zero-success). dropped_iterations fails high scheduler drop. Numeric 6000 eps remains
 * a DEC-006 candidate, not an automatic gate here.
 */

const dropped = new Counter("track_dropped");
const accepted = new Counter("track_accepted");
const dropRate = new Rate("track_drop_rate");
const trackBusinessOk = new Rate("track_business_ok");

const EVENTS_PER_BATCH = 50;
const TARGET_EVENTS_PER_SEC = Number(__ENV.TRACK_EPS || 6000);
const RATE = Math.ceil(TARGET_EVENTS_PER_SEC / EVENTS_PER_BATCH);

function pad8k() {
  const target = Number(__ENV.TRACK_BIG_BYTES || 7800);
  return "x".repeat(target);
}

function batch() {
  const big = pad8k();
  const events = [];
  for (let i = 0; i < EVENTS_PER_BATCH; i += 1) {
    const event = {
      code: i === 0 ? "page.view" : "task.card.exposure",
      clientTime: new Date().toISOString(),
      props:
        i === 0
          ? { route: "/home", blob: big }
          : { taskId: 1, taskCode: "e2e_core" },
    };
    events.push(event);
  }
  return JSON.stringify({
    events,
    platform: "WEB",
    appVersion: "0.1.0",
  });
}

export const options = {
  scenarios: {
    track: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 80,
      maxVUs: Number(__ENV.TRACK_MAX_VUS || 400),
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
    "http_req_duration{name:track}": ["p(95)<=100"],
    track_drop_rate: ["rate<0.001"],
    checks: ["rate>0.99"],
    track_business_ok: ["rate>0.99"],
    // Structural floor: some events must be accepted (zero-success / silent fail catch).
    // Full events/s capacity remains DEC-006; do not gate 6000 eps here.
    track_accepted: ["count>0", "rate>0"],
    dropped_iterations: ["rate<0.05"],
  },
};

const BODY = batch();

export default function () {
  const deviceId = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const res = http.post(`${BASE}/api/common/track/batch`, BODY, {
    headers: jsonHeaders({
      "X-Device-Id": deviceId,
      "X-Client-Platform": "WEB",
    }),
    tags: { name: "track" },
  });
  let payload = {};
  try {
    payload = res.json();
  } catch (e) {
    payload = {};
  }
  const data = payload.data || {};
  const acc = Number(data.accepted || 0);
  const drop = Number(data.dropped || 0);
  accepted.add(acc);
  dropped.add(drop);
  const total = acc + drop;
  if (total > 0) {
    for (let i = 0; i < total; i += 1) {
      dropRate.add(i < drop);
    }
  }
  const httpOk = res.status === 200;
  const codeOk = payload.code === 0;
  const acceptedOk = acc > 0;
  const businessOk = httpOk && codeOk && acceptedOk;
  check(res, {
    "track http 200": () => httpOk,
    "track code 0": () => codeOk,
    "track accepted events > 0": () => acceptedOk,
  });
  trackBusinessOk.add(businessOk);
}
