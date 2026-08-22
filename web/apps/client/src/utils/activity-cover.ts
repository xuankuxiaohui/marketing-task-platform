import { formatBeijing } from "./datetime";

export function activityCover(activity: {
  coverUrl?: string;
  imageUrl?: string;
  bannerUrl?: string;
}): string | undefined {
  for (const value of [activity.coverUrl, activity.imageUrl, activity.bannerUrl]) {
    if (typeof value === "string" && value.trim()) {
      return value.trim();
    }
  }
  return undefined;
}

export function activityWindow(startTime?: string, endTime?: string): string {
  const start = formatBeijing(startTime);
  const end = formatBeijing(endTime);
  if (start && end) {
    return `${start} – ${end}`;
  }
  return start || end;
}
