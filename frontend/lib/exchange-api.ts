import { request, SpringPage } from '@/lib/api';

export type ExchangeStatus = 'REQUESTED' | 'PREPARING' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED';
export type CancelledBy = 'USER' | 'ADMIN';

export interface ExchangeOrderData {
  id: number;
  userId: number;
  cardId: number;
  cardName: string;
  exchangeProductId: number;
  exchangeProductName: string;
  usedCardCount: number;
  status: ExchangeStatus;
  cancelledBy: CancelledBy | null;
  cancelReason: string | null;
  cancelledAt: string | null;
  deliveredAt: string | null;
  receiverName: string;
  receiverPhone: string;
  zipCode: string | null;
  address: string;
  addressDetail: string | null;
  requestedAt: string;
}

export interface ExchangeRequestPayload {
  cardId: number;
  receiverName: string;
  receiverPhone: string;
  zipCode: string;
  address: string;
  addressDetail?: string;
}

export function requestExchange(
  payload: ExchangeRequestPayload,
  accessToken: string,
): Promise<ExchangeOrderData> {
  return request<ExchangeOrderData>('/api/v1/exchanges', {
    method: 'POST',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function getMyExchanges(
  accessToken: string,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<SpringPage<ExchangeOrderData>> {
  return request<SpringPage<ExchangeOrderData>>(`/api/v1/exchanges?page=${page}&size=${size}`, {
    accessToken,
    signal,
  });
}

export function getMyExchange(
  exchangeId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<ExchangeOrderData> {
  return request<ExchangeOrderData>(`/api/v1/exchanges/${exchangeId}`, {
    accessToken,
    signal,
  });
}

export function cancelExchange(
  exchangeId: number,
  reason: string | undefined,
  accessToken: string,
): Promise<void> {
  return request<void>(`/api/v1/exchanges/${exchangeId}/cancel`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ reason }),
  });
}

// ---- Admin ----

export function getExchangesForAdmin(
  accessToken: string,
  status?: ExchangeStatus,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<SpringPage<ExchangeOrderData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) query.set('status', status);
  return request<SpringPage<ExchangeOrderData>>(`/api/v1/admin/exchanges?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function prepareExchange(exchangeId: number, accessToken: string): Promise<ExchangeOrderData> {
  return request<ExchangeOrderData>(`/api/v1/admin/exchanges/${exchangeId}/prepare`, {
    method: 'PATCH',
    accessToken,
  });
}

export function shipExchange(exchangeId: number, accessToken: string): Promise<ExchangeOrderData> {
  return request<ExchangeOrderData>(`/api/v1/admin/exchanges/${exchangeId}/ship`, {
    method: 'PATCH',
    accessToken,
  });
}

export function deliverExchange(exchangeId: number, accessToken: string): Promise<ExchangeOrderData> {
  return request<ExchangeOrderData>(`/api/v1/admin/exchanges/${exchangeId}/deliver`, {
    method: 'PATCH',
    accessToken,
  });
}

export function adminCancelExchange(
  exchangeId: number,
  reason: string | undefined,
  accessToken: string,
): Promise<ExchangeOrderData> {
  return request<ExchangeOrderData>(`/api/v1/admin/exchanges/${exchangeId}/cancel`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ reason }),
  });
}
