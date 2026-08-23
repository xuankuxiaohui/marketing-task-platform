import { describe, expect, it } from "vitest";
import { remainingRoleIdsAfterRevoke, toCheckedPermissionIds } from "./role-assign";

describe("remainingRoleIdsAfterRevoke", () => {
  const catalog = [
    { id: 1, code: "super-admin" },
    { id: 2, code: "ops" },
    { id: 3, code: "auditor" },
  ];

  it("drops only the revoked role and keeps the rest", () => {
    expect(remainingRoleIdsAfterRevoke(catalog, ["super-admin", "ops", "auditor"], 2)).toEqual([1, 3]);
  });

  it("returns empty when the user only had the revoked role", () => {
    expect(remainingRoleIdsAfterRevoke(catalog, ["ops"], 2)).toEqual([]);
  });
});

describe("toCheckedPermissionIds", () => {
  it("dedupes finite ids", () => {
    expect(toCheckedPermissionIds([10, 11, 10])).toEqual([10, 11]);
  });
});
