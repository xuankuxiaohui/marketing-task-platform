import { describe, expect, it } from "vitest";
import { formatRewardPreview } from "./reward-preview";

describe("formatRewardPreview", () => {
  it("shows the first reward name when there is only one", () => {
    expect(formatRewardPreview({ firstName: "积分礼包", totalCount: 1 })).toBe("积分礼包");
  });

  it("appends 等 N 项 when the snapshot has multiple rewards", () => {
    expect(formatRewardPreview({ firstName: "积分礼包", totalCount: 3 })).toBe("积分礼包 等 3 项");
  });

  it("returns empty when the preview name is missing", () => {
    expect(formatRewardPreview(undefined)).toBe("");
    expect(formatRewardPreview({ firstName: "  ", totalCount: 2 })).toBe("");
  });
});
