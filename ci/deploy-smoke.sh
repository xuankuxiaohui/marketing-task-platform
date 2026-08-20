#!/usr/bin/env bash
# R31.1: compose up twice → health green, Flyway validate, login smoke,
# register-claim-grant-points twice with identical fingerprints.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/home/box/.local/jdk/jdk-26.0.2+10}"
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:${PATH:-}"

ENV_FILE="$ROOT/deploy/.env"
if [ ! -f "$ENV_FILE" ]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [ "${REDIS_DATABASE}" != "2" ]; then
  echo "REDIS_DATABASE must be 2" >&2
  exit 1
fi

ADMIN_JAR="$ROOT/server/admin-app/target/admin-app-0.1.0-SNAPSHOT.jar"
PORTAL_JAR="$ROOT/server/portal-app/target/portal-app-0.1.0-SNAPSHOT-exec.jar"
if [ ! -f "$ADMIN_JAR" ] || [ ! -f "$PORTAL_JAR" ]; then
  (cd "$ROOT/server" && mvn -q -DskipTests -DskipITs package)
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml")

"${COMPOSE[@]}" up -d --build
wait_stack() {
  local tries=60
  local i
  for i in $(seq 1 "$tries"); do
    if curl -fsS "http://127.0.0.1:${NGINX_HTTP_PORT:-18080}/healthz" >/dev/null 2>&1 \
      && "${COMPOSE[@]}" exec -T admin-app curl -fsS http://127.0.0.1:8080/actuator/health/readiness | grep -q UP \
      && "${COMPOSE[@]}" exec -T portal-app-1 curl -fsS http://127.0.0.1:8081/actuator/health/readiness | grep -q UP \
      && "${COMPOSE[@]}" exec -T portal-app-2 curl -fsS http://127.0.0.1:8081/actuator/health/readiness | grep -q UP; then
      return 0
    fi
    sleep 5
  done
  echo "stack not healthy" >&2
  "${COMPOSE[@]}" ps
  return 1
}

wait_stack
"${COMPOSE[@]}" up -d
wait_stack

flyway_versions() {
  "${COMPOSE[@]}" exec -T mysql \
    mysql -N -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" \
    -e "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank"
}

VERS1="$(flyway_versions | tr '\n' ' ')"
echo "flyway: $VERS1"
echo "$VERS1" | grep -q '1' && echo "$VERS1" | grep -q '4'

sql_count() {
  "${COMPOSE[@]}" exec -T mysql \
    mysql -N -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" \
    -e "$1"
}

BASE="http://127.0.0.1:${NGINX_HTTP_PORT:-18080}"

