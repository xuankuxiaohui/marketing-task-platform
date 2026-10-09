import { spawnSync } from "node:child_process";
import { createConnection } from "node:net";
import { join } from "node:path";
import { envFilePath, loadDotEnv, repoRoot } from "./env";

type RedisTunnel = { host: string; port: number };

function redisTunnel(): RedisTunnel | undefined {
  const host = process.env.E2E_REDIS_HOST?.trim();
  const portText = process.env.E2E_REDIS_PORT?.trim();
  if (!host && !portText) {
    return undefined;
  }
  const port = Number(portText);
  if (
    !host ||
    !["127.0.0.1", "localhost", "::1"].includes(host) ||
    !portText ||
    !/^\d+$/.test(portText) ||
    !Number.isInteger(port) ||
    port < 1 ||
    port > 65535 ||
    port === 6379
  ) {
    throw new Error("E2E Redis requires a loopback tunnel host and a dedicated port other than 6379");
  }
  return { host, port };
}

function redisCommand(...args: string[]): Buffer {
  return Buffer.concat([
    Buffer.from(`*${args.length}\r\n`),
    ...args.flatMap((arg) => {
      const value = Buffer.from(arg);
      return [Buffer.from(`$${value.length}\r\n`), value, Buffer.from("\r\n")];
    }),
  ]);
}

function redisReply(buffer: Buffer): { value: string; bytes: number } | undefined {
  const lineEnd = buffer.indexOf("\r\n");
  if (lineEnd < 0) {
    return undefined;
  }
  const type = buffer.toString("ascii", 0, 1);
  const line = buffer.toString("utf8", 1, lineEnd);
  if (type === "+") {
    return { value: line, bytes: lineEnd + 2 };
  }
  if (type === "$") {
    const length = Number(line);
    if (!/^-?\d+$/.test(line) || !Number.isInteger(length) || length < -1 || length > 8192) {
      throw new Error("Redis tunnel returned an invalid captcha reply");
    }
    if (length === -1) {
      return { value: "", bytes: lineEnd + 2 };
    }
    const end = lineEnd + 2 + length;
    if (buffer.length < end + 2) {
      return undefined;
    }
    if (buffer.toString("ascii", end, end + 2) !== "\r\n") {
      throw new Error("Redis tunnel returned an invalid captcha reply");
    }
    return { value: buffer.toString("utf8", lineEnd + 2, end), bytes: end + 2 };
  }
  // Redis error text can contain submitted values; never include it in logs.
  throw new Error("Redis tunnel rejected a captcha command");
}

async function tunnelRedisGet(tunnel: RedisTunnel, password: string, key: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const socket = createConnection(tunnel);
    let buffer: Buffer = Buffer.alloc(0);
    let replies = 0;
    let settled = false;
    const finish = (error?: Error, value = ""): void => {
      if (settled) {
        return;
      }
      settled = true;
      socket.destroy();
      if (error) {
        reject(error);
      } else {
        resolve(value);
      }
    };
    socket.setTimeout(5_000, () => finish(new Error("Redis tunnel captcha read timed out")));
    socket.on("error", () => finish(new Error("Redis tunnel connection failed")));
    socket.on("close", () => finish(new Error("Redis tunnel closed before the captcha reply")));
    socket.on("connect", () => {
      socket.write(Buffer.concat([
        redisCommand("AUTH", password),
        redisCommand("SELECT", "2"),
        redisCommand("GET", key),
      ]));
    });
    socket.on("data", (chunk: Buffer) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (buffer.length > 16_384) {
        finish(new Error("Redis tunnel captcha reply exceeded its limit"));
        return;
      }
      try {
        for (let reply = redisReply(buffer); reply; reply = redisReply(buffer)) {
          buffer = buffer.subarray(reply.bytes);
          replies += 1;
          if (replies < 3 && reply.value !== "OK") {
            throw new Error("Redis tunnel rejected captcha authentication or database selection");
          }
          if (replies === 3) {
            finish(undefined, reply.value);
            return;
          }
        }
      } catch {
        finish(new Error("Redis tunnel returned an invalid captcha reply"));
      }
    });
  });
}

function captchaReader(key: string): () => Promise<string> {
  if (!/^captcha:(admin|portal):[A-Za-z0-9_-]+$/.test(key)) {
    throw new Error("E2E Redis reads are limited to admin or portal captcha keys");
  }
  const env = loadDotEnv();
  const password = env.REDIS_PASSWORD;
  if (!password) {
    throw new Error("REDIS_PASSWORD missing");
  }
  const tunnel = redisTunnel();
  if (tunnel) {
    return () => tunnelRedisGet(tunnel, password, key);
  }
  const root = repoRoot();
  return async () => {
    const result = spawnSync(
      "docker",
      [
        "compose",
        "--env-file",
        envFilePath(),
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
      throw new Error(`Redis compose captcha read failed for ${key}`);
    }
    return (result.stdout || "").trim();
  };
}

export async function redisGet(key: string): Promise<string> {
  return captchaReader(key)();
}

export async function waitRedisGet(key: string, tries = 20): Promise<string> {
  const read = captchaReader(key);
  for (let i = 0; i < tries; i += 1) {
    try {
      const value = await read();
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
