import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { formatRemain, remainLabel, remainingMs } from "./countdown";

describe("prize countdown", () => {
  it("formats remaining time for pending prizes (R35.1)", () => {
    expect(formatRemain(90_000)).toBe(`${zhCN.prize.remain} 00:01:30`);
    expect(formatRemain(0)).toBe(`${zhCN.prize.remain} 00:00:00`);
    expect(formatRemain(90_000_000)).toContain(zhCN.prize.day);
  });

  it("computes remain from expireAt against a frozen clock", () => {
    const now = Date.parse("2026-08-20T00:00:00.000Z");
    const expireAt = "2026-08-20T00:01:05.000Z";
    expect(remainingMs(expireAt, now)).toBe(65_000);
    expect(remainLabel(expireAt, now)).toBe(`${zhCN.prize.remain} 00:01:05`);
  });
});
