#!/usr/bin/env bash
# NFR 可维护性 2 / design §7.9: regen openapi-typescript and fail on uncommitted drift.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root/web"
pnpm --filter @mkt/shared gen:api
if ! git -C "$root" diff --exit-code -- web/packages/shared/src/openapi web/packages/shared/openapi; then
  echo "openapi-typescript diff: backend contract changed but frontend types were not regenerated." >&2
  echo "Fix: pnpm --filter @mkt/shared gen:api && commit packages/shared" >&2
  exit 1
fi
