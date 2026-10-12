#!/usr/bin/env python3
"""Measurement hygiene: expand risk stripe capacity without full-DB reseed.

After claim-pool hygiene (clear_today_claim_counts), grant throughput rises and
the finite (user × riskTask) striped pool in risk-delta.js burns through in a
formal 5m @ RISK_VUs=5. dailyLimitPerUser=20 binds successful grants per user,
so expanding riskTaskIds alone past 20 does not raise success capacity — we
must add formal users (and keep tasks ≥ 20 so k6 capacity matches the daily cap).

Does NOT: raise dailyLimitPerUser, delete/relax grant_data_exhausted, full reseed,
lower Δ, touch Tomcat/Redisson/1536M.

Usage (repo root, compose mkt up):
  python3 perf/seed/expand_risk_stripe.py --target-users 2800
  python3 perf/seed/expand_risk_stripe.py --target-users 2800 --target-risk-tasks 20
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

# Import seed helpers (same process; seed loads compose env / BASE).
sys.path.insert(0, str(Path(__file__).resolve().parent))
import seed as seedmod  # noqa: E402

STATE = seedmod.STATE_PATH


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--target-users", type=int, default=2800,
                    help="Total users in state.json after expand (default 2800)")
    ap.add_argument("--target-risk-tasks", type=int, default=20,
                    help="Total riskTaskIds after expand (default 20; ≥ dailyLimit)")
    ap.add_argument("--state", type=Path, default=STATE)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    if not args.state.exists():
        print(f"missing state {args.state}", file=sys.stderr)
        return 2
    state = json.loads(args.state.read_text())
    users = list(state.get("users") or [])
    risk_ids = list(state.get("riskTaskIds") or [])
    pre_u, pre_t = len(users), len(risk_ids)
    print(f"pre users={pre_u} riskTasks={pre_t}")

    # Capacity preview (shared fresh pool: risk-delta uses all fresh for each variant)
    warm, prestarted = 20, 150
    formal = max(0, args.target_users - prestarted - warm)
    cap = formal * max(args.target_risk_tasks, 1)
    print(f"target users={args.target_users} riskTasks={args.target_risk_tasks} "
          f"formal≈{formal} k6_capacity≈{cap} (dailyLimit binds ≤ formal*20)")

    if args.dry_run:
        print("dry-run: no changes")
        return 0

    admin = seedmod.admin_login()
    state["admin"] = admin

    # 1) Ensure enough risk tasks (idempotent by code)
    prize = int(state.get("prizeAutoId") or seedmod.ensure_prize(admin, "perf_auto_pts", "AUTO"))
    state["prizeAutoId"] = prize
    steps, transitions = seedmod.cascade_steps(prize)
    # Existing codes perf_risk_00..; continue index from max existing
    existing_codes = set()
    # Discover next index from state length and by trying codes
    next_i = len(risk_ids)
    while len(risk_ids) < args.target_risk_tasks:
        code = f"perf_risk_{next_i:02d}"
        print(f"add risk task {code}", flush=True)
        tid = seedmod.save_and_publish(
            admin,
            {
                "code": code,
                "name": f"perf risk {next_i}",
                "cycleType": "NONE",
                "sortWeight": 0,
                "gray": {"type": "NONE"},
                "steps": steps,
                "transitions": transitions,
                "actions": [],
            },
        )
        if tid not in risk_ids:
            risk_ids.append(tid)
        next_i += 1
    state["riskTaskIds"] = risk_ids

    # 2) Register additional users (no wipe / no bulk instance reseed)
    password = "Perfuser1"
    seed_tag = datetime.now(timezone.utc).strftime("%m%d%H%M%S")
    start_i = len(users)
    need = max(0, args.target_users - len(users))
    print(f"registering {need} users (tag={seed_tag})", flush=True)
    # Headroom for captcha/login storms from one compose IP
    seedmod.put_config(admin, "ratelimit.login.ip.per-minute", "100000")
    seedmod.put_config(admin, "ratelimit.login.account.per-minute", "100000")
    seedmod.put_config(admin, "auth.captcha.ttl-seconds", "600")
    for j in range(need):
        i = start_i + j
        username = f"perf_stripe_{i}_{seed_tag}"
        last_err: Exception | None = None
        for attempt in range(1, 4):
            try:
                users.append(seedmod.register_user(username, password))
                last_err = None
                break
            except SystemExit as exc:
                last_err = exc
                msg = str(exc)
                if "captcha" not in msg.lower():
                    raise
                print(f"user {username} attempt {attempt}/3 failed: {msg}; retry", flush=True)
                time.sleep(0.5 * attempt)
        if last_err is not None:
            raise SystemExit(f"register failed for {username} after retries ({len(users)} done): {last_err}")
        if (j + 1) % 50 == 0 or j + 1 == need:
            print(f"users {len(users)}/{args.target_users}", flush=True)
    state["users"] = users
    state["stripeExpandedAt"] = int(time.time())
    state["stripeExpand"] = {
        "targetUsers": args.target_users,
        "targetRiskTasks": args.target_risk_tasks,
        "preUsers": pre_u,
        "preRiskTasks": pre_t,
        "formalApprox": formal,
        "k6CapacityApprox": cap,
        "note": "shared fresh pool in risk-delta.js; clear_today between on/off",
    }
    args.state.write_text(json.dumps(state) + "\n")
    print(f"wrote {args.state} users={len(users)} riskTasks={len(risk_ids)} capacity≈{cap}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"expand failed: {exc}", file=sys.stderr)
        raise
