import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, describe, expect, it } from "vitest";

const pkgRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const specDir = join(pkgRoot, "openapi");
const committedDir = join(pkgRoot, "src/openapi");
const GROUPS = ["admin", "portal", "internal"] as const;

const temps: string[] = [];

function generate(jsonPath: string, outPath: string): void {
  execFileSync("pnpm", ["exec", "openapi-typescript", jsonPath, "-o", outPath], {
    cwd: pkgRoot,
    stdio: "pipe",
  });
}

describe("openapi-typescript contract gate", () => {
  afterEach(() => {
    for (const dir of temps) {
      rmSync(dir, { recursive: true, force: true });
    }
    temps.length = 0;
  });

  it("green: committed springdoc JSON regenerates to committed types", () => {
    const dir = mkdtempSync(join(tmpdir(), "openapi-green-"));
    temps.push(dir);
    for (const name of GROUPS) {
      const out = join(dir, `${name}.ts`);
      generate(join(specDir, `${name}.json`), out);
      expect(readFileSync(out, "utf8"), name).toBe(readFileSync(join(committedDir, `${name}.ts`), "utf8"));
    }
  }, 20_000);

  it("red: backend contract change without regenerating frontend types is a diff", () => {
    const dir = mkdtempSync(join(tmpdir(), "openapi-red-"));
    temps.push(dir);
    const json = JSON.parse(readFileSync(join(specDir, "portal.json"), "utf8")) as {
      paths: Record<string, unknown>;
    };
    json.paths["/api/common/__contract-probe"] = {
      get: {
        operationId: "contractProbe",
        responses: { "200": { description: "probe" } },
      },
    };
    const mutated = join(dir, "portal.json");
    writeFileSync(mutated, `${JSON.stringify(json)}\n`);
    const out = join(dir, "portal.ts");
    generate(mutated, out);
    const generated = readFileSync(out, "utf8");
    const committed = readFileSync(join(committedDir, "portal.ts"), "utf8");
    expect(generated).toContain("/api/common/__contract-probe");
    expect(committed).not.toContain("/api/common/__contract-probe");
    expect(generated).not.toBe(committed);
  }, 20_000);
});
