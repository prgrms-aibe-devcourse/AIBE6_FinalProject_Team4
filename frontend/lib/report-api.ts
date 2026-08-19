import { request, SpringPage } from '@/lib/api';

export type ReportTargetType = 'JOURNAL' | 'USER' | 'POST' | 'COMMENT';
export type ReportStatus = 'PENDING' | 'COMPLETED' | 'REJECTED';

export interface ReportData {
  id: number;
  reporterId: number;
  reporterName: string;
  targetType: ReportTargetType;
  targetId: number;
  reason: string;
  status: ReportStatus;
  createdAt: string;
  actionType: string | null;
  actionDetail: string | null;
  processedAdminId: number | null;
  processedAdminName: string | null;
  processedAt: string | null;
}

export interface ReportCreatePayload {
  targetType: ReportTargetType;
  targetId: number;
  reason: string;
}

export interface ReportActionPayload {
  actionType: string;
  actionDetail: string;
}

export function createReport(
  payload: ReportCreatePayload,
  accessToken: string,
): Promise<ReportData> {
  return request<ReportData>('/api/v1/reports', {
    method: 'POST',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function getMyReports(
  accessToken: string,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<SpringPage<ReportData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request<SpringPage<ReportData>>(`/api/v1/reports?${query.toString()}`, {
    accessToken,
    signal,
  });
}

// ---- Admin ----

export function getReportsForAdmin(
  accessToken: string,
  status: ReportStatus | undefined,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<SpringPage<ReportData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) query.set('status', status);
  return request<SpringPage<ReportData>>(`/api/v1/admin/reports?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function completeReport(
  reportId: number,
  payload: ReportActionPayload,
  accessToken: string,
): Promise<ReportData> {
  return request<ReportData>(`/api/v1/admin/reports/${reportId}/complete`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function rejectReport(
  reportId: number,
  payload: ReportActionPayload,
  accessToken: string,
): Promise<ReportData> {
  return request<ReportData>(`/api/v1/admin/reports/${reportId}/reject`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify(payload),
  });
}
