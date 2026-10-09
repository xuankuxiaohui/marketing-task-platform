import { describe, expect, it } from "vitest";
import { activityAttributionLabel, ownersForTask, ownersLabel } from "./activity-ownership";

describe("activity-ownership", () => {
  it("maps TASK submodules by ascending activity id", () => {
    const owners = ownersForTask(8, [
      { id: 20, name: "夏日", submodules: [{ type: "TASK", refId: 8 }] },
      { id: 7, name: "春日", submodules: [{ type: "TASK", refId: 8 }] },
      { id: 9, name: "其它", submodules: [{ type: "SIGNIN", refId: 8 }] },
    ]);
    expect(owners.map((row) => row.activityId)).toEqual([7, 20]);
    expect(ownersLabel(owners)).toContain("7、20");
    expect(ownersLabel(owners)).toContain("春日、夏日");
    expect(ownersForTask(99, [{ id: 7, name: "春日", submodules: [{ type: "TASK", refId: 8 }] }])).toEqual([]);
  });

  it("formats a single activity id and name", () => {
    expect(activityAttributionLabel({ activityId: 3, activityName: "夏季专题" })).toBe("3 · 夏季专题");
    expect(activityAttributionLabel({})).toBe("");
  });
});
