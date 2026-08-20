import { describe, expect, it } from "vitest";
import { flattenPermissionTree } from "./permission-tree";

describe("flattenPermissionTree", () => {
  it("keeps stable ids and depth", () => {
    const flat = flattenPermissionTree([
      {
        id: 1,
        name: "系统",
        type: "MENU",
        children: [{ id: 2, name: "用户", type: "MENU", code: "identity:admin-user:query", children: [] }],
      },
    ]);
    expect(flat.map((item) => ({ id: item.id, depth: item.depth }))).toEqual([
      { id: 1, depth: 0 },
      { id: 2, depth: 1 },
    ]);
  });
});
