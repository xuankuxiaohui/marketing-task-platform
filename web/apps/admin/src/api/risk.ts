import type { components } from "@mkt/shared/openapi/admin";
import type { Result } from "@mkt/shared";
import type { PageParams } from "./identity";
import { request } from "./http";

export type RiskListItemResponse = components["schemas"]["RiskListItemResponse"];
export type RiskListItemCreateCommand = components["schemas"]["RiskListItemCreateCommand"];
export type RiskListItemImportCommand = components["schemas"]["RiskListItemImportCommand"];
export type RiskListItemRemoveCommand = components["schemas"]["RiskListItemRemoveCommand"];
export type RiskListImportResponse = components["schemas"]["RiskListImportResponse"];
export type PageDataRiskListItemResponse = components["schemas"]["PageDataRiskListItemResponse"];
export type RiskHitLogResponse = components["schemas"]["RiskHitLogResponse"];
export type PageDataRiskHitLogResponse = components["schemas"]["PageDataRiskHitLogResponse"];
export type RiskCaseHandleCommand = components["schemas"]["RiskCaseHandleCommand"];
export type IdResponse = components["schemas"]["IdResponse"];
export type OkResponse = components["schemas"]["OkResponse"];

export function pageListItems(
  params: PageParams & {
    dimension?: "USER" | "IP" | "DEVICE";
    listType?: "BLACK" | "WHITE";
    value?: string;
    from?: string;
    to?: string;
  },
): Promise<Result<PageDataRiskListItemResponse>> {
  return request("GET", "/admin/risk/list-items", undefined, params);
}

export function addListItem(body: RiskListItemCreateCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/risk/list-items", body);
}

export function importListItems(body: RiskListItemImportCommand): Promise<Result<RiskListImportResponse>> {
  return request("POST", "/admin/risk/list-items/import", body);
}

export function removeListItem(id: number, body: RiskListItemRemoveCommand): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/risk/list-items/${id}`, body);
}

export function pageHits(
  params: PageParams & {
    ruleCode?: string;
    hitType?: string;
    dimensionValue?: string;
    userId?: number;
    actionResult?: string;
    from?: string;
    to?: string;
  },
): Promise<Result<PageDataRiskHitLogResponse>> {
  return request("GET", "/admin/risk/hits", undefined, params);
}

export function handleCase(body: RiskCaseHandleCommand): Promise<Result<OkResponse>> {
  return request("POST", "/admin/risk/cases/handle", body);
}
