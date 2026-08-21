import type { components } from "@mkt/shared/openapi/admin";
import type { Result } from "@mkt/shared";
import type { PageParams } from "./identity";
import { request } from "./http";

export type PrizeCategoryResponse = components["schemas"]["PrizeCategoryResponse"];
export type PrizeCategorySaveCommand = components["schemas"]["PrizeCategorySaveCommand"];
export type PageDataPrizeCategoryResponse = components["schemas"]["PageDataPrizeCategoryResponse"];
export type PrizeResponse = components["schemas"]["PrizeResponse"];
export type PrizeSaveCommand = components["schemas"]["PrizeSaveCommand"];
export type PageDataPrizeResponse = components["schemas"]["PageDataPrizeResponse"];
export type PrizeConfirmCommand = components["schemas"]["PrizeConfirmCommand"];
export type PrizeImpactResponse = components["schemas"]["PrizeImpactResponse"];
export type StockLogView = components["schemas"]["StockLogView"];
export type PageDataStockLogView = components["schemas"]["PageDataStockLogView"];
export type StockReplenishCommand = components["schemas"]["StockReplenishCommand"];
export type StockReplenishResponse = components["schemas"]["StockReplenishResponse"];
export type ManualGrantCommand = components["schemas"]["ManualGrantCommand"];
export type ManualGrantResponse = components["schemas"]["ManualGrantResponse"];
export type GrantRetryResponse = components["schemas"]["GrantRetryResponse"];
export type FulfillmentCallbackResponse = components["schemas"]["FulfillmentCallbackResponse"];
export type SpendResponse = components["schemas"]["SpendResponse"];
export type SpendRowView = components["schemas"]["SpendRowView"];
export type ReconBatchResponse = components["schemas"]["ReconBatchResponse"];
export type ReconBatchCreateCommand = components["schemas"]["ReconBatchCreateCommand"];
export type PageDataReconBatchResponse = components["schemas"]["PageDataReconBatchResponse"];
export type ReconImportCommand = components["schemas"]["ReconImportCommand"];
export type ReconMatchResponse = components["schemas"]["ReconMatchResponse"];
export type ReconItemView = components["schemas"]["ReconItemView"];
export type PageDataReconItemView = components["schemas"]["PageDataReconItemView"];
export type ReconReviewCommand = components["schemas"]["ReconReviewCommand"];
export type ReconReviewResponse = components["schemas"]["ReconReviewResponse"];
export type ReconActionCommand = components["schemas"]["ReconActionCommand"];
export type ReconActionResponse = components["schemas"]["ReconActionResponse"];
export type PointsAccountView = components["schemas"]["PointsAccountView"];
export type PageDataPointsAccountView = components["schemas"]["PageDataPointsAccountView"];
export type PointsAdjustCommand = components["schemas"]["PointsAdjustCommand"];
export type PointsBalanceResponse = components["schemas"]["PointsBalanceResponse"];
export type PointsTransactionView = components["schemas"]["PointsTransactionView"];
export type PageDataPointsTransactionView = components["schemas"]["PageDataPointsTransactionView"];
export type IdResponse = components["schemas"]["IdResponse"];
export type OkResponse = components["schemas"]["OkResponse"];

export function pageCategories(params: PageParams): Promise<Result<PageDataPrizeCategoryResponse>> {
  return request("GET", "/admin/reward/prize-categories", undefined, params);
}

export function createCategory(body: PrizeCategorySaveCommand): Promise<Result<PrizeCategoryResponse>> {
  return request("POST", "/admin/reward/prize-categories", body);
}

export function updateCategory(code: string, body: PrizeCategorySaveCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/reward/prize-categories/${encodeURIComponent(code)}`, body);
}

export function deleteCategory(code: string): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/reward/prize-categories/${encodeURIComponent(code)}`);
}

export function disableCategory(code: string): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/reward/prize-categories/${encodeURIComponent(code)}/disable`);
}

export function enableCategory(code: string): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/reward/prize-categories/${encodeURIComponent(code)}/enable`);
}

export function pagePrizes(
  params: PageParams & { code?: string; name?: string; categoryCode?: string; status?: string; groupId?: number },
): Promise<Result<PageDataPrizeResponse>> {
  return request("GET", "/admin/reward/prizes", undefined, params);
}

