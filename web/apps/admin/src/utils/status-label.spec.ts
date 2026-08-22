import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusClass, adminStatusLabel } from "./status-label";

describe("adminStatusLabel", () => {
  it("maps closed enums to Chinese and leaves unknowns", () => {
    expect(adminStatusLabel("ENABLED")).toBe(zhCN.status.ENABLED);
    expect(adminStatusLabel("PUBLISHED")).toBe(zhCN.status.PUBLISHED);
    expect(adminStatusLabel("DRAFT")).toBe(zhCN.status.DRAFT);
    expect(adminStatusLabel("NOT_A_REAL_STATUS")).toBe("NOT_A_REAL_STATUS");
    expect(adminStatusLabel("")).toBe("");
  });

  it("picks tag tone classes for live / wait / off", () => {
    expect(adminStatusClass("PUBLISHED")).toBe("status-tag--on");
    expect(adminStatusClass("SCHEDULED")).toBe("status-tag--wait");
    expect(adminStatusClass("DISABLED")).toBe("status-tag--off");
    expect(adminStatusClass("EXPIRED")).toBe("status-tag--bad");
  });
});
