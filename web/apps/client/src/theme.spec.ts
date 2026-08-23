import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { PORTAL_PRIMARY } from "./theme";

describe("portal theme", () => {
  it("does not use the old teal primary", () => {
    const css = readFileSync(join(process.cwd(), "src/styles.css"), "utf8");
    expect(PORTAL_PRIMARY.toLowerCase()).not.toBe("#0f766e");
    expect(PORTAL_PRIMARY.toLowerCase()).not.toBe("#e11d48");
    expect(css).not.toMatch(/--portal-primary:\s*#0f766e/i);
    expect(css).not.toMatch(/--portal-primary:\s*#e11d48/i);
    expect(css).toContain(`--portal-primary: ${PORTAL_PRIMARY}`);
  });
});
