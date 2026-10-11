#!/usr/bin/env python3
"""Seed staging compose for k6 P0/P1 (design §7.8). Writes perf/state.json."""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
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


_REDIS_CID: str | None = None


def redis_container() -> str:
    """Resolve compose redis container id once; prefer docker exec over compose exec.

    p1 seed issues ~2000 captchas; each prior read spawned `docker compose exec`
    (YAML parse + API). Under sustained load that path produced empty GETs
    (~1020/2000 miss on #152). Direct `docker exec` on a cached cid is faster
    and avoids that flaky path; DB index stays 2 (DEC / InfraRedisProperties).
    """
    global _REDIS_CID
    if _REDIS_CID:
        return _REDIS_CID
    out = subprocess.check_output(COMPOSE + ["ps", "-q", "redis"], stderr=subprocess.DEVNULL)
    cid = out.decode().strip().split("\n", 1)[0].strip()
    if not cid:
        raise SystemExit("redis container not running (compose ps -q redis empty)")
    _REDIS_CID = cid
    return cid


def redis_get(key: str) -> str:
    cid = redis_container()
    out = subprocess.check_output(
        [
            "docker",
            "exec",
            "-i",
            cid,
            "redis-cli",
            "-a",
            ENV["REDIS_PASSWORD"],
            "--no-auth-warning",
            "-n",
            "2",
            "--raw",
            "GET",
            key,
        ],
        stderr=subprocess.DEVNULL,
    )
    val = out.decode().strip()
    return "" if val in ("", "(nil)") else val


# Seed-only hash; capu* rows never log in. Token pool still uses HTTP register.
SEED_PASSWORD_HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW"
EVENT_ID_BASE = 9_000_000_000_000
AD_POSITIONS = (
    ("home_banner", "CAROUSEL"),
    ("home_popup", "POPUP"),
    ("home_float", "FLOAT"),
    ("app_splash", "SPLASH"),
    ("home_image", "IMAGE"),
)


