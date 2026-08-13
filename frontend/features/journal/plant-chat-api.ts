import { request } from "@/lib/api";

export interface PlantChatRequestPayload {
  question: string;
  conversationId: string | null;
}

export interface PlantChatResponseData {
  conversationId: string;
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
