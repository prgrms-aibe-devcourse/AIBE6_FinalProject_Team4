import { request, SpringPage } from '@/lib/api';

export interface JournalImageData {
  imageUrl: string;
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
