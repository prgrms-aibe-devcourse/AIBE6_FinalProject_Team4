import { request } from "@/lib/api";

export type JournalImageQuality = "CLEAR" | "LIMITED" | "UNUSABLE";
export type JournalPlantCondition =
  "HEALTHY" | "NEEDS_ATTENTION" | "URGENT_CHECK" | "UNKNOWN";

export interface JournalImageAnalysisData {
  id: number;
  journalId: number;
  imageHash: string;
  imageQuality: JournalImageQuality;
  condition: JournalPlantCondition;
  summary: string;
  observations: string[];
  possibleCauses: string[];
  recommendedActions: string[];
  additionalChecks: string[];
  analyzedAt: string;
}

export function getJournalImageAnalyses(
  journalId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<JournalImageAnalysisData[]> {
  return request<JournalImageAnalysisData[]>(
    `/api/v1/ai/journals/${journalId}/image-analysis`,
    { accessToken, signal },
  );
}

export function analyzeJournalImage(
  journalId: number,
  imageHash: string,
  accessToken: string,
  signal: AbortSignal,
): Promise<JournalImageAnalysisData> {
  return request<JournalImageAnalysisData>(
    `/api/v1/ai/journals/${journalId}/image-analysis`,
    {
      method: "POST",
      accessToken,
      signal,
      body: JSON.stringify({ imageHash }),
    },
  );
}
