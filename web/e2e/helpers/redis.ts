import { spawnSync } from "node:child_process";
import { join } from "node:path";
import { loadDotEnv, repoRoot } from "./env";

export function redisGet(key: string): string {
  const env = loadDotEnv();
  const password = env.REDIS_PASSWORD;
  if (!password) {
    throw new Error("REDIS_PASSWORD missing");
  }
  const root = repoRoot();
  const result = spawnSync(
    "docker",
    [
      "compose",
      "--env-file",
      join(root, "deploy/.env"),
      "-f",
      join(root, "deploy/docker-compose.yml"),
      "exec",
      "-T",
      "redis",
      "redis-cli",
      "-a",
      password,
      "-n",
      "2",
      "--raw",
      "GET",
      key,
    ],
    { encoding: "utf8" },
  );
  if (result.status !== 0) {
    throw new Error(`redis GET ${key} failed: ${result.stderr || result.stdout}`);
  }
  return (result.stdout || "").trim();
}

export async function waitRedisGet(key: string, tries = 20): Promise<string> {
  for (let i = 0; i < tries; i += 1) {
    try {
      const value = redisGet(key);
      if (value) {
        return value;
      }
    } catch {
      // compose redis may still be starting
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`captcha missing in redis: ${key}`);
}
