import { describe, expect, it } from "vitest";
import { dictLabel } from "./dict";

describe("dictLabel", () => {
  it("falls back to the raw value when the label is missing", () => {
    expect(dictLabel([{ value: "daily", label: "日常" }], "daily")).toBe("日常");
    expect(dictLabel([{ value: "daily" }], "daily")).toBe("daily");
    expect(dictLabel([], "daily")).toBe("daily");
    expect(dictLabel([], undefined)).toBe("");
  });
});
