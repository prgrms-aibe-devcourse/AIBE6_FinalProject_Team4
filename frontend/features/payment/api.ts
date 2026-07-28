import { request } from '@/lib/api';

export interface ChargeProduct {
  id: number;
  name: string;
  price: number;
  pointAmount: number;
  isActive: boolean;
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
