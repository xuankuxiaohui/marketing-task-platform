import { zhCN } from "@/locales/zh-CN";

/**
 * Risk closed-enum display labels (Top2 U2), same pattern as status-label.ts:
 * mapped → Chinese; unknown → original code as-is (never blank).
 * Rule codes (R-a..R-f) are identifiers and are intentionally NOT translated.
 */
export type RiskLabelKind = keyof typeof zhCN.risk;

export function riskLabel(kind: RiskLabelKind, code: string | undefined | null): string {
  if (code == null || code === "") {
    return "";
  }
  const mapped = (zhCN.risk[kind] as Record<string, string>)[code];
  return mapped ?? code;
}

export const riskActionLabel = (code: string | undefined | null): string => riskLabel("action", code);
export const riskActionResultLabel = (code: string | undefined | null): string => riskLabel("actionResult", code);
export const riskListTypeLabel = (code: string | undefined | null): string => riskLabel("listType", code);
export const riskHitTypeLabel = (code: string | undefined | null): string => riskLabel("hitType", code);
export const riskHandleActionLabel = (code: string | undefined | null): string => riskLabel("handleAction", code);
export const riskDimensionLabel = (code: string | undefined | null): string => riskLabel("dimension", code);
