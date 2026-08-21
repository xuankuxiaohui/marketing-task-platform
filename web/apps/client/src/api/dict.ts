import type { components, paths } from "@mkt/shared/openapi/portal";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type DictResult = Star<paths["/api/common/dict/{typeCode}"]["get"]["responses"][200]>;
export type DictEntries = NonNullable<Envelope<DictResult>>;
export type DictPortalEntry = components["schemas"]["DictPortalEntry"];

export const TASK_CATEGORY_DICT = "task_category";

export function fetchDict(typeCode: string): Promise<Result<DictEntries>> {
  return request<DictEntries>("GET", `/api/common/dict/${typeCode}`);
}

export function dictLabel(entries: DictPortalEntry[], value?: string): string {
  if (!value) {
    return "";
  }
  const hit = entries.find((entry) => entry.value === value);
  const label = hit?.label?.trim();
  return label || value;
}
