export type RoleCatalogItem = {
  id?: number;
  code?: string;
};

/** Remaining role ids after removing `revokeRoleId`; other bound roles stay. */
export function remainingRoleIdsAfterRevoke(
  catalog: readonly RoleCatalogItem[],
  userRoleCodes: readonly string[] | undefined,
  revokeRoleId: number,
): number[] {
  const codes = new Set(userRoleCodes ?? []);
  const ids: number[] = [];
  for (const role of catalog) {
    if (role.id == null || Number(role.id) === revokeRoleId) {
      continue;
    }
    if (codes.has(role.code ?? "")) {
      ids.push(Number(role.id));
    }
  }
  return ids;
}

export function toCheckedPermissionIds(ids: readonly number[] | undefined): number[] {
  return [...new Set((ids ?? []).filter((id) => Number.isFinite(id)).map(Number))];
}
