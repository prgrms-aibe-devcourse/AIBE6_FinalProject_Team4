import { request } from '@/lib/api';

export type CardStatus = 'ON_SALE' | 'HIDDEN';

export interface CardData {
  id: number;
  name: string;
  pointPrice: number;
  exchangeProductId: number;
  exchangeProductName: string;
  exchangeProductDescription: string | null;
  exchangeProductImageUrl: string | null;
  exchangeProductStock: number;
  requiredCountForExchange: number;
  description: string | null;
  imageUrl: string | null;
  status: CardStatus;
  createdAt: string;
  updatedAt: string;
  ownedCount: number | null;
}

export interface CardPurchaseData {
  purchaseId: number;
  cardId: number;
  cardName: string;
  unitPoint: number;
  quantity: number;
  usedPoint: number;
  usedFreePoint: number;
  usedPaidPoint: number;
  ownedCount: number;
  remainingBalance: number;
  purchasedAt: string;
}

export function getCards(
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<CardData[]> {
  return request<CardData[]>('/api/v1/card', {
    accessToken,
    signal,
  });
}

export function getCard(
  cardId: number,
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<CardData> {
  return request<CardData>(`/api/v1/card/${cardId}`, {
    accessToken,
    signal,
  });
}

export function getMyCards(
  accessToken: string,
  signal?: AbortSignal,
): Promise<CardData[]> {
  return request<CardData[]>('/api/v1/card/me', {
    accessToken,
    signal,
  });
}

export function purchaseCard(
  cardId: number,
  quantity: number,
  accessToken: string,
  idempotencyKey: string,
): Promise<CardPurchaseData> {
  return request<CardPurchaseData>('/api/v1/card/purchase', {
    method: 'POST',
    accessToken,
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify({ cardId, quantity }),
  });
}
