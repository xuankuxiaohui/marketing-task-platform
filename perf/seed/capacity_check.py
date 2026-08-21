#!/usr/bin/env python3
"""Assert capacity seed matches NFR 性能 8 / design §2.6. Writes capacity.json."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from seed import mysql

PEAK_EPS = 3000
USERS = 1_000_000
INSTANCES = 500_000
EVENTS = 5_000_000


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report-dir", required=True)
    args = parser.parse_args()
    report_dir = Path(args.report_dir)
    report_dir.mkdir(parents=True, exist_ok=True)

    users = int(mysql("SELECT COUNT(*) FROM sys_portal_user WHERE deleted=0").strip() or "0")
    instances = int(mysql("SELECT COUNT(*) FROM task_instance").strip() or "0")
    events = int(mysql("SELECT COUNT(*) FROM evt_event_log").strip() or "0")
    partitions = [
        p.strip()
        for p in mysql(
            "SELECT PARTITION_NAME FROM information_schema.PARTITIONS "
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='evt_event_log' "
            "AND PARTITION_NAME IS NOT NULL"
        ).split()
        if p.strip()
    ]
    payload = {
        "nfr": "性能8",
        "peakEps": PEAK_EPS,
        "users": users,
        "instances": instances,
        "events": events,
        "partitions": partitions,
        "thresholds": {"users": USERS, "instances": INSTANCES, "events": EVENTS, "peakEps": PEAK_EPS},
    }
    (report_dir / "capacity.json").write_text(json.dumps(payload, indent=2) + "\n")
    print(
        f"capacity users={users} instances={instances} events={events} partitions={partitions} peakEps={PEAK_EPS}"
    )
    errors = []
    if users < USERS:
        errors.append(f"portal users {users} < {USERS}")
    if instances < INSTANCES:
        errors.append(f"instances {instances} < {INSTANCES}")
    if events < EVENTS:
        errors.append(f"event rows {events} < {EVENTS}")
    if not partitions:
        errors.append("evt_event_log has no monthly partitions")
    if errors:
        raise SystemExit("NFR 性能 8 capacity seed short: " + "; ".join(errors))


if __name__ == "__main__":
    main()
