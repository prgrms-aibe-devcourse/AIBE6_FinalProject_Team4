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
