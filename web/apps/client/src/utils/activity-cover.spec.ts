import { describe, expect, it } from "vitest";
import { activityCover, activityWindow } from "./activity-cover";

describe("activityCover", () => {
  it("prefers coverUrl then imageUrl then bannerUrl", () => {
    expect(activityCover({ coverUrl: " https://cdn.example/c.png " })).toBe("https://cdn.example/c.png");
    expect(activityCover({ imageUrl: "https://cdn.example/i.png" })).toBe("https://cdn.example/i.png");
    expect(activityCover({ bannerUrl: "https://cdn.example/b.png" })).toBe("https://cdn.example/b.png");
    expect(activityCover({ coverUrl: "  ", imageUrl: "" })).toBeUndefined();
  });
});

describe("activityWindow", () => {
  it("joins start and end when both exist", () => {
    expect(activityWindow("2026-08-01T00:00:00+08:00", "2026-08-31T23:59:00+08:00")).toContain("2026");
    expect(activityWindow(undefined, undefined)).toBe("");
  });
});
