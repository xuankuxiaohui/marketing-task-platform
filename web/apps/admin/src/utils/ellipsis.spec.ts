import { describe, expect, it } from "vitest";
import { clipCellText } from "./ellipsis";

describe("clipCellText", () => {
  it("keeps short values whole", () => {
    expect(clipCellText("ok", 8)).toEqual({ display: "ok", full: "ok", clipped: false });
  });

  it("hides overflow and keeps the full string", () => {
    const full = "abcdefghijklmnopqrstuvwxyz";
    const clipped = clipCellText(full, 8);
    expect(clipped.clipped).toBe(true);
    expect(clipped.display).toBe("abcdefgh…");
    expect(clipped.full).toBe(full);
    expect(clipped.display.length).toBeLessThan(full.length);
  });
});
