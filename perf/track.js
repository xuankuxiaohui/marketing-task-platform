import http from "k6/http";
import { check } from "k6";
import { Counter, Rate } from "k6/metrics";
import { BASE, DURATION, jsonHeaders } from "./lib.js";

/**
 * NFR 性能 5: 50 条满批（含 8KB 单条）集群吞吐 ≥ 6000 events/s, P95 ≤ 100 ms, 丢弃率 < 0.1%.
 */
const dropped = new Counter("track_dropped");
const accepted = new Counter("track_accepted");
const dropRate = new Rate("track_drop_rate");

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
  check(res, {
    "track http 200": (r) => r.status === 200,
    "track code 0": () => payload.code === 0,
  });
}
