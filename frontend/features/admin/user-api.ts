import { request, SpringPage } from '@/lib/api';

export type AdminUserStatus = 'ACTIVE' | 'SUSPENDED' | 'RESTRICTED' | 'WITHDRAWN';
export type AdminUserRole = 'ADMIN' | 'USER';

export interface AdminUserSummary {
  id: number;
  email: string;
  nickname: string;
  name: string;
  role: AdminUserRole;
  status: AdminUserStatus;
  suspendedReason: string | null;
  withdrawnAt: string | null;
  createdAt: string;
  reportCount: number;
}

interface AdminUserSearchParams {
  accessToken: string;
  keyword?: string;
  status?: AdminUserStatus;
  page: number;
  size?: number;
  signal?: AbortSignal;
}

export function getAdminUsers({
  accessToken,
  keyword,
  status,
  page,
  size = 10,
  signal,
}: AdminUserSearchParams): Promise<SpringPage<AdminUserSummary>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (keyword) query.set('keyword', keyword);
  if (status) query.set('status', status);

  return request<SpringPage<AdminUserSummary>>(`/api/v1/admin/user?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function suspendAdminUser(
  userId: number,
  reason: string,
  accessToken: string,
): Promise<void> {
  return request<void>(`/api/v1/admin/user/${userId}/suspend`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ reason }),
  });
}

export function reactivateAdminUser(userId: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/admin/user/${userId}/reactivate`, {
    method: 'PATCH',
    accessToken,
  });
}
