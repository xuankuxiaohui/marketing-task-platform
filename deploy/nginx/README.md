# Nginx gateway: static assets + TLS wiring (F16)

## What the template provides

| Piece | Path | Role |
|---|---|---|
| Main config | [nginx.conf](nginx.conf) | Portal + admin `server` blocks, API proxy, deny `/internal` `/actuator` OpenAPI, SPA `try_files`, asset cache headers |
| HTTP→HTTPS redirect stub | [redirect-https.inc](redirect-https.inc) | Empty by default (compose HTTP-only). Replace with [redirect-https.inc.tls](redirect-https.inc.tls) when HTTPS is live |
| HTTPS servers stub | [tls-servers.inc](tls-servers.inc) | Empty by default. Replace with [tls-servers.inc.example](tls-servers.inc.example) after certs exist |
| TLS settings | [tls.conf.example](tls.conf.example) | TLS 1.2+ / session / HSTS / certificate paths — copy to `tls.conf` and mount |
| Certs dir | [certs/](certs/) | Place `fullchain.pem` + `privkey.pem` (not committed) |
| Placeholders | [html-placeholders/](html-placeholders/) | Minimal index HTML so compose can start before a real frontend build |

Hosts: `portal.local` (default `_`) serves the client SPA at `/`, proxies `/api/` and also `/admin/` (so Host-less CI / same-origin `127.0.0.1` still reach admin APIs); `admin.local` serves the admin SPA at `/console/` and `/admin/`. Point real DNS / `/etc/hosts` at the published nginx port.

## Static assets

Compose mounts placeholders by default (see `docker-compose.yml` `nginx.volumes`). For a release build:

```bash
pnpm --filter client build
VITE_BASE=/console/ pnpm --filter admin build
```

Then switch the nginx volumes to:

```yaml
- ../web/apps/client/dist:/usr/share/nginx/html/client:ro
- ../web/apps/admin/dist:/usr/share/nginx/html/admin/console:ro
```

Admin **must** be built with `VITE_BASE=/console/` so asset URLs match the `/console/` location. Local Vite dev (`pnpm --filter admin dev`) keeps default base `/` and does not need this.

## Enable TLS

1. Put certificates in `deploy/nginx/certs/{fullchain,privkey}.pem` (local drill: `./deploy/nginx/generate-self-signed-certs.sh`; production: real CA chain).
2. `cp deploy/nginx/tls.conf.example deploy/nginx/tls.conf`
3. `cp deploy/nginx/tls-servers.inc.example deploy/nginx/tls-servers.inc`
4. `cp deploy/nginx/redirect-https.inc.tls deploy/nginx/redirect-https.inc`
5. Set `NGINX_HTTPS_PORT` in `deploy/.env` (compose publishes `127.0.0.1:${NGINX_HTTPS_PORT}:443`).
6. Reload nginx / recreate the container.

`tls.conf` and real certs stay out of git (see repo ignore rules / local only). Isolated stub/self-signed static evidence: [drills/2026-10-11-f16-tls-static-browser.md](drills/2026-10-11-f16-tls-static-browser.md). Production CA + Secure Cookie checks remain on the [go-live checklist](../R31-go-live-checklist.md).

## Isolated TLS / static drill (F16 evidence)

Self-signed certs + throwaway nginx compose (no DB/apps). Reproduces HTTPS static + Host routing + HTTP→HTTPS redirect:

```bash
./deploy/nginx/generate-self-signed-certs.sh   # → certs/*.pem (gitignored)
bash deploy/nginx/drills/verify-tls-static.sh # nginx -t, curl/OpenSSL checks, teardown
```

Evidence: [drills/2026-10-11-f16-tls-static-browser.md](drills/2026-10-11-f16-tls-static-browser.md) (curl + headless Chrome screenshots). Stub/self-signed is enough for template verification; production still needs a real chain and full-stack Secure Cookie checks on the [go-live checklist](../R31-go-live-checklist.md).

## Local check without full stack

```bash
# syntax only (needs docker): mount this directory and run nginx -t
docker run --rm -v "$PWD/deploy/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v "$PWD/deploy/nginx/redirect-https.inc:/etc/nginx/redirect-https.inc:ro" \
  -v "$PWD/deploy/nginx/tls-servers.inc:/etc/nginx/tls-servers.inc:ro" \
  -v "$PWD/deploy/nginx/html-placeholders/client:/usr/share/nginx/html/client:ro" \
  -v "$PWD/deploy/nginx/html-placeholders/admin:/usr/share/nginx/html/admin:ro" \
  nginx:1.27-alpine nginx -t
```
