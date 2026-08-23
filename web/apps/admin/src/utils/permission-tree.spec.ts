import { describe, expect, it } from "vitest";
import { flattenPermissionTree, toPermissionTreeData } from "./permission-tree";

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

describe("toPermissionTreeData", () => {
  it("maps nested menus to antd tree keys", () => {
    const data = toPermissionTreeData([
      {
        id: 1,
        name: "系统",
        type: "MENU",
        children: [{ id: 2, name: "用户", type: "MENU", code: "identity:admin-user:query", children: [] }],
      },
    ]);
    expect(data).toEqual([
      {
        key: 1,
        title: "系统",
        children: [{ key: 2, title: "用户" }],
      },
    ]);
  });
});