def mysql(sql: str) -> str:
    proc = subprocess.run(
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
        check=False,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        raise SystemExit(f"mysql failed rc={proc.returncode}: {proc.stderr or proc.stdout}\nSQL: {sql[:400]}")
    return proc.stdout


def captcha(path: str, realm: str, attempts: int = 6) -> tuple[str, str]:
    """Issue captcha via HTTP then read plaintext code from Redis DB 2.

    Honest seed path: never invent codes. Portal CaptchaService SETs
    captcha:{realm}:{id} before returning captchaId; we still poll briefly and
    re-issue on miss (observed #152 fail-stop at ~1020/2000 portal logins with
    empty Redis GET via compose-exec). Fail-stop only after real retries.
    """
    last_id = None
    for attempt in range(1, attempts + 1):
        data = require_ok(load(BASE + path), path)
        captcha_id = data["captchaId"]
        last_id = captcha_id
        key = f"captcha:{realm}:{captcha_id}"
        for poll in range(10):
            code = redis_get(key)
            if code:
                if attempt > 1 or poll > 0:
                    print(
                        f"captcha recovered realm={realm} attempt={attempt} poll={poll} id={captcha_id}",
                        flush=True,
                    )
                return captcha_id, code
            time.sleep(0.05 * (poll + 1))
        print(
            f"captcha miss realm={realm} attempt={attempt}/{attempts} id={captcha_id}; re-issue",
            flush=True,
        )
        time.sleep(0.25 * attempt)
    raise SystemExit(
        f"captcha missing in redis for {realm} after {attempts} attempts (lastId={last_id}; "
        "check Redis DB 2, portal CaptchaService SET, rate-limit, docker exec)"
    )


def cookie_header() -> str:
    return "; ".join(f"{c.name}={c.value}" for c in jar)


def unlocked_admin_password(init: str) -> str:
    return (init[:-1] + "?") if init.endswith("!") else (init + "!")


def try_admin_login(password: str) -> dict:
    captcha_id, code = captcha("/admin/captcha", "admin")
    try:
        return load(
            BASE + "/admin/auth/login",
            {
                "username": "admin",
                "password": password,
                "captchaId": captcha_id,
                "captchaCode": code,
            },
        )
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode()
        return json.loads(raw) if raw else {"code": exc.code}


def admin_login() -> dict:
    init = ENV["MKT_INIT_ADMIN_PASSWORD"]
    unlocked = unlocked_admin_password(init)
    login = try_admin_login(init)
    used = init
    if login.get("code") != 0:
        login = try_admin_login(unlocked)
        used = unlocked
    data = require_ok(login, "admin login")
    session = {"cookie": cookie_header(), "csrfToken": data["csrfToken"]}
    if data.get("mustChangePassword"):
        require_ok(
            load(
                BASE + "/admin/auth/password",
                {"oldPassword": used, "newPassword": unlocked},
                headers=admin_headers(session),
                method="PUT",
            ),
            "admin change password",
        )
        jar.clear()
        data = require_ok(try_admin_login(unlocked), "admin login after change")
        session = {"cookie": cookie_header(), "csrfToken": data["csrfToken"]}
    return session


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


def redis_cli(*args: str) -> str:
    cid = redis_container()
    out = subprocess.check_output(
        [
            "docker",
            "exec",
            "-i",
            cid,
            "redis-cli",
            "-a",
            ENV["REDIS_PASSWORD"],
            "--no-auth-warning",
            "-n",
            "2",
            *args,
        ],
        stderr=subprocess.DEVNULL,
    )
    return out.decode().strip()


def flush_risk_ip_cnt() -> None:
    """Drop compose-shared-IP window counters (R-c / R-f) left by seed/k6 via nginx hop."""
    keys = redis_cli("--scan", "--pattern", "risk:cnt:R-c:IP:*").split()
    keys += redis_cli("--scan", "--pattern", "risk:cnt:R-f:IP:*").split()
    keys = [k for k in keys if k]
    if not keys:
        return
    # redis-cli DEL key1 key2 ...
    redis_cli("DEL", *keys)
    print(f"flushed risk ip cnt keys={len(keys)}", flush=True)


def set_risk(enabled: int) -> None:
    """Toggle risk engine for capacity measurement.

    When enabling: turn on user/device window rules (R-a/R-b/R-d) so the engine is
    actually evaluating, but keep compose seed artifacts off:
      R-c / R-f — all k6/seed traffic collapses on the nginx container IP
      R-e — cascade grant is instantaneous (< appendix A min elapsed)
    Also flush stale IP window keys so prior blocked runs cannot poison the next.
    DEC-006 k6 gates are unchanged; this is measurement hygiene only.
    """
    if int(enabled) == 0:
        mysql("UPDATE risk_rule_config SET enabled=0")
        return
    # Capacity volume: keep R-a/R-b ceilings high enough that p0 VU×duration does not
    # trip user windows (appendix A 30/50 is fine for prod, not for shared-token k6).
    mysql(
        """
        UPDATE risk_rule_config SET enabled=1, threshold=10000, window_seconds=3600 WHERE rule_code='R-a';
        UPDATE risk_rule_config SET enabled=1, threshold=10000, window_seconds=86400 WHERE rule_code='R-b';
        UPDATE risk_rule_config SET enabled=0 WHERE rule_code='R-c';
        UPDATE risk_rule_config SET enabled=1, threshold=1000, window_seconds=86400 WHERE rule_code='R-d';
        UPDATE risk_rule_config SET enabled=0 WHERE rule_code='R-e';
        UPDATE risk_rule_config SET enabled=0 WHERE rule_code='R-f';
        """
    )
    flush_risk_ip_cnt()


def ensure_seq(rows: int = 1_000_000) -> None:
    mysql("CREATE TABLE IF NOT EXISTS perf_seq (n INT NOT NULL PRIMARY KEY)")
    existing = int(mysql("SELECT COUNT(*) FROM perf_seq").strip() or "0")
    if existing >= rows:
        return
    mysql(
        """
        INSERT IGNORE INTO perf_seq (n)
        SELECT a.i + b.i*10 + c.i*100 + d.i*1000 + e.i*10000 + f.i*100000 + 1
        FROM
          (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
           UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
          (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
           UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
          (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
           UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
          (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
           UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d,
          (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
           UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) e,
          (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
           UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) f
        """
    )
    print(f"perf_seq {mysql('SELECT COUNT(*) FROM perf_seq').strip()}", flush=True)


def ensure_event_partitions() -> None:
    now = datetime.now(timezone.utc)
    existing = {
        name.strip()
        for name in mysql(
            "SELECT PARTITION_NAME FROM information_schema.PARTITIONS "
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='evt_event_log' "
            "AND PARTITION_NAME IS NOT NULL"
        ).split()
        if name.strip()
    }
    year, month = now.year, now.month
    for _ in range(4):
        name = f"p{year:04d}{month:02d}"
        ny, nm = year, month + 1
        if nm > 12:
            nm = 1
            ny += 1
        bound = f"{ny:04d}-{nm:02d}-01 00:00:00"
        if name not in existing:
            mysql(
                f"ALTER TABLE evt_event_log ADD PARTITION (PARTITION {name} VALUES LESS THAN ('{bound}'))"
            )
            existing.add(name)
            print(f"added partition {name}", flush=True)
        month += 1
        if month > 12:
            month = 1
            year += 1


def bulk_users(count: int) -> None:
    if count <= 0:
        return
    ensure_seq(min(count, 1_000_000))
    mysql(
        f"""
        INSERT IGNORE INTO sys_portal_user
          (username, nickname, password_hash, status, deleted, registered_at)
        SELECT CONCAT('capu', LPAD(s.n, 7, '0')),
               CONCAT('用户', LPAD(s.n, 6, '0')),
               '{SEED_PASSWORD_HASH}',
               'ENABLED', 0, UTC_TIMESTAMP(3)
        FROM perf_seq s
        WHERE s.n <= {int(count)}
        """
    )
    print(f"bulk users target={count} now={mysql('SELECT COUNT(*) FROM sys_portal_user').strip()}", flush=True)


def bulk_events(count: int) -> None:
    if count <= 0:
        return
    ensure_seq(1_000_000)
    ensure_event_partitions()
    batches = (count + 999_999) // 1_000_000
    for batch in range(batches):
        offset = batch * 1_000_000
        take = min(1_000_000, count - offset)
        mysql(
            f"""
            INSERT IGNORE INTO evt_event_log
              (id, source, event_code, device_id, platform, app_version, events,
               batch_size, registered, simulated, server_time)
            SELECT {EVENT_ID_BASE + offset} + s.n,
                   'CLIENT',
                   IF(s.n % 2 = 0, 'page.view', 'task.card.exposure'),
                   CONCAT('cap-dev-', {offset} + s.n),
                   'WEB',
                   '0.1.0',
                   CAST('[{{"code":"page.view","props":{{"route":"/home"}}}}]' AS JSON),
                   1, 1, 0, UTC_TIMESTAMP(3)
            FROM perf_seq s
            WHERE s.n <= {int(take)}
            """
        )
        print(f"events batch {batch + 1}/{batches}", flush=True)
    print(f"event rows now={mysql('SELECT COUNT(*) FROM evt_event_log').strip()}", flush=True)


def ensure_ads(admin: dict) -> list[str]:
    start = (datetime.now(timezone.utc) - timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    end = (datetime.now(timezone.utc) + timedelta(days=365)).strftime("%Y-%m-%dT%H:%M:%SZ")
    codes: list[str] = []
    for code, form in AD_POSITIONS:
        page = require_ok(
            load(BASE + f"/admin/ad/positions?code={code}&page=1&pageSize=5"),
            "ad positions",
        )
        records = page.get("records") or []
        if records:
            position_id = int(records[0]["id"])
        else:
            saved = require_ok(
                load(
                    BASE + "/admin/ad/positions",
                    {
                        "code": code,
                        "name": code,
                        "form": form,
                        "platforms": ["WEB"],
                        "status": "ENABLED",
                    },
                    headers=admin_headers(admin),
                ),
                f"ad position {code}",
            )
            position_id = int(saved["id"])
        mats = require_ok(
            load(BASE + f"/admin/ad/materials?title=perf-{code}&page=1&pageSize=5"),
            "ad materials",
        )
        mrec = mats.get("records") or []
        if mrec:
            material_id = int(mrec[0]["id"])
        else:
            created = require_ok(
                load(
                    BASE + "/admin/ad/materials",
                    {
                        "title": f"perf-{code}",
                        "imageUrl": "https://example.com/perf-ad.png",
                        "jumpType": "NONE",
                        "weight": 50,
                        "startTime": start,
                        "endTime": end,
                        "status": "ENABLED",
                    },
                    headers=admin_headers(admin),
                ),
                f"ad material {code}",
            )
            material_id = int(created["id"])
        require_ok(
            load(
                BASE + f"/admin/ad/positions/{position_id}/materials",
                {
                    "materialId": material_id,
                    "weight": 50,
                    "startTime": start,
                    "endTime": end,
                    "platforms": ["WEB"],
                    "grayType": "NONE",
                    "status": "ENABLED",
                },
                headers=admin_headers(admin),
            ),
            f"ad bind {code}",
        )
        codes.append(code)
    return codes


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scale", choices=["p0", "nfr", "p1"], default=os.environ.get("SEED_SCALE", "p0"))
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
    # p0 complete: TaskSettings.dailyLimitPerUser defaults to 20 and is NOT ConfigService-backed.
    # Seed pre-starts progress (users[:50]) + callback (users[50:150]) consume 1 claim each.
    # Keep cascade_n ≤ 18 so every user can finish the cartesian product.
    # users_n=1200: risk-on/off variant-split after warm; ~5m×~25grant/s headroom
    # per variant under dailyLimit=20 (finite striped pool, no wrap).
    users_n = 1200 if args.scale == "p0" else 2000
    instances = 0 if args.scale == "p0" else 500_000
    archive_users = 1_000_000 if args.scale == "p1" else 0
    archive_events = 5_000_000 if args.scale == "p1" else 0
    cascade_n = 18 if args.scale == "p0" else 20
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
    # Seed registers many portal users from one compose client IP; raise login/register windows.
    put_config(admin, "ratelimit.login.ip.per-minute", "100000")
    put_config(admin, "ratelimit.login.account.per-minute", "100000")
    # Headroom for docker-exec poll after issue; default 120s is enough for one
    # round-trip but re-issue storms under miss benefit from a longer TTL.
    put_config(admin, "auth.captcha.ttl-seconds", "600")
    ad_codes = ensure_ads(admin)

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

    # Unique usernames per seed run: TaskSettings daily claim limit is in-process (default 20)
    # and countToday accumulates across same-day re-seeds if logins reuse perf_u{i}.
    seed_tag = datetime.now(timezone.utc).strftime("%m%d%H%M%S")
    users = []
    for i in range(users_n):
        username = f"perf_u{i}_{seed_tag}"
        last_err: Exception | None = None
        for attempt in range(1, 4):
            try:
                users.append(register_user(username, password))
                last_err = None
                break
            except SystemExit as exc:
                last_err = exc
                msg = str(exc)
                if "captcha missing" not in msg and "captcha" not in msg.lower():
                    raise
                print(f"user {username} attempt {attempt}/3 failed: {msg}; retry", flush=True)
                time.sleep(0.5 * attempt)
        if last_err is not None:
            raise SystemExit(
                f"portal login/register failed for {username} after retries "
                f"({len(users)}/{users_n} done): {last_err}"
            )
        if i % 20 == 0:
            print(f"users {i}/{users_n}", flush=True)

    # Seed many starts from one compose client IP; risk IP-claim rules block otherwise.
    set_risk(0)
    progress_instances = start_instances(users[:50], progress_id, "pg", 50)
    callback_instances = start_instances(users[50:150], callback_id, "cb", 100)
    set_risk(1)

    bulk_instances(instances, int(users[0]["userId"]))
    bulk_users(archive_users)
    bulk_events(archive_events)

    user_rows = int(mysql("SELECT COUNT(*) FROM sys_portal_user WHERE deleted=0").strip() or "0")
    instance_rows = int(mysql("SELECT COUNT(*) FROM task_instance").strip() or "0")
    event_rows = int(mysql("SELECT COUNT(*) FROM evt_event_log").strip() or "0")

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
        "adCodes": ad_codes,
        "peakEps": 3000,
        "capacity": {
            "users": user_rows,
            "instances": instance_rows,
            "events": event_rows,
        },
        "scale": args.scale,
        "seededAt": int(time.time()),
    }
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(state) + "\n")
    print(
        f"wrote {STATE_PATH} tokenUsers={len(users)} listTasks={len(list_ids)} "
        f"instancesScale={instances} archiveUsers={archive_users} events={archive_events}"
    )


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as exc:
        print(exc.read().decode(), file=sys.stderr)
        raise
