import { zhCN } from "@/locales/zh-CN";

export const TASK_USER_STATUSES = [
  "NOT_STARTED",
  "IN_PROGRESS",
  "COMPLETED",
  "ABANDONED",
  "EXPIRED",
] as const;

export type TaskUserStatus = (typeof TASK_USER_STATUSES)[number];

export type TaskButtonKind = "claim" | "continue" | "terminal";

export type TaskButtonState = {
  kind: TaskButtonKind;
  label: string;
  disabled: boolean;
};

export function isTaskUserStatus(value: string | undefined): value is TaskUserStatus {
  return value != null && (TASK_USER_STATUSES as readonly string[]).includes(value);
}

export function taskButtonState(status: string | undefined): TaskButtonState {
  switch (status) {
    case "NOT_STARTED":
      return { kind: "claim", label: zhCN.task.claim, disabled: false };
    case "IN_PROGRESS":
      return { kind: "continue", label: zhCN.task.continue, disabled: false };
    case "COMPLETED":
      return { kind: "terminal", label: zhCN.task.completed, disabled: true };
    case "ABANDONED":
      return { kind: "terminal", label: zhCN.task.abandoned, disabled: true };
    case "EXPIRED":
      return { kind: "terminal", label: zhCN.task.expired, disabled: true };
    default:
      return { kind: "terminal", label: zhCN.task.ended, disabled: true };
  }
}

export function terminalStatusLabel(status: string | undefined): string {
  return taskButtonState(status).label;
}
