import { request, SpringPage } from "@/lib/api";

export interface JournalImageData {
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
  content: string;
  writtenDate: string;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  images: JournalImageData[];
  gachaReward: GachaRewardData;
}

export interface GetJournalsParams {
  profileId?: number;
  year?: number;
  month?: number;
  page?: number;
  size?: number;
}

export function getJournals(
  params: GetJournalsParams,
  accessToken: string,
  signal?: AbortSignal,
): Promise<SpringPage<PlantJournalData>> {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  if (params.profileId) query.set("profileId", String(params.profileId));
  if (params.year) query.set("year", String(params.year));
  if (params.month) query.set("month", String(params.month));

  return request<SpringPage<PlantJournalData>>(
    `/api/v1/journals?${query.toString()}`,
    {
      accessToken,
      signal,
    },
  );
}

export function getJournal(
  journalId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantJournalData> {
  return request<PlantJournalData>(`/api/v1/journals/${journalId}`, {
    accessToken,
    signal,
  });
}

export function deleteJournal(
  journalId: number,
  accessToken: string,
): Promise<void> {
  return request<void>(`/api/v1/journals/${journalId}`, {
    method: "DELETE",
    accessToken,
  });
}

export interface JournalImageUploadData {
  imageUrl: string;
  imageHash: string;
}

export function uploadJournalImage(
  file: File,
  accessToken: string,
): Promise<JournalImageUploadData> {
  const formData = new FormData();
  formData.append("file", file);
  return request<JournalImageUploadData>("/api/v1/journals/images", {
    method: "POST",
    accessToken,
    body: formData,
  });
}

// 업로드는 성공했지만 뒤이은 작성/수정이 실패해 일지에 연결되지 못한 이미지를 정리한다.
// best-effort 정리이므로 실패해도 호출부의 에러 처리를 방해하지 않도록 별도로 호출한다.
export function deleteJournalImage(
  imageUrl: string,
  accessToken: string,
): Promise<void> {
  return request<void>(
    `/api/v1/journals/images?imageUrl=${encodeURIComponent(imageUrl)}`,
    {
      method: "DELETE",
      accessToken,
    },
  );
}

export interface JournalImagePayload {
  imageUrl: string;
  imageHash: string;
  representative: boolean;
}

export interface CreateJournalPayload {
  plantProfileId: number;
  content: string;
  images: JournalImagePayload[];
}

export interface PlantJournalCreateData {
  journal: PlantJournalData;
  rewardGranted: boolean;
  rewardAmount: number;
}

export function createJournal(
  payload: CreateJournalPayload,
  accessToken: string,
): Promise<PlantJournalCreateData> {
  return request<PlantJournalCreateData>("/api/v1/journals", {
    method: "POST",
    accessToken,
    body: JSON.stringify(payload),
  });
}

export interface UpdateJournalPayload {
  content: string;
  images: JournalImagePayload[];
}

export function updateJournal(
  journalId: number,
  payload: UpdateJournalPayload,
  accessToken: string,
): Promise<PlantJournalData> {
  return request<PlantJournalData>(`/api/v1/journals/${journalId}`, {
    method: "PATCH",
    accessToken,
    body: JSON.stringify(payload),
  });
}
