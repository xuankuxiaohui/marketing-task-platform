import type { PrizeImpactResponse } from "@/api/reward";

export function isPrizeImpactPreview(data: PrizeImpactResponse | null | undefined): boolean {
  return data != null && data.confirmed === false;
}

export function formatPrizeImpact(data: PrizeImpactResponse, action: "disable" | "enable"): string {
  const affected = data.affectedTaskCount ?? 0;
  const inFlight = data.inFlightInstanceCount ?? 0;
  if (action === "disable") {
    return `停用后新发放将被永久失败（PRIZE_DISABLED）。受影响任务 ${affected} 个，在途实例 ${inFlight} 个。`;
  }
  return `重新启用需再次确认影响面。受影响任务 ${affected} 个，在途实例 ${inFlight} 个。`;
}
