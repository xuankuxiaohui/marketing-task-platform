import { zhCN } from "@/locales/zh-CN";

export function remainingMs(expireAt: string | undefined, nowMs = Date.now()): number | undefined {
  if (!expireAt) {
    return undefined;
  }
  const date = new Date(expireAt);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }
  return date.getTime() - nowMs;
}

export function formatRemain(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (value: number) => String(value).padStart(2, "0");
  if (days > 0) {
    return `${zhCN.prize.remain} ${days}${zhCN.prize.day}${pad(hours)}${zhCN.prize.hour}${pad(minutes)}${zhCN.prize.minute}`;
  }
  return `${zhCN.prize.remain} ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

export function remainLabel(expireAt: string | undefined, nowMs = Date.now()): string {
  const remain = remainingMs(expireAt, nowMs);
  if (remain == null) {
    return "";
  }
  return formatRemain(remain);
}
