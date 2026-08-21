import { describe, expect, it } from "vitest";
import { progressFraction, progressLabel, timelineTone } from "./timeline";

describe("timelineTone", () => {
  it("marks completed and skipped as done, current as current, inactive as idle", () => {
    expect(timelineTone("COMPLETED", "a", "b")).toBe("done");
    expect(timelineTone("SKIPPED", "a", "b")).toBe("done");
    expect(timelineTone("ACTIVE", "b", "b")).toBe("current");
    expect(timelineTone("INACTIVE", "c", "b")).toBe("idle");
  });
});

describe("progressLabel", () => {
  it("renders x/N and percent for progress steps", () => {
    expect(progressLabel(2, 5)).toBe("2/5");
    expect(progressLabel(undefined, 5)).toBe("0/5");
    expect(progressLabel(1, undefined)).toBeUndefined();
    expect(progressFraction(2, 5)).toBe(40);
    expect(progressFraction(5, 5)).toBe(100);
    expect(progressFraction(0, 5)).toBe(0);
  });
});
