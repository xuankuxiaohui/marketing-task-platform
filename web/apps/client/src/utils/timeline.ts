export type TimelineTone = "done" | "current" | "idle";

export function timelineTone(
  status: string | undefined,
  stepCode: string | undefined,
  currentStepCode: string | undefined,
): TimelineTone {
  if (stepCode && currentStepCode && stepCode === currentStepCode) {
    return "current";
  }
  if (status === "COMPLETED" || status === "SKIPPED") {
    return "done";
  }
  return "idle";
}

export function progressFraction(current?: number, target?: number): number {
  if (target == null || target <= 0) {
    return 0;
  }
  const value = Number(current ?? 0);
  if (value <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((value / target) * 100));
}

export function progressLabel(current?: number, target?: number): string | undefined {
  if (target == null) {
    return undefined;
  }
  return `${Number(current ?? 0)}/${target}`;
}
