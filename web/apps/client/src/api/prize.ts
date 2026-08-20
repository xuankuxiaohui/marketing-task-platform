import type { components, paths } from "@mkt/shared/openapi/portal";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type PrizeListResult = Star<paths["/api/common/prize/list"]["get"]["responses"][200]>;
export type PrizeListPage = NonNullable<Envelope<PrizeListResult>>;
export type PrizeCardView = components["schemas"]["PrizeCardView"];
export type PrizeClaimResult = Star<paths["/api/common/prize/records/{id}/claim"]["post"]["responses"][200]>;
export type PrizeClaimView = NonNullable<Envelope<PrizeClaimResult>>;

export const PRIZE_TABS = ["PENDING", "ALL"] as const;
export type PrizeTab = (typeof PRIZE_TABS)[number];

export type PrizePageQuery = {
  tab?: PrizeTab;
  page?: number;
  pageSize?: number;
};

export function fetchPrizeList(query: PrizePageQuery = {}): Promise<Result<PrizeListPage>> {
  return request<PrizeListPage>("GET", "/api/common/prize/list", undefined, {
    tab: query.tab ?? "PENDING",
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  });
}

export function claimPrize(recordId: number): Promise<Result<PrizeClaimView>> {
  return request<PrizeClaimView>("POST", `/api/common/prize/records/${recordId}/claim`);
}
