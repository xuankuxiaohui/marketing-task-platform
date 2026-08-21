import type { PublishResponse } from "@/api/task";

/** R12.7 / PublishResponse.REVISION_HINT */
export const PUBLISH_REVISION_HINT = "新实例将使用新版本，存量实例不受影响";

export function isPublishPreview(data: PublishResponse | null | undefined): boolean {
  return Boolean(data?.requiresConfirm);
}

export function formatPublishImpact(data: PublishResponse): string {
  const hint = data.message && data.message.length > 0 ? data.message : PUBLISH_REVISION_HINT;
  const inFlight = data.inFlightInstanceCount ?? 0;
  return `${hint}（在途实例 ${inFlight}）`;
}
