import type { RewardPreviewView } from "@/api/task";

export function formatRewardPreview(preview?: RewardPreviewView | null): string {
  const name = preview?.firstName?.trim() ?? "";
  if (!name) {
    return "";
  }
  const total = Number(preview?.totalCount ?? 0);
  if (total > 1) {
    return `${name} 等 ${total} 项`;
  }
  return name;
}
