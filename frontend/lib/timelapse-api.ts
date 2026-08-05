import { request } from '@/lib/api';

export type TimelapseStatus = 'NONE' | 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface PlantTimelapseData {
  status: TimelapseStatus;
  videoUrl: string | null;
  failReason: string | null;
  requestedAt: string | null;
  completedAt: string | null;
}

export function getTimelapse(
  plantId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantTimelapseData> {
  return request<PlantTimelapseData>(`/api/v1/plants/${plantId}/timelapse`, {
    accessToken,
    signal,
  });
}

export function requestTimelapse(
  plantId: number,
  accessToken: string,
): Promise<PlantTimelapseData> {
  return request<PlantTimelapseData>(`/api/v1/plants/${plantId}/timelapse`, {
    method: 'POST',
    accessToken,
  });
}
