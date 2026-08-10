import { request } from "@/lib/api";

export type PlantChatRole = "USER" | "ASSISTANT";

export interface PlantChatMessagePayload {
  role: PlantChatRole;
  content: string;
}

export interface PlantChatRequestPayload {
  question: string;
  currentJournalContent: string | null;
  recentMessages: PlantChatMessagePayload[];
}

export interface PlantChatResponseData {
  answer: string;
  recommendedActions: string[];
  additionalChecks: string[];
}

export function askPlantChat(
  profileId: number,
  payload: PlantChatRequestPayload,
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantChatResponseData> {
  return request<PlantChatResponseData>(
    `/api/v1/ai/plant-profiles/${profileId}/chat`,
    {
      method: "POST",
      accessToken,
      signal,
      body: JSON.stringify(payload),
    },
  );
}
