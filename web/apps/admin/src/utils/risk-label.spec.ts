import { describe, expect, it } from "vitest";
import {
  riskActionLabel,
  riskActionResultLabel,
  riskDimensionLabel,
  riskHandleActionLabel,
  riskHitTypeLabel,
  riskListTypeLabel,
} from "./risk-label";

describe("risk labels (Top2 U2)", () => {
  it("maps rule actions", () => {
    expect(riskActionLabel("REJECT")).toBe("拦截");
    expect(riskActionLabel("SILENT_REJECT")).toBe("静默拦截");
    expect(riskActionLabel("MARK")).toBe("标记观察");
    expect(riskActionLabel("PASS")).toBe("放行");
  });

  it("maps case results", () => {
    expect(riskActionResultLabel("REJECTED")).toBe("已拦截");
    expect(riskActionResultLabel("SILENT_REJECTED")).toBe("已静默拦截");
    expect(riskActionResultLabel("MARKED")).toBe("已标记");
    expect(riskActionResultLabel("FROZEN")).toBe("已冻结");
  });

  it("maps list type, hit type, handle action and dimension", () => {
    expect(riskListTypeLabel("BLACK")).toBe("黑名单");
    expect(riskListTypeLabel("WHITE")).toBe("白名单");
    expect(riskHitTypeLabel("RULE")).toBe("规则命中");
    expect(riskHitTypeLabel("LIST")).toBe("名单命中");
    expect(riskHandleActionLabel("ADD_BLACK")).toBe("加入黑名单");
    expect(riskHandleActionLabel("REMOVE_BLACK")).toBe("移出黑名单");
    expect(riskHandleActionLabel("MARK_FALSE_POSITIVE")).toBe("标记误判");
    expect(riskDimensionLabel("USER")).toBe("用户");
    expect(riskDimensionLabel("IP")).toBe("IP");
    expect(riskDimensionLabel("DEVICE")).toBe("设备");
  });

  it("shows unknown codes as-is (never blank) and does not translate rule codes", () => {
    expect(riskActionLabel("NEW_ACTION")).toBe("NEW_ACTION");
    expect(riskActionResultLabel("PASSED")).toBe("PASSED");
    expect(riskDimensionLabel("PHONE")).toBe("PHONE");
    expect(riskActionLabel("R-a")).toBe("R-a");
    expect(riskActionLabel("")).toBe("");
    expect(riskActionLabel(undefined)).toBe("");
  });
});
