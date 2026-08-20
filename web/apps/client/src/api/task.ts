import type { paths } from "@mkt/shared/openapi/portal";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type MineTaskResult = Star<paths["/api/common/task/mine"]["get"]["responses"][200]>;
export type MineTaskPage = NonNullable<Envelope<MineTaskResult>>;

export function fetchMineTasks(status = "IN_PROGRESS"): Promise<Result<MineTaskPage>> {
  return request<MineTaskPage>("GET", "/api/common/task/mine", undefined, { status, page: 1, pageSize: 1 });
}
