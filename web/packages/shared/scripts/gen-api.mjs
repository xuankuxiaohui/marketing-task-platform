#!/usr/bin/env node
/**
 * Consume springdoc JSON from the two apps (design §4.1 / 07 §2).
 * MUST NOT use KernelTestApplication or spike/5 JSON.
 *
 *   pnpm --filter @mkt/shared gen:api         # generate TS from committed JSON
 *   pnpm --filter @mkt/shared gen:api:fetch   # pull live JSON then generate
 */
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const specDir = join(root, "openapi");
const outDir = join(root, "src/openapi");
const fetchLive = process.argv.includes("--fetch");

const groups = [
  {
    name: "admin",
    url: `${process.env.ADMIN_OPENAPI_BASE ?? "http://127.0.0.1:8080"}/admin/v3/api-docs/admin`,
  },
  {
    name: "portal",
    url: `${process.env.PORTAL_OPENAPI_BASE ?? "http://127.0.0.1:8081"}/api/v3/api-docs/portal`,
  },
  {
    name: "internal",
    url: `${process.env.PORTAL_OPENAPI_BASE ?? "http://127.0.0.1:8081"}/api/v3/api-docs/internal`,
  },
];

mkdirSync(specDir, { recursive: true });
mkdirSync(outDir, { recursive: true });

if (fetchLive) {
  for (const group of groups) {
    const res = await fetch(group.url);
    if (!res.ok) {
      throw new Error(`fetch ${group.url} failed: HTTP ${res.status}`);
    }
    const body = await res.text();
    JSON.parse(body);
    writeFileSync(join(specDir, `${group.name}.json`), `${body}\n`);
  }
}

for (const group of groups) {
  const jsonPath = join(specDir, `${group.name}.json`);
  if (!existsSync(jsonPath)) {
    throw new Error(`missing ${jsonPath}; run pnpm --filter @mkt/shared gen:api:fetch`);
  }
  const outPath = join(outDir, `${group.name}.ts`);
  execFileSync("pnpm", ["exec", "openapi-typescript", jsonPath, "-o", outPath], {
    cwd: root,
    stdio: "inherit",
  });
}
