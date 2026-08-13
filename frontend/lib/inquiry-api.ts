import { request, SpringPage } from '@/lib/api';

export type InquiryCategory = 'PAYMENT' | 'DELIVERY' | 'ACCOUNT' | 'ETC';
export type InquiryStatus = 'OPEN' | 'ANSWERED';

export interface InquiryData {
  id: number;
  userId: number;
  userName: string;
  category: InquiryCategory;
  title: string;
  content: string;
  status: InquiryStatus;
  createdAt: string;
  answerContent: string | null;
  answerAdminId: number | null;
  answerAdminName: string | null;
  answeredAt: string | null;
}

export interface InquiryCreatePayload {
  category: InquiryCategory;
  title: string;
  content: string;
}

export function getMyInquiries(
  accessToken: string,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<SpringPage<InquiryData>> {
  return request<SpringPage<InquiryData>>(`/api/v1/inquiries?page=${page}&size=${size}`, {
    accessToken,
    signal,
  });
}

export function getInquiry(
  inquiryId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<InquiryData> {
  return request<InquiryData>(`/api/v1/inquiries/${inquiryId}`, {
    accessToken,
    signal,
  });
}

export function createInquiry(
  payload: InquiryCreatePayload,
  accessToken: string,
): Promise<InquiryData> {
  return request<InquiryData>('/api/v1/inquiries', {
    method: 'POST',
    accessToken,
    body: JSON.stringify(payload),
  });
}

// ---- Admin ----

export function getInquiriesForAdmin(
  accessToken: string,
  status: InquiryStatus | undefined,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<SpringPage<InquiryData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) query.set('status', status);
  return request<SpringPage<InquiryData>>(`/api/v1/admin/inquiries?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function answerInquiry(
  inquiryId: number,
  answerContent: string,
  accessToken: string,
): Promise<InquiryData> {
  return request<InquiryData>(`/api/v1/admin/inquiries/${inquiryId}/answer`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ answerContent }),
  });
}
