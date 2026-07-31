import { request } from '@/lib/api';

export type PlantStatus = 'GROWING' | 'HARVESTED' | 'FAILED';

export interface PlantProfileData {
  id: number;
  userId: number;
  speciesId: number;
  speciesName: string;
  nickname: string;
  startDate: string;
  thumbnailUrl: string | null;
  status: PlantStatus;
  createdAt: string;
}

export interface PlantProfileRequest {
  speciesId: number;
  nickname: string;
  startDate: string;
  thumbnailUrl?: string;
}

export interface PlantProfileUpdateRequest {
  nickname?: string;
  thumbnailUrl?: string;
  status?: PlantStatus;
}

export function createPlant(
  payload: PlantProfileRequest,
  accessToken: string,
): Promise<PlantProfileData> {
  return request<PlantProfileData>('/api/v1/plants', {
    method: 'POST',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function getMyPlants(
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantProfileData[]> {
  return request<PlantProfileData[]>('/api/v1/plants', {
    accessToken,
    signal,
  });
}

export function getPlant(
  plantId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantProfileData> {
  return request<PlantProfileData>(`/api/v1/plants/${plantId}`, {
    accessToken,
    signal,
  });
}

export function updatePlant(
  plantId: number,
  payload: PlantProfileUpdateRequest,
  accessToken: string,
): Promise<PlantProfileData> {
  return request<PlantProfileData>(`/api/v1/plants/${plantId}`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function deletePlant(plantId: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/plants/${plantId}`, {
    method: 'DELETE',
    accessToken,
  });
}

export interface PlantImageUploadData {
  imageUrl: string;
  imageHash: string;
}

export function uploadPlantImage(file: File, accessToken: string): Promise<PlantImageUploadData> {
  const formData = new FormData();
  formData.append('file', file);
  return request<PlantImageUploadData>('/api/v1/plants/images', {
    method: 'POST',
    accessToken,
    body: formData,
  });
}