run_flow() {
  python3 - "$BASE" "$REDIS_PASSWORD" "$MKT_INIT_ADMIN_PASSWORD" "${COMPOSE[*]}" <<'PY'
import http.cookiejar
import json
import subprocess
import sys
import urllib.parse
import urllib.request

base, redis_password, admin_password, compose_flat = sys.argv[1:5]
compose = compose_flat.split()

class Policy(http.cookiejar.DefaultCookiePolicy):
    def set_ok_secure(self, cookie, request):
        return True
    def return_ok_secure(self, cookie, request):
        return True

jar = http.cookiejar.CookieJar(policy=Policy())
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def load(url, data=None, headers=None, method=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, method=method or ("GET" if body is None else "POST"))
    req.add_header("Accept", "application/json")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    with opener.open(req, timeout=30) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def require_ok(payload, label):
    if payload.get("code") != 0:
        raise SystemExit(f"{label} failed: {payload}")
    return payload.get("data")


def redis_get(key):
    out = subprocess.check_output(
        compose + ["exec", "-T", "redis", "redis-cli", "-a", redis_password, "-n", "2", "--raw", "GET", key],
        stderr=subprocess.DEVNULL,
    )
    return out.decode().strip()


def captcha(path, realm):
    data = require_ok(load(base + path), path)
    code = redis_get(f"captcha:{realm}:{data['captchaId']}")
    if not code:
        raise SystemExit(f"captcha missing in redis for {realm}")
    return data["captchaId"], code


captcha_id, code = captcha("/admin/captcha", "admin")
login = require_ok(
    load(
        base + "/admin/auth/login",
        {
            "username": "admin",
            "password": admin_password,
            "captchaId": captcha_id,
            "captchaCode": code,
        },
    ),
    "admin login",
)
csrf = login["csrfToken"]
admin_headers = {"X-CSRF-Token": csrf}

prizes = require_ok(load(base + "/admin/reward/prizes?code=smoke_pts&page=1&pageSize=5"), "prize page")
records = prizes.get("records") or []
if records:
    prize_id = records[0]["id"]
else:
    created = require_ok(
        load(
            base + "/admin/reward/prizes",
            {
                "code": "smoke_pts",
                "name": "smoke points",
                "categoryCode": "POINTS",
                "typeParams": {"points": 10},
                "totalStock": 100,
                "dailyClaimLimit": 0,
                "totalClaimLimit": 0,
                "claimMode": "AUTO",
            },
            headers=admin_headers,
        ),
        "prize create",
    )
    prize_id = created["id"]
    require_ok(
        load(base + f"/admin/reward/prizes/{prize_id}/enable", {"confirm": True}, headers=admin_headers),
        "prize enable",
    )

tasks = require_ok(load(base + "/admin/task/definitions?code=smoke_task&page=1&pageSize=5"), "task page")
trecords = tasks.get("records") or []
if trecords:
    task_id = trecords[0]["id"]
    status = trecords[0].get("status")
    if status != "PUBLISHED":
        require_ok(
            load(base + f"/admin/task/definitions/{task_id}/publish", {"confirm": True}, headers=admin_headers),
            "task publish",
        )
else:
    saved = require_ok(
        load(
            base + "/admin/task/definitions/save-aggregate",
            {
                "code": "smoke_task",
                "name": "smoke task",
                "cycleType": "NONE",
                "sortWeight": 0,
                "gray": {"type": "NONE"},
                "steps": [{"code": "rwd", "name": "reward", "seq": 1, "type": "REWARD", "prizeId": prize_id}],
                "transitions": [],
                "actions": [],
            },
            headers=admin_headers,
        ),
        "task save",
    )
    task_id = saved["id"]
    require_ok(
        load(base + f"/admin/task/definitions/{task_id}/publish", {"confirm": True}, headers=admin_headers),
        "task publish",
    )

avail = require_ok(
    load(base + "/api/common/auth/username-available?" + urllib.parse.urlencode({"username": "smoke_p0"})),
    "username-available",
)
captcha_id, code = captcha("/api/common/captcha", "portal")
portal_headers = {"X-Device-Id": "smoke-device", "X-Client-Platform": "WEB"}
if avail.get("available") is True or avail.get("available") == True:
    auth = require_ok(
        load(
            base + "/api/common/auth/register",
            {
                "username": "smoke_p0",
                "password": "Smokeusr1",
                "captchaId": captcha_id,
                "captchaCode": code,
            },
            headers=portal_headers,
        ),
        "register",
    )
else:
    auth = require_ok(
        load(
            base + "/api/common/auth/login",
            {
                "username": "smoke_p0",
                "password": "Smokeusr1",
                "captchaId": captcha_id,
                "captchaCode": code,
            },
            headers=portal_headers,
        ),
        "portal login",
    )
token = auth["token"]
portal_headers["Authorization"] = f"Bearer {token}"
started = require_ok(
    load(base + f"/api/common/task/{task_id}/start", {}, headers=portal_headers),
    "task start",
)
points = require_ok(load(base + "/api/common/points/balance", headers=portal_headers), "points")
print(json.dumps({"taskId": task_id, "prizeId": prize_id, "instanceId": started["instanceId"], "balance": points["balance"]}))
PY
}

FLOW1="$(run_flow)"
FLOW2="$(run_flow)"
echo "flow1 $FLOW1"
echo "flow2 $FLOW2"

BAL1="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["balance"])' "$FLOW1")"
BAL2="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["balance"])' "$FLOW2")"
INST1="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["instanceId"])' "$FLOW1")"
INST2="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["instanceId"])' "$FLOW2")"
if [ "$BAL1" != "$BAL2" ] || [ "$INST1" != "$INST2" ] || [ "$BAL1" != "10" ]; then
  echo "dataset mismatch: $FLOW1 vs $FLOW2" >&2
  exit 1
fi

USERS="$(sql_count "SELECT COUNT(*) FROM sys_portal_user WHERE username='smoke_p0' AND deleted=0")"
INSTANCES="$(sql_count "SELECT COUNT(*) FROM task_instance ti JOIN sys_portal_user u ON u.id=ti.user_id WHERE u.username='smoke_p0'")"
GRANTS="$(sql_count "SELECT COUNT(*) FROM rwd_grant_record g JOIN sys_portal_user u ON u.id=g.user_id WHERE u.username='smoke_p0'")"
if [ "$USERS" != "1" ] || [ "$INSTANCES" != "1" ] || [ "$GRANTS" != "1" ]; then
  echo "sql fingerprint $USERS $INSTANCES $GRANTS" >&2
  exit 1
fi

curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT:-19090}/api/v1/targets" | grep -q admin-app
curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT:-19090}/api/v1/targets" | grep -q portal-app
echo "deploy-smoke ok"
