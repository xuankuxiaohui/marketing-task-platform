import { describe, expect, it } from "vitest";
import { formatDateTime, toIsoInstant } from "./datetime";

describe("formatDateTime", () => {
  it("renders ISO-8601 in Asia/Shanghai", () => {
    expect(formatDateTime("2026-08-20T00:00:00Z")).toBe("2026-08-20 08:00:00");
  });

  it("uses em dash for empty", () => {
    expect(formatDateTime(undefined)).toBe("—");
    expect(formatDateTime("")).toBe("—");
  });
});

describe("toIsoInstant", () => {
  it("returns undefined for empty", () => {
    expect(toIsoInstant(undefined)).toBeUndefined();
    expect(toIsoInstant("")).toBeUndefined();
  });

  it("converts datetime-local to ISO-8601", () => {
    const iso = toIsoInstant("2026-08-20T08:00");
    expect(iso).toBe(new Date("2026-08-20T08:00").toISOString());
  });
});
