import type { Result } from "@mkt/shared";
import { request } from "./http";

export type PortalAdMaterialView = {
  materialId: number;
  trackId: string;
  title: string;
  subtitle?: string | null;
  imageUrl: string;
  jumpType: string;
  jumpParams?: Record<string, unknown>;
  weight: number;
};

export type PortalAdPositionView = {
  code: string;
  form: string;
  materials: PortalAdMaterialView[];
  splashDurationSeconds?: number | null;
  carouselIntervalSeconds?: number | null;
};

export function fetchAdPosition(code: string): Promise<Result<PortalAdPositionView>> {
  return request<PortalAdPositionView>("GET", `/api/common/ad/positions/${encodeURIComponent(code)}`);
}

export function dismissAdMaterial(materialId: number, positionCode: string): Promise<Result<{ ok: boolean }>> {
  return request<{ ok: boolean }>("POST", `/api/common/ad/materials/${materialId}/dismiss`, { positionCode });
}
