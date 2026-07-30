import { request, SpringPage } from '@/lib/api';

export type InquiryCategory = 'PAYMENT' | 'DELIVERY' | 'ACCOUNT' | 'ETC';
export type InquiryStatus = 'OPEN' | 'ANSWERED';

export interface InquiryData {
  id: number;
  userId: number;
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
