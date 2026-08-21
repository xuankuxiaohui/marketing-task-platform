import type { Result } from "@mkt/shared";
import { request } from "./http";

export type MetricsGrain = "DAY" | "WEEK" | "MONTH";

export type MetricsQuery = {
  from?: string;
  to?: string;
  grain?: MetricsGrain;
  dimKey?: string;
};

export type FunnelPointView = {
  period: string;
  dimKey: string;
  exposureCount: number;
  startCount: number;
  completeCount: number;
  startRate?: number | null;
  completeRate?: number | null;
};

export type SpendPointView = {
  period: string;
  dimKey: string;
  arrivedCount: number;
  arrivedCostFen: number;
  sendingCount: number;
  sendingCostFen: number;
  remainingStock: number;
  totalStock: number;
};

export type RiskPointView = {
  period: string;
  dimKey: string;
  hitCount: number;
  interceptCount: number;
  interceptRate?: number | null;
};

export type AdPointView = {
  period: string;
  dimKey: string;
  exposureCount: number;
  clickCount: number;
  ctr?: number | null;
};

export function fetchFunnel(query: MetricsQuery): Promise<Result<{ records: FunnelPointView[] }>> {
  return request("GET", "/admin/metrics/funnel", undefined, query);
}

export function fetchSpendMetrics(query: MetricsQuery): Promise<Result<{ records: SpendPointView[] }>> {
  return request("GET", "/admin/metrics/spend", undefined, query);
}

export function fetchRiskMetrics(query: MetricsQuery): Promise<Result<{ records: RiskPointView[] }>> {
  return request("GET", "/admin/metrics/risk", undefined, query);
}

export function fetchAdMetrics(query: MetricsQuery): Promise<Result<{ records: AdPointView[] }>> {
  return request("GET", "/admin/metrics/ad", undefined, query);
}
