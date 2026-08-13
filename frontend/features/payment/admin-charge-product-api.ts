import { request } from "@/lib/api";
import { ChargeProduct } from "@/features/payment/api";

export interface AdminChargeProductInput {
  name: string;
  price: number;
  pointAmount: number;
  isActive: boolean;
}

export interface AdminChargeProductUpdateInput extends AdminChargeProductInput {
  version: number;
}

export function getAdminChargeProducts(
  accessToken: string,
  signal?: AbortSignal,
): Promise<ChargeProduct[]> {
  return request<ChargeProduct[]>("/api/v1/admin/payments/products", {
    accessToken,
    signal,
  });
}

export function createAdminChargeProduct(
  accessToken: string,
  idempotencyKey: string,
  payload: AdminChargeProductInput,
): Promise<ChargeProduct> {
  return request<ChargeProduct>("/api/v1/admin/payments/products", {
    method: "POST",
    accessToken,
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(payload),
  });
}

export function updateAdminChargeProduct(
  accessToken: string,
  productId: number,
  payload: AdminChargeProductUpdateInput,
): Promise<ChargeProduct> {
  return request<ChargeProduct>(
    `/api/v1/admin/payments/products/${productId}`,
    {
      method: "PATCH",
      accessToken,
      body: JSON.stringify(payload),
    },
  );
}
