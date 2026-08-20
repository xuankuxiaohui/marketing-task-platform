import { describe, expect, it } from "vitest";
import { formatPublishImpact, isPublishPreview, PUBLISH_REVISION_HINT } from "./publish-confirm";

describe("publish two-stage confirm R12.7", () => {
  it("treats requiresConfirm as preview", () => {
    expect(isPublishPreview({ requiresConfirm: true, message: PUBLISH_REVISION_HINT, inFlightInstanceCount: 2 })).toBe(true);
    expect(isPublishPreview({ requiresConfirm: false, id: 1, code: "t", version: 1, status: "PUBLISHED" })).toBe(false);
  });

  it("formats impact with in-flight count", () => {
    expect(formatPublishImpact({ requiresConfirm: true, message: PUBLISH_REVISION_HINT, inFlightInstanceCount: 4 })).toBe(
      `${PUBLISH_REVISION_HINT}（在途实例 4）`,
    );
  });
});
