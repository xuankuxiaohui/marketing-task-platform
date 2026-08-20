#!/usr/bin/env python3
"""Seed staging compose for k6 P0 (design §7.8). Writes perf/state.json."""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / "deploy" / ".env"
COMPOSE_FILE = ROOT / "deploy" / "docker-compose.yml"
STATE_PATH = ROOT / "perf" / "state.json"


def load_env() -> dict[str, str]:
    path = ENV_FILE if ENV_FILE.exists() else ROOT / "deploy" / ".env.example"
    out: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        out[key] = value
    return out


ENV = load_env()
BASE = os.environ.get("BASE_URL", f"http://127.0.0.1:{ENV.get('NGINX_HTTP_PORT', '18080')}")
COMPOSE = [
    "docker",
    "compose",
    "--env-file",
    str(ENV_FILE if ENV_FILE.exists() else ROOT / "deploy" / ".env.example"),
    "-f",
    str(COMPOSE_FILE),
]


class Policy(http.cookiejar.DefaultCookiePolicy):
    def set_ok_secure(self, cookie, request):
        return True

    def return_ok_secure(self, cookie, request):
        return True


jar = http.cookiejar.CookieJar(policy=Policy())
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def load(url: str, data=None, headers=None, method=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, method=method or ("GET" if body is None else "POST"))
    req.add_header("Accept", "application/json")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    with opener.open(req, timeout=60) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def require_ok(payload, label):
    if payload.get("code") != 0:
        raise SystemExit(f"{label} failed: {payload}")
    return payload.get("data")


def redis_get(key: str) -> str:
    out = subprocess.check_output(
        COMPOSE + ["exec", "-T", "redis", "redis-cli", "-a", ENV["REDIS_PASSWORD"], "-n", "2", "--raw", "GET", key],
        stderr=subprocess.DEVNULL,
    )
    return out.decode().strip()


def mysql(sql: str) -> str:
    return subprocess.check_output(
        COMPOSE
        + [
            "exec",
            "-T",
            "mysql",
            "mysql",
            "-N",
            "-uroot",
            f"-p{ENV['MYSQL_ROOT_PASSWORD']}",
            ENV.get("MYSQL_DATABASE", "mkt_platform"),
            "-e",
            sql,
        ],
        stderr=subprocess.DEVNULL,
    ).decode()


def captcha(path: str, realm: str) -> tuple[str, str]:
    data = require_ok(load(BASE + path), path)
    code = redis_get(f"captcha:{realm}:{data['captchaId']}")
    if not code:
        raise SystemExit(f"captcha missing in redis for {realm}")
    return data["captchaId"], code


def cookie_header() -> str:
    return "; ".join(f"{c.name}={c.value}" for c in jar)


def admin_login() -> dict:
    captcha_id, code = captcha("/admin/captcha", "admin")
    login = require_ok(
        load(
            BASE + "/admin/auth/login",
            {
                "username": "admin",
                "password": ENV["MKT_INIT_ADMIN_PASSWORD"],
                "captchaId": captcha_id,
                "captchaCode": code,
            },
        ),
        "admin login",
    )
    return {"cookie": cookie_header(), "csrfToken": login["csrfToken"]}


def admin_headers(admin: dict) -> dict:
    return {"Cookie": admin["cookie"], "X-CSRF-Token": admin["csrfToken"]}


def ensure_prize(admin: dict, code: str, claim_mode: str) -> int:
    prizes = require_ok(load(BASE + f"/admin/reward/prizes?code={code}&page=1&pageSize=5"), "prize page")
    records = prizes.get("records") or []
    if records:
        return int(records[0]["id"])
    created = require_ok(
        load(
            BASE + "/admin/reward/prizes",
            {
                "code": code,
                "name": f"{code} points",
                "categoryCode": "POINTS",
                "typeParams": {"points": 10},
                "totalStock": 10_000_000,
                "dailyClaimLimit": 0,
                "totalClaimLimit": 0,
                "claimMode": claim_mode,
            },
            headers=admin_headers(admin),
        ),
        "prize create",
    )
    prize_id = int(created["id"])
    require_ok(
        load(BASE + f"/admin/reward/prizes/{prize_id}/enable", {"confirm": True}, headers=admin_headers(admin)),
        "prize enable",
    )
    return prize_id


def find_task(code: str) -> dict | None:
    page = require_ok(load(BASE + f"/admin/task/definitions?code={code}&page=1&pageSize=5"), "task page")
    records = page.get("records") or []
    return records[0] if records else None


