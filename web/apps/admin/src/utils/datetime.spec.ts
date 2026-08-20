import { describe, expect, it } from "vitest";
import { formatDateTime } from "./datetime";

describe("formatDateTime", () => {
  it("renders ISO-8601 in Asia/Shanghai", () => {
    expect(formatDateTime("2026-08-20T00:00:00Z")).toBe("2026-08-20 08:00:00");
  });

  it("uses em dash for empty", () => {
    expect(formatDateTime(undefined)).toBe("—");
    expect(formatDateTime("")).toBe("—");
  });
});
