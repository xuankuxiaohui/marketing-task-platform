import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export function repoRoot(): string {
  const here = dirname(fileURLToPath(import.meta.url));
  let candidate = resolve(here);
  for (let i = 0; i < 8; i += 1) {
    if (existsSync(join(candidate, "deploy/docker-compose.yml"))) {
      return candidate;
    }
    const parent = dirname(candidate);
    if (parent === candidate) {
      break;
    }
    candidate = parent;
  }
  throw new Error("deploy/docker-compose.yml not found");
}

export function loadDotEnv(): Record<string, string> {
  const file = join(repoRoot(), "deploy/.env");
  const source = existsSync(file)
    ? readFileSync(file, "utf8")
    : readFileSync(join(repoRoot(), "deploy/.env.example"), "utf8");
  const out: Record<string, string> = {};
  for (const line of source.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || !trimmed.includes("=")) {
      continue;
    }
    const eq = trimmed.indexOf("=");
    out[trimmed.slice(0, eq)] = trimmed.slice(eq + 1);
  }
  return out;
}

export function apiBase(): string {
  return process.env.E2E_API_BASE ?? "http://127.0.0.1:18080";
}

export function portalBase(): string {
  return process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:5174";
}

export function adminBase(): string {
  return process.env.PLAYWRIGHT_ADMIN_BASE_URL ?? "http://127.0.0.1:5173";
}

export function statePath(): string {
  return join(repoRoot(), "web/.e2e-state.json");
}
