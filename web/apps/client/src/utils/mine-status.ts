import { zhCN } from "@/locales/zh-CN";

export const MINE_TASK_STATUSES = ["IN_PROGRESS", "COMPLETED", "ABANDONED", "EXPIRED"] as const;

export type MineTaskStatus = (typeof MINE_TASK_STATUSES)[number];

const TITLE_TO_STATUS: Record<string, MineTaskStatus> = {
  [zhCN.task.inProgress]: "IN_PROGRESS",
  [zhCN.task.completed]: "COMPLETED",
  [zhCN.task.abandoned]: "ABANDONED",
  [zhCN.task.expired]: "EXPIRED",
};

const ALIAS_TO_STATUS: Record<string, MineTaskStatus> = {
  SUCCESS: "COMPLETED",
  DONE: "COMPLETED",
  FINISHED: "COMPLETED",
  COMPLETE: "COMPLETED",
};

export function resolveMineStatus(value: string | number | undefined | null): MineTaskStatus | undefined {
  if (value == null || value === "") {
    return undefined;
  }
  if (typeof value === "number" || (typeof value === "string" && /^\d+$/.test(value))) {
    return MINE_TASK_STATUSES[Number(value)];
  }
  const raw = String(value);
  const upper = raw.toUpperCase();
  if ((MINE_TASK_STATUSES as readonly string[]).includes(upper)) {
    return upper as MineTaskStatus;
  }
  return ALIAS_TO_STATUS[upper] ?? TITLE_TO_STATUS[raw];
}
