import { request } from '@/lib/api';

export interface CartItemData {
  id: number;
  userId: number;
  productId: number;
  productName: string;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
  availableStock: number;
  soldOut: boolean;
  stockShortage: boolean;
}

export interface CartData {
  items: CartItemData[];
  expectedTotal: number;
  walletBalance: number;
}

export function getCart(accessToken?: string | null): Promise<CartData> {
  return request<CartData>('/api/v1/order/cart', { accessToken });
}

export function addCartItem(
  productId: number,
  quantity: number,
  accessToken?: string | null,
): Promise<CartItemData> {
  return request<CartItemData>('/api/v1/order/cart/items', {
    method: 'POST',
    accessToken,
    body: JSON.stringify({ productId, quantity }),
  });
}

export function updateCartItemQuantity(
  cartItemId: number,
  quantity: number,
  accessToken?: string | null,
): Promise<CartItemData> {
  return request<CartItemData>(`/api/v1/order/cart/items/${cartItemId}`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ quantity }),
  });
}

export function deleteCartItem(cartItemId: number, accessToken?: string | null): Promise<void> {
  return request<void>(`/api/v1/order/cart/items/${cartItemId}`, {
    method: 'DELETE',
    accessToken,
  });
}

export function deleteCartItems(cartItemIds: number[], accessToken?: string | null): Promise<void> {
  const query = new URLSearchParams();
  cartItemIds.forEach((id) => query.append('ids', String(id)));
  return request<void>(`/api/v1/order/cart/items?${query.toString()}`, {
    method: 'DELETE',
    accessToken,
  });
}

export type OrderStatus = 'PAID' | 'CANCELLED' | 'PURCHASE_CONFIRMED';
export type DeliveryStatus = 'PREPARING' | 'SHIPPING' | 'DELIVERED';
export type ConfirmedBy = 'USER' | 'SYSTEM';
export type CancelledBy = 'USER' | 'ADMIN';

export interface OrderData {
  id: number;
  userId: number;
  totalPoint: number;
  usedFreePoint: number;
  usedPaidPoint: number;
  status: OrderStatus;
  deliveryStatus: DeliveryStatus;
  receiverName: string;
  receiverPhone: string;
  zipCode: string | null;
  address: string;
  addressDetail: string | null;
  orderedAt: string;
  deliveredAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  cancelledBy: CancelledBy | null;
  confirmedAt: string | null;
  confirmedBy: ConfirmedBy | null;
  cancellable: boolean;
  confirmable: boolean;
}

export interface OrderItemData {
  id: number;
  orderId: number;
  productId: number;
  productName: string;
  imageUrl: string | null;
  quantity: number;
  unitPoint: number;
}

export interface OrderDetailData {
  order: OrderData;
  items: OrderItemData[];
}

export interface OrderPage {
  content: OrderData[];
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
}

export interface OrderDetailPage {
  content: OrderDetailData[];
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
}

export interface OrderCreatePayload {
  cartItemIds: number[];
  requestedFreePoint: number;
  receiverName: string;
  receiverPhone: string;
  zipCode: string;
  address: string;
  addressDetail?: string;
}

export function createOrder(
  payload: OrderCreatePayload,
  idempotencyKey: string,
  accessToken?: string | null,
): Promise<OrderDetailData> {
  return request<OrderDetailData>('/api/v1/order', {
    method: 'POST',
    accessToken,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload),
  });
}

export function getOrders(accessToken?: string | null, page = 0, size = 20): Promise<OrderPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request<OrderPage>(`/api/v1/order?${query.toString()}`, { accessToken });
}

export function getOrder(orderId: number, accessToken?: string | null): Promise<OrderDetailData> {
  return request<OrderDetailData>(`/api/v1/order/${orderId}`, { accessToken });
}

export function cancelOrder(orderId: number, accessToken?: string | null): Promise<void> {
  return request<void>(`/api/v1/order/${orderId}/cancel`, {
    method: 'POST',
    accessToken,
  });
}

export function confirmOrder(orderId: number, accessToken?: string | null): Promise<void> {
  return request<void>(`/api/v1/order/${orderId}/confirm`, {
    method: 'POST',
    accessToken,
  });
}

// ---- Admin ----

export function getOrdersForAdmin(
  accessToken: string,
  filters?: {
    status?: OrderStatus;
    deliveryStatus?: DeliveryStatus;
    userId?: number;
    from?: string;
    to?: string;
  },
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<OrderDetailPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters?.status) query.set('status', filters.status);
  if (filters?.deliveryStatus) query.set('deliveryStatus', filters.deliveryStatus);
  if (filters?.userId) query.set('userId', String(filters.userId));
  if (filters?.from) query.set('from', filters.from);
  if (filters?.to) query.set('to', filters.to);
  return request<OrderDetailPage>(`/api/v1/admin/order?${query.toString()}`, { accessToken, signal });
}

export function getOrderForAdmin(
  orderId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<OrderDetailData> {
  return request<OrderDetailData>(`/api/v1/admin/order/${orderId}`, { accessToken, signal });
}

export function shipOrderForAdmin(orderId: number, accessToken: string): Promise<OrderData> {
  return request<OrderData>(`/api/v1/admin/order/${orderId}/ship`, {
    method: 'PATCH',
    accessToken,
  });
}

export function deliverOrderForAdmin(orderId: number, accessToken: string): Promise<OrderData> {
  return request<OrderData>(`/api/v1/admin/order/${orderId}/deliver`, {
    method: 'PATCH',
    accessToken,
  });
}

export function cancelOrderForAdmin(
  orderId: number,
  reason: string | undefined,
  accessToken: string,
): Promise<OrderData> {
  return request<OrderData>(`/api/v1/admin/order/${orderId}/cancel`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ reason }),
  });
}
