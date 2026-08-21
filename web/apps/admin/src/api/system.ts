import type { components } from "@mkt/shared/openapi/admin";
import type { Result } from "@mkt/shared";
import { request } from "./http";
import type { PageParams } from "./identity";

export type DictTypeView = components["schemas"]["DictTypeView"];
export type DictTypeCreateCommand = components["schemas"]["DictTypeCreateCommand"];
export type DictTypeUpdateCommand = components["schemas"]["DictTypeUpdateCommand"];
export type DictEntryOption = components["schemas"]["DictEntryOption"];
export type DictEntryCreateCommand = components["schemas"]["DictEntryCreateCommand"];
export type PageDataDictTypeView = components["schemas"]["PageDataDictTypeView"];
export type ConfigView = components["schemas"]["ConfigView"];
export type ConfigCreateCommand = components["schemas"]["ConfigCreateCommand"];
export type PageDataConfigView = components["schemas"]["PageDataConfigView"];
export type CacheStatsView = components["schemas"]["CacheStatsView"];
export type CacheEvictResponse = components["schemas"]["CacheEvictResponse"];
export type PageDataCacheStatsView = components["schemas"]["PageDataCacheStatsView"];
export type AuditView = components["schemas"]["AuditView"];
export type PageDataAuditView = components["schemas"]["PageDataAuditView"];
export type IdResponse = components["schemas"]["IdResponse"];
export type OkResponse = components["schemas"]["OkResponse"];

export function pageDictTypes(params: PageParams): Promise<Result<PageDataDictTypeView>> {
  return request("GET", "/admin/system/dict-types", undefined, params);
}

export function createDictType(body: DictTypeCreateCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/system/dict-types", body);
}

export function updateDictType(id: number, body: DictTypeUpdateCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/system/dict-types/${id}`, body);
}

export function deleteDictType(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/system/dict-types/${id}`);
}

export function listDictEntries(typeCode: string): Promise<Result<DictEntryOption[]>> {
  return request("GET", `/admin/system/dict-types/${encodeURIComponent(typeCode)}/entries`);
}

export function createDictEntry(body: DictEntryCreateCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/system/dict-entries", body);
}

export function pageConfigs(
  params: PageParams & { configGroup?: string; key?: string },
): Promise<Result<PageDataConfigView>> {
  return request("GET", "/admin/system/configs", undefined, params);
}

export function createConfig(body: ConfigCreateCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/system/configs", body);
}

export function updateConfig(key: string, body: Record<string, unknown>): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/system/configs/${encodeURIComponent(key)}`, body);
}

export function fetchCacheStats(): Promise<Result<PageDataCacheStatsView>> {
  return request("GET", "/admin/system/cache/stats");
}

export function evictCache(body: {
  level: string;
  namespace?: string;
  prefix?: string;
  key?: string;
}): Promise<Result<CacheEvictResponse>> {
  return request("POST", "/admin/system/cache/evict", body);
}

export function pageAudits(
  params: PageParams & {
    operatorId?: number;
    module?: string;
    action?: string;
    result?: string;
    from?: string;
    to?: string;
  },
): Promise<Result<PageDataAuditView>> {
  return request("GET", "/admin/system/audits", undefined, params);
}