def save_and_publish(admin: dict, body: dict) -> int:
    existing = find_task(body["code"])
    if existing:
        task_id = int(existing["id"])
        if existing.get("status") != "PUBLISHED":
            require_ok(
                load(
                    BASE + f"/admin/task/definitions/{task_id}/publish",
                    {"confirm": True},
                    headers=admin_headers(admin),
                ),
                "task publish",
            )
        return task_id
    saved = require_ok(
        load(BASE + "/admin/task/definitions/save-aggregate", body, headers=admin_headers(admin)),
        f"save {body['code']}",
    )
    task_id = int(saved["id"])
    require_ok(
        load(BASE + f"/admin/task/definitions/{task_id}/publish", {"confirm": True}, headers=admin_headers(admin)),
        f"publish {body['code']}",
    )
    return task_id


def cascade_steps(prize_id: int) -> tuple[list, list]:
    steps = []
    transitions = []
    for seq in range(1, 50):
        steps.append({"code": f"p{seq:02d}", "name": f"passive {seq}", "seq": seq, "type": "PASSIVE"})
        if seq < 49:
            transitions.append({"fromStepCode": f"p{seq:02d}", "toStepCode": f"p{seq + 1:02d}"})
    steps.append({"code": "rwd", "name": "reward", "seq": 50, "type": "REWARD", "prizeId": prize_id})
    transitions.append({"fromStepCode": "p49", "toStepCode": "rwd"})
    return steps, transitions


def gray_for(index: int) -> dict:
    if index % 20 == 0:
        return {"type": "RATIO", "ratio": 0}
    if index % 10 == 0:
        return {"type": "RATIO", "ratio": 50}
    return {"type": "NONE"}


def register_user(username: str, password: str) -> dict:
    avail = require_ok(
        load(BASE + "/api/common/auth/username-available?" + urllib.parse.urlencode({"username": username})),
        "username-available",
    )
    captcha_id, code = captcha("/api/common/captcha", "portal")
    path = "/api/common/auth/login" if avail.get("available") is False else "/api/common/auth/register"
    auth = require_ok(
        load(
            BASE + path,
            {"username": username, "password": password, "captchaId": captcha_id, "captchaCode": code},
            headers={"X-Device-Id": f"k6-{username}", "X-Client-Platform": "WEB"},
        ),
        path,
    )
    return {
        "username": username,
        "password": password,
        "token": auth["token"],
        "userId": auth["userId"],
        "deviceId": f"k6-{username}",
    }


def put_config(admin: dict, key: str, value: str) -> None:
    require_ok(
        load(
            BASE + f"/admin/system/configs/{urllib.parse.quote(key, safe='')}",
            {"value": value},
            headers=admin_headers(admin),
            method="PUT",
        ),
        f"config {key}",
    )


def ensure_internal_app(admin: dict, name: str) -> dict:
    page = require_ok(load(BASE + "/admin/identity/internal-apps?page=1&pageSize=50"), "internal apps")
    for row in page.get("records") or []:
        if row.get("appName") == name and row.get("status") == "ENABLED":
            # secret is write-once; rotate to capture it for k6
            rotated = require_ok(
                load(
                    BASE + f"/admin/identity/internal-apps/{row['id']}/rotate-secret",
                    {},
                    headers=admin_headers(admin),
                ),
                "rotate secret",
            )
            return {"appId": row["appId"], "secret": rotated["secret"]}
    created = require_ok(
        load(BASE + "/admin/identity/internal-apps", {"appName": name}, headers=admin_headers(admin)),
        "create internal app",
    )
    return {"appId": created["appId"], "secret": created["secret"]}


def start_instances(users: list[dict], task_id: int, step_code: str, limit: int) -> list[dict]:
    out = []
    for user in users[:limit]:
        started = require_ok(
            load(
                BASE + f"/api/common/task/{task_id}/start",
                {},
                headers={
                    "Authorization": f"Bearer {user['token']}",
                    "X-Device-Id": user["deviceId"],
                    "X-Client-Platform": "WEB",
                },
            ),
            "start instance",
        )
        out.append({"instanceId": started["instanceId"], "stepCode": step_code, "userId": user["userId"]})
    return out


def bulk_instances(count: int, user_id: int) -> None:
    if count <= 0:
        return
    mysql(
        f"""
        INSERT INTO task_instance (task_id, task_code, version, snapshot_id, user_id, cycle_key, status, expire_at, simulated)
        SELECT d.id, d.code, d.version, s.id, {int(user_id)}, CONCAT('bulk-', seq.n), 'COMPLETED', DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 30 DAY), 0
        FROM task_definition d
        JOIN task_version_snapshot s ON s.task_id = d.id AND s.version = d.version
        JOIN (
          SELECT @n := @n + 1 AS n FROM
          (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
          (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
          (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
          (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) dgt,
          (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) e,
          (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) f,
          (SELECT @n := 0) init
        ) seq
        WHERE d.code = 'perf_list_0000' AND seq.n <= {int(count)}
        ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
        """
    )


