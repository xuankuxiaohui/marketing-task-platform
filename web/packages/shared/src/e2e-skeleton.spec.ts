import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const e2eDir = join(dirname(fileURLToPath(import.meta.url)), "../../../e2e");

describe("Playwright journeys (task 43 / design §7.9)", () => {
  it("journey-core implements design §7.9 portal steps", () => {
    const src = readFileSync(join(e2eDir, "journey-core.spec.ts"), "utf8");
    expect(src).not.toMatch(/test\.skip\(/);
    for (const needle of [
      "R32.1",
      "R32.4",
      "R34.5",
      "task.card.exposure",
      "/api/common/track/batch",
      "R34.4",
      "R35.4",
      "R33.5",
    ]) {
      expect(src).toContain(needle);
    }
  });

  it("journey-admin implements login / aggregate / expression / publish / instance query", () => {
    const src = readFileSync(join(e2eDir, "journey-admin.spec.ts"), "utf8");
    expect(src).not.toMatch(/test\.skip\(/);
    for (const needle of ["login", "aggregate", "expression", "publish", "R14.9"]) {
      expect(src).toContain(needle);
    }
  });
});
