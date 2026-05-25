#!/usr/bin/env bash
#
# Generate TypeScript types from the backend OpenAPI spec.
#
# Prerequisites:
#   1. Backend running on http://localhost:8080 (springdoc-openapi enabled)
#   2. openapi-typescript installed in admin-web and client-web
#
# Usage:
#   ./scripts/generate-api-types.sh
#
# Optionally pass a backend URL:
#   ./scripts/generate-api-types.sh http://my-backend:8080
#

set -euo pipefail

BACKEND_URL="${1:-http://localhost:8080}"
OPENAPI_JSON_URL="${BACKEND_URL}/v3/api-docs"
TEMP_FILE="$(mktemp -d)/openapi.json"
OUTPUT_FILE="schema.d.ts"

echo "==> Fetching OpenAPI spec from ${OPENAPI_JSON_URL} ..."
curl -fsSL -o "${TEMP_FILE}" "${OPENAPI_JSON_URL}" || {
  echo "ERROR: Failed to fetch OpenAPI spec. Is the backend running at ${BACKEND_URL}?"
  exit 1
}

echo "==> Generating TypeScript types for admin-web ..."
mkdir -p admin-web/src/api/generated
npx --prefix admin-web openapi-typescript "${TEMP_FILE}" -o "admin-web/src/api/generated/${OUTPUT_FILE}"
echo "    -> admin-web/src/api/generated/${OUTPUT_FILE}"

echo "==> Generating TypeScript types for client-web ..."
mkdir -p client-web/src/api/generated
npx --prefix client-web openapi-typescript "${TEMP_FILE}" -o "client-web/src/api/generated/${OUTPUT_FILE}"
echo "    -> client-web/src/api/generated/${OUTPUT_FILE}"

rm -f "${TEMP_FILE}"
echo "==> Done. Types generated successfully."