def set_risk(enabled: int) -> None:
    mysql(f"UPDATE risk_rule_config SET enabled={int(enabled)}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scale", choices=["p0", "nfr"], default=os.environ.get("SEED_SCALE", "p0"))
    parser.add_argument("--tokens-only", action="store_true")
    parser.add_argument("--risk", choices=["on", "off"])
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.risk:
        set_risk(1 if args.risk == "on" else 0)
        print(f"risk {args.risk}")
        return

    list_tasks = 1000
    users_n = 200 if args.scale == "p0" else 2000
    instances = 0 if args.scale == "p0" else 500_000
    cascade_n = 20
    password = "Perfuser1"

    admin = admin_login()
    if args.tokens_only and STATE_PATH.exists():
        state = json.loads(STATE_PATH.read_text())
        users = [register_user(u["username"], password) for u in state.get("users", [])]
        state["users"] = users
        state["admin"] = admin
        STATE_PATH.write_text(json.dumps(state, indent=2) + "\n")
        print(f"refreshed tokens {len(users)}")
        return

    put_config(admin, "ratelimit.internal.accesskey.per-second", "5000")
    put_config(admin, "ratelimit.track.batch.per-user-per-minute", "1000000")
    put_config(admin, "ratelimit.portal-write.user.per-second", "5000")

    prize_auto = ensure_prize(admin, "perf_auto_pts", "AUTO")
    prize_manual = ensure_prize(admin, "perf_manual_pts", "MANUAL")
    internal = ensure_internal_app(admin, "k6-p0")

    list_ids = []
    for i in range(list_tasks):
        code = f"perf_list_{i:04d}"
        list_ids.append(
            save_and_publish(
                admin,
                {
                    "code": code,
                    "name": f"perf list {i}",
                    "cycleType": "NONE",
                    "sortWeight": i,
                    "gray": gray_for(i),
                    "steps": [{"code": "rwd", "name": "reward", "seq": 1, "type": "REWARD", "prizeId": prize_auto}],
                    "transitions": [],
                    "actions": [],
                },
            )
        )
        if i % 50 == 0:
            print(f"list tasks {i}/{list_tasks}", flush=True)

    steps, transitions = cascade_steps(prize_auto)
    cascade_ids = []
    risk_ids = []
    for i in range(cascade_n):
        cascade_ids.append(
            save_and_publish(
                admin,
                {
                    "code": f"perf_cascade_{i:02d}",
                    "name": f"perf cascade {i}",
                    "cycleType": "NONE",
                    "sortWeight": 0,
                    "gray": {"type": "NONE"},
                    "steps": steps,
                    "transitions": transitions,
                    "actions": [],
                },
            )
        )
        risk_ids.append(
            save_and_publish(
                admin,
                {
                    "code": f"perf_risk_{i:02d}",
                    "name": f"perf risk {i}",
                    "cycleType": "NONE",
                    "sortWeight": 0,
                    "gray": {"type": "NONE"},
                    "steps": steps,
                    "transitions": transitions,
                    "actions": [],
                },
            )
        )

    callback_id = save_and_publish(
        admin,
        {
            "code": "perf_callback",
            "name": "perf callback",
            "cycleType": "NONE",
            "sortWeight": 0,
            "gray": {"type": "NONE"},
            "steps": [{"code": "cb", "name": "callback", "seq": 1, "type": "CALLBACK"}],
            "transitions": [],
            "actions": [],
        },
    )
    progress_id = save_and_publish(
        admin,
        {
            "code": "perf_progress",
            "name": "perf progress",
            "cycleType": "NONE",
            "sortWeight": 0,
            "gray": {"type": "NONE"},
            "steps": [{"code": "pg", "name": "progress", "seq": 1, "type": "PROGRESS", "progressTarget": 2000000000}],
            "transitions": [],
            "actions": [],
        },
    )

    users = []
    for i in range(users_n):
        users.append(register_user(f"perf_u{i}", password))
        if i % 20 == 0:
            print(f"users {i}/{users_n}", flush=True)

    progress_instances = start_instances(users[:50], progress_id, "pg", 50)
    callback_instances = start_instances(users[50:150], callback_id, "cb", 100)

    bulk_instances(instances, int(users[0]["userId"]))

    state = {
        "admin": admin,
        "internal": internal,
        "prizeAutoId": prize_auto,
        "prizeManualId": prize_manual,
        "listTaskIds": list_ids,
        "cascadeTaskIds": cascade_ids,
        "riskTaskIds": risk_ids,
        "callbackTaskId": callback_id,
        "progressTaskId": progress_id,
        "users": users,
        "progressInstances": progress_instances,
        "callbackInstances": callback_instances,
        "scale": args.scale,
        "seededAt": int(time.time()),
    }
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(state) + "\n")
    print(f"wrote {STATE_PATH} users={len(users)} listTasks={len(list_ids)} instancesScale={instances}")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as exc:
        print(exc.read().decode(), file=sys.stderr)
        raise
