import { request } from "@/lib/api";

export type PaymentStatus =
  "PENDING" | "COMPLETED" | "FAILED" | "REFUNDED";

export interface PaymentData {
  id: number;
  userId: number;
  cashAmount: number;
  pointAmount: number;
  status: PaymentStatus;
  provider: "TOSS";
  providerOrderId: string;
  providerPaymentKey: string | null;
  approvedAt: string | null;
  createdAt: string;
  message: string | null;
}

export type PaymentRefundStatus = "REQUESTED" | "COMPLETED" | "FAILED";

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

export function requestCharge(
  accessToken: string,
  pointAmount: number,
  idempotencyKey: string,
): Promise<PaymentData> {
  return request<PaymentData>("/api/v1/payments/charge", {
    method: "POST",
    accessToken,
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ pointAmount }),
  });
}

export function reportPaymentFailure(
  accessToken: string,
  providerOrderId: string,
  code: string,
  idempotencyKey: string,
): Promise<PaymentData> {
  return request<PaymentData>("/api/v1/payments/fail", {
    method: "POST",
    accessToken,
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ providerOrderId, code }),
  });
}

interface ConfirmPaymentInput {
  providerOrderId: string;
  paymentKey: string;
  amount: number;
}

export function confirmPayment(
  accessToken: string,
  payload: ConfirmPaymentInput,
  idempotencyKey: string,
): Promise<PaymentData> {
  return request<PaymentData>("/api/v1/payments/confirm", {
    method: "POST",
    accessToken,
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(payload),
  });
}

export function getPaymentHistory(
  accessToken: string,
  signal?: AbortSignal,
): Promise<PaymentHistory[]> {
  return request<PaymentHistory[]>("/api/v1/payments", {
    accessToken,
    signal,
  });
}

export function refundPayment(
  accessToken: string,
  paymentId: number,
  reason: string,
  idempotencyKey: string,
): Promise<PaymentRefundData> {
  return request<PaymentRefundData>(`/api/v1/payments/${paymentId}/refund`, {
    method: "POST",
    accessToken,
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ reason }),
  });
}
