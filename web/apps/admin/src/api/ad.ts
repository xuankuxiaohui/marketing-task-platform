import type { Result } from "@mkt/shared";
import type { PageParams } from "./identity";
import { request } from "./http";

export type AdMaterialView = {
  id: number;
  title: string;
  subtitle?: string | null;
  imageUrl: string;
  jumpType: string;
  jumpParams?: Record<string, unknown>;
  weight: number;
  startTime: string;
  endTime: string;
  status: string;
};

export type AdPlacementView = {
  id: number;
  positionId: number;
  materialId: number;
  weight: number;
  startTime: string;
  endTime: string;
  platforms: string[];
  grayType: string;
  grayRatio?: number | null;
  crowdId?: number | null;
  status: string;
  material?: AdMaterialView | null;
};

export type AdPositionView = {
  id: number;
  code: string;
  name: string;
  form: string;
  platforms: string[];
  status: string;
  placements: AdPlacementView[];
  overlapCount: number;
};

export type AdSaveResponse = { id: number };

export function pagePositions(
  params: PageParams & { code?: string; form?: string; status?: string },
): Promise<Result<{ total: number; records: AdPositionView[] }>> {
  return request("GET", "/admin/ad/positions", undefined, params);
}

export function getPosition(id: number): Promise<Result<AdPositionView>> {
  return request("GET", `/admin/ad/positions/${id}`);
}

export function savePosition(body: {
  id?: number;
  code: string;
  name: string;
  form: string;
  platforms?: string[];
  status?: string;
}): Promise<Result<AdSaveResponse>> {
  return request("POST", "/admin/ad/positions", body);
}

export function deletePosition(id: number): Promise<Result<{ ok: boolean }>> {
  return request("DELETE", `/admin/ad/positions/${id}`);
}

export function bindPlacement(
  positionId: number,
  body: {
    materialId: number;
    weight: number;
    startTime: string;
    endTime: string;
    platforms?: string[];
    grayType?: string;
    grayRatio?: number | null;
    crowdId?: number | null;
    status?: string;
  },
): Promise<Result<AdSaveResponse>> {
  return request("POST", `/admin/ad/positions/${positionId}/materials`, body);
}

export function unbindPlacement(positionId: number, materialId: number): Promise<Result<{ ok: boolean }>> {
  return request("DELETE", `/admin/ad/positions/${positionId}/materials/${materialId}`);
}

export function pageMaterials(
  params: PageParams & { title?: string; status?: string },
): Promise<Result<{ total: number; records: AdMaterialView[] }>> {
  return request("GET", "/admin/ad/materials", undefined, params);
}

export function saveMaterial(body: {
  id?: number;
  title: string;
  subtitle?: string;
  imageUrl: string;
  jumpType?: string;
  jumpParams?: Record<string, unknown>;
  weight: number;
  startTime: string;
  endTime: string;
  status?: string;
}): Promise<Result<AdSaveResponse>> {
  return request("POST", "/admin/ad/materials", body);
}

export function deleteMaterial(id: number): Promise<Result<{ ok: boolean }>> {
  return request("DELETE", `/admin/ad/materials/${id}`);
}
