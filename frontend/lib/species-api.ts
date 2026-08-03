import { request } from '@/lib/api';

export interface PlantSpeciesData {
  id: number;
  name: string;
  category: string | null;
  careGuide: string | null;
  createdAt: string;
  updatedAt: string;
}

export function getSpecies(
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantSpeciesData[]> {
  return request<PlantSpeciesData[]>('/api/v1/plants/species', {
    accessToken,
    signal,
  });
}

export interface PlantSpeciesRequest {
  name: string;
  category?: string;
  careGuide?: string;
}

export function createSpecies(
  payload: PlantSpeciesRequest,
  accessToken: string,
): Promise<PlantSpeciesData> {
  return request<PlantSpeciesData>('/api/v1/admin/plants/species', {
    method: 'POST',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function updateSpecies(
  speciesId: number,
  payload: PlantSpeciesRequest,
  accessToken: string,
): Promise<PlantSpeciesData> {
  return request<PlantSpeciesData>(`/api/v1/admin/plants/species/${speciesId}`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify(payload),
  });
}