export function getPrize(id: number): Promise<Result<PrizeResponse>> {
  return request("GET", `/admin/reward/prizes/${id}`);
}

export function createPrize(body: PrizeSaveCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/reward/prizes", body);
}

export function updatePrize(id: number, body: PrizeSaveCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/reward/prizes/${id}`, body);
}

export function deletePrize(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/reward/prizes/${id}`);
}

export function disablePrize(id: number, body: PrizeConfirmCommand): Promise<Result<PrizeImpactResponse>> {
  return request("POST", `/admin/reward/prizes/${id}/disable`, body);
}

export function enablePrize(id: number, body: PrizeConfirmCommand): Promise<Result<PrizeImpactResponse>> {
  return request("POST", `/admin/reward/prizes/${id}/enable`, body);
}

export function pageStockLogs(id: number, params: PageParams): Promise<Result<PageDataStockLogView>> {
  return request("GET", `/admin/reward/prizes/${id}/stock-logs`, undefined, params);
}

export function replenishStock(id: number, body: StockReplenishCommand): Promise<Result<StockReplenishResponse>> {
  return request("POST", `/admin/reward/prizes/${id}/stock-replenish`, body);
}

export function retryGrant(id: number): Promise<Result<GrantRetryResponse>> {
  return request("POST", `/admin/reward/records/${id}/retry`);
}

export function fulfillConfirm(id: number): Promise<Result<FulfillmentCallbackResponse>> {
  return request("POST", `/admin/reward/records/${id}/fulfill-confirm`);
}

export function fulfillRetry(id: number): Promise<Result<FulfillmentCallbackResponse>> {
  return request("POST", `/admin/reward/records/${id}/fulfill-retry`);
}

export function manualGrant(body: ManualGrantCommand): Promise<Result<ManualGrantResponse>> {
  return request("POST", "/admin/reward/records/manual-grant", body);
}

export function fetchSpend(params: { categoryCode?: string; prizeId?: number; from?: string; to?: string }): Promise<Result<SpendResponse>> {
  return request("GET", "/admin/reward/spend", undefined, params);
}

export function pageReconBatches(
  params: PageParams & { categoryCode?: string; billDate?: string; status?: string },
): Promise<Result<PageDataReconBatchResponse>> {
  return request("GET", "/admin/reward/recon/batches", undefined, params);
}

export function createReconBatch(body: ReconBatchCreateCommand): Promise<Result<ReconBatchResponse>> {
  return request("POST", "/admin/reward/recon/batches", body);
}

export function importReconLines(id: number, body: ReconImportCommand): Promise<Result<OkResponse>> {
  return request("POST", `/admin/reward/recon/batches/${id}/import`, body);
}

export function matchReconBatch(id: number): Promise<Result<ReconMatchResponse>> {
  return request("POST", `/admin/reward/recon/batches/${id}/match`);
}

export function pageReconItems(
  id: number,
  params: PageParams & { result?: string; reviewStatus?: string },
): Promise<Result<PageDataReconItemView>> {
  return request("GET", `/admin/reward/recon/batches/${id}/items`, undefined, params);
}

export function reviewReconItem(id: number, body: ReconReviewCommand): Promise<Result<ReconReviewResponse>> {
  return request("POST", `/admin/reward/recon/items/${id}/review`, body);
}

export function actionReconItem(id: number, body: ReconActionCommand): Promise<Result<ReconActionResponse>> {
  return request("POST", `/admin/reward/recon/items/${id}/action`, body);
}

export function pagePointsAccounts(params: PageParams & { userId?: number }): Promise<Result<PageDataPointsAccountView>> {
  return request("GET", "/admin/points/accounts", undefined, params);
}

export function adjustPoints(body: PointsAdjustCommand): Promise<Result<PointsBalanceResponse>> {
  return request("POST", "/admin/points/accounts/adjust", body);
}

export function pagePointsTransactions(
  params: PageParams & { userId?: number; type?: string; from?: string; to?: string },
): Promise<Result<PageDataPointsTransactionView>> {
  return request("GET", "/admin/points/transactions", undefined, params);
}
