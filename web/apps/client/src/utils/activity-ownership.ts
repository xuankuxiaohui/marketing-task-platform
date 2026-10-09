export type ActivityOwner = {
  activityId: number;
  activityName: string;
};

export type ActivityModule = {
  id?: number;
  name?: string;
  submodules?: Array<{ type?: string; refId?: number }>;
};

export function ownersForTask(taskId: number | undefined, activities: ActivityModule[]): ActivityOwner[] {
  if (taskId == null || !Number.isFinite(taskId)) {
    return [];
  }
  const owners: ActivityOwner[] = [];
  for (const activity of activities) {
    if (activity.id == null || !activity.submodules?.some((sub) => sub.type === "TASK" && sub.refId === taskId)) {
      continue;
    }
    owners.push({ activityId: activity.id, activityName: activity.name?.trim() || "" });
  }
  owners.sort((a, b) => a.activityId - b.activityId);
  return owners;
}

export function activityAttributionLabel(row: {
  activityId?: number | null;
  activityName?: string | null;
}): string {
  const id = row.activityId != null && Number.isFinite(row.activityId) ? String(row.activityId) : "";
  const name = row.activityName?.trim() ?? "";
  return [id, name].filter(Boolean).join(" · ");
}

export function ownersLabel(owners: ActivityOwner[]): string {
  if (owners.length === 0) {
    return "";
  }
  const ids = owners.map((row) => String(row.activityId)).join("、");
  const names = owners
    .map((row) => row.activityName)
    .filter(Boolean)
    .join("、");
  return [ids, names].filter(Boolean).join(" · ");
}
