import { request } from "@/lib/api";

export type AdminGachaDrawStatus =
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "RETRYABLE_FAILED"
  | "MANUAL_REVIEW"
  | "REFUNDED";

export interface AdminGachaDraw {
  drawId: number;
  userId: number;
  userNickname: string;
  sourceType: "LOG_REWARD" | "PURCHASE" | "ADMIN" | "EVENT";
  sourceId: number;
  status: AdminGachaDrawStatus;
  drawCount: number;
  attemptCount: number;
  lastErrorCode: string | null;
  nextRetryAt: string | null;
  resultViewedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminGachaDrawPage {
  content: AdminGachaDraw[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function getAdminGachaDraws(
  accessToken: string,
  params: {
    status?: AdminGachaDrawStatus;
    userId?: number;
    page?: number;
    size?: number;
    signal?: AbortSignal;
  } = {},
): Promise<AdminGachaDrawPage> {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  if (params.status) query.set("status", params.status);
  if (params.userId) query.set("userId", String(params.userId));
  return request<AdminGachaDrawPage>(
    `/api/v1/admin/card/gacha/draws?${query.toString()}`,
    { accessToken, signal: params.signal },
  );
}

export function retryAdminGachaDraw(
  drawId: number,
  accessToken: string,
): Promise<{ drawId: number; status: AdminGachaDrawStatus }> {
  return request(`/api/v1/admin/card/gacha/draws/${drawId}/retry`, {
    method: "PATCH",
    accessToken,
  });
}
