export type PlantCareEvidenceStatus = "VERIFIED" | "GENERAL_FALLBACK";
export type PlantCareEvidenceScope = "EXACT_SPECIES" | "BASE_SPECIES" | "NONE";

export interface PlantCareEvidenceSource {
  sourceId: string;
  sourceName: string;
  sourceUrl: string;
  version: string;
  contentHash: string;
}

export interface PlantCareGrounding {
  status: PlantCareEvidenceStatus;
  scope: PlantCareEvidenceScope;
  resolvedSpeciesName: string | null;
  sources: PlantCareEvidenceSource[];
}
