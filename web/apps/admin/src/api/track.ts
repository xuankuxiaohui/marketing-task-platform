import type { components } from "@mkt/shared/openapi/admin";
import type { Result } from "@mkt/shared";
import type { PageParams } from "./identity";
import { request } from "./http";

export type TrackMetadataResponse = components["schemas"]["TrackMetadataResponse"];
export type TrackMetadataSaveCommand = components["schemas"]["TrackMetadataSaveCommand"];
export type PageDataTrackMetadataResponse = components["schemas"]["PageDataTrackMetadataResponse"];
export type TrackDebugEventResponse = components["schemas"]["TrackDebugEventResponse"];
export type PageDataTrackDebugEventResponse = components["schemas"]["PageDataTrackDebugEventResponse"];
export type IdResponse = components["schemas"]["IdResponse"];
export type OkResponse = components["schemas"]["OkResponse"];

export function pageMetadata(
  params: PageParams & { eventCode?: string; status?: string },
): Promise<Result<PageDataTrackMetadataResponse>> {
  return request("GET", "/admin/track/metadata", undefined, params);
}

export function createMetadata(body: TrackMetadataSaveCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/track/metadata", body);
}

export function updateMetadata(id: number, body: TrackMetadataSaveCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/track/metadata/${id}`, body);
}

export function deleteMetadata(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/track/metadata/${id}`);
}

export function debugEvents(
  params: PageParams & {
    eventCode?: string;
    userId?: number;
    source?: string;
    deviceId?: string;
    from?: string;
    to?: string;
  },
): Promise<Result<PageDataTrackDebugEventResponse>> {
  return request("GET", "/admin/track/events/debug", undefined, params);
}
