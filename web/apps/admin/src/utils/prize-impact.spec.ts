import { describe, expect, it } from "vitest";
import { formatPrizeImpact, isPrizeImpactPreview } from "./prize-impact";

describe("prize disable impact R17.7", () => {
  it("treats confirmed=false as preview", () => {
    expect(isPrizeImpactPreview({ confirmed: false, affectedTaskCount: 2, inFlightInstanceCount: 5 })).toBe(true);
    expect(isPrizeImpactPreview({ confirmed: true, affectedTaskCount: 2, inFlightInstanceCount: 5 })).toBe(false);
  });

  it("shows affected tasks and in-flight instances", () => {
    expect(formatPrizeImpact({ confirmed: false, affectedTaskCount: 2, inFlightInstanceCount: 5 }, "disable")).toContain(
      "受影响任务 2 个，在途实例 5 个",
    );
  });
});
