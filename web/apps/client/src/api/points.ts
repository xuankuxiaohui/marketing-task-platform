import type { components, paths } from "@mkt/shared/openapi/portal";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type PointsBalanceResult = Star<paths["/api/common/points/balance"]["get"]["responses"][200]>;
export type PointsBalanceView = NonNullable<Envelope<PointsBalanceResult>>;
export type PointsTxResult = Star<paths["/api/common/points/transactions"]["get"]["responses"][200]>;
export type PointsTxPage = NonNullable<Envelope<PointsTxResult>>;
export type PointsPortalTxView = components["schemas"]["PointsPortalTxView"];

export type PointsTxQuery = {
  type?: string;
  page?: number;
  pageSize?: number;
};

export function fetchPointsBalance(): Promise<Result<PointsBalanceView>> {
  return request<PointsBalanceView>("GET", "/api/common/points/balance");
}

export function fetchPointsTransactions(query: PointsTxQuery = {}): Promise<Result<PointsTxPage>> {
  return request<PointsTxPage>("GET", "/api/common/points/transactions", undefined, {
    type: query.type,
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  });
}
