import { request } from "@/lib/api";

export interface PlantProfileData {
  id: number;
  userId: number;
  speciesName: string;
  nickname: string;
  startDate: string;
  thumbnailUrl: string | null;
  status: "GROWING" | "HARVESTED" | "FAILED";
  createdAt: string;
}

export interface JournalImageInput {
  imageUrl: string;
  imageHash: string;
  representative: boolean;
}

export interface GachaRewardData {
  granted: boolean;
  drawId: number | null;
  status:
    | "PENDING"
    | "PROCESSING"
    | "COMPLETED"
    | "RETRYABLE_FAILED"
    | "MANUAL_REVIEW"
    | null;
}

export interface PlantJournalData {
  id: number;
  plantProfileId: number;
  plantProfileNickname: string;
  userId: number;
  content: string | null;
  writtenDate: string;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  images: {
    id: number;
    imageUrl: string;
    representative: boolean;
  }[];
  gachaReward: GachaRewardData;
}

export function getMyPlantProfiles(
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantProfileData[]> {
  return request<PlantProfileData[]>("/api/v1/plants", {
    accessToken,
    signal,
  });
}

export function createPlantJournal(
  accessToken: string,
  input: {
    plantProfileId: number;
    content: string;
    images: JournalImageInput[];
  },
): Promise<PlantJournalData> {
  return request<PlantJournalData>("/api/v1/journals", {
    method: "POST",
    accessToken,
    body: JSON.stringify(input),
  });
}
