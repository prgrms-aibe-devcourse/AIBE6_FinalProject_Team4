import { request } from '@/lib/api';

export type ReportTargetType = 'JOURNAL' | 'USER';

export interface ReportData {
  id: number;
  reporterId: number;
  reporterName: string;
  targetType: ReportTargetType;
  targetId: number;
  reason: string;
  status: 'PENDING' | 'COMPLETED' | 'REJECTED';
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
