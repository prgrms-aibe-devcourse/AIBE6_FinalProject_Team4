import { request, SpringPage } from '@/lib/api';

export interface JournalImageData {
  imageUrl: string;
  imageHash: string;
  representative: boolean;
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
  if (params.profileId) query.set('profileId', String(params.profileId));
  if (params.year) query.set('year', String(params.year));
  if (params.month) query.set('month', String(params.month));

  return request<SpringPage<PlantJournalData>>(`/api/v1/journals?${query.toString()}`, {
    accessToken,
    signal,
  });
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

export function deleteJournal(journalId: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/journals/${journalId}`, {
    method: 'DELETE',
    accessToken,
  });
}

export interface JournalImageUploadData {
  imageUrl: string;
  imageHash: string;
}

export function uploadJournalImage(file: File, accessToken: string): Promise<JournalImageUploadData> {
  const formData = new FormData();
  formData.append('file', file);
  return request<JournalImageUploadData>('/api/v1/journals/images', {
    method: 'POST',
    accessToken,
    body: formData,
  });
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

export function createJournal(payload: CreateJournalPayload, accessToken: string): Promise<PlantJournalData> {
  return request<PlantJournalData>('/api/v1/journals', {
    method: 'POST',
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
    method: 'PATCH',
    accessToken,
    body: JSON.stringify(payload),
  });
}
