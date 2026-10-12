#!/usr/bin/env python3
"""Measurement hygiene: clear *today's* task_instance claim counts for perf users.

Does NOT raise TaskSettings.dailyLimitPerUser (product limit stays 20).
Does NOT change k6 gates / treat HTTP 400 as success.
Scoped to userIds listed in perf/state.json (throwaway mkt). Backdates
created_at by 1 day so TaskClaimAppService.countToday returns 0 for those users.

Usage (from repo root, compose project mkt up):
  python3 perf/seed/clear_today_claim_counts.py
  python3 perf/seed/clear_today_claim_counts.py --dry-run
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / "perf" / "state.json"
MYSQL_CONTAINER = os.environ.get("MYSQL_CONTAINER", "mkt-mysql-1")
MYSQL_DB = os.environ.get("MYSQL_DATABASE", "mkt_platform")
MYSQL_USER = os.environ.get("MYSQL_USER", "mkt")
MYSQL_PASSWORD = os.environ.get("MYSQL_PASSWORD", "CHANGE_ME_APP")


def mysql(sql: str) -> str:
    cmd = [
        "docker",
        "exec",
        "-i",
        MYSQL_CONTAINER,
        "mysql",
        f"-u{MYSQL_USER}",
        f"-p{MYSQL_PASSWORD}",
        MYSQL_DB,
        "-N",
        "-e",
        sql,
    ]
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        err = (p.stderr or p.stdout or "").strip()
        raise RuntimeError(f"mysql failed: {err}")
    # strip password warnings
    lines = [ln for ln in (p.stdout or "").splitlines() if "Warning" not in ln]
    return "\n".join(lines).strip()


def today_bounds_sql() -> str:
    # Match TaskClaimAppService / CycleKeyResolver Asia/Shanghai day window, stored UTC.
    return """
SET @today_start = CONVERT_TZ(CONCAT(DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00')),' 00:00:00'),'+08:00','+00:00');
SET @tomorrow_start = CONVERT_TZ(CONCAT(DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00')) + INTERVAL 1 DAY,' 00:00:00'),'+08:00','+00:00');
"""


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--state", type=Path, default=STATE)
    args = ap.parse_args()
    state = json.loads(args.state.read_text())
    user_ids = sorted({int(u["userId"]) for u in state.get("users") or [] if u.get("userId") is not None})
    if not user_ids:
        print("no userIds in state.json", file=sys.stderr)
        return 2
    id_list = ",".join(str(i) for i in user_ids)
    bounds = today_bounds_sql()
    pre = mysql(
        bounds
        + f"""
SELECT COUNT(*) FROM task_instance
 WHERE user_id IN ({id_list})
   AND created_at >= @today_start AND created_at < @tomorrow_start;
"""
    )
    at_limit = mysql(
        bounds
        + f"""
SELECT COUNT(*) FROM (
  SELECT user_id FROM task_instance
   WHERE user_id IN ({id_list})
     AND created_at >= @today_start AND created_at < @tomorrow_start
   GROUP BY user_id HAVING COUNT(*) >= 20
) t;
"""
    )
    print(f"state_users={len(user_ids)} instances_today={pre} users_at_limit_ge20={at_limit}")
    if args.dry_run:
        print("dry-run: no UPDATE")
        return 0
    # Backdate created_at only (countToday key). Keep rows; no wipe/scale.
    updated = mysql(
        bounds
        + f"""
UPDATE task_instance
   SET created_at = created_at - INTERVAL 1 DAY
 WHERE user_id IN ({id_list})
   AND created_at >= @today_start AND created_at < @tomorrow_start;
SELECT ROW_COUNT();
"""
    )
    # ROW_COUNT may be last line
    post = mysql(
        bounds
        + f"""
SELECT COUNT(*) FROM task_instance
 WHERE user_id IN ({id_list})
   AND created_at >= @today_start AND created_at < @tomorrow_start;
"""
    )
    at_limit2 = mysql(
        bounds
        + f"""
SELECT COUNT(*) FROM (
  SELECT user_id FROM task_instance
   WHERE user_id IN ({id_list})
     AND created_at >= @today_start AND created_at < @tomorrow_start
   GROUP BY user_id HAVING COUNT(*) >= 20
) t;
"""
    )
    print(f"updated_rows≈{updated} instances_today_after={post} users_at_limit_after={at_limit2}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
