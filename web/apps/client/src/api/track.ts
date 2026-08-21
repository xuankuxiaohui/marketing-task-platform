import type { components, paths } from "@mkt/shared/openapi/portal";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Json<T> = T extends { content: { "application/json": infer B } } ? B : never;
type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type TrackBatchBody = Json<paths["/api/common/track/batch"]["post"]["requestBody"]>;
export type TrackBatchResult = Star<paths["/api/common/track/batch"]["post"]["responses"][200]>;
export type TrackBatchView = NonNullable<Envelope<TrackBatchResult>>;
export type TrackEventCommand = components["schemas"]["TrackEventCommand"];

export const TRACK_BATCH_PATH = "/api/common/track/batch";

export function postTrackBatch(body: TrackBatchBody): Promise<Result<TrackBatchView>> {
  return request<TrackBatchView>("POST", TRACK_BATCH_PATH, body);
}
