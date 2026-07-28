import { request } from '@/lib/api';

export interface ChargeProduct {
  id: number;
  name: string;
  price: number;
  pointAmount: number;
  isActive: boolean;
}

export type PaymentScenario = 'SUCCESS' | 'FAILURE' | 'CANCEL';
export type PaymentStatus =
  | 'PENDING'
  | 'PAID'
  | 'FAILED'
  | 'CANCELED'
  | 'REFUNDED'
  | 'PARTIAL_REFUNDED';

export interface PaymentData {
  id: number;
  userId: number;
  chargeProductId: number;
  chargeProductName: string;
  cashAmount: number;
  pointAmount: number;
  status: PaymentStatus;
  provider: 'MOCK' | 'TOSS';
  providerOrderId: string;
  providerPaymentKey: string | null;
  approvedAt: string | null;
  createdAt: string;
  message: string | null;
}

export type PaymentRefundStatus = 'REQUESTED' | 'COMPLETED' | 'FAILED';

export interface PaymentRefundData {
  id: number;
  paymentId: number;
  cashAmount: number;
  pointAmount: number;
  status: PaymentRefundStatus;
  reason: string | null;
  refundKey: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface PaymentHistory {
  payment: PaymentData;
  refunds: PaymentRefundData[];
}

export function getChargeProducts(
  accessToken: string,
  signal?: AbortSignal,
): Promise<ChargeProduct[]> {
  return request<ChargeProduct[]>('/api/v1/payments/products', {
    accessToken,
    signal,
  });
}

export function requestCharge(
  accessToken: string,
  chargeProductId: number,
  idempotencyKey: string,
): Promise<PaymentData> {
  return request<PaymentData>('/api/v1/payments/charge', {
    method: 'POST',
    accessToken,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ chargeProductId }),
  });
}

interface ConfirmPaymentInput {
  providerOrderId: string;
  paymentKey: string;
  amount: number;
  scenario: PaymentScenario;
}

export function confirmPayment(
  accessToken: string,
  payload: ConfirmPaymentInput,
  idempotencyKey: string,
): Promise<PaymentData> {
  return request<PaymentData>('/api/v1/payments/confirm', {
    method: 'POST',
    accessToken,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload),
  });
}

export function getPaymentHistory(accessToken: string, signal?: AbortSignal): Promise<PaymentHistory[]> {
  return request<PaymentHistory[]>('/api/v1/payments', {
    accessToken,
    signal,
  });
}
